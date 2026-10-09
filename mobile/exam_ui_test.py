"""Exercise exam metadata through the actual UI and native action boundary."""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':390,'height':844})
    errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.add_init_script("window.Native={action:(name,data)=>window.lastNativeAction=[name,JSON.parse(data)]}")
    page.clock.install(time=1791511200)
    page.goto((root/'assets/index.html').as_uri())
    page.evaluate("""const now=Date.now();onNativeState({tasks:[
    {id:'exam',platform:'chaoxing',courseKey:'cx',courseTitle:'概率论与数理统计',title:'期中考试',kind:'exam',phase:'exam',status:'pending',due:now+3*3600000,examStart:now-3600000,lastSeen:now,sourceUrl:'https://mooc1-api.chaoxing.com/exam-ans/exam/phone/examcode'},
    {id:'work',platform:'chaoxing',courseKey:'cx',courseTitle:'概率论与数理统计',title:'第二章作业',kind:'homework',phase:'submit',status:'pending',due:now+3*3600000,lastSeen:now-3600000},
    {id:'done-exam',platform:'chaoxing',courseKey:'cx',courseTitle:'概率论与数理统计',title:'第一次考试',kind:'exam',phase:'exam',status:'done',due:now-3600000,lastSeen:now}],
    courses:[{key:'cx',platform:'chaoxing',title:'概率论与数理统计',enabled:true}],
    connections:[{platform:'chaoxing',connected:true,status:'partial',message:'本轮读取 0 项作业、2 项考试；1 项旧记录本轮未更新，保留供查看；部分内容未更新：作业列表',lastAttempt:now,lastSuccess:now-3600000}],settings:{offsets:[72,24,3]},notificationEnabled:true})""")
    page.locator('#health-banner').click()
    expect(page.locator('#sync-details')).to_contain_text('2 项考试')
    expect(page.locator('#sync-details')).to_contain_text('旧记录本轮未更新')
    page.locator('#close-sync').click()
    expect(page.locator('#sync-label')).to_contain_text('上次尝试')
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(2)
    page.locator('[data-kind=exam]').click()
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(1)
    expect(page.locator('#assignment-list .kind')).to_have_text('考试')
    page.locator('#filter-button').click()
    page.locator('[data-filter=all]').click()
    page.locator('#apply-filters').click()
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(2)
    page.locator('#filter-button').click()
    page.locator('[data-filter=done]').click()
    page.locator('#apply-filters').click()
    expect(page.locator('#assignment-list [data-task=done-exam]')).to_have_count(1)
    page.locator('#filter-button').click()
    page.locator('[data-filter=pending]').click()
    page.locator('#apply-filters').click()
    page.locator('#assignment-list [data-task=exam]').click()
    expect(page.locator('#detail-content')).to_contain_text('开始：')
    page.locator('#open-source').click()
    assert page.evaluate('window.lastNativeAction')==['openSource',{'id':'exam'}]
    page.locator('#close-detail').click()
    page.evaluate('onNativeState(JSON.stringify(state))')
    assert page.locator('#kind-filter').input_value()=='exam'
    page.locator('nav [data-page=calendar]').click()
    expect(page.locator('#day-list [data-task=exam]')).to_have_count(1)
    page.locator('nav [data-page=settings]').click()
    page.locator('[data-settings-open=courses]').click()
    page.locator('.course-summary[data-platform=chaoxing] summary').click()
    page.locator('[data-select-platform-courses=chaoxing]').click()
    page.locator('#course-dialog [data-course=cx]').uncheck()
    page.locator('#close-courses').click()
    assert page.evaluate('window.lastNativeAction')==['applyCourseChoices',{'choices':{'cx':False},'remove':[]}]
    page.evaluate("state.courses[0].enabled=false;onNativeState(JSON.stringify(state))")
    page.locator('nav [data-page=tasks]').click()
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(0)
    expect(page.locator('#count-urgent')).to_have_text('0')
    for width in [320,390,780]:
        page.set_viewport_size({'width':width,'height':844})
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    assert not errors,errors
    browser.close()
print('Passed 19 exam UI, calendar, selection and status checks.')
