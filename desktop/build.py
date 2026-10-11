"""Build the shared Java core and a private Java runtime for the Windows desktop app."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--runtime',action='store_true')
args=parser.parse_args()
tools=json.loads((REPO/'mobile/tools.local.json').read_text(encoding='utf-8'))
for dependency in json.loads((ROOT/'lib/manifest.json').read_text(encoding='utf-8')):
    path=ROOT/'lib'/dependency['file']
    if not path.exists():
        with urlopen(dependency['url'],timeout=60) as response: content=response.read()
        if hashlib.sha256(content).hexdigest()!=dependency['sha256']: raise SystemExit('Dependency checksum mismatch')
        path.write_bytes(content)
    if hashlib.sha256(path.read_bytes()).hexdigest()!=dependency['sha256']: raise SystemExit('Dependency checksum mismatch: '+path.name)
out=ROOT/'build/core'
out.mkdir(parents=True,exist_ok=True)
pure=['Core','Collectors','ChaoxingCourses','ChaoxingExams','ChaoxingReadPolicy','ChaoxingRequests','ChaoxingTaskReader','CourseSelection','ConnectionPolicy','LoginNavigation']
sources=[REPO/'mobile/src/com/keqi/reminder'/(name+'.java') for name in pure]
sources+=list((REPO/'shared/core-java').rglob('*.java'))+list((ROOT/'jvm').rglob('*.java'))
libs=list((ROOT/'lib').glob('*.jar'))+[REPO/'mobile/lib/jsoup.jar']
classpath=os.pathsep.join(map(str,libs))
subprocess.run([tools['javac'],'-encoding','UTF-8','--release','17','-cp',classpath,'-d',str(out/'classes'),*map(str,sources)],check=True)
for library in libs: shutil.copy2(library,out/library.name)
if args.runtime:
    runtime=ROOT/'runtime'
    if not runtime.exists():
        jlink=Path(tools['java']).with_name('jlink.exe')
        subprocess.run([str(jlink),'--add-modules','java.base,java.desktop,java.logging,java.sql,java.naming,jdk.httpserver,jdk.crypto.ec,jdk.unsupported','--strip-debug','--no-header-files','--no-man-pages','--output',str(runtime)],check=True)
    keytool=runtime/'bin/keytool.exe'
    if not keytool.exists(): shutil.copy2(tools['keytool'],keytool)
print('Desktop Java core ready:',out)

ui=ROOT/'build/ui'
shutil.copytree(REPO/'mobile/assets',ui,dirs_exist_ok=True)

shutil.copytree(ROOT/'assets/licenses',ui/'licenses/desktop',dirs_exist_ok=True)
with (ui/'licenses.html').open('a',encoding='utf-8') as page:
    page.write('<h2>桌面运行组件</h2><p>Electron、QR Code、SQLite JDBC、JmDNS、SLF4J 与 JSON-java。完整许可：</p>')
    for file in (ROOT/'assets/licenses').glob('*.txt'): page.write('<p><a href="licenses/desktop/'+file.name+'">'+file.stem+'</a></p>')
