"""Run the production pending-response and browser-continuation policy in the JVM."""
from pathlib import Path
import json, subprocess, os, sys
sys.stdout.reconfigure(encoding='utf-8')
r=Path(__file__).resolve().parent
t=json.loads((r/'tools.local.json').read_text(encoding='utf-8'))
out=r/'build/read-tests';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join([str(r/'lib/jsoup.jar'),str(r/'lib/json-tests-only.jar'),t['android_jar']])
names=['ConnectionPolicy.java','LoginNavigation.java','Core.java','Collectors.java','ChaoxingCourses.java','CourseSelection.java','ChaoxingExams.java','ChaoxingTaskReader.java','ChaoxingReadPolicy.java','ChaoxingRequests.java','Http.java']
sources=[r/'src/com/keqi/reminder'/name for name in names]+[r/'tests/ChaoxingReadTests.java',r/'tests/ChaoxingReadProbe.java',r/'tests/ChaoxingExamTests.java']
subprocess.run([t['javac'],'-encoding','UTF-8','-cp',cp,'-d',str(out),*map(str,sources)],check=True)
subprocess.run([t['java'],'-Dfile.encoding=UTF-8','-cp',os.pathsep.join([str(out),cp]),'com.keqi.reminder.ChaoxingReadTests'],check=True)
subprocess.run([t['java'],'-Dfile.encoding=UTF-8','-cp',os.pathsep.join([str(out),cp]),'com.keqi.reminder.ChaoxingExamTests'],check=True)
