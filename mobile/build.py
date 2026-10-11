"""Portable Android build. No system PATH changes or Android Studio required."""
from pathlib import Path
import json
import subprocess
import zipfile
import shutil
import tempfile
import sys
import hashlib
import xml.etree.ElementTree as ET
import argparse
import os

sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output-dir', type=Path, help='APK / build-info output directory; defaults to mobile/')
parser.add_argument('--debug', action='store_true', help='Enable app-only debugging for connected-device diagnostics')
args = parser.parse_args()
OUTPUT = args.output_dir.resolve() if args.output_dir else ROOT
OUTPUT.mkdir(parents=True, exist_ok=True)
TOOLS = json.loads((ROOT / 'tools.local.json').read_text(encoding='utf-8'))
for lib in json.loads((ROOT / 'lib/manifest.json').read_text(encoding='utf-8')):
    if hashlib.sha256((ROOT/'lib'/lib['file']).read_bytes()).hexdigest()!=lib['sha256']:
        raise SystemExit('Dependency checksum mismatch: '+lib['file'])
# Android's native Windows tools misread Unicode paths. Stage the build alongside
# the portable toolchain, whose path must use ASCII, then copy the APK back.
WORK = Path(TOOLS['build_tools']).parents[1] / 'work'
WORK.mkdir(parents=True, exist_ok=True)
if not str(WORK).isascii():
    raise SystemExit('Configure the toolchain in an ASCII-only path before building.')
STAGE = Path(tempfile.mkdtemp(prefix='keqi-', dir=WORK))
for name in ('src', 'res', 'assets', 'lib'):
    shutil.copytree(ROOT / name, STAGE / name)
shutil.copytree(ROOT.parent / 'shared/core-java', STAGE / 'shared')
shutil.copy2(ROOT / 'AndroidManifest.xml', STAGE / 'AndroidManifest.xml')
if args.debug:
    manifest_tree = ET.parse(STAGE / 'AndroidManifest.xml')
    manifest_tree.getroot().find('application').set('{http://schemas.android.com/apk/res/android}debuggable', 'true')
    manifest_tree.write(STAGE / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
BUILD = STAGE / 'build'
for directory in ('classes', 'generated', 'dex'):
    (BUILD / directory).mkdir(parents=True, exist_ok=True)
BT = Path(TOOLS['build_tools'])

def run(args):
    result = subprocess.run([str(a) for a in args], cwd=STAGE, text=True, encoding='utf-8', errors='replace', capture_output=True)
    if result.stdout.strip():
        print(result.stdout.strip(), flush=True)
    if result.stderr.strip():
        print(result.stderr.strip(), flush=True)
    if result.returncode:
        raise SystemExit(result.returncode)

run([BT/'aapt2.exe', 'compile', '--dir', STAGE/'res', '-o', BUILD/'resources.zip'])
run([BT/'aapt2.exe', 'link', '-o', BUILD/'resources.apk', '--manifest', STAGE/'AndroidManifest.xml',
     '-I', TOOLS['android_jar'], '--min-sdk-version', '26', '--target-sdk-version', '35',
     '-A', STAGE/'assets', '--java', BUILD/'generated', BUILD/'resources.zip'])
sources = list((STAGE/'shared').rglob('*.java')) + list((STAGE/'src').rglob('*.java')) + list((BUILD/'generated').rglob('*.java'))
run([TOOLS['javac'], '-J-Dfile.encoding=UTF-8', '-encoding', 'UTF-8', '--release', '8',
     '-cp', str(TOOLS['android_jar'])+';'+str(STAGE/'lib/jsoup.jar')+';'+str(STAGE/'lib/zxing-core.jar'),
     '-d', BUILD/'classes', *sources])
with zipfile.ZipFile(BUILD/'classes.jar', 'w') as zf:
    for path in (BUILD/'classes').rglob('*.class'):
        zf.write(path, path.relative_to(BUILD/'classes').as_posix())
run([TOOLS['java'], '-cp', BT/'lib/d8.jar', 'com.android.tools.r8.D8',
     '--lib', TOOLS['android_jar'], '--min-api', '26', '--output', BUILD/'dex',
     BUILD/'classes.jar', STAGE/'lib/jsoup.jar', STAGE/'lib/zxing-core.jar'])
with zipfile.ZipFile(BUILD/'resources.apk') as source, zipfile.ZipFile(BUILD/'unsigned.apk', 'w') as dest:
    for info in source.infolist():
        dest.writestr(info, source.read(info.filename))
    for dex in (BUILD/'dex').glob('*.dex'):
        dest.write(dex, dex.name, compress_type=zipfile.ZIP_STORED)
run([BT/'zipalign.exe', '-f', '4', BUILD/'unsigned.apk', BUILD/'aligned.apk'])
custom_key = os.environ.get('KEQI_KEYSTORE')
legacy_key = Path(TOOLS['java']).parent.parent / 'keqi-debug.keystore'
key = Path(custom_key).resolve() if custom_key else legacy_key if legacy_key.exists() else WORK / 'keys/keqi-debug.keystore'
alias = os.environ.get('KEQI_KEY_ALIAS', 'keqi') if custom_key else 'keqi-debug'
if custom_key and (not key.is_file() or not os.environ.get('KEQI_KEYSTORE_PASSWORD')):
    raise SystemExit('Custom signing requires an existing KEQI_KEYSTORE and KEQI_KEYSTORE_PASSWORD.')
if not str(key).isascii():
    raise SystemExit('Use an ASCII-only signing-key path.')
if not key.exists():
    key.parent.mkdir(parents=True, exist_ok=True)
    run([TOOLS['keytool'], '-genkeypair', '-keystore', key, '-storepass', 'android',
         '-keypass', 'android', '-alias', 'keqi-debug', '-keyalg', 'RSA', '-keysize', '2048',
         '-validity', '3650', '-dname', 'CN=Keqi Personal Build'])
output = BUILD/'keqi.apk'
run([TOOLS['java'], '-jar', BT/'lib/apksigner.jar', 'sign', '--ks', key,
     '--ks-key-alias', alias, '--ks-pass', 'env:KEQI_KEYSTORE_PASSWORD' if custom_key else 'pass:android',
     '--key-pass', 'env:KEQI_KEY_PASSWORD' if custom_key and os.environ.get('KEQI_KEY_PASSWORD') else 'env:KEQI_KEYSTORE_PASSWORD' if custom_key else 'pass:android',
     '--out', output, BUILD/'aligned.apk'])
run([TOOLS['java'], '-jar', BT/'lib/apksigner.jar', 'verify', '--verbose', output])
run([BT/'zipalign.exe', '-c', '4', output])
with zipfile.ZipFile(output) as apk:
    names = apk.namelist()
    assert 'classes.dex' in names and 'AndroidManifest.xml' in names
    assert 'assets/licenses.html' in names
    manifest = apk.read('AndroidManifest.xml')
    assert '课期'.encode('utf-8') in manifest or '课期'.encode('utf-16le') in manifest
    assert not any(name.endswith('.so') or name.endswith('.jar') for name in names)
metadata = subprocess.run([str(BT/'aapt2.exe'), 'dump', 'badging', str(output)], capture_output=True, check=True).stdout.decode('ascii',errors='ignore')
assert "minSdkVersion:'26'" in metadata or "sdkVersion:'26'" in metadata
assert "targetSdkVersion:'35'" in metadata and "name='com.keqi.reminder.MainActivity'" in metadata
assert 'android.permission.POST_NOTIFICATIONS' in metadata and 'android.permission.RECEIVE_BOOT_COMPLETED' in metadata
shutil.copy2(output, OUTPUT/'课期-安卓.apk')
output = OUTPUT/'课期-安卓.apk'
version=ET.parse(ROOT/'AndroidManifest.xml').getroot().attrib['{http://schemas.android.com/apk/res/android}versionName']
report = {'version':version, 'min_sdk':26, 'target_sdk':35, 'package':'com.keqi.reminder',
          'debug':args.debug,
          'apk':output.name, 'bytes':output.stat().st_size,
          'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
          'checks':['APK signature verification', 'ZIP alignment', 'DEX / manifest / bundled license assets / Chinese app label', 'No native ABI library or test JAR bundled', 'aapt2 SDK / launcher / permission metadata']}
source_hashes={}
for folder in ('src','res','assets'):
    for file in (STAGE/folder).rglob('*'):
        if file.is_file():
            relative=file.relative_to(STAGE).as_posix()
            digest=hashlib.sha256(file.read_bytes()).hexdigest()
            if digest!=hashlib.sha256((ROOT/relative).read_bytes()).hexdigest():
                raise SystemExit('Source changed during build: '+relative)
            source_hashes[relative]=digest
for file in (STAGE/'shared').rglob('*.java'):
    relative='../shared/core-java/'+file.relative_to(STAGE/'shared').as_posix()
    digest=hashlib.sha256(file.read_bytes()).hexdigest()
    if digest!=hashlib.sha256((ROOT/relative).read_bytes()).hexdigest(): raise SystemExit('Shared source changed during build: '+relative)
    source_hashes[relative]=digest
source_hashes['AndroidManifest.xml']=hashlib.sha256((STAGE/'AndroidManifest.xml').read_bytes()).hexdigest()
report['source_sha256']=source_hashes
(OUTPUT/'build-info.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('APK ready:', output, 'bytes:', output.stat().st_size, flush=True)
