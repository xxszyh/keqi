"""Replay course removal/restoration and long-list workflows through the actual UI."""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import sys
sys.stdout.reconfigure(encoding='utf-8')
root = Path(__file__).resolve().parent
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    page = browser.new_page(viewport={'width':390,'height':844})
    errors=[]; page.on('pageerror', lambda e:errors.append(str(e)))
    page.clock.install(time=1791511200)
    page.goto((root/'assets/index.html').as_uri())
    def manage():
        page.locator('#course-picker-button').click()
        page.locator('#picker-management').click()
    manage()
    expect(page.locator('#course-dialog .course-switch:visible')).to_have_count(0)
    page.locator('[data-course-expand=mooc]').click()
    page.locator('[data-remove-course=mooc-math]').click()
    page.locator('#cancel-courses').click()
    assert page.evaluate("state.courses.some(c=>c.key==='mooc-math')")
    manage();page.locator('[data-course-expand=mooc]').click()
    page.locator('[data-remove-course=mooc-math]').click()
    page.once('dialog',lambda d:d.dismiss());page.locator('#close-courses').click()
    expect(page.locator('#course-dialog')).to_be_visible()
    assert page.evaluate("state.courses.some(c=>c.key==='mooc-math')")
    page.once('dialog',lambda d:d.accept());page.locator('#close-courses').click()
    expect(page.locator('#course-dialog')).to_be_hidden()
    assert page.evaluate("state.removedCourses[0].key")=='mooc-math'
    expect(page.locator('#assignment-list [data-task=demo2]')).to_have_count(0)
    expect(page.locator('#count-week')).to_have_text('1')
    page.evaluate('onNativeState(JSON.stringify(state))')
    page.locator('#course-picker-button').click()
    expect(page.locator('[data-pick-course=mooc-math]')).to_have_count(0)
    page.locator('#close-picker').click()
    page.locator('nav [data-page=calendar]').click()
    assert '高等数学' not in page.locator('#day-list').inner_text()
    page.locator('nav [data-page=tasks]').click()
    page.locator('#undo-courses').click()
    expect(page.locator('#assignment-list [data-task=demo2]')).to_have_count(1)
    manage();page.locator('[data-course-expand=mooc]').click()
    page.locator('[data-remove-course=mooc-math]').click()
    page.once('dialog',lambda d:d.accept());page.locator('#close-courses').click()
    page.locator('nav [data-page=settings]').click()
    expect(page.locator('#settings-home .settings-entry')).to_have_count(6)
    expect(page.locator('#settings-home .course-switch')).to_have_count(0)
    page.locator('[data-settings-open=courses]').click()
    assert page.locator('.course-summary[open]').count()==0
    page.locator('#reimport-button').click()
    page.locator('#reimport-search').fill('高等数学')
    page.locator('[data-reimport=mooc-math]').click()
    expect(page.locator('[data-reimport=mooc-math]')).to_have_count(0)
    assert page.evaluate('state.removedCourses.length')==0
    assert page.evaluate('handleBack()') is True
    assert page.evaluate('handleBack()') is True
    expect(page.locator('#settings-home')).to_be_visible()
    page.locator('[data-settings-open=notifications]').click()
    page.evaluate('state.notificationEnabled=true;render()')
    expect(page.locator('#notification-permission')).to_have_text('管理通知权限')
    page.locator('#settings-back').click()
    page.locator('[data-settings-open=appearance]').click()
    expect(page.get_by_role('switch')).to_be_visible()
    page.locator('nav [data-page=tasks]').click()
    # Unknown groups start collapsed, remember explicit expansion, reveal search results.
    page.locator('[data-group="日期待确认"]').click()
    page.evaluate('onNativeState(JSON.stringify(state))')
    expect(page.locator('[data-group="日期待确认"]')).to_have_attribute('aria-expanded','true')
    page.locator('[data-group="日期待确认"]').click()
    page.locator('#search-button').click();page.locator('#search').fill('链表')
    expect(page.locator('#assignment-list [data-task=demo5]')).to_be_visible()
    page.locator('#search-button').click()
    # Filter drafts do not change the list until applied, and platform changes clear incompatible course selection.
    page.locator('#course-picker-button').click();page.locator('[data-pick-course=mooc-math]').click()
    page.locator('#filter-button').click();page.locator('[data-filter-platform=chaoxing]').click()
    page.locator('#cancel-filters').click()
    assert page.locator('#platform-filter').input_value()=='all'
    assert page.locator('#course-filter').input_value()=='mooc-math'
    page.locator('#filter-button').click();page.locator('[data-filter-platform=chaoxing]').click()
    page.locator('[data-time=unknown]').click();page.locator('#apply-filters').click()
    assert page.locator('#course-filter').input_value()=='all'
    expect(page.locator('#assignment-list [data-task=demo5]')).to_be_visible()
    page.locator('#filter-button').click();page.locator('#reset-filters').click();page.locator('#apply-filters').click()
    # A refresh during editing preserves explicit choices and newer courses, then sends only edits.
    page.evaluate("state.courses=Array.from({length:54},(_,i)=>({key:'c'+i,title:'大学物理与工程实验 '+i+'（跨学科综合实践）',platform:i%2?'mooc':'chaoxing',enabled:true}));state.tasks=[];render();window.actions=[];window.Native={action:(a,p)=>actions.push([a,JSON.parse(p)])}")
    manage();page.locator('[data-course-expand=chaoxing]').click()
    page.locator('[data-course=c0]').uncheck()
    page.locator('#course-search').fill('工程实验 0')
    page.evaluate("state.courses.push({key:'new',title:'新发现课程',platform:'mooc',enabled:true});onNativeState(JSON.stringify(state))")
    expect(page.locator('#course-search')).to_have_value('工程实验 0')
    expect(page.locator('[data-course=c0]')).not_to_be_checked()
    page.locator('#close-courses').click()
    assert page.evaluate('actions.at(-1)')==['applyCourseChoices',{'choices':{'c0':False},'remove':[]}]
    for width in [320,390,780]:
        page.set_viewport_size({'width':width,'height':740})
        manage();page.locator('[data-course-expand=chaoxing]').click()
        assert page.locator('#course-dialog').evaluate('(e)=>e.scrollWidth<=e.clientWidth')
        bounds=page.locator('#close-courses').bounding_box()
        assert bounds and bounds['y']+bounds['height']<=740
        page.locator('#course-dialog-list').evaluate('(e)=>e.scrollTop=e.scrollHeight')
        expect(page.locator('#close-courses')).to_be_in_viewport()
        if width==390:page.screenshot(path=str(root/'preview/课程管理-展开示例.png'))
        assert page.evaluate('handleBack()') is True
        expect(page.locator('#course-dialog')).to_be_hidden()
    page.set_viewport_size({'width':390,'height':844})
    page.locator('nav [data-page=settings]').click()
    page.evaluate("document.querySelector('#undo-bar').classList.add('hidden')")
    page.screenshot(path=str(root/'preview/设置首页-新版.png'))
    assert not errors,errors
    browser.close()
print('Passed removal/cancel/undo/reimport, date-group memory, filter drafts, refresh-safe edits, settings/back and 55-course narrow-screen workflows.')
