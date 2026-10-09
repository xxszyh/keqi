"""Verify the current APK, matching source and previous signer, then write versioned downloads."""
from pathlib import Path
import hashlib, json, re, shutil, subprocess, tempfile, zipfile, sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
tools=json.loads((root/'tools.local.json').read_text(encoding='utf-8'))
info=json.loads((root/'build-info.json').read_text(encoding='utf-8'))
apk=root/'课期-安卓.apk'
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
assert info['version']=='0.8.0' and info['sha256']==digest
assert info.get('source_sha256'), 'Rebuild first to capture compiled source fingerprints'
for relative,expected in info['source_sha256'].items():
    assert hashlib.sha256((root/relative).read_bytes()).hexdigest()==expected,relative
with zipfile.ZipFile(apk) as z:
    for path in (root/'assets').rglob('*'):
        if path.is_file():assert z.read(path.relative_to(root).as_posix())==path.read_bytes(),str(path)
    dex=b''.join(z.read(name) for name in z.namelist() if name.endswith('.dex'))
    for module in ['ChaoxingExams','ChaoxingTaskReader','ChaoxingReadPolicy','ChaoxingPageBrowser','Notifications']:
        assert ('Lcom/keqi/reminder/'+module+';').encode() in dex,module
    assert not any(name.endswith('.jar') or name.endswith('.so') for name in z.namelist())
bt=Path(tools['build_tools'])
work=bt.parents[1]/'work'
previous=root/'课期-安卓-v0.7.0.apk'
same_signer=None
with tempfile.TemporaryDirectory(prefix='release-check-',dir=work) as tmp:
    current=Path(tmp)/'current.apk';old=Path(tmp)/'previous.apk'
    shutil.copy2(apk,current)
    def run(args):return subprocess.run(list(map(str,args)),capture_output=True,check=True).stdout.decode('utf-8',errors='replace')
    def certificate(path):
        output=run([tools['java'],'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',path])
        assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in output
        assert 'Verified using v3 scheme (APK Signature Scheme v3): true' in output
        return re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',output).group(1)
    current_signer=certificate(current)
    if previous.exists():
        shutil.copy2(previous,old)
        assert current_signer==certificate(old),'Previous release signature differs'
        same_signer=True
    run([bt/'zipalign.exe','-c','-p','4',current])
    metadata=run([bt/'aapt2.exe','dump','badging',current])
    assert "versionCode='8'" in metadata and "versionName='0.8.0'" in metadata
    assert ("sdkVersion:'26'" in metadata or "minSdkVersion:'26'" in metadata) and "targetSdkVersion:'35'" in metadata
versioned=root/'课期-安卓-v0.8.0.apk'
shutil.copy2(apk,versioned)
archive=root/'课期-安装包.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for path in [versioned,root/'安装说明.txt']:z.write(path,path.name)
with zipfile.ZipFile(archive) as z:
    assert set(z.namelist())=={versioned.name,'安装说明.txt'}
    assert hashlib.sha256(z.read(versioned.name)).hexdigest()==digest
    assert '0.8.0' in z.read('安装说明.txt').decode('utf-8')
report={'version':'0.8.0','version_code':8,'bytes':apk.stat().st_size,'sha256':digest,
        'same_signer_as_0.7.0':same_signer,'v2_v3_verified':True,'zip_aligned':True,
        'compiled_source_matches_workspace':True,'bundled_assets_match_source':True,
        'exam_reader_modules_present':True,'zip_contents_verified':True,'min_sdk':26,'target_sdk':35,
        'phone_real_exam_coverage_verified':False,'phone_real_response_body_captured':False,
        'test_records':'验证记录.md'}
(root/'build/release-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
