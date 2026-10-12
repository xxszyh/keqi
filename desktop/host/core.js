'use strict';
const {spawn}=require('node:child_process');
const {createInterface}=require('node:readline');
const {randomUUID}=require('node:crypto');
const path=require('node:path');
const {EventEmitter}=require('node:events');
const MAX_MESSAGE_CHARS=16_000_000;

class Core extends EventEmitter {
  constructor(java,folder,dataFolder,readPage){
    super();this.pending=new Map();this.launchArgs={java,folder,dataFolder,readPage};this.restarts=0;this.closed=false;this.dead=false;this.boot();
  }
  boot(){
    const {java,folder,dataFolder,readPage}=this.launchArgs;this.dead=false;this.process=spawn(java,['-cp',`${path.join(folder,'classes')};${path.join(folder,'*')}`,'com.keqi.reminder.DesktopMain',dataFolder],{windowsHide:true,stdio:['pipe','pipe','pipe']});
    createInterface({input:this.process.stdout}).on('line',async line=>{
      if(line.length>MAX_MESSAGE_CHARS)return this.fail(new Error('核心响应过大'));
      let message;try{message=JSON.parse(line);}catch{return this.fail(new Error('核心响应格式错误'));}
      if(message.type==='readPage'){
        try{const data=await readPage(message.data);this.write({type:'pageResult',id:message.id,data});}catch{this.write({type:'pageResult',id:message.id,error:'平台浏览器读取未完成'});}return;
      }
      if(message.type==='state'){this.emit('state',message.data);return;}
      const item=this.pending.get(message.id);if(!item)return;clearTimeout(item.timer);this.pending.delete(message.id);message.error?item.reject(new Error(message.error)):item.resolve(message.data);
    });
    this.process.stderr.on('data',()=>{});
    this.process.on('error',()=>this.fail(new Error('无法启动任务核心')));
    this.process.on('exit',()=>{this.dead=true;if(this.closed)return;this.fail(new Error('任务核心已停止，正在恢复本机数据'));if(this.initialization&&this.restarts<3){const delay=1000*2**this.restarts++;this.restartTimer=setTimeout(()=>{if(this.closed)return;this.boot();this.call('initialize',this.initialization).then(data=>this.emit('state',data)).catch(()=>{});},delay);}});
  }
  write(message){if(this.closed||this.process.stdin.destroyed||this.process.stdin.writableEnded)return;const line=JSON.stringify(message);if(line.length>MAX_MESSAGE_CHARS)throw new Error('请求或平台响应过大');this.process.stdin.write(line+'\n');}
  call(action,data={}){if(this.closed||this.dead)return Promise.reject(new Error("任务核心暂不可用，已保存的数据仍保留"));if(action==='initialize')this.initialization=data;const id=randomUUID();return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{this.pending.delete(id);reject(new Error('操作等待超时，数据仍保留在本机'));},90_000);this.pending.set(id,{resolve,reject,timer});try{this.write({type:'command',id,action,data});}catch(error){clearTimeout(timer);this.pending.delete(id);reject(error);}});}
  fail(error){for(const item of this.pending.values()){clearTimeout(item.timer);item.reject(error);}this.pending.clear();this.emit('failure',error);}
  close(){this.closed=true;clearTimeout(this.restartTimer);this.process.stdin.end();setTimeout(()=>this.process.kill(),1000).unref();}
}
module.exports={Core};
