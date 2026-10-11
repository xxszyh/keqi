from pathlib import Path
import json, os, subprocess
root=Path(__file__).resolve().parent.parent
tools=json.loads((root/'mobile/tools.local.json').read_text(encoding='utf-8'))
out=root/'desktop/build/tests'
out.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join(map(str,[root/'desktop/build/core/classes',*list((root/'desktop/lib').glob('*.jar'))]))
sources=list((root/'shared/tests').rglob('*.java'))
subprocess.run([tools['javac'],'-encoding','UTF-8','-cp',classpath,'-d',str(out),*map(str,sources)],check=True)
subprocess.run([tools['java'],'-cp',str(out)+os.pathsep+classpath,'com.keqi.reminder.WorkspaceTests'],check=True)

subprocess.run([tools['java'],'-cp',str(out)+os.pathsep+classpath,'com.keqi.reminder.LanHostTests'],check=True)
