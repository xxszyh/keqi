"""Run production Store/Collaboration/Notifications with anonymous SQLite fixtures.

The Android API harness substitutes SQLite JDBC and alarm/notification services;
it does not assert device-specific WebView, SQLite or lock-screen behavior.
"""
from pathlib import Path
import hashlib
import json
import os
import subprocess
from urllib.request import urlopen

root = Path(__file__).resolve().parent
tools = json.loads((root / 'tools.local.json').read_text(encoding='utf-8'))
out = root / 'build/migration-tests'
out.mkdir(parents=True, exist_ok=True)

# Reuse pinned JDBC dependencies without depending on desktop compiled classes.
libs = [root / 'lib/json-tests-only.jar', root / 'lib/jsoup.jar']
for dependency in json.loads((root.parent / 'desktop/lib/manifest.json').read_text(encoding='utf-8')):
    if dependency['file'] not in ('sqlite-jdbc.jar', 'slf4j-api.jar', 'slf4j-nop.jar'):
        continue
    path = root.parent / 'desktop/lib' / dependency['file']
    if not path.exists():
        with urlopen(dependency['url'], timeout=60) as response:
            content = response.read()
        assert hashlib.sha256(content).hexdigest() == dependency['sha256']
        path.write_bytes(content)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == dependency['sha256'], path.name
    libs.append(path)
classpath = os.pathsep.join(map(str, libs))
pure = ['Core', 'Collectors', 'ChaoxingCourses', 'ChaoxingExams', 'ChaoxingReadPolicy',
        'ChaoxingRequests', 'ChaoxingTaskReader', 'CourseSelection', 'ConnectionPolicy', 'LoginNavigation',
        'Store', 'Collaboration', 'Notifications']
sources = list((root / 'tests/store-harness').rglob('*.java'))
sources += [root / 'src/com/keqi/reminder' / (name + '.java') for name in pure]
sources += list((root.parent / 'shared/core-java').rglob('*.java'))
sources += [root.parent / 'desktop/jvm/com/keqi/reminder/Http.java']
subprocess.run([tools['javac'], '-encoding', 'UTF-8', '--release', '8', '-cp', classpath,
                '-d', str(out), *map(str, sources)], check=True)
for main, arguments in [('MigrationRepro', [str(out / 'snapshot.json')]), ('NotificationScaleRepro', [])]:
    subprocess.run([tools['java'], '-cp', str(out) + os.pathsep + classpath,
                    'com.keqi.reminder.' + main, *arguments], check=True)
