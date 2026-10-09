"""Exercise the packaged local UI using Edge; preview fixtures are explicitly labeled."""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import sys

sys.stdout.reconfigure(encoding='utf-8')

ROOT = Path(__file__).resolve().parent
out = ROOT / 'preview'
out.mkdir(exist_ok=True)
checks = 0
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    page = browser.new_page(viewport={'width': 390, 'height': 844}, device_scale_factor=1)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto((ROOT / 'assets/index.html').as_uri())
    expect(page.locator('#preview-banner')).to_be_visible()
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(5)
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    checks += 3
    page.screenshot(path=str(out / '作业总览-示例.png'), full_page=True)
    page.locator('#search-button').click()
    page.locator('#search').fill('链表')
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(1)
    page.locator('#assignment-list .assignment-card').click()
    expect(page.locator('#detail-dialog')).to_be_visible()
    page.locator('#manual-deadline').fill('2027-01-05T18:00')
    page.locator('#save-deadline').click()
    assert page.evaluate("state.tasks.find(t=>t.id==='demo5').manualDue") == 1799143200000
    checks += 3
    page.locator('#search').fill('栈与队列')
    page.locator('#assignment-list .assignment-card').press('Enter')
    page.locator('#toggle-done').click()
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(0)
    assert page.evaluate("state.tasks.find(t=>t.id==='demo1').status") == 'pending'
    checks += 2
    page.locator('#search').fill('')
    page.locator('nav [data-page=calendar]').click()
    expect(page.locator('#calendar-grid button')).to_have_count(42)
    page.locator('#next-month').click()
    page.locator('#prev-month').click()
    page.locator('#calendar-today').click()
    page.screenshot(path=str(out / '截止日历-示例.png'), full_page=True)
    page.locator('nav [data-page=settings]').click()
    page.locator('#offsets').fill('48,12,2')
    page.locator('#save-settings').click()
    assert page.evaluate('state.settings.offsets') == [48,12,2]
    page.once('dialog', lambda dialog: dialog.accept('2026'))
    page.locator('[data-year]').click()
    assert page.evaluate('state.settings.chaoxingYear') == 2026
    page.locator('#course-settings [data-course=cx-ds]').uncheck()
    assert page.evaluate("state.courses.find(c=>c.key==='cx-ds').enabled") is False
    checks += 4
    page.screenshot(path=str(out / '连接与提醒-示例.png'), full_page=True)
    page.set_viewport_size({'width':320,'height':740})
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    checks += 1
    live = browser.new_page(viewport={'width':390,'height':844})
    live.on('pageerror', lambda error: errors.append(str(error)))
    live.add_init_script("window.Native={action:(name,data)=>{window.lastNativeAction=[name,JSON.parse(data)]}}")
    live.goto((ROOT / 'assets/index.html').as_uri())
    live.evaluate("onNativeState({tasks:[],connections:[],courses:[],settings:{offsets:[72,24,3]},notificationEnabled:false})")
    expect(live.locator('#preview-banner')).to_be_hidden()
    expect(live.locator('#assignment-list .assignment-card')).to_have_count(0)
    live.locator('[data-go-settings]').click()
    live.locator('[data-login=mooc]').click()
    assert live.evaluate('window.lastNativeAction') == ['login', {'platform':'mooc'}]
    checks += 3
    live.evaluate("onNativeState({tasks:[],connections:[],courses:[{key:'first',title:'课程一',platform:'mooc'},{key:'second',title:'课程二',platform:'mooc'}],settings:{offsets:[72,24,3]},notificationEnabled:false})")
    live.locator('nav [data-page=tasks]').click()
    live.locator('[data-select-courses]').click()
    live.locator('#course-dialog [data-course-bulk=hide]').click()
    assert live.evaluate('window.lastNativeAction') == ['toggleCourses',{'keys':['first','second'],'enabled':False}]
    live.evaluate("state.courses[0].enabled=false;onNativeState(JSON.stringify(state))")
    expect(live.locator('#course-dialog [data-course=first]')).not_to_be_checked()
    checks += 2
    assert not errors, errors
    checks += 1
    browser.close()
print(f'Passed {checks} UI checks. Preview images: {out}')
