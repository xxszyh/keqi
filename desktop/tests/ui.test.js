'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const {_electron}=require(process.env.KEQI_PLAYWRIGHT||'playwright');

test('desktop commands persist through the isolated UI and JVM', {timeout:90000}, async()=>{
  const data=await fs.mkdtemp(path.join(os.tmpdir(),'keqi-ui-'));
  const errors=[];
  async function launch(){
    const packaged=process.env.KEQI_PACKAGED_EXE;
    const app=await _electron.launch({executablePath:packaged||require('electron'),args:packaged?[]:[path.resolve(__dirname,'..')],env:{...process.env,KEQI_TEST:'1',KEQI_DATA_DIR:data}});
    const page=await app.firstWindow();
    page.on('pageerror',e=>errors.push(e.message));
    await page.waitForFunction(()=>typeof state!=='undefined'&&!!state.workspace);
    return {app,page};
  }
  let app;
  try{
    let opened=await launch();app=opened.app;let page=opened.page;
    assert.equal(await page.locator('#settings-about h2').textContent(),'课期 '+await app.evaluate(({app})=>app.getVersion()));
    assert.equal(await page.locator('#preview-banner').isVisible(),false);
    await page.locator('#new-task').click();
    await page.locator('#task-title').fill('集成检查：整理课堂笔记');
    await page.locator('#task-note').fill('电脑离线保存，手机连接时同步');
    await page.locator('#task-tags').fill('学习, 本周');
    await page.locator('#editor-form button[type=submit]').click();
    await page.waitForFunction(()=>state.tasks.some(t=>t.title==='集成检查：整理课堂笔记'));
    const task=await page.evaluate(()=>state.tasks.find(t=>t.title==='集成检查：整理课堂笔记'));
    assert.equal(task.note,'电脑离线保存，手机连接时同步');
    assert.equal(task.manualDue,null);
    await page.evaluate(id=>showDetail(id),task.id);
    await page.locator('#subtask-title').fill('检查第三章');
    await page.locator('#subtask-form button').click();
    await page.waitForFunction(id=>state.tasks.find(t=>t.id===id).subtasks.length===1,task.id);
    await page.evaluate(id=>showDetail(id),task.id);
    await page.locator('[data-step]').check();
    await page.waitForFunction(id=>state.tasks.find(t=>t.id===id).subtasks[0].done,task.id);
    await page.evaluate(id=>{
      const t=state.tasks.find(t=>t.id===id),step=t.subtasks[0];
      step.conflicts=[{field:'title',candidates:[{value:'电脑步骤'},{value:'手机步骤'}]}];
      showDetail(id);document.querySelector('[data-step-resolve][data-version="1"]').click();
    },task.id);
    await page.waitForFunction(id=>state.tasks.find(t=>t.id===id).subtasks[0].title==='手机步骤',task.id);
    await page.evaluate(id=>showDetail(id),task.id);
    const shell=await page.locator('.shell').boundingBox();
    assert.ok(shell.x>=200&&shell.width>700,'desktop uses the available width');
    await page.screenshot({path:path.resolve(__dirname,'../build/desktop-ui.png'),fullPage:true});
    await app.close();app=null;
    opened=await launch();app=opened.app;page=opened.page;
    await page.waitForFunction(id=>state.tasks.some(t=>t.id===id),task.id);
    assert.equal(await page.evaluate(id=>state.tasks.find(t=>t.id===id).subtasks[0].done,task.id),true);
    await page.evaluate(id=>Native.action('deleteTask',JSON.stringify({id})),task.id);
    await page.waitForFunction(id=>state.trash.some(t=>t.id===id),task.id);
    await page.evaluate(id=>Native.action('restoreTask',JSON.stringify({id})),task.id);
    await page.waitForFunction(id=>state.tasks.some(t=>t.id===id),task.id);
    assert.deepEqual(errors,[]);
  } finally {if(app)await app.close();await fs.rm(data,{recursive:true,force:true});}
});
