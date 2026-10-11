'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const {Core}=require('../host/core');

test('unexpected JVM exit restores persisted tasks without a desktop restart',{timeout:30000},async()=>{
  const data=await fs.mkdtemp(path.join(os.tmpdir(),'keqi-core-'));
  const core=new Core(path.resolve(__dirname,'../runtime/bin/java.exe'),path.resolve(__dirname,'../build/core'),data,()=>Promise.reject(new Error('No school session in test')));
  core.on('failure',()=>{});
  try{
    await core.call('initialize',{password:'temporary-integration-password'});
    const before=await core.call('createTask',{title:'进程恢复检查'});
    const saved=before.tasks.find(t=>t.title==='进程恢复检查');
    const recovered=new Promise((resolve,reject)=>{
      const timeout=setTimeout(()=>reject(new Error('Core recovery timed out')),12000);
      core.once('state',state=>{clearTimeout(timeout);resolve(state);});
    });
    core.process.kill();
    const after=await recovered;
    assert.ok(after.tasks.some(t=>t.id===saved.id));
    assert.ok((await core.call('getState')).tasks.some(t=>t.id===saved.id));
  }finally{core.close();await new Promise(resolve=>setTimeout(resolve,1500));await fs.rm(data,{recursive:true,force:true});}
});
