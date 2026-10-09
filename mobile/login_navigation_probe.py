"""Public login check through the production native navigation policy.
Two mobile openings share cookies, then the desktop alternative is opened.
No credentials, captcha solving, SMS request or authenticated session is used.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import json, subprocess, sys
from urllib.parse import urlparse,parse_qs
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
out=root/'build/login-navigation';out.mkdir(parents=True,exist_ok=True)
tools=json.loads((root/'tools.local.json').read_text(encoding='utf-8'))
subprocess.run([tools['javac'],'-encoding','UTF-8','-d',str(out),str(root/'src/com/keqi/reminder/LoginNavigation.java'),str(root/'tests/NavigationProbe.java')],check=True)
def policy(*args):
    return subprocess.run([tools['java'],'-cp',str(out),'com.keqi.reminder.NavigationProbe',*args],check=True,capture_output=True,text=True,encoding='utf-8').stdout.strip()
mobile='Mozilla/5.0 (Linux; Android 16; V2454A Build/BP2A.250605.031.A3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.7339.123 Mobile Safari/537.36'
desktop='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36'
runs=[];callbacks=[]
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    context=browser.new_context(viewport={'width':390,'height':650},user_agent=mobile,is_mobile=True,has_touch=True)
    for index,is_desktop in enumerate([False,False,True]):
        page=context.new_page()
        page.set_extra_http_headers({'User-Agent':desktop if is_desktop else mobile})
        routes=[];errors=[]
        page.on('pageerror',lambda error: errors.append(str(error)[:150]))
        def route_handler(route):
            request=route.request
            if request.is_navigation_request():
                main=request.frame==page.main_frame
                target=policy('target',str(main).lower(),request.url)
                routes.append({'url':request.url,'main':main,'accepted':target!='BLOCKED'})
                if target=='BLOCKED':
                    route.abort();return
                if target!=request.url:
                    # WebView's v.loadUrl(target) is a new navigation. A local redirect
                    # reproduces that behavior without requesting the school HTTP host.
                    route.fulfill(status=302,headers={'Location':target},body='');return
                route.continue_();return
            route.continue_()
        page.route('**/*',route_handler)
        page.goto(policy('start',str(is_desktop).lower()),wait_until='domcontentloaded',timeout=60000)
        expect(page.locator('#phone')).to_be_visible(timeout=15000)
        page.get_by_text('验证码登录',exact=True).click()
        expect(page.locator('#phone')).to_be_visible()
        expect(page.locator('input[placeholder*=验证码]')).to_be_visible()
        assert not any(not r['accepted'] for r in routes),routes
        run={'mode':'desktop' if is_desktop else 'mobile','opening':index+1,'routes':list(routes),'page_errors':list(errors)}
        runs.append(run)
        page.screenshot(path=str(out/f'public-{index+1}.png'),full_page=True)
        if index in (0,2):
            routes.clear();errors.clear()
            page.goto('http://i.mooc.hdu.edu.cn',wait_until='domcontentloaded',timeout=60000)
            expect(page.locator('#phone')).to_be_visible(timeout=15000)
            # Playwright routes the initial request of a redirect chain only.
            # Verify the actual server's anonymous redirect returned the intended refer.
            final=urlparse(page.url)
            assert routes[0]['accepted'] and final.hostname=='passport2.chaoxing.com' and parse_qs(final.query).get('refer')==['https://mooc1-1.chaoxing.com/visit/interaction'],routes
            assert not any(not r['accepted'] for r in routes),routes
            assert not errors,errors
            callbacks.append({'mode':run['mode'],'routes':list(routes),'page_errors':list(errors),'anonymous_session':'returned to official login, not accepted as authenticated'})
        page.close()
    (out/'result.json').write_text(json.dumps({'login_openings':runs,'school_callbacks':callbacks},ensure_ascii=False,indent=2),encoding='utf-8')
    browser.close()
print('PASS: first and repeated mobile login openings, and desktop alternative, through native routing policy.')
print(json.dumps([{'mode':r['mode'],'opening':r['opening'],'navigation_count':len(r['routes']),'page_errors':r['page_errors']} for r in runs],ensure_ascii=False))
print('PASS: phone and desktop HDU return URLs reach the actual official course home; an anonymous session returns to login.')
