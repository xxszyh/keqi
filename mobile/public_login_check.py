"""Read-only check of public platform pages in Edge, without credentials or SMS requests.

This checks web page loading, not Android WebView or authenticated login.
Diagnostic screenshots contain only an anonymous public login form.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import json, sys

sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
out=root/'build/public-login-check'; out.mkdir(parents=True,exist_ok=True)
agent='Mozilla/5.0 (Linux; Android 15; V2454A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.7339.123 Mobile Safari/537.36'
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':390,'height':650},user_agent=agent,is_mobile=True,has_touch=True)
    errors=[]
    page.on('pageerror',lambda error: errors.append(str(error)[:150]))
    page.goto('https://i.chaoxing.com/',wait_until='domcontentloaded',timeout=60000)
    expect(page.locator('button').first).to_be_visible(timeout=30000)
    (out/'chaoxing-initial.html').write_text(page.content(),encoding='utf-8')
    initial=page.evaluate('({url:location.origin+location.pathname,charset:document.characterSet,inputs:Array.from(document.querySelectorAll("input")).map(e=>({id:e.id,type:e.type})),actions:Array.from(document.querySelectorAll("a,button")).map(e=>({text:e.textContent,click:e.getAttribute("onclick")}))})')
    (out/'initial.json').write_text(json.dumps(initial,ensure_ascii=False,indent=2),encoding='utf-8')
    page.get_by_text('验证码登录',exact=True).click()
    expect(page.locator('input[type=tel],input#phone,input#phoneCode').first).to_be_visible()
    screenshot=out/'chaoxing-public-mobile.png'
    page.screenshot(path=str(screenshot),full_page=True)
    info={'public_login_loaded':True,'sms_inputs_visible':True,'viewport':page.evaluate('({innerWidth,scrollWidth:document.documentElement.scrollWidth,scale:visualViewport.scale})'),'page_errors':errors}
    # The personal-center HTML exposes webUser only for signed-in sessions.
    mooc=page.request.get('https://www.icourse163.org/home.htm',timeout=30000)
    (out/'anonymous-mooc.html').write_text(mooc.text(),encoding='utf-8')
    base=page.request.get('https://i.chaoxing.com/base',timeout=30000)
    (out/'anonymous-chaoxing.html').write_text(base.text(),encoding='utf-8')
    info.update(mooc_status=mooc.status,chaoxing_status=base.status)
    csrf=next((c['value'] for c in page.context.cookies('https://www.icourse163.org/') if c['name']=='NTESSTUDYSI'),'')
    rpc=page.request.post('https://www.icourse163.org/web/j/learnerCourseRpcBean.getMyLearnedCoursePanelList.rpc',params={'csrfKey':csrf},form={'type':30,'p':1,'psize':8,'courseType':1},headers={'edu-script-token':csrf,'Referer':'https://www.icourse163.org/'},timeout=30000)
    info['anonymous_rpc_status']=rpc.status
    try:
        data=rpc.json()
        info['anonymous_rpc_code']=data.get('code')
        (out/'anonymous-courses.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    except Exception:
        info['anonymous_rpc_format']='non-JSON response'
    (out/'result.json').write_text(json.dumps(info,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(info,ensure_ascii=False))
    browser.close()
