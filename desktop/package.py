"""Package Windows desktop; stage legacy installer tools under an ASCII-only path."""
from pathlib import Path
import json, os, shutil, subprocess, tempfile, sys, zipfile, hashlib

sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parent
NODE=shutil.which('node')
if not NODE: raise SystemExit('Install Node.js first.')
subprocess.run([sys.executable,str(ROOT/'build.py'),'--runtime'],check=True)
cli=ROOT/'node_modules/@electron-forge/cli/dist/electron-forge.js'
subprocess.run([NODE,str(cli),'package'],cwd=ROOT,check=True)
packaged=ROOT/'dist/课期-win32-x64'
if not (packaged/'Keqi.exe').exists(): raise SystemExit('Expected desktop executable missing.')
out=ROOT/'dist/downloads';out.mkdir(parents=True,exist_ok=True)
version=json.loads((ROOT/'package.json').read_text(encoding='utf-8'))['version']
with tempfile.TemporaryDirectory(prefix='keqi-installer-') as temporary:
    stage=Path(temporary)
    if not str(stage).isascii(): raise SystemExit('Use an ASCII-only TEMP directory for Windows installer tools.')
    shutil.copytree(packaged,stage/'app')
    shutil.copy2(ROOT/'assets/icon.ico',stage/'icon.ico')
    helper=stage/'make.cjs'
    helper.write_text("""const {createWindowsInstaller}=require(process.argv[2]);
createWindowsInstaller({appDirectory:process.argv[3],outputDirectory:process.argv[4],
  authors:'Keqi contributors',description:'Keqi task manager and local network sync',
  exe:'Keqi.exe',name:'Keqi',version:process.argv[5],setupIcon:process.argv[6],
  setupExe:'Keqi-Setup.exe',noMsi:true}).catch(e=>{console.error(e.message);process.exitCode=1;});
""",encoding='utf-8')
    subprocess.run([NODE,str(helper),str(ROOT/'node_modules/electron-winstaller'),str(stage/'app'),str(stage/'out'),version,str(stage/'icon.ico')],check=True)
    shutil.copy2(stage/'out/Keqi-Setup.exe',out/f'课期-Windows-v{version}-安装.exe')
archive=out/f'课期-Windows-v{version}-便携.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as zipped:
    for file in packaged.rglob('*'):
        if file.is_file(): zipped.write(file,'Keqi/'+file.relative_to(packaged).as_posix())
report={file.name:{'bytes':file.stat().st_size,'sha256':hashlib.sha256(file.read_bytes()).hexdigest()} for file in out.iterdir() if file.suffix in ['.exe','.zip']}
(out/'build-info.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('Desktop downloads:',out)
