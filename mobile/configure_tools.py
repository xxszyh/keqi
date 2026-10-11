"""Configure an existing Windows JDK / Android SDK without downloading anything."""
from pathlib import Path
import argparse
import json
import os
import sys

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--android-sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--android-jar', help='Explicit platform android.jar (portable SDK layout)')
    parser.add_argument('--build-tools', help='Explicit directory containing aapt2.exe')
    args = parser.parse_args()
    if sys.platform != 'win32':
        parser.error('The current build scripts support Windows x64.')
    if not args.java_home:
        parser.error('Set JAVA_HOME or pass --java-home (JDK 17).')
    if not args.android_sdk and not (args.android_jar and args.build_tools):
        parser.error('Set ANDROID_HOME or pass --android-sdk / explicit SDK paths.')
    jdk = Path(args.java_home).resolve()
    sdk = Path(args.android_sdk).resolve() if args.android_sdk else None
    platform = Path(args.android_jar).resolve() if args.android_jar else sdk / 'platforms/android-35/android.jar'
    build_tools = Path(args.build_tools).resolve() if args.build_tools else sdk / 'build-tools/35.0.0'
    paths = {name:str(jdk / 'bin' / (name + '.exe')) for name in ['java', 'javac', 'keytool']}
    paths.update(android_jar=str(platform), build_tools=str(build_tools))
    required = [Path(paths[k]) for k in ['java', 'javac', 'keytool', 'android_jar']]
    required += [build_tools / name for name in ['aapt2.exe', 'zipalign.exe', 'lib/apksigner.jar', 'lib/d8.jar']]
    for path in required:
        if not path.is_file():
            parser.error('Required tool not found: ' + str(path))
        if not str(path).isascii():
            parser.error('Use ASCII-only JDK / SDK paths: ' + str(path))
    (ROOT / 'tools.local.json').write_text(json.dumps(paths, indent=2), encoding='utf-8')
    print('Configured tools.local.json (local only; ignored by Git).')


if __name__ == '__main__':
    main()
