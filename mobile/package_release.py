"""Verify the current APK, matching source and previous signer, then write versioned downloads."""
from pathlib import Path
import argparse, hashlib, json, re, shutil, subprocess, tempfile, zipfile, sys
import xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--previous-apk',type=Path,help='Optional previous official APK; verify upgrade signer')
parser.add_argument('--build-dir',type=Path,help='Directory containing APK and build-info.json')
args=parser.parse_args()
build_dir=args.build_dir.resolve() if args.build_dir else root
manifest=ET.parse(root/'AndroidManifest.xml').getroot().attrib
version=manifest['{http://schemas.android.com/apk/res/android}versionName']
version_code=int(manifest['{http://schemas.android.com/apk/res/android}versionCode'])
tools=json.loads((root/'tools.local.json').read_text(encoding='utf-8'))
info=json.loads((build_dir/'build-info.json').read_text(encoding='utf-8'))
apk=build_dir/'课期-安卓.apk'
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
assert info['version']==version and info['sha256']==digest
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
previous=args.previous_apk.resolve() if args.previous_apk else None
if previous is not None and not previous.is_file():parser.error('Previous APK does not exist')
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
    if previous is not None:
        shutil.copy2(previous,old)
        assert current_signer==certificate(old),'Previous release signature differs'
        same_signer=True
    run([bt/'zipalign.exe','-c','-p','4',current])
    metadata=run([bt/'aapt2.exe','dump','badging',current])
    assert ("versionCode='"+str(version_code)+"'") in metadata and ("versionName='"+version+"'") in metadata
    assert ("sdkVersion:'26'" in metadata or "minSdkVersion:'26'" in metadata) and "targetSdkVersion:'35'" in metadata
versioned=build_dir/('课期-安卓-v'+version+'.apk')
shutil.copy2(apk,versioned)
archive=build_dir/'课期-安装包.zip'
bundled=[versioned,root/'安装说明.txt',root.parent/'LICENSE',root.parent/'THIRD_PARTY.md']
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for path in bundled:z.write(path,path.name)
with zipfile.ZipFile(archive) as z:
    assert set(z.namelist())=={path.name for path in bundled}
    assert hashlib.sha256(z.read(versioned.name)).hexdigest()==digest
    assert version in z.read('安装说明.txt').decode('utf-8')
    assert z.read('LICENSE')== (root.parent/'LICENSE').read_bytes()
report={'version':version,'version_code':version_code,'bytes':apk.stat().st_size,'sha256':digest,
        'previous_apk_checked':previous is not None,'same_signer_as_previous':same_signer,'v2_v3_verified':True,'zip_aligned':True,
        'compiled_source_matches_workspace':True,'bundled_assets_match_source':True,
        'exam_reader_modules_present':True,'zip_contents_verified':True,'min_sdk':26,'target_sdk':35,
        'phone_real_exam_coverage_verified':False,'phone_real_response_body_captured':False,
        'test_records':'验证记录.md'}
(root/'build').mkdir(exist_ok=True)
(root/'build/release-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
