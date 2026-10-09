"""DOM extraction + actual Java connection/selection path, using explicit test pages."""
from pathlib import Path
from playwright.sync_api import sync_playwright
import os, json, re, subprocess, sys
sys.stdout.reconfigure(encoding='utf-8')
r=Path(__file__).resolve().parent;t=json.loads((r/'tools.local.json').read_text(encoding='utf-8'))
out=r/'build/connection-tests'
cp=os.pathsep.join([str(out),str(r/'lib/jsoup.jar'),str(r/'lib/json-tests-only.jar'),t['android_jar']])
subprocess.run([t['javac'],'-encoding','UTF-8','-cp',cp,'-d',str(out),str(r/'tests/ChaoxingSnapshotProbe.java')],check=True)
source=(r/'src/com/keqi/reminder/ChaoxingBrowser.java').read_text(encoding='utf-8')
script=json.loads(re.search(r'public static final String SNAPSHOT=("(?:\\.|[^"\\])*");',source).group(1))
classic="<li class='course-item'><a href='/visit/stucoursemiddle?courseid=1001&amp;clazzid=2001&amp;cpi=3001'><span class='course-name' title='数学'>数学</span></a></li>"
modern="<li class='course-item'><a title='物理' href='/mycourse/studentcourse?cpi=3002&amp;clazzid=2002&amp;courseId=1002'>封面</a></li>"
hidden="<li><input name='courseId' value='1003'><input name='classId' value='2003'><input name='cpi' value='3003'><a title='逻辑电路' href='javascript:void(0)'>封面</a></li>"
split="<div class='course'><div class='cover'><a href='/mycourse/studentcourse?courseId=255733792&amp;clazzid=2009&amp;cpi=3009'><img></a></div><span class='course-name' title='形势与政策3'>形势与政策3</span></div>"
late="<div class='cover'><a href='/mycourse/studentcourse?courseId=1013&amp;clazzid=2013&amp;cpi=3013'><img></a></div><a href='/mycourse/studentcourse?courseId=1013&amp;clazzid=2013&amp;cpi=3013' title='大学生与法'>大学生与法</a>"
def verify(snapshot,cookies):
 payload=json.dumps({'snapshot':json.dumps(snapshot,ensure_ascii=False),'cookies':cookies},ensure_ascii=False)
 result=subprocess.run([t['java'],'-Dfile.encoding=UTF-8','-cp',cp,'com.keqi.reminder.ChaoxingSnapshotProbe'],input=payload,capture_output=True,text=True,encoding='utf-8',check=True)
 return json.loads(result.stdout)
checks=0
with sync_playwright() as p:
 browser=p.chromium.launch(channel='msedge',headless=True)
 page=browser.new_page(viewport={'width':390,'height':844})
 page.route('https://mooc1-1.chaoxing.com/**',lambda route:route.fulfill(status=200,content_type='text/html; charset=utf-8',body='<html><body>我学的课<ul>'+classic+modern+hidden+'</ul>'+split+late+'</body></html>'))
 page.goto('https://mooc1-1.chaoxing.com/visit/interaction')
 snapshot=page.evaluate(script)
 result=verify(snapshot,'_uid=123; JSESSIONID=fixture')
 assert result['accepted'] and len(result['courses'])==5;checks+=1
 names={c['courseId']:c['title'] for c in result['courses']}
 assert names=={'1001':'数学','1002':'物理','1003':'逻辑电路','255733792':'形势与政策3','1013':'大学生与法'},names;checks+=1
 assert all(c['platform']=='chaoxing' for c in result['courses']);checks+=1
 page.evaluate("document.body.insertAdjacentHTML('beforeend','<input type=\"password\" value=\"fixture-password-value\">')")
 assert 'fixture-password-value' not in page.evaluate(script);checks+=1
 assert not verify(snapshot,'JSESSIONID=guest')['accepted'];checks+=1
 page.evaluate("document.body.insertAdjacentHTML('beforeend','<button id=\"loginBtn\">登录</button>')")
 assert not verify(page.evaluate(script),'_uid=123')['accepted'];checks+=1
 page.route('https://passport2.chaoxing.com/login',lambda route:route.fulfill(status=200,content_type='text/html; charset=utf-8',body=classic+modern))
 page.goto('https://passport2.chaoxing.com/login')
 assert not verify(page.evaluate(script),'_uid=123')['accepted'];checks+=1
 browser.close()
print(f'Passed {checks} browser DOM / Chaoxing connection checks (test pages, not a real account).')
