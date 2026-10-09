"""Check the list redesign at the real UI/native boundary using isolated fixtures."""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':390,'height':844})
    errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    page.clock.install(time=1791511200)  # 2026-10-09 10:00 Asia/Shanghai
    page.goto((root/'assets/index.html').as_uri())
    page.evaluate("""const now=Date.now();state.tasks=[
      {id:'late',platform:'chaoxing',courseKey:'cx-ds',title:'已逾期练习',kind:'homework',status:'pending',due:now-3600000},
      {id:'today-exam',platform:'chaoxing',courseKey:'cx-ds',title:'期中考试',kind:'exam',phase:'exam',status:'pending',due:now+3600000,deadlineObserved:false,deadlineCheckedAt:now-86400000},
      {id:'tomorrow',platform:'mooc',courseKey:'mooc-math',title:'明天练习',kind:'quiz',status:'pending',due:now+86400000},
      {id:'week',platform:'chaoxing',courseKey:'cx-eng',title:'本周练习',kind:'homework',status:'pending',due:now+3*86400000},
      {id:'future',platform:'chaoxing',courseKey:'cx-eng',title:'下月任务',kind:'homework',status:'pending',due:now+35*86400000},
      {id:'unknown',platform:'chaoxing',courseKey:'cx-eng',title:'没有日期',kind:'exam',status:'unknown'}];
      state.connections=[{platform:'chaoxing',status:'partial',connected:true,lastAttempt:now,lastSuccess:now-3600000,message:'本轮读取 4 项作业、2 项考试；概率论：详情读取失败，沿用旧记录'}];render();""")
    assert page.locator('#assignment-list .group-heading strong').all_text_contents()==['已逾期','今天 · 10月9日','明天','7 天内','之后','日期待确认']
    page.locator('[data-kind=exam]').click()
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(2)
    page.locator('#assignment-list [data-task=today-exam] .row-state').wait_for()
    expect(page.locator('#assignment-list [data-task=today-exam]')).to_contain_text('截止沿用旧记录')
    page.locator('#health-banner').click()
    expect(page.locator('#sync-details')).to_contain_text('概率论：详情读取失败')
    page.locator('#close-sync').click()
    page.locator('#search-button').click();page.locator('#search').fill('没有日期')
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(1)
    page.locator('#search-button').click()
    expect(page.locator('#assignment-list .assignment-card')).to_have_count(2)
    page.once('dialog',lambda d:d.dismiss())
    page.locator('#assignment-list [data-local-task=today-exam]').click()
    assert not page.evaluate("state.tasks.find(t=>t.id==='today-exam').localDone")
    page.once('dialog',lambda d:d.accept())
    page.locator('#assignment-list [data-local-task=today-exam]').click()
    expect(page.locator('#assignment-list [data-task=today-exam]')).to_have_count(0)
    assert page.evaluate("state.tasks.find(t=>t.id==='today-exam').status")=='pending'
    page.locator('#course-picker-button').click();page.locator('#picker-management').click()
    page.locator('#course-search').fill('大学英语')
    expect(page.locator('#course-dialog .course-switch:visible')).to_have_count(1)
    page.evaluate('onNativeState(JSON.stringify(state))')
    expect(page.locator('#course-dialog .course-switch:visible')).to_have_count(1)
    page.locator('#close-courses').click()
    page.locator('nav [data-page=settings]').click()
    page.locator('[data-settings-open=appearance]').click()
    page.locator('#dark-mode').check()
    assert page.evaluate('state.settings.darkMode') is True
    assert page.evaluate('document.documentElement.classList.contains("dark")')
    page.evaluate('window.Native={action:(a,p)=>window.lastAction=[a,JSON.parse(p)]}')
    page.locator('#dark-mode').uncheck()
    assert page.evaluate('window.lastAction')==['saveSettings',{'darkMode':False}]
    page.evaluate('state.settings.darkMode=false;onNativeState(JSON.stringify(state))')
    assert not page.evaluate('document.documentElement.classList.contains("dark")')
    page.locator('nav [data-page=tasks]').click()
    page.evaluate("state.tasks.find(t=>t.id==='today-exam').localDone=false;state.tasks.find(t=>t.id==='today-exam').manualDue=Date.now()+7200000;render()")
    expect(page.locator('#assignment-list [data-task=today-exam]')).not_to_contain_text('截止沿用旧记录')
    for width in [320,390,780]:
        page.set_viewport_size({'width':width,'height':844})
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.locator('#course-picker-button').click();page.locator('#picker-management').click()
        assert page.locator('#course-dialog').evaluate('(el)=>el.scrollWidth<=el.clientWidth')
        page.locator('#close-courses').click()
    page.evaluate("state.tasks[0].title='<img src=x onerror=alert(1)>';state.tasks[0].courseTitle='<svg onload=alert(1)>';document.querySelector('#kind-filter').value='all';render()")
    assert page.locator('#assignment-list img').count()==0
    page.set_viewport_size({'width':390,'height':844})
    page.evaluate("state.tasks[0].title='已逾期练习';state.tasks.find(t=>t.id==='today-exam').manualDue=null;render()")
    page.evaluate("document.querySelector('#toast').classList.remove('visible')")
    page.screenshot(path=str(root/'preview/列表版-示例.png'),full_page=False)
    page.locator('nav [data-page=settings]').click()
    page.evaluate('state.settings.darkMode=true;render()')
    page.locator('nav [data-page=tasks]').click()
    page.evaluate("document.querySelector('#toast').classList.remove('visible')")
    page.screenshot(path=str(root/'preview/列表版-深色示例.png'),full_page=False)
    assert not errors,errors
    browser.close()
print('Passed list UI checks: deadline groups, type/search filters, stale deadline evidence, sync details, local completion, course search/refresh, persisted appearance actions, narrow layouts and escaped titles.')
