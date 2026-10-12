'use strict';
const {app}=require('electron');
const path=require('node:path');
const root=path.resolve(__dirname,'../..');
const {Platforms}=require(path.join(root,'host/platforms'));
const {Core}=require(path.join(root,'host/core'));
app.setPath('userData',process.env.KEQI_DATA_DIR);
let platforms,core;
let mode='static';
const pendingResources=[];
app.whenReady().then(async()=>{
  platforms=new Platforms(()=>{});
  const session=platforms.session('mooc');
  session.protocol.handle('https',async request=>{
    const url=new URL(request.url);
    if(url.hostname!=='www.icourse163.org')return new Response('Not found',{status:404});
    if(['catalog','large-page'].includes(mode)&&url.pathname.startsWith('/web/j/')){
      if(url.pathname.endsWith('getMyLearnedCoursePanelList.rpc')){
        const form=new URLSearchParams(await request.text());
        const list=form.get('courseType')==='1'?[{id:101,name:'匿名课程',termPanel:{id:202},schoolPanel:{shortName:'SAMPLE'}}]:[];
        return Response.json({code:0,result:{list,query:{totlePageCount:1}}});
      }
      return Response.json({code:0,result:{units:[{id:303,name:'匿名作业',contentType:3,submitStatus:'未完成',submitEndTime:'2026-11-01 23:59'},{id:304,name:'匿名考试',contentType:6,submitStatus:'未完成',examEndTime:'2026-11-02 23:59'}]}});
    }
    if(url.pathname==='/identity.js')return new Response('window.webUser={id:'+(mode==='mismatch'?8:7)+'};',{headers:{'content-type':'text/javascript'}});
    if(url.pathname==='/slow-image.png')return new Promise(resolve=>setTimeout(()=>resolve(new Response('',{headers:{'content-type':'image/png'}})),2000));
    if(url.pathname==='/late-image.png')return new Promise(resolve=>setTimeout(()=>resolve(new Response('',{headers:{'content-type':'image/png'}})),7000));
    if(url.pathname==='/unfinished-image.png')return new Promise(resolve=>pendingResources.push(resolve));
    if((mode==='redirect'||mode==='slow-redirect')&&!url.searchParams.has('userId'))return new Response('<img src="/slow-image.png"><script>setTimeout(()=>location.replace("http://www.icourse163.org/home.htm?userId=7#/home/course"),25)</script>',{headers:{'content-type':'text/html;charset=utf-8'}});
    if(mode==='server-redirect')return Response.redirect('https://example.invalid/');
    const identity=mode==='runtime'||mode==='missing-cookie'?'<script src="/identity.js"></script>':'<script>window.webUser={id:7};</script>'+(mode==='mismatch'?'<script src="/identity.js"></script>':'');
    const redirect=mode==='blocked'?'<img src="/slow-image.png"><script>setTimeout(()=>location.replace("https://example.invalid/"),25)</script>':mode==='slow-redirect'?'<img src="/late-image.png">':mode==='unfinished-resource'?'<img src="/unfinished-image.png">':'';
    const padding=mode==='large-page'?'匿名'.repeat(1100000):'课程样例 '.repeat(100);
    return new Response('<html><head>'+identity+'</head><body><h1>我的课程 · 匿名样例</h1><div>'+padding+'</div>'+redirect+'</body></html>',{headers:{'content-type':'text/html;charset=utf-8'}});
  });
  const connect=async next=>{
    mode=next;
    await session.cookies.remove('https://www.icourse163.org/','NTESSTUDYSI');
    if(next!=='missing-cookie')await session.cookies.set({url:'https://www.icourse163.org/',name:'NTESSTUDYSI',value:'synthetic-login-token',secure:true,httpOnly:true});
    let calls=0;
    core=new Core(path.join(root,'runtime/bin/java.exe'),path.join(root,'build/core'),path.join(process.env.KEQI_DATA_DIR,next),request=>{
      if(++calls>2&&!['catalog','large-page'].includes(next))throw new Error('No background task reads in connection fixture');
      return platforms.read(request);
    });
    core.on('failure',()=>{});
    try{
      let state=await core.call('connectPlatform',{platform:'mooc'});
      if(['catalog','large-page'].includes(next)){
        const expires=Date.now()+10000;
        while(!state.connections.find(c=>c.platform==='mooc')?.lastAttempt&&Date.now()<expires){await new Promise(resolve=>setTimeout(resolve,100));state=await core.call('getState');}
        return {status:state.connections.find(c=>c.platform==='mooc')?.status,courses:state.courses.length,kinds:state.tasks.map(t=>t.kind).sort()};
      }
      return {connected:state.connections.find(c=>c.platform==='mooc')?.connected};
    }finally{core.close();while(pendingResources.length)pendingResources.pop()(new Response(''));}
  };
  globalThis.keqiConnectionFixture={connect,ready:true};
});
app.on('before-quit',()=>{core?.close();platforms?.close();});
