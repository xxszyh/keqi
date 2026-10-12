'use strict';
const {app,BrowserWindow,Tray,Menu,Notification,ipcMain,protocol,net,safeStorage,dialog,powerMonitor}=require('electron');
if(require('electron-squirrel-startup'))return;
const path=require('node:path');
const fs=require('node:fs');
const {randomBytes}=require('node:crypto');
const {pathToFileURL}=require('node:url');
const QRCode=require('qrcode');
const {Core}=require('./core');
const {Platforms}=require('./platforms');
protocol.registerSchemesAsPrivileged([{scheme:'keqi',privileges:{standard:true,secure:true,supportFetchAPI:true}}]);
if(process.env.KEQI_DATA_DIR)app.setPath('userData',path.resolve(process.env.KEQI_DATA_DIR));
app.setAppUserModelId('com.squirrel.Keqi.Keqi');
if(!app.requestSingleInstanceLock()){app.quit();process.exit(0);}
let window,core,platforms,tray,quitting=false,lastState=null,lastRead=0,timer;
const posted=new Set();
const ui=app.isPackaged?path.join(app.getAppPath(),'build/ui'):path.join(__dirname,'../build/ui');
function emit(channel,data){if(window&&!window.isDestroyed())window.webContents.send(channel,data);}
function show(){if(!window||window.isDestroyed())return;window.show();window.focus();}
function secret(folder){if(!safeStorage.isEncryptionAvailable())throw new Error('Windows 机密保护不可用');const file=path.join(folder,'tls.secret');if(fs.existsSync(file))return safeStorage.decryptString(fs.readFileSync(file));const value=randomBytes(32).toString('hex');fs.writeFileSync(file,safeStorage.encryptString(value));return value;}
function notify(key,title,body,id){if(!Notification.isSupported())return;const n=new Notification({title,body});n.on('click',()=>{show();if(id)window.webContents.executeJavaScript('showDetail('+JSON.stringify(id)+')').catch(()=>{});});n.show();}
function update(state){state.appVersion=app.getVersion();state.openAtLogin=app.getLoginItemSettings().openAtLogin;lastState=state;emit('state',state);reminders(state);}
function reminders(state){const now=Date.now(),settings=state.settings||{},start=settings.quietStart||'',end=settings.quietEnd||'',hm=new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Shanghai',hour:'2-digit',minute:'2-digit',hour12:false}).format(now);const quiet=start&&end&&start!==end&&(start<end?hm>=start&&hm<end:hm>=start||hm<end);
  for(const t of state.tasks||[]){const due=t.manualDue??t.due,c=(state.courses||[]).find(c=>c.key===t.courseKey);if(t.reminderSuppressed||!due||due<=now||t.localDone||t.status==='done'||t.snoozeUntil>now||c?.enabled===false||c?.removed)continue;const offsets=(settings.offsets||[72,24,3]).filter(h=>due-now<=h*3600000);if(!offsets.length||quiet&&!(settings.urgentOverride&&due-now<=10800000))continue;const offset=Math.min(...offsets),key=[t.id,due,offset].join(':');if(posted.has(key))continue;posted.add(key);notify(key,t.kind==='exam'?'考试提醒':'课期待办提醒',t.title+' · '+new Date(due).toLocaleString('zh-CN'),t.id);}
  const file=path.join(app.getPath('userData'),'notices.json');fs.writeFile(file,JSON.stringify([...posted]),()=>{});
}
const allowed=new Set(['getState','sync','login','openSource','disconnect','saveSettings','updateTask','createTask','deleteTask','restoreTask','addSubtask','updateSubtask','reorderSubtasks','materializeRange','changeSeries','batchTasks','resolveConflict','applyCourseChoices','startHost','pairingCode','approvePairing','revokeDevice','testNotification','notificationPermission','backup','restoreBackup','setStartup']);
app.whenReady().then(async()=>{
  protocol.handle('keqi',request=>{const u=new URL(request.url);if(u.hostname!=='app')return new Response('Forbidden',{status:403});const relative=decodeURIComponent(u.pathname).replace(/^\//,'')||'index.html',file=path.resolve(ui,relative);if(!file.startsWith(path.resolve(ui)+path.sep))return new Response('Forbidden',{status:403});return net.fetch(pathToFileURL(file).href);});
  const data=app.getPath('userData');fs.mkdirSync(data,{recursive:true});try{for(const key of JSON.parse(fs.readFileSync(path.join(data,'notices.json'),'utf8')))posted.add(key);}catch{}
  window=new BrowserWindow({width:1240,height:850,minWidth:780,minHeight:600,title:'课期',show:process.env.KEQI_TEST!=='1',autoHideMenuBar:true,webPreferences:{preload:path.join(__dirname,'preload.js'),nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true}});window.webContents.setWindowOpenHandler(()=>({action:'deny'}));window.webContents.on('will-navigate',(event,url)=>{if(!url.startsWith('keqi://app/'))event.preventDefault();});window.on('close',event=>{if(!quitting){event.preventDefault();window.hide();}});
  platforms=new Platforms(async(platform,login)=>{try{update(await core.call('connectPlatform',{platform}));login.close();emit('message','连接已验证，正在读取课程任务');}catch(error){dialog.showMessageBox(login,{type:'info',message:'尚未完成连接',detail:error.message});}});
  const resources=app.isPackaged?process.resourcesPath:path.join(__dirname,'..');core=new Core(path.join(resources,'runtime/bin/java.exe'),app.isPackaged?path.join(resources,'core'):path.join(resources,'build/core'),data,request=>platforms.read(request));core.on('state',update);core.on('failure',error=>emit('message',error.message));
  ipcMain.handle('action',async(event,action,payload)=>{
    if(event.sender!==window.webContents||event.senderFrame!==window.webContents.mainFrame||event.senderFrame.url!=='keqi://app/index.html'||!allowed.has(action)||typeof payload!=='string'||payload.length>500_000)throw new Error('界面动作无效');
    try{const input=JSON.parse(payload);if(action==='login'){await platforms.login(input.platform);return;}if(action==='openSource'){const t=lastState?.tasks.find(t=>t.id===input.id);if(t)await platforms.login(t.platform,t.sourceUrl);return;}
      if(action==='testNotification'){notify('test','课期通知测试','课期正在此电脑独立提醒。');return;}
      if(action==='notificationPermission'){emit('message','请在 Windows 设置中允许课期通知');return;}
      if(action==='setStartup'){app.setLoginItemSettings({openAtLogin:!!input.enabled});emit('message','开机启动设置已保存');return;}
      if(action==='pairingCode'){const pairing=await core.call(action,input);emit('pairing',{text:JSON.stringify(pairing),image:await QRCode.toDataURL(JSON.stringify(pairing),{width:320,margin:2})});return;}
      if(action==='backup'){const choice=await dialog.showSaveDialog(window,{defaultPath:'课期备份.json',filters:[{name:'课期数据',extensions:['json']}]});if(!choice.canceled){const backup=await core.call('backup');fs.writeFileSync(choice.filePath,JSON.stringify(backup,null,2),'utf8');emit('message','任务备份已导出，不含登录会话与配对密钥');}return;}
      if(action==='restoreBackup'){const choice=await dialog.showOpenDialog(window,{properties:['openFile'],filters:[{name:'课期任务备份',extensions:['json']}]});if(choice.canceled)return;const file=choice.filePaths[0];if(fs.statSync(file).size>16_000_000)throw new Error('备份文件过大');const archive=JSON.parse(fs.readFileSync(file,'utf8'));if(archive.format!=='keqi-data-v1'||!Array.isArray(archive.snapshot?.entities))throw new Error('不是课期任务备份');const confirm=await dialog.showMessageBox(window,{type:'question',buttons:['取消','合并恢复'],defaultId:0,cancelId:0,message:'恢复 '+archive.snapshot.entities.length+' 项任务及设置记录？',detail:'保留本机内容，不同修改保留为冲突；平台登录与手机配对仍使用当前设备的配置。'});if(confirm.response===1)update(await core.call('restoreBackup',archive));return;}
      if(action==='disconnect')await platforms.clear(input.platform);
      update(await core.call(action,input));
    }catch(error){emit('message',error.message||'操作失败，请重试');}
  });
  const {nativeImage}=require('electron');const icon=nativeImage.createFromPath(path.join(__dirname,'../assets/icon.png')).resize({width:16,height:16});tray=new Tray(icon);tray.setToolTip('课期 · 托盘运行中');tray.setContextMenu(Menu.buildFromTemplate([{label:'打开课期',click:show},{label:'刷新平台任务',click:()=>core.call('sync').then(update).catch(()=>{})},{label:'退出课期',click:()=>{quitting=true;app.quit();}}]));tray.on('double-click',show);
  await window.loadURL('keqi://app/index.html');update(await core.call('initialize',{password:secret(data)}));
  timer=setInterval(async()=>{try{const state=await core.call('getState');update(state);if(Date.now()-lastRead>(state.settings.syncMinutes||30)*60000){lastRead=Date.now();update(await core.call('sync'));}}catch(error){emit('message',error.message);}},15000);
  powerMonitor.on('resume',()=>core.call('sync').then(update).catch(()=>{}));
}).catch(error=>{dialog.showErrorBox('课期启动失败',error.message);quitting=true;app.quit();});
app.on('second-instance',show);
app.on('before-quit',()=>{quitting=true;clearInterval(timer);platforms?.close();core?.close();});
