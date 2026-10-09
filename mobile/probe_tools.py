from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import requests

URLS = {
 'google-cn': 'https://dl.google.com/android/repository/repository2-3.xml',
 'google-downloads': 'https://googledownloads.cn/android/repository/repository2-1.xml',
 'huawei': 'https://mirrors.huaweicloud.com/android/repository/repository2-1.xml',
 'microsoft-jdk': 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip',
}

def probe(pair):
    name, url = pair
    try:
        response = requests.get(url, timeout=(12, 20), stream=True)
        print(name, response.status_code, response.headers.get('content-length'), flush=True)
        if name != 'microsoft-jdk' and response.ok:
            data = response.content
            Path(__file__).with_name(name + '.xml').write_bytes(data)
            print(name, 'saved', len(data), flush=True)
        response.close()
    except requests.RequestException as exc:
        print(name, type(exc).__name__, flush=True)

with ThreadPoolExecutor(max_workers=4) as pool:
    list(pool.map(probe, URLS.items()))
