'use strict';
const {contextBridge,ipcRenderer}=require('electron');
const allowed=new Set(['getState','sync','login','openSource','disconnect','saveSettings','updateTask','createTask','deleteTask','restoreTask','addSubtask','updateSubtask','reorderSubtasks','materializeRange','changeSeries','batchTasks','resolveConflict','applyCourseChoices','startHost','pairingCode','approvePairing','revokeDevice','testNotification','notificationPermission','backup','restoreBackup','setStartup']);
contextBridge.exposeInMainWorld('Native',{
  desktop:true,
  action(action,payload){if(!allowed.has(action)||typeof payload!=='string'||payload.length>500_000)return;ipcRenderer.invoke('action',action,payload).catch(()=>{});},
  subscribe(channel,callback){if(!['state','message','pairing'].includes(channel)||typeof callback!=='function')return;const listener=(_,data)=>callback(data);ipcRenderer.on(channel,listener);return ()=>ipcRenderer.removeListener(channel,listener);}
});
