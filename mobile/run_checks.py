"""Run isolated regression checks sequentially; no real account or live platform needed."""
from pathlib import Path
import argparse
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
DOMAIN = ['test.py', 'connection_test.py', 'chaoxing_read_test.py']
BROWSER = ['ui_test.py', 'exam_ui_test.py', 'list_ui_test.py', 'course_management_ui_test.py',
           'course_visibility_test.py', 'chaoxing_course_selection_test.py', 'course_name_test.py',
           'chaoxing_connection_test.py', 'chaoxing_202_browser_test.py']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--domain-only', action='store_true', help='Run JVM checks without Edge / Playwright')
    args = parser.parse_args()
    checks = DOMAIN if args.domain_only else DOMAIN + BROWSER
    for name in checks:
        print('Running ' + name, flush=True)
        subprocess.run([sys.executable, str(ROOT / name)], cwd=ROOT.parent, check=True)
    print('All ' + str(len(checks)) + ' check scripts passed.', flush=True)


if __name__ == '__main__':
    main()
