'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const {_electron}=require(process.env.KEQI_PLAYWRIGHT||'playwright');

test('MOOC connection follows the authenticated document through Electron and the JVM',{timeout:45000},async()=>{
  const data=await fs.mkdtemp(path.join(os.tmpdir(),'keqi-platform-'));
  let app;
  try{
    app=await _electron.launch({executablePath:require('electron'),args:[path.join(__dirname,'fixtures/platform-app.cjs')],env:{...process.env,KEQI_DATA_DIR:data}});
    for(let i=0;i<100;i++){
      if(await app.evaluate(()=>globalThis.keqiConnectionFixture?.ready))break;
      await new Promise(resolve=>setTimeout(resolve,50));
    }
    assert.deepEqual(await app.evaluate(()=>globalThis.keqiConnectionFixture.connect('static')),{connected:true});
    assert.deepEqual(await app.evaluate(()=>globalThis.keqiConnectionFixture.connect('redirect')),{connected:true});
    assert.deepEqual(await app.evaluate(()=>globalThis.keqiConnectionFixture.connect('slow-redirect')),{connected:true});
    assert.deepEqual(await app.evaluate(()=>globalThis.keqiConnectionFixture.connect('runtime')),{connected:true});
    await assert.rejects(app.evaluate(()=>globalThis.keqiConnectionFixture.connect('missing-cookie')),/请先完成平台登录/);
    await assert.rejects(app.evaluate(()=>globalThis.keqiConnectionFixture.connect('mismatch')),/登录已失效/);
    await assert.rejects(app.evaluate(()=>globalThis.keqiConnectionFixture.connect('blocked')),/操作未完成/);
    await assert.rejects(app.evaluate(()=>globalThis.keqiConnectionFixture.connect('server-redirect')),/操作未完成/);
    assert.deepEqual(await app.evaluate(()=>globalThis.keqiConnectionFixture.connect('catalog')),{status:'ok',courses:1,kinds:['exam','homework']});
  }finally{if(app)await app.close();await fs.rm(data,{recursive:true,force:true});}
});

for(const mode of ['unfinished-resource','large-page'])test('MOOC connection handles '+mode,{timeout:40000},async()=>{
  const data=await fs.mkdtemp(path.join(os.tmpdir(),'keqi-platform-'));
  let app;
  try{
    app=await _electron.launch({executablePath:require('electron'),args:[path.join(__dirname,'fixtures/platform-app.cjs')],env:{...process.env,KEQI_DATA_DIR:data}});
    await app.evaluate(async()=>{for(let i=0;i<100&&!globalThis.keqiConnectionFixture?.ready;i++)await new Promise(resolve=>setTimeout(resolve,50));});
    const started=Date.now();
    const result=await app.evaluate((_electron,mode)=>globalThis.keqiConnectionFixture.connect(mode),mode);
    assert.deepEqual(result,mode==='large-page'?{status:'ok',courses:1,kinds:['exam','homework']}:{connected:true});
    if(mode==='unfinished-resource')assert.ok(Date.now()-started<6000,'course verification does not wait for unrelated resources');
  }finally{if(app)await app.close();await fs.rm(data,{recursive:true,force:true});}
});
