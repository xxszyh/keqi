"""Fast regression loop for the two connection states visible in the user's video."""
from pathlib import Path
import json, subprocess, os, sys
sys.stdout.reconfigure(encoding='utf-8')
r=Path(__file__).resolve().parent
t=json.loads((r/'tools.local.json').read_text(encoding='utf-8'))
out=r/'build/connection-tests';out.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join([str(r/'lib/jsoup.jar'),str(r/'lib/json-tests-only.jar'),t['android_jar']])
files=[r/'src/com/keqi/reminder'/name for name in ['ConnectionPolicy.java','LoginNavigation.java','Core.java','Collectors.java','ChaoxingCourses.java','Authentication.java','MoocCourses.java','Http.java']]+[r/'tests/ConnectionTests.java',r.parent/'shared/core-java/com/keqi/reminder/MoocCatalog.java']
subprocess.run([t['javac'],'-encoding','UTF-8','-cp',classpath,'-d',str(out),*map(str,files)],check=True)
subprocess.run([t['java'],'-cp',os.pathsep.join([str(out),classpath]),'com.keqi.reminder.ConnectionTests',str(r/'build/public-login-check'),str(r/'build/school-callback')],check=True)
