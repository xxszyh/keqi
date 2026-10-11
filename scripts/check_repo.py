"""Validate project docs, vendored dependencies and files in the Git index."""
from pathlib import Path
from urllib.parse import unquote, urlsplit
import hashlib
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = ['LICENSE', 'README.md', 'CONTRIBUTING.md', 'SECURITY.md', 'CODE_OF_CONDUCT.md',
            'CHANGELOG.md', 'docs/BUILDING.md', 'docs/TESTING.md', 'docs/ARCHITECTURE.md',
            'docs/USAGE.md', 'docs/RELEASING.md', 'docs/ci.yml',
            '.github/ISSUE_TEMPLATE/bug_report.yml', '.github/ISSUE_TEMPLATE/feature_request.yml',
            '.github/PULL_REQUEST_TEMPLATE.md']


def main():
    for relative in REQUIRED:
        assert (ROOT / relative).is_file(), 'Missing project file: ' + relative
    license_text = (ROOT / 'LICENSE').read_text(encoding='utf-8')
    assert license_text == (ROOT / 'mobile/assets/licenses/keqi.txt').read_text(encoding='utf-8'), 'Bundled project license differs'
    for lib in json.loads((ROOT / 'mobile/lib/manifest.json').read_text(encoding='utf-8')):
        assert hashlib.sha256((ROOT / 'mobile/lib' / lib['file']).read_bytes()).hexdigest() == lib['sha256'], lib['file']
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')
    for relative in filter(None, tracked):
        path = Path(relative)
        assert path.suffix.lower() not in ['.apk', '.zip', '.keystore', '.jks', '.p12', '.pfx', '.db', '.sqlite'], 'Private/generated file in Git: ' + relative
        assert relative not in ['mobile/tools.local.json', 'mobile/build-info.json', '.env'], relative
        assert not any(part in ['.venv', 'build', 'debug', '.codex-remote-attachments'] for part in path.parts), relative
    docs = [ROOT / name for name in ['README.md', 'CONTRIBUTING.md', 'SECURITY.md', 'CODE_OF_CONDUCT.md', 'CHANGELOG.md', 'THIRD_PARTY.md']]
    docs += list((ROOT / 'docs').glob('*.md'))
    checked = 0
    for doc in docs:
        for target in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)', doc.read_text(encoding='utf-8')):
            target = target.split(' "', 1)[0].strip('<>')
            if urlsplit(target).scheme or target.startswith('#'):
                continue
            target = unquote(target.split('#', 1)[0])
            resolved = (doc.parent / target).resolve()
            assert resolved.is_relative_to(ROOT), 'Link escapes repository: ' + doc.name
            assert resolved.exists(), 'Broken link in ' + doc.name + ': ' + target
            checked += 1
    print('Project files, ' + str(checked) + ' local document links, dependency hashes and Git exclusions passed.')


if __name__ == '__main__':
    main()
