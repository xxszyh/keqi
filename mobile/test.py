from pathlib import Path
import json
import subprocess
import os
import sys

sys.stdout.reconfigure(encoding='utf-8')

root=Path(__file__).resolve().parent
tools=json.loads((root/'tools.local.json').read_text(encoding='utf-8'))
out=root/'build/tests'
out.mkdir(parents=True,exist_ok=True)
classpath=os.pathsep.join(str(p) for p in [root/'lib/jsoup.jar',root/'lib/json-tests-only.jar'])
sources=[root/'src/com/keqi/reminder/Core.java',root/'src/com/keqi/reminder/Collectors.java',root/'src/com/keqi/reminder/ChaoxingCourses.java',root/'src/com/keqi/reminder/CourseSelection.java',root/'tests/DomainTests.java']
subprocess.run([tools['javac'],'-encoding','UTF-8','-d',str(out),'-cp',classpath,*map(str,sources)],check=True)
subprocess.run([tools['java'],'-cp',os.pathsep.join([str(out),classpath]),'com.keqi.reminder.DomainTests'],check=True)
