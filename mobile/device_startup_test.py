"""Check native snapshot delivery on a USB-connected diagnostic build.

Restarts only com.keqi.reminder; reads its WebView through a temporary host port.
No device files are written and app data is not cleared. Requires --debug build.
"""
from pathlib import Path
import argparse, json, subprocess, time
from playwright.sync_api import sync_playwright

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', required=True, type=Path)
parser.add_argument('--timeout', type=float, default=8)
parser.add_argument('--courses', type=int)
args = parser.parse_args()

def adb(*command):
    return subprocess.check_output([str(args.adb), *command], encoding='utf-8', errors='replace', timeout=15).strip()

started = time.monotonic()
adb('shell', 'am', 'start', '-S', '-n', 'com.keqi.reminder/.MainActivity')
pid = adb('shell', 'pidof', 'com.keqi.reminder')
port = adb('forward', 'tcp:0', 'localabstract:webview_devtools_remote_' + pid)
try:
    with sync_playwright() as p:
        browser = p.chromium.connect_over_cdp('http://127.0.0.1:' + port, timeout=10000)
        page = next(q for q in browser.contexts[0].pages if q.url == 'file:///android_asset/index.html')
        verdict = {}
        while time.monotonic() - started < args.timeout:
            verdict = page.evaluate('({platforms:document.querySelectorAll("#connections [data-platform]").length,courses:state.courses.length,tasks:state.tasks.length,received:!!state.lan})')
            if verdict['received'] and verdict['platforms'] == 2:
                if args.courses is not None:
                    assert verdict['courses'] == args.courses, verdict
                break
            page.wait_for_timeout(100)
        else:
            raise AssertionError('Phone stays blank before startup deadline: ' + json.dumps(verdict))
        print('Native startup PASS', json.dumps(verdict), 'seconds', round(time.monotonic() - started, 2))
finally:
    adb('forward', '--remove', 'tcp:' + port)
