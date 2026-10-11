from pathlib import Path
import json, os, subprocess
root=Path(__file__).resolve().parent.parent
jdk=os.environ.get('JAVA_HOME')
configured=root/'mobile/tools.local.json'
if jdk: tools={name:str(Path(jdk)/'bin'/(name+'.exe')) for name in ['java','javac']}
elif configured.exists(): tools=json.loads(configured.read_text(encoding='utf-8'))
else: raise SystemExit('Set JAVA_HOME to JDK 17 before running shared checks.')
out=root/'desktop/build/tests'
out.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join(map(str,[root/'desktop/build/core/classes',*list((root/'desktop/build/core').glob('*.jar'))]))
sources=list((root/'shared/tests').rglob('*.java'))
subprocess.run([tools['javac'],'-encoding','UTF-8','-cp',classpath,'-d',str(out),*map(str,sources)],check=True)
subprocess.run([tools['java'],'-cp',str(out)+os.pathsep+classpath,'com.keqi.reminder.WorkspaceTests'],check=True)

subprocess.run([tools['java'],'-cp',str(out)+os.pathsep+classpath,'com.keqi.reminder.LanHostTests'],check=True)
subprocess.run([tools['java'],'-cp',str(out)+os.pathsep+classpath,'com.keqi.reminder.ReviewRegressionTests'],check=True)
