"""Scoped batch edits preserve the other platform and survive a native refresh."""
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
import sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':390,'height':844});page.clock.install(time=1791511200)
    page.goto((root/'assets/index.html').as_uri())
    page.locator('nav [data-page=settings]').click();page.locator('[data-settings-open=courses]').click()
    assert page.locator('.course-summary[open]').count()==0
    page.locator('.course-summary[data-platform=chaoxing] summary').click()
    page.locator('[data-select-platform-courses=chaoxing]').click()
    expect(page.locator('#course-dialog input[data-course]')).to_have_count(2)
    expect(page.locator('#course-dialog [data-course=mooc-math]')).to_have_count(0)
    expect(page.locator('#course-dialog h2')).to_contain_text('学习通')
    page.locator('[data-course-bulk=hide][data-scope=chaoxing]').click()
    page.evaluate('onNativeState(JSON.stringify(state))')
    expect(page.locator('#course-dialog [data-course=cx-ds]')).not_to_be_checked()
    assert page.evaluate("state.courses.find(c=>c.key==='cx-ds').enabled!==false") # still draft
    page.locator('#close-courses').click()
    assert page.evaluate("state.courses.filter(c=>c.platform==='chaoxing').every(c=>c.enabled===false)")
    assert page.evaluate("state.courses.find(c=>c.key==='mooc-math').enabled!==false")
    for task in ['demo1','demo4','demo5']:expect(page.locator(f'#assignment-list [data-task={task}]')).to_have_count(0)
    page.locator('[data-select-platform-courses=chaoxing]').click()
    page.locator('#course-dialog [data-course=cx-ds]').check();page.locator('#close-courses').click()
    expect(page.locator('#assignment-list [data-task=demo1]')).to_have_count(1)
    assert page.evaluate("state.courses.find(c=>c.key==='cx-eng').enabled===false")
    page.locator('[data-select-platform-courses=chaoxing]').click()
    page.evaluate('window.calls=[];window.Native={action:(action,payload)=>calls.push({action,data:JSON.parse(payload)})}')
    page.locator('[data-course-bulk=show][data-scope=chaoxing]').click();page.locator('#close-courses').click()
    assert page.evaluate('calls.at(-1)')=={'action':'applyCourseChoices','data':{'choices':{'cx-eng':True},'remove':[]}}
    page.locator('.course-summary[data-platform=mooc] summary').click()
    page.locator('[data-select-platform-courses=mooc]').click()
    expect(page.locator('#course-dialog input[data-course]')).to_have_count(1)
    page.locator('[data-course-bulk=hide][data-scope=mooc]').click();page.locator('#close-courses').click()
    assert page.evaluate('calls.at(-1)')=={'action':'applyCourseChoices','data':{'choices':{'mooc-math':False},'remove':[]}}
    page.locator('[data-select-courses]').click()
    expect(page.locator('#course-dialog input[data-course]')).to_have_count(3)
    page.screenshot(path=str(root/'preview/学习通课程选择-示例.png'),full_page=False)
    page.locator('#cancel-courses').click()
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
    browser.close()
print('Passed independent platform choices, draft refresh, one-batch native actions and collapsed management checks.')
