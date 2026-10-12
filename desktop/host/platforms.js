'use strict';
const {BrowserWindow,session,Menu}=require('electron');
const HOME='https://mooc1-1.chaoxing.com/visit/interaction';
function trusted(address,platform,login=false){try{const u=new URL(address);if(u.protocol!=='https:'||u.username||u.password||u.port&&u.port!=='443')return false;return platform==='chaoxing'?(u.hostname==='chaoxing.com'||u.hostname.endsWith('.chaoxing.com')):platform==='mooc'&&(u.hostname==='icourse163.org'||u.hostname.endsWith('.icourse163.org')||login&&(u.hostname==='163.com'||u.hostname.endsWith('.163.com')));}catch{return false;}}
function target(address,platform){try{const u=new URL(address);if(platform==='chaoxing'&&u.hostname==='i.mooc.hdu.edu.cn'&&['/','/base','/base/'].includes(u.pathname))return HOME;if(u.protocol==='http:'){u.protocol='https:';if(u.port==='80')u.port='';}return trusted(u.href,platform,true)?u.href:null;}catch{return null;}}
async function bounded(operation,remaining){let timer;try{return await Promise.race([operation,new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('平台页面尚未就绪，请重试')),Math.max(1,remaining));})]);}finally{clearTimeout(timer);}}
class Platforms {
  constructor(connected){this.connected=connected;this.windows=new Map();this.readers=new Map();this.queues=new Map();}
  session(platform){if(!['mooc','chaoxing'].includes(platform))throw new Error('平台无效');const s=session.fromPartition('persist:keqi-'+platform);s.setPermissionRequestHandler((web,permission,callback)=>callback(false));s.setPermissionCheckHandler(()=>false);return s;}
  secure(window,platform,reading=false){
    const navigate=(event,url)=>{
      const next=target(url,platform);
      if(!next||reading&&!trusted(next,platform)){
        if(!reading){try{const u=new URL(url);if(platform==='chaoxing'&&u.protocol==='https:'&&!u.username&&!u.password&&(!u.port||u.port==='443'))return;}catch{}}
        if(window.keqiReadState)window.keqiReadState.blocked=true;
        event.preventDefault();
      }else if(next!==url){event.preventDefault();window.loadURL(next).catch(()=>{});}
    };
    window.webContents.setWindowOpenHandler(({url})=>{
      const next=target(url,platform);
      if(next&&(!reading||trusted(next,platform)))window.loadURL(next).catch(()=>{});
      else if(reading&&window.keqiReadState)window.keqiReadState.blocked=true;
      return {action:'deny'};
    });
    window.webContents.on('will-navigate',navigate);
    if(reading){
      window.webContents.on('will-redirect',(event,url,_inPlace,isMainFrame)=>{if(event.isMainFrame??isMainFrame)navigate(event,url||event.url);});
      window.webContents.on('did-start-navigation',(event,_url,inPlace,isMainFrame)=>{if((event.isMainFrame??isMainFrame)&&!(event.isSameDocument??inPlace)&&window.keqiReadState){window.keqiReadState.committed=false;window.keqiReadState.domReady=false;window.keqiReadState.revision++;}});
      window.webContents.on('did-navigate',()=>{if(window.keqiReadState)window.keqiReadState.committed=true;});
      window.webContents.on('dom-ready',()=>{if(window.keqiReadState)window.keqiReadState.domReady=true;});
      window.webContents.on('did-fail-load',(_event,code,_description,_url,isMainFrame)=>{if(isMainFrame&&code!==-3&&window.keqiReadState)window.keqiReadState.failed=true;});
    }
  }
  async login(platform,source){let w=this.windows.get(platform);if(w&&!w.isDestroyed()){w.show();w.focus();return;}w=new BrowserWindow({width:1120,height:780,title:platform==='mooc'?'中国大学 MOOC · 官方登录':'学习通 · 官方登录',webPreferences:{session:this.session(platform),nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true}});this.secure(w,platform);this.windows.set(platform,w);w.on('closed',()=>this.windows.delete(platform));
    w.setMenu(Menu.buildFromTemplate([{label:'连接',submenu:[{label:'完成登录并连接',click:()=>this.connected(platform,w)},{label:'打开课程首页',click:()=>w.loadURL(platform==='mooc'?'https://www.icourse163.org/home.htm#/home/course':HOME)},{label:'重新打开登录页',click:()=>w.loadURL(this.start(platform))},{label:'清除本机平台登录',click:async()=>{await this.session(platform).clearStorageData();await w.loadURL(this.start(platform));}}]}]));await w.loadURL(source&&trusted(source,platform)?source:this.start(platform));
  }
  start(p){return p==='mooc'?'https://www.icourse163.org/':'https://passport2.chaoxing.com/login?fid=&newversion=true&refer='+encodeURIComponent(HOME);}
  read(request){const platform=request.platform;const prior=this.queues.get(platform)||Promise.resolve();const result=prior.catch(()=>{}).then(()=>this.readOne(request));this.queues.set(platform,result);return result;}
  async readOne(request){const {platform,url,stage,body,contentType}=request;if(!trusted(url,platform))throw new Error('平台地址无效');const s=this.session(platform);
    if(stage==='cookies'){const cookies=await s.cookies.get({url});return {cookies:cookies.map(c=>c.name+'='+c.value).join('; ')};}
    if(platform==='chaoxing'&&!/(?:\/visit\/interaction|\/courselistdata|\/stucoursemiddle|\/studentcourse|\/studentstudy|\/mooc2\/work\/list|\/work(?:\/(?:task|dowork|doHomeWorkNew|selectWorkQuestionYiPiYue|selectWorkQuestion|doWork|viewWork))?|\/exam\/phone\/(?:examcode|task-list)|\/exam\/test\/examcode\/examlist)\/?$/.test(new URL(url).pathname))throw new Error('禁止读取非列表或详情入口');
    if(body!=null){const u=new URL(url);if(platform!=='mooc'||!/^\/web\/j\/(?:learnerCourseRpcBean\.getMyLearnedCoursePanelList|courseBean\.(?:getLastLearnedMocTermDto|getMocTermDto))\.rpc$/.test(u.pathname))throw new Error('RPC 入口无效');const cookies=await s.cookies.get({url});const token=cookies.find(c=>c.name==='NTESSTUDYSI');if(!token)return {status:401,url,html:''};const response=await s.fetch(url,{method:'POST',redirect:'error',headers:{'Content-Type':contentType,'edu-script-token':token.value,'Referer':'https://www.icourse163.org/'},body,signal:AbortSignal.timeout(30000)});const html=await response.text();if(html.length>12_000_000)throw new Error('响应过大');
      // Electron's net-backed fetch may leave Response.url empty. Redirects
      // are rejected, so the verified request URL is the response's origin.
      return {url,status:response.status,html};}
    // Rendered protected pages retain scripts, third-party resources and scoped cookies.
    let reader=this.readers.get(platform);if(!reader||reader.isDestroyed()){reader=new BrowserWindow({show:false,width:1200,height:900,webPreferences:{session:s,nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true,backgroundThrottling:false}});this.secure(reader,platform,true);this.readers.set(platform,reader);}
    const started=Date.now(),expires=started+30000;
    if(!request.keepPage||reader.keqiRequestedURL!==url){
      reader.keqiRequestedURL=null;
      const state=reader.keqiReadState={committed:false,domReady:false,blocked:false,failed:false,revision:0};
      // loadURL waits for all resources. A valid document can be ready while
      // a third-party image keeps loading; observe navigation errors separately.
      reader.loadURL(url).catch(error=>{if(reader.keqiReadState===state&&error.code!=='ERR_ABORTED'&&error.errno!==-3)state.failed=true;});
      reader.keqiRequestedURL=url;
    }
    while(Date.now()<expires){
      await new Promise(resolve=>setTimeout(resolve,500));
      if(reader.isDestroyed())throw new Error('读取窗口已关闭');
      const state=reader.keqiReadState;
      if(state.blocked||state.failed)throw new Error('平台页面加载失败');
      if(!state.committed||!state.domReady)continue;
      const revision=state.revision;
      let value;
      try{value=await bounded(reader.webContents.mainFrame.executeJavaScript('({url:location.href,html:document.documentElement.outerHTML,browserAccount:/^[1-9][0-9]*$/.test(String(window.webUser?.id||""))?String(window.webUser.id):""})'),expires-Date.now());}catch{if(Date.now()>=expires)break;continue;}
      if(state.blocked||state.failed)throw new Error('平台页面加载失败');
      if(!state.committed||!state.domReady||revision!==state.revision)continue;
      if(value.html.length>12_000_000||!trusted(value.url,platform))throw new Error('平台响应无效');
      if(/login|mlogin/.test(new URL(value.url).pathname))return {...value,status:401};
      if(value.html.length>600&&(Date.now()-started>=2000||/暂无课程|暂无作业|暂无考试/.test(value.html)))return {...value,status:200};
    }
    reader.keqiRequestedURL=null;
    throw new Error('平台页面尚未就绪，请重试');
  }
  async clear(platform){await this.session(platform).clearStorageData();}
  close(){for(const w of [...this.windows.values(),...this.readers.values()])if(!w.isDestroyed())w.destroy();}
}
module.exports={Platforms,trusted,target};
