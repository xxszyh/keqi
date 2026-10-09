"""Real HTTP 202 fixture documents render normally; production Java decides when data is ready."""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import os, json, re, subprocess, sys
sys.stdout.reconfigure(encoding='utf-8')
r=Path(__file__).resolve().parent;t=json.loads((r/'tools.local.json').read_text(encoding='utf-8'))
cp=os.pathsep.join([str(r/'build/read-tests'),str(r/'lib/jsoup.jar'),str(r/'lib/json-tests-only.jar'),t['android_jar']])
source=(r/'src/com/keqi/reminder/ChaoxingPageBrowser.java').read_text(encoding='utf-8')
script=json.loads(re.search(r'public static final String SNAPSHOT=("(?:\\.|[^"\\])*");',source).group(1))
host='https://mooc1.chaoxing.com'
course_url=host+'/visit/stucoursemiddle?courseid=1&clazzid=2&cpi=3'
list_url=host+'/mooc-ans/mooc2/work/list?courseId=1&classId=2&cpi=3'
detail_url=host+'/mooc-ans/work/doHomeWorkNew?workId=8'
work="<li data='"+detail_url+"'><p class='overHidden2'>波的衍射与干涉</p><p class='status'>未提交</p></li>"
detail='<h1>作业详情</h1><p>作答时间：2026-10-09 08:00 至 2026-10-15 18:00</p>'
def verify(snapshot,stage,cookies='_uid=123; JSESSIONID=fixture'):
    payload=json.dumps({'snapshot':json.dumps(snapshot,ensure_ascii=False),'cookies':cookies,'stage':stage},ensure_ascii=False)
    result=subprocess.run([t['java'],'-Dfile.encoding=UTF-8','-cp',cp,'com.keqi.reminder.ChaoxingReadProbe'],input=payload,capture_output=True,text=True,encoding='utf-8',check=True)
    return json.loads(result.stdout)
checks=0
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':390,'height':844})
    def route(request):
        url=request.request.url
        if '/visit/stucoursemiddle?' in url:
            body="<p>页面加载中</p><script>setTimeout(()=>location.replace('/mooc-ans/mycourse/stu?courseId=1&clazzid=2&cpi=3'),700)</script>"
            status=202
        elif '/mycourse/stu?' in url:
            body="<input id='workEnc' value='fixture-enc'><input id='courseId' value='1'>";status=200
        elif '/mooc2/work/list?' in url:
            body='<p>正在加载</p><script>setTimeout(()=>document.body.innerHTML='+json.dumps(work,ensure_ascii=False)+',700)</script>';status=202
        elif '/work/doHomeWorkNew?' in url:
            body='<p>请稍候</p><script>setTimeout(()=>document.body.innerHTML='+json.dumps(detail,ensure_ascii=False)+',700)</script>';status=202
        elif 'workId=10' in url:
            body='<h1>作业详情</h1><iframe src="'+detail_url+'"></iframe>';status=200
        else:
            body='<p>请完成验证，拖动滑块</p>';status=202
        request.fulfill(status=status,content_type='text/html; charset=utf-8',body='<html><body>'+body+'</body></html>')
    page.route(host+'/**',route)
    response=page.goto(course_url)
    assert response.status==202;checks+=1
    assert not verify(page.evaluate(script),'COURSE')['ready'];checks+=1
    page.wait_for_url('**/mycourse/stu?**')
    assert verify(page.evaluate(script),'COURSE')['ready'];checks+=1
    response=page.goto(list_url)
    assert response.status==202 and not verify(page.evaluate(script),'WORK_LIST')['ready'];checks+=1
    page.wait_for_function("document.querySelector('li[data]')!==null")
    result=verify(page.evaluate(script),'WORK_LIST')
    assert result['ready'] and len(result['tasks'])==1 and result['tasks'][0]['title']=='波的衍射与干涉';checks+=1
    response=page.goto(detail_url)
    assert response.status==202 and not verify(page.evaluate(script),'WORK_DETAIL')['ready'];checks+=1
    page.wait_for_function("document.body.innerText.includes('2026-10-15 18:00')")
    result=verify(page.evaluate(script),'WORK_DETAIL')
    assert result['ready'] and result['task']['due']==1792058400000 and result['task']['deadlineObserved'];checks+=1
    assert verify(page.evaluate(script),'WORK_DETAIL','_uid=456')['error']=='LoginRequired';checks+=1
    page.goto(host+'/work/task?workId=10')
    page.wait_for_function("document.querySelector('iframe').contentDocument.body.innerText.includes('2026-10-15 18:00')")
    result=verify(page.evaluate(script),'WORK_DETAIL')
    assert result['ready'] and result['task']['due']==1792058400000;checks+=1
    exam_url='https://mooc1-api.chaoxing.com/exam-ans/exam/phone/examcode'
    exam_html="<ul class='ks_list'><li data='/exam-ans/exam/phone/start?taskrefId=7&courseId=1&classId=2'><dl><dt>期中考试</dt><dd>2026-10-09 08:00 至 2026-10-15 18:00</dd></dl><span class='ks_state'>未完成</span></li></ul>"
    page.route(exam_url,lambda route:route.fulfill(status=202,content_type='text/html; charset=utf-8',body='<p>正在加载</p><script>setTimeout(()=>document.body.innerHTML='+json.dumps(exam_html,ensure_ascii=False)+',700)</script>'))
    response=page.goto(exam_url)
    assert response.status==202 and not verify(page.evaluate(script),'EXAM_LIST')['ready'];checks+=1
    page.wait_for_function("document.querySelector('ul.ks_list li')!==null")
    result=verify(page.evaluate(script),'EXAM_LIST')
    assert result['ready'] and len(result['tasks'])==1 and result['tasks'][0]['kind']=='exam' and result['tasks'][0]['due']==1792058400000;checks+=1
    assert result['tasks'][0]['sourceUrl']==exam_url and '/start?' not in result['tasks'][0]['sourceUrl'];checks+=1
    page.goto(host+'/work/task?workId=9')
    assert verify(page.evaluate(script),'WORK_DETAIL')['error']=='InteractionRequired';checks+=1
    page.goto((r/'assets/index.html').as_uri())
    page.evaluate("state.connections.find(c=>c.platform==='chaoxing').verificationUrl='https://mooc1.chaoxing.com/mycourse/studentcourse?courseId=1&clazzid=2&cpi=3';onNativeState(JSON.stringify(state))")
    page.locator('nav [data-page=settings]').click()
    page.locator('[data-settings-open=platforms]').click()
    page.locator('#connections [data-platform=chaoxing]').click()
    expect(page.locator('[data-verify-platform=chaoxing]')).to_have_text('打开验证页面');checks+=1
    page.evaluate('window.calls=[];window.Native={action:(action,payload)=>calls.push({action,data:JSON.parse(payload)})}')
    page.locator('[data-verify-platform=chaoxing]').click()
    assert page.evaluate("calls.length===1&&calls[0].action==='verifyPlatform'&&calls[0].data.platform==='chaoxing'&&!calls[0].data.url");checks+=1
    assert page.evaluate("state.courses.length===3&&state.courses.every(c=>c.enabled!==false)");checks+=1
    page.evaluate("delete state.connections.find(c=>c.platform==='chaoxing').verificationUrl;onNativeState(JSON.stringify(state))")
    expect(page.locator('[data-verify-platform]')).to_have_count(0);checks+=1
    browser.close()
print(f'Passed {checks} HTTP 202 browser-rendering / verification handoff checks (explicit test pages).')
