"""Verify the packaged Windows core and UI against this checkout, without launching it."""
from pathlib import Path
import hashlib, json, shutil, subprocess, zipfile

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
packaged=ROOT/'dist/课期-win32-x64'
core=packaged/'resources/core'
record=json.loads((core/'build-info.json').read_text(encoding='utf-8'))
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
for relative,expected in record['source_sha256'].items():
    assert digest(REPO/relative)==expected,relative
for relative,expected in record['compiled_sha256'].items():
    assert digest(core/relative)==expected,relative
assert (packaged/'resources/runtime/bin/java.exe').is_file()
assert (packaged/'resources/runtime/bin/keytool.exe').is_file()
assert list((packaged/'resources/runtime/legal').rglob('LICENSE'))
asar=ROOT/'node_modules/@electron/asar/lib/asar.js'
js="""const a=require(process.argv[1]),fs=require('node:fs'),p=require('node:path');
const archive=process.argv[2],repo=process.argv[3],files=JSON.parse(process.argv[4]);
for(const name of files){const original=fs.readFileSync(p.join(repo,name)),packed=a.extractFile(archive,p.normalize(name.startsWith('desktop/')?name.slice(8):'build/ui/'+name.slice(14)));if(!packed.equals(original))throw Error('Packaged source differs: '+name);}
const paths=a.listPackage(archive).map(name=>name.split(p.sep).join('/'));if(paths.some(p=>p.startsWith('/build/tests/')||p.startsWith('/tests/')))throw Error('Test files included');
"""
files=[p for p in record['source_sha256'] if p.startswith('desktop/host/') or p.startswith('mobile/assets/')]
# licenses.html receives the desktop notices during build.
files.remove('mobile/assets/licenses.html')
subprocess.run([shutil.which('node'),'-e',js,str(asar),str(packaged/'resources/app.asar'),str(REPO),json.dumps(files)],check=True)
version=json.loads((ROOT/'package.json').read_text(encoding='utf-8'))['version']
downloads=ROOT/'dist/downloads'
report=json.loads((downloads/'build-info.json').read_text(encoding='utf-8'))
for name,data in report.items():
    assert digest(downloads/name)==data['sha256'] and (downloads/name).stat().st_size==data['bytes'],name
assert report[f'课期-Windows-v{version}-安装.exe']['bytes']>1000000
with zipfile.ZipFile(downloads/f'课期-Windows-v{version}-便携.zip') as archive:
    assert archive.read('Keqi/resources/core/build-info.json')==(core/'build-info.json').read_bytes()
    assert archive.read('Keqi/resources/app.asar')==(packaged/'resources/app.asar').read_bytes()
print('Packaged Windows sources, JVM classes/dependencies, UI, runtime legal notices and download hashes passed.')
