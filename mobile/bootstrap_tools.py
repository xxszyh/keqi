"""Download portable official build tools into the user cache, without installers."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
import requests
import os
import sys

sys.stdout.reconfigure(encoding='utf-8')
ROOT = Path(os.environ.get('KEQI_TOOL_CACHE', str(Path.home() / '.cache' / 'course-reminder-tools')))
if not str(ROOT.resolve()).isascii():
    raise SystemExit('Set KEQI_TOOL_CACHE to an ASCII-only directory (for example C:/KeqiTools).')
ROOT.mkdir(parents=True, exist_ok=True)
HERE = Path(__file__).parent
manifest_path = HERE / 'google-cn.xml'
if not manifest_path.exists():
    response = requests.get('https://googledownloads.cn/android/repository/repository2-3.xml', timeout=(20, 60))
    response.raise_for_status()
    manifest_path.write_bytes(response.content)
manifest = ET.parse(manifest_path)

def local(tag):
    return tag.rsplit('}', 1)[-1]

def child(element, name):
    return next(x for x in element if local(x.tag) == name)

def archive(path):
    package = next(x for x in manifest.getroot() if local(x.tag) == 'remotePackage' and x.attrib['path'] == path)
    archives = child(package, 'archives')
    for item in archives:
        host = next((x.text for x in item if local(x.tag) == 'host-os'), None)
        if host in (None, 'windows'):
            complete = child(item, 'complete')
            return child(complete, 'url').text, child(complete, 'checksum').text
    raise RuntimeError('Windows archive not found: ' + path)

def extract_verified(zip_path, destination):
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as zf:
        root = destination.resolve()
        for name in zf.namelist():
            target = (root / name).resolve()
            if not target.is_relative_to(root):
                raise ValueError('Unsafe archive path')
        zf.extractall(destination)

def download(name, url, expected=None, algorithm='sha1'):
    target = ROOT / (name + '.zip')
    destination = ROOT / name
    if destination.exists() and any(destination.iterdir()):
        print(name, 'already extracted', flush=True)
        return
    digest = hashlib.new(algorithm)
    print(name, 'downloading', flush=True)
    with requests.get(url, stream=True, timeout=(20, 60)) as response:
        response.raise_for_status()
        with target.open('wb') as f:
            for chunk in response.iter_content(65536):
                f.write(chunk)
                digest.update(chunk)
    if expected and digest.hexdigest().lower() != expected.lower():
        raise RuntimeError(name + ': checksum mismatch')
    print(name, 'downloaded', target.stat().st_size, 'checksum', digest.hexdigest(), flush=True)
    extract_verified(target, destination)
    print(name, 'extracted', flush=True)

platform_url, platform_hash = archive('platforms;android-35')
build_url, build_hash = archive('build-tools;35.0.0')
jobs = [
    ('platform', 'https://googledownloads.cn/android/repository/' + platform_url, platform_hash),
    ('build-tools', 'https://googledownloads.cn/android/repository/' + build_url, build_hash),
    ('jdk', 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip', None),
]
with ThreadPoolExecutor(max_workers=3) as pool:
    futures = [pool.submit(download, *job) for job in jobs]
    for future in futures:
        future.result()
paths = {
    'java': str(next((ROOT / 'jdk').rglob('java.exe'))),
    'javac': str(next((ROOT / 'jdk').rglob('javac.exe'))),
    'keytool': str(next((ROOT / 'jdk').rglob('keytool.exe'))),
    'android_jar': str(next((ROOT / 'platform').rglob('android.jar'))),
    'build_tools': str(next((ROOT / 'build-tools').rglob('aapt2.exe')).parent),
}
(HERE / 'tools.local.json').write_text(json.dumps(paths, indent=2), encoding='utf-8')
print('Portable tools ready', flush=True)
