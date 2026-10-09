"""Replay code-only saved metadata, followed by a course-name refresh in the real UI."""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':390,'height':844})
    page.goto((root/'assets/index.html').as_uri())
    page.evaluate("state.courses.find(c=>c.key==='cx-ds').title='课程 255733792';state.tasks.filter(t=>t.courseKey==='cx-ds').forEach(t=>t.courseTitle='课程 255733792');onNativeState(JSON.stringify(state))")
    page.locator('nav [data-page=settings]').click()
    page.locator('[data-select-platform-courses=chaoxing]').click()
    expect(page.locator('#course-dialog-list')).not_to_contain_text('255733792')
    expect(page.locator('#course-dialog [data-course=cx-ds]')).to_have_attribute('aria-label','显示 课程名称待更新 的作业和考试')
    page.locator('#course-dialog [data-course=cx-ds]').uncheck()
    page.evaluate("state.courses.find(c=>c.key==='cx-ds').title='形势与政策3';onNativeState(JSON.stringify(state))")
    expect(page.locator('#course-dialog-list')).to_contain_text('形势与政策3')
    expect(page.locator('#course-dialog [data-course=cx-ds]')).not_to_be_checked()
    expect(page.locator('#course-dialog [data-course=cx-eng]')).to_be_checked()
    page.locator('#course-dialog [data-course=cx-ds]').check()
    page.locator('#close-courses').click()
    page.locator('nav [data-page=tasks]').click()
    expect(page.locator('#course-filter')).to_contain_text('形势与政策3')
    expect(page.locator('#assignment-list [data-task=demo1] .course-name')).to_have_text('形势与政策3')
    expect(page.locator('#assignment-list')).not_to_contain_text('255733792')
    page.locator('#assignment-list [data-task=demo1]').click()
    expect(page.locator('#detail-content')).to_contain_text('形势与政策3')
    expect(page.locator('#detail-content')).not_to_contain_text('255733792')
    expect(page.locator('#detail-content')).not_to_contain_text('课程名称待更新')
    browser.close()
print('Passed 11 saved-course-name display and refresh checks.')
