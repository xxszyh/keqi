'use strict';
const $ = id => document.getElementById(id);
const labels = {mooc:'中国大学 MOOC',chaoxing:'学习通',manual:'个人待办'};
const phases = {submit:'提交',peerreview:'互评',exam:'考试'};
const kinds = {homework:'作业',quiz:'测验',exam:'考试',todo:'待办'};
let state = {tasks:[],connections:[],courses:[],settings:{offsets:[72,24,3],syncMinutes:30,quietStart:'23:00',quietEnd:'07:00',urgentOverride:false},notificationEnabled:false};
let selectedFilter = 'pending';
let month = new Date(); month.setDate(1);
let day = dateKey(new Date());
let detailId = null;
let courseSelectionPlatform = 'all';
let toastTimer;
let currentPage='tasks',settingsSection='home',timeFilter='all',filterDraft=null,platformDetail=null;
let courseDraft=null,courseExpanded=new Set(),pendingCourseChange=null,lastCourseUndo=null;
let groupExpansion=new Map(),scrollPositions={tasks:0,calendar:0,settings:0},reminderDraft=null;
const expandDefaults=title=>!['日期待确认','之后','之前记录'].includes(title);
const tickIcon='<svg viewBox="0 0 24 24"><path d="m5 12 4 4 10-10"/></svg>';
function applyTheme(){const dark=!!state.settings?.darkMode;document.documentElement.classList.toggle('dark',dark);$('dark-mode').checked=dark;document.querySelector('meta[name=theme-color]').content=dark?'#1e2420':'#ffffff';}
function esc(s){return String(s ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function courseName(c){const name=String(c.title??'').trim();return c.platform==='chaoxing'&&(!name||/^(?:课程\s*[:：#]?\s*)?\d+$/.test(name))?'课程名称待更新':name;}
function taskCourseName(t){return courseName(state.courses.find(c=>c.key===t.courseKey&&c.platform===t.platform)||{title:t.courseTitle,platform:t.platform});}
function dateKey(d){return new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(d);}
function fmt(t){return new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(t));}
function done(t){return t.status==='done'||t.localDone;}
function due(t){return t.manualDue ?? t.due ?? null;}
function muted(t){return (state.removedCourses||[]).some(c=>c.key===t.courseKey)||state.courses.find(c=>c.key===t.courseKey)?.enabled===false;}
function activeTasks(){return state.tasks.filter(t=>!muted(t));}
function classify(t){const at=due(t);if(done(t))return 'done';if(!at)return t.platform==='manual'?'undated':'unknown';if(at<Date.now())return 'overdue';if(at-Date.now()<=86400000)return 'urgent';return 'pending';}
function remaining(t){const at=due(t);if(done(t))return t.localDone?'本地已处理':'平台已完成';if(!at)return t.platform==='manual'?'未设置截止':'截止待确认';const hours=(at-Date.now())/3600000;if(hours<0)return '已逾期';if(hours<1)return `${Math.max(1,Math.ceil(hours*60))} 分钟内`;if(hours<24)return `${Math.ceil(hours)} 小时内`;return `${Math.ceil(hours/24)} 天后`;}
function toast(message){$('toast').textContent=message;$('toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),1800);}
function call(action,data={}){if(window.Native){Native.action(action,JSON.stringify(data));}else{previewAction(action,data);}}
window.onNativeState = payload => {state=typeof payload==='string'?JSON.parse(payload):payload;render();verifyCourseChange();};
window.onNativeMessage = message => {toast(message);if(message.includes('失败')||message.includes('列表已变化'))pendingCourseChange=null;};
function render(){
  const tasks=activeTasks();
  $('today').textContent=new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',month:'long',day:'numeric',weekday:'long'}).format(new Date());
  $('count-urgent').textContent=tasks.filter(t=>classify(t)==='urgent').length;
  $('count-week').textContent=tasks.filter(t=>!done(t)&&due(t)&&due(t)>=Date.now()&&due(t)-Date.now()<=604800000).length;
  $('count-overdue').textContent=tasks.filter(t=>classify(t)==='overdue').length;
  $('count-unknown').textContent=tasks.filter(t=>classify(t)==='unknown').length;
  $('refresh').disabled=!!state.syncing;
  $('refresh').classList.toggle('is-syncing',!!state.syncing);
  $('refresh').setAttribute('aria-label',state.syncing?'正在同步':'同步作业和考试');
  applyTheme();
  $('count-today').textContent=tasks.filter(t=>!done(t)&&due(t)&&dateKey(new Date(due(t)))===dateKey(new Date())).length;
  $('count-pending').textContent=tasks.filter(t=>!done(t)).length;
  const successes=state.connections.filter(c=>c.lastSuccess).map(c=>c.lastSuccess);
  const attempts=state.connections.filter(c=>c.lastAttempt).map(c=>c.lastAttempt);
  const incomplete=state.connections.some(c=>['partial','error','needs_login'].includes(c.status));
  $('sync-label').textContent=state.syncing?'正在同步…':incomplete&&attempts.length?`上次尝试 ${fmt(Math.max(...attempts))}`:successes.length?`上次完整同步 ${fmt(Math.min(...successes))}`:'尚未同步';
  const issues=state.connections.filter(c=>['partial','error','needs_login'].includes(c.status));
  $('health-banner').classList.toggle('hidden',!issues.length);
  $('health-banner').innerHTML=issues.length?'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v6m0 4h.01"/></svg><span class="summary-text">'+esc(issues.map(c=>labels[c.platform]).join('、'))+'：'+(issues.some(c=>c.status==='needs_login')?'需要重新登录':issues.some(c=>c.status==='error')?'同步异常，查看详情':'部分内容未更新，查看详情')+'</span><span aria-hidden="true">›</span>':'';
  $('sync-details').innerHTML=state.connections.length?state.connections.map(c=>'<section><h3>'+esc(labels[c.platform]||c.platform)+'</h3><p>'+esc(c.message||'尚无同步结果')+(c.lastAttempt?'<br>上次尝试：'+fmt(c.lastAttempt):'')+(c.lastSuccess?'<br>上次完整同步：'+fmt(c.lastSuccess):'')+'</p></section>').join(''):'<p>尚未连接课程平台。</p>';
  const existing=$('course-filter').value;
  const visibleCourses=state.courses.filter(c=>c.enabled!==false);
  $('course-filter').innerHTML='<option value="all">所有已选课程</option>'+visibleCourses.map(c=>`<option value="${esc(c.key)}">${esc(courseName(c))}</option>`).join('');
  if(visibleCourses.some(c=>c.key===existing))$('course-filter').value=existing;
  $('course-scope-summary').textContent=$('course-filter').value==='all'?'全部保留课程':courseName(state.courses.find(c=>c.key===$('course-filter').value)||{});
  renderList();renderCalendar();renderConnections();
}
function deadlineText(t){const at=due(t);if(!at)return t.platform==='manual'?'未设置截止':'日期待确认';const today=dateKey(new Date());const key=dateKey(new Date(at));const time=new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(at));return key===today?'今天 '+time+' 截止':fmt(at)+' 截止';}
function card(t){const category=classify(t);const old=t.manualDue==null&&t.deadlineObserved===false&&due(t);const notes=[done(t)?remaining(t):'',t.status==='unknown'&&!t.localDone?'完成状态待确认':'',old?'截止沿用旧记录 '+(t.deadlineCheckedAt?fmt(t.deadlineCheckedAt):''):(!due(t)&&t.rawDeadline?t.rawDeadline:'')].filter(Boolean);return `<article class="assignment-card ${done(t)?'is-done':''}" data-task="${esc(t.id)}" tabindex="0" role="button" aria-label="查看 ${esc(t.title)}"><button class="local-check" data-local-task="${esc(t.id)}" aria-label="${t.localDone?'取消':'标记'} ${esc(t.title)} 的人工处理状态"><span class="circle">${done(t)?tickIcon:''}</span></button><h3>${esc(t.title)}</h3><p class="course-name">${esc(taskCourseName(t))}</p><div class="card-bottom"><span><span class="platform">${labels[t.platform]||esc(t.platform)}</span><span class="kind">${t.phase==='peerreview'?'作业互评':kinds[t.kind]||'作业'}</span></span><time class="${category}">${esc(deadlineText(t))}</time></div>${notes.length?'<div class="row-state '+(old?'stale':'')+'">'+notes.map(esc).join(' · ')+'</div>':''}</article>`;}
function groupedCards(tasks){
  const today=dateKey(new Date()),tomorrow=dateKey(new Date(Date.now()+86400000)),week=dateKey(new Date(Date.now()+7*86400000));const groups=new Map();
  for(const t of tasks){const at=due(t),key=at?dateKey(new Date(at)):null;const group=!at?(t.platform==='manual'?'无截止日期':'日期待确认'):!done(t)&&at<Date.now()?'已逾期':key<today?'之前记录':key===today?'今天 · '+new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',month:'long',day:'numeric'}).format(new Date()):key===tomorrow?'明天':key<=week?'7 天内':'之后';if(!groups.has(group))groups.set(group,[]);groups.get(group).push(t);}
  return [...groups].map(([title,items])=>{const open=$('search').value.trim()||timeFilter==='unknown'?true:groupExpansion.has(title)?groupExpansion.get(title):expandDefaults(title);return '<section><button class="group-heading" data-group="'+esc(title)+'" aria-expanded="'+open+'"><strong>'+esc(title)+'</strong><small>'+items.length+' 项 <span aria-hidden="true">'+(open?'⌃':'⌄')+'</span></small></button><div class="group-body '+(open?'':'hidden')+'">'+items.map(card).join('')+'</div></section>';}).join('')+'<div class="list-end">共 '+tasks.length+' 项</div>';
}
function filteredTasks(){const search=$('search').value.trim().toLowerCase(),pf=$('platform-filter').value,cf=$('course-filter').value,kf=$('kind-filter').value;
  return activeTasks().filter(t=>(pf==='all'||t.platform===pf)&&(cf==='all'||t.courseKey===cf)&&(kf==='all'||t.kind===kf)&&(!search||`${t.title} ${taskCourseName(t)} ${t.note||''} ${(t.tags||[]).join(' ')}`.toLowerCase().includes(search))).filter(t=>selectedFilter==='all'||selectedFilter==='done'&&done(t)||selectedFilter==='pending'&&!done(t)).filter(t=>timeFilter==='all'||timeFilter==='week'&&!done(t)&&due(t)>=Date.now()&&due(t)-Date.now()<=604800000||classify(t)===timeFilter).sort((a,b)=>(due(a)??Infinity)-(due(b)??Infinity));
}
function renderList(){const tasks=filteredTasks(),kf=$('kind-filter').value,cf=$('course-filter').value,pf=$('platform-filter').value;
  $('list-count').textContent=`本次筛选 ${tasks.length} 项`;$('list-title').textContent={pending:'待办',all:'所有记录',done:'已完成'}[selectedFilter];
  document.querySelectorAll('[data-kind]').forEach(b=>{const active=b.dataset.kind===kf;b.classList.toggle('active',active);b.setAttribute('aria-pressed',active);});
  const filters=[pf==='all'?'':labels[pf],cf==='all'?'':courseName(state.courses.find(c=>c.key===cf)||{}),timeFilter==='all'?'':{urgent:'24 小时内',week:'7 天内',overdue:'已逾期',unknown:'日期待确认'}[timeFilter]].filter(Boolean);
  $('filter-label').textContent=({pending:'待办',all:'全部状态',done:'已完成'}[selectedFilter]||'待办')+' · 筛选';
  $('active-filter-summary').classList.toggle('hidden',!filters.length);$('active-filter-summary').innerHTML=filters.map(x=>'<span>'+esc(x)+'</span>').join('')+'<button class="text-button" id="clear-active-filters">清除</button>';
  $('course-scope-summary').textContent=cf==='all'?'全部保留课程':courseName(state.courses.find(c=>c.key===cf)||{});
  const noneSelected=state.courses.length>0&&state.courses.every(c=>c.enabled===false);
  $('assignment-list').innerHTML=tasks.length?groupedCards(tasks):noneSelected?emptyHtml('还没有启用课程','在课程管理中开启需要提醒的课程。'):emptyHtml(state.tasks.length?'当前筛选没有记录':'先连接你的课程平台',state.tasks.length?'调整课程、类型或筛选条件。':'连接学习通和 MOOC，集中查看作业和考试。',!state.tasks.length);
  $('unknown-tools').classList.toggle('hidden',!tasks.some(t=>t.platform!=='manual'&&!due(t)));
}
function emptyHtml(title,description,connect=false){return `<div class="empty"><strong>${esc(title)}</strong><div>${esc(description)}</div>${connect?'<button class="primary" data-go-settings>连接课程平台</button>':''}</div>`;}
function renderCalendar(){
  const year=month.getFullYear(),mo=month.getMonth();$('month-title').textContent=`${year} 年 ${mo+1} 月`;
  const first=new Date(year,mo,1,12);const start=new Date(first);start.setDate(1-(first.getDay()+6)%7);
  const tasks=activeTasks().filter(t=>due(t)&&!done(t));
  const marked=new Set(tasks.map(t=>dateKey(new Date(due(t)))));
  let html='';for(let i=0;i<42;i++){const d=new Date(start);d.setDate(start.getDate()+i);const k=dateKey(d);const classes=[d.getMonth()!==mo?'outside':'',k===day?'selected':'',k===dateKey(new Date())?'today':'',marked.has(k)?'dot':''].join(' ');html+=`<button class="${classes}" data-day="${k}" aria-label="${k}">${d.getDate()}</button>`;}
  $('calendar-grid').innerHTML=html;$('selected-day-title').textContent=day.replaceAll('-',' / ');
  const selected=tasks.filter(t=>dateKey(new Date(due(t)))===day);
  $('day-list').innerHTML=selected.length?selected.map(card).join(''):emptyHtml('这一天没有待办截止','可以查看其他日期。');
  const missing=activeTasks().filter(t=>t.platform!=='manual'&&!done(t)&&!due(t)).length;
  $('calendar-unknown').classList.toggle('hidden',!missing);$('calendar-unknown').textContent=`还有 ${missing} 项截止时间待确认，仍保留在待办列表中。`;
}
function reminderLabel(hours){return hours>=24&&hours%24===0?hours/24+' 天前':hours>=1?hours+' 小时前':Math.round(hours*60)+' 分钟前';}
function renderReminders(){const values=reminderDraft||state.settings.offsets||[72,24,3];$('offsets').value=values.join(',');const choices=[...new Set([72,24,3,...values])].sort((a,b)=>b-a);$('reminder-presets').innerHTML=choices.map(n=>'<button data-offset="'+n+'" class="'+(values.includes(n)?'active':'')+'" aria-pressed="'+values.includes(n)+'">'+reminderLabel(n)+'</button>').join('');}
function renderConnections(){
  const expanded=[...document.querySelectorAll('.course-summary[open]')].map(el=>el.dataset.platform);
  const names={unconfigured:'未连接',ok:'已同步',partial:'部分同步',needs_login:'需要登录',error:'同步异常'};
  $('connections').innerHTML=['mooc','chaoxing'].map(p=>{const c=state.connections.find(c=>c.platform===p)||{};return '<button class="settings-entry" data-platform="'+p+'"><span>'+labels[p]+'<small>'+esc(names[c.status]||'未连接')+'</small></span><span class="chevron">›</span></button>';}).join('');
  $('platform-summary').textContent=['mooc','chaoxing'].map(p=>labels[p]+' '+(names[state.connections.find(c=>c.platform===p)?.status]||'未连接')).join(' · ');
  $('management-summary').textContent=state.courses.filter(c=>c.enabled!==false).length+' / '+state.courses.length+' 门课程已启用';
  $('reminder-summary').textContent=(state.settings.offsets||[72,24,3]).map(reminderLabel).join('、');
  $('notification-summary').textContent=state.notificationEnabled?'通知已开启 · 后台设置':'通知未开启';$('appearance-summary').textContent=state.settings.darkMode?'深色':'浅色';
  $('notification-state').textContent=state.notificationEnabled?'App 通知已开启':'App 通知尚未开启';$('notification-permission').textContent=state.notificationEnabled?'管理通知权限':'开启 App 通知';
  if(!reminderDraft&&!document.activeElement?.closest('#settings-reminders')){const settings=state.settings||{};$('sync-minutes').value=String(settings.syncMinutes||30);$('quiet-start').value=settings.quietStart??'23:00';$('quiet-end').value=settings.quietEnd??'07:00';$('urgent-override').checked=!!settings.urgentOverride;}
  renderReminders();
  $('course-settings').innerHTML=['mooc','chaoxing'].map(p=>{const courses=state.courses.filter(c=>c.platform===p);return '<details class="course-summary" data-platform="'+p+'" '+(expanded.includes(p)?'open':'')+'><summary>'+labels[p]+'<small>'+courses.filter(c=>c.enabled!==false).length+' / '+courses.length+' 门启用</small></summary><p class="help">列表默认收起，在管理界面统一选择和移出课程。</p><button class="secondary" data-select-platform-courses="'+p+'">管理课程</button></details>';}).join('')+'<button class="secondary full-width" data-select-courses>管理两个平台的课程</button>';
  if(courseDraft&&$('course-dialog').open)renderCourseDraft();if($('picker-dialog').open)renderPicker();if($('reimport-dialog').open)renderReimport();if($('platform-dialog').open&&platformDetail)showPlatform(platformDetail);
}
function showPlatform(p){platformDetail=p;const c=state.connections.find(c=>c.platform===p)||{status:'unconfigured'};$('platform-dialog-title').textContent=labels[p];$('platform-dialog-content').innerHTML='<p>'+esc(c.message||'在官方页面完成登录后连接。')+'</p>'+(c.lastSuccess?'<p>上次完整同步 '+fmt(c.lastSuccess)+'</p>':'')+'<button class="primary full-width" data-login="'+p+'">'+(c.status==='unconfigured'?'连接平台':'重新登录')+'</button><button class="secondary full-width" data-select-platform-courses="'+p+'">管理课程</button>'+(p==='chaoxing'&&c.verificationUrl?'<button class="secondary full-width" data-verify-platform="chaoxing">打开验证页面</button>':'')+(p==='chaoxing'?'<button class="text-button" data-year>确认日期年份</button>':'')+(c.status!=='unconfigured'?'<details class="account-options"><summary>账号操作</summary><button class="text-button danger" data-disconnect="'+p+'">断开连接</button></details>':'');$('platform-dialog').showModal();}
function openCourseSelection(platform='all'){
  courseSelectionPlatform=platform;courseDraft={choices:new Map(),remove:new Set(),original:new Map(state.courses.map(c=>[c.key,c.enabled!==false]))};courseExpanded=new Set(platform==='all'?[]:[platform]);
  $('course-dialog-title').textContent=platform==='all'?'管理保留课程':labels[platform]+' · 课程管理';$('course-search').value='';renderCourseDraft();$('course-dialog').showModal();
}
function renderCourseDraft(){const query=$('course-search').value.trim().toLowerCase();let total=0,enabled=0;
  $('course-dialog-list').innerHTML=['mooc','chaoxing'].filter(p=>courseSelectionPlatform==='all'||p===courseSelectionPlatform).map(p=>{const courses=state.courses.filter(c=>c.platform===p);total+=courses.length;enabled+=courses.filter(c=>!courseDraft.remove.has(c.key)&&(courseDraft.choices.get(c.key)??c.enabled!==false)).length;const rows=courses.filter(c=>courseName(c).toLowerCase().includes(query));if(!rows.length)return '';const open=query||courseExpanded.has(p);return '<section class="course-section"><button class="group-heading" data-course-expand="'+p+'" aria-expanded="'+!!open+'"><strong>'+labels[p]+'</strong><small>'+courses.length+' 门 '+(open?'⌃':'⌄')+'</small></button><div class="'+(open?'':'hidden')+'"><div class="course-actions"><button class="text-button" data-course-bulk="show" data-scope="'+p+'">启用当前平台</button><button class="text-button" data-course-bulk="hide" data-scope="'+p+'">关闭当前平台提醒</button></div>'+rows.map(c=>{const removed=courseDraft.remove.has(c.key),on=courseDraft.choices.get(c.key)??c.enabled!==false;return '<div class="managed-course '+(removed?'will-remove':'')+'"><label class="course-switch"><div>'+esc(courseName(c))+'<small>'+ (removed?'保存后移出课期':on?'显示任务并提醒':'保留在管理中，隐藏任务')+'</small></div><input type="checkbox" data-course="'+esc(c.key)+'" aria-label="启用 '+esc(courseName(c))+'" '+(on?'checked':'')+' '+(removed?'disabled':'')+'></label><button class="remove-course text-button" data-remove-course="'+esc(c.key)+'">'+(removed?'撤销移出':'移出')+'</button></div>';}).join('')+'</div></section>';}).join('')||'<div class="empty">没有匹配的课程。</div>';
  $('course-draft-summary').textContent='启用 '+enabled+' / '+total+' 门'+(courseDraft.remove.size?' · 待移出 '+courseDraft.remove.size+' 门':'');
}
function saveCourseDraft(){if(!courseDraft)return;const remove=[...courseDraft.remove],choices={};for(const [key,value] of courseDraft.choices)if(courseDraft.original.get(key)!==value&&!courseDraft.remove.has(key))choices[key]=value;
  if(remove.length&&!confirm('移出 '+remove.length+' 门课程？移出后不再显示和提醒，后续同步也会保持排除。可在设置中重新导入。'))return;
  if(!remove.length&&!Object.keys(choices).length){$('course-dialog').close();courseDraft=null;return;}
  const undoChoices={};for(const key of Object.keys(choices))undoChoices[key]=courseDraft.original.get(key);
  pendingCourseChange={choices,remove,undo:{choices:undoChoices,restore:remove},message:remove.length?'已移出 '+remove.length+' 门课程':'已保存课程选择'};
  $('course-dialog').close();courseDraft=null;call('applyCourseChoices',{choices,remove});
}
function verifyCourseChange(){if(!pendingCourseChange)return;const change=pendingCourseChange;const match=change.remove.every(key=>(state.removedCourses||[]).some(c=>c.key===key))&&Object.entries(change.choices).every(([key,on])=>state.courses.some(c=>c.key===key&&(c.enabled!==false)===on));if(!match)return;lastCourseUndo=change.undo;pendingCourseChange=null;$('undo-message').textContent=change.message;$('undo-bar').classList.remove('hidden');setTimeout(()=>$('undo-bar').classList.add('hidden'),8000);}
function renderPicker(){const query=$('picker-search').value.trim().toLowerCase(),pf=$('platform-filter').value,cf=$('course-filter').value;
  const all='<button class="picker-option '+(cf==='all'?'selected':'')+'" data-pick-course="all"><span>全部保留课程</span><span>'+ (cf==='all'?tickIcon:'')+'</span></button>';
  $('picker-list').innerHTML=all+['mooc','chaoxing'].filter(p=>pf==='all'||p===pf).map(p=>{const courses=state.courses.filter(c=>c.enabled!==false&&c.platform===p&&courseName(c).toLowerCase().includes(query));return courses.length?'<h3 class="picker-group">'+labels[p]+'</h3>'+courses.map(c=>'<button class="picker-option '+(cf===c.key?'selected':'')+'" data-pick-course="'+esc(c.key)+'"><span>'+esc(courseName(c))+'</span><span>'+(cf===c.key?tickIcon:'')+'</span></button>').join(''):'';}).join('');
}
function openPicker(){$('picker-search').value='';renderPicker();$('picker-dialog').showModal();}
function renderReimport(){const q=$('reimport-search').value.trim().toLowerCase(),courses=(state.removedCourses||[]).filter(c=>courseName(c).toLowerCase().includes(q));$('reimport-list').innerHTML=courses.length?courses.map(c=>'<div class="reimport-course"><span>'+esc(courseName(c))+'<small>'+esc(labels[c.platform])+'</small></span><button class="secondary" data-reimport="'+esc(c.key)+'">重新导入</button></div>').join(''):'<div class="empty">没有已移出的匹配课程。</div>';}
function openFilters(){filterDraft={platform:$('platform-filter').value,course:$('course-filter').value,status:selectedFilter,time:timeFilter};renderFilters();$('filter-dialog').showModal();}
function renderFilters(){$('filter-platforms').innerHTML=[['all','所有平台'],['mooc','中国大学 MOOC'],['chaoxing','学习通'],['manual','个人待办']].map(([v,name])=>'<button data-filter-platform="'+v+'" class="'+(filterDraft.platform===v?'active':'')+'">'+name+'</button>').join('');document.querySelectorAll('#status-chips [data-filter]').forEach(b=>{const active=b.dataset.filter===filterDraft.status;b.classList.toggle('active',active);b.setAttribute('aria-pressed',active);});document.querySelectorAll('[data-time]').forEach(b=>{const active=b.dataset.time===filterDraft.time;b.classList.toggle('active',active);b.setAttribute('aria-pressed',active);});}
function applyFilters(){$('course-filter').value=filterDraft.course;$('platform-filter').value=filterDraft.platform;selectedFilter=filterDraft.status;timeFilter=filterDraft.time;const selected=state.courses.find(c=>c.key===$('course-filter').value);if(selected&&filterDraft.platform!=='all'&&selected.platform!==filterDraft.platform)$('course-filter').value='all';$('filter-dialog').close();filterDraft=null;renderList();}
function showSettingsSection(name){settingsSection=name;document.querySelectorAll('.settings-section').forEach(el=>el.classList.toggle('hidden',el.id!=='settings-'+name));$('page-title').textContent={home:'设置',platforms:'课程平台',courses:'课程管理',reminders:'提醒与免打扰',notifications:'通知与后台',appearance:'外观',about:'关于课期'}[name];$('settings-back').classList.toggle('hidden',name==='home');if(name==='reminders'){reminderDraft=[...(state.settings.offsets||[72,24,3])];renderReminders();}window.scrollTo(0,0);}
function showPage(page){scrollPositions[currentPage]=window.scrollY;currentPage=page;document.querySelectorAll('.page').forEach(el=>el.classList.toggle('hidden',el.id!==page+'-page'));document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));$('page-title').textContent={tasks:'课期',calendar:'日历',settings:'设置'}[page];$('search-button').classList.toggle('hidden',page!=='tasks');$('settings-back').classList.add('hidden');if(page==='settings'){settingsSection='home';showSettingsSection('home');}else window.scrollTo(0,scrollPositions[page]);if(page==='calendar')renderCalendar();}
window.handleBack=()=>{const open=document.querySelector('dialog[open]');if(open){open.close();if(open.id==='course-dialog')courseDraft=null;if(open.id==='filter-dialog')filterDraft=null;return true;}if(currentPage==='settings'&&settingsSection!=='home'){showSettingsSection('home');return true;}if(currentPage!=='tasks'){showPage('tasks');return true;}return false;};
function showDetail(id){const t=state.tasks.find(t=>t.id===id);if(!t)return;detailId=id;
 $('detail-content').innerHTML=`<span class="platform ${esc(t.platform)}">${labels[t.platform]}</span><h3>${esc(t.title)}</h3><p>${esc(taskCourseName(t))} · ${phases[t.phase]||'提交'}${t.kind==='exam'&&(t.examStart||t.rawStart)?'<br>开始：'+(t.examStart?fmt(t.examStart):esc(t.rawStart)):''}<br>截止：${due(t)?fmt(due(t)):esc(t.rawDeadline||'待确认')}<br>平台状态：${{done:'已完成',pending:'待完成',unknown:'待确认'}[t.status]}${t.localDone?'<br>人工标记：已处理':''}</p>${t.manualDue==null&&t.deadlineObserved===false&&due(t)?'<p class="source-note">截止沿用旧记录'+(t.deadlineCheckedAt?'，上次核实 '+fmt(t.deadlineCheckedAt):'')+'。本轮未读取到新的完整截止。</p>':''}<p class="source-note">人工标记和补充的日期保存在本机，不会改变平台任务。互评与提交分别提醒。</p><button class="primary" id="open-source">打开平台页面</button><button class="secondary" id="toggle-done">${t.localDone?'取消人工标记':'标记已处理'}</button><button class="secondary" id="snooze">稍后 1 小时提醒</button><label>补充或修正截止时间<input type="datetime-local" id="manual-deadline"></label><button class="secondary" id="save-deadline">保存补充时间</button>${t.manualDue?'<button class="text-button" id="reset-deadline">恢复平台时间</button>':''}`;
 $('detail-dialog').showModal();$('open-source').onclick=()=>call('openSource',{id});$('toggle-done').onclick=()=>{call('updateTask',{id,localDone:!t.localDone});$('detail-dialog').close();};$('snooze').onclick=()=>{call('updateTask',{id,snoozeUntil:Date.now()+3600000});$('detail-dialog').close();};$('save-deadline').onclick=()=>{const value=$('manual-deadline').value;if(!value)return toast('请先选择日期和时间');const ms=Date.parse(value+':00+08:00');if(!Number.isFinite(ms))return toast('日期格式不正确');call('updateTask',{id,manualDue:ms});$('detail-dialog').close();};if($('reset-deadline'))$('reset-deadline').onclick=()=>{call('updateTask',{id,manualDue:null});$('detail-dialog').close();};
}
document.addEventListener('click',e=>{
  const b=e.target.closest('button'),local=e.target.closest('[data-local-task]');
  if(local){const t=state.tasks.find(t=>t.id===local.dataset.localTask);if(!t)return;if(t.status==='done'&&!t.localDone)return toast('平台已完成，无需人工标记');if(confirm(t.localDone?'取消人工已处理标记？':'仅在课期标记已处理，不会改变平台的作业或考试状态。'))call('updateTask',{id:t.id,localDone:!t.localDone});return;}
  const task=e.target.closest('[data-task]');if(task)return showDetail(task.dataset.task);if(!b)return;
  if(b.dataset.page)showPage(b.dataset.page);if(b.hasAttribute('data-go-settings')){showPage('settings');showSettingsSection('platforms');}
  if(b.dataset.settingsOpen)showSettingsSection(b.dataset.settingsOpen);if(b.dataset.platform)showPlatform(b.dataset.platform);
  if(b.hasAttribute('data-select-courses'))openCourseSelection();if(b.dataset.selectPlatformCourses){if($('platform-dialog').open)$('platform-dialog').close();openCourseSelection(b.dataset.selectPlatformCourses);}
  if(b.dataset.courseExpand){courseExpanded.has(b.dataset.courseExpand)?courseExpanded.delete(b.dataset.courseExpand):courseExpanded.add(b.dataset.courseExpand);renderCourseDraft();}
  if(b.dataset.courseBulk&&courseDraft){state.courses.filter(c=>c.platform===b.dataset.scope).forEach(c=>courseDraft.choices.set(c.key,b.dataset.courseBulk==='show'));renderCourseDraft();}
  if(b.dataset.removeCourse&&courseDraft){const key=b.dataset.removeCourse;courseDraft.remove.has(key)?courseDraft.remove.delete(key):courseDraft.remove.add(key);renderCourseDraft();}
  if(b.dataset.pickCourse){$('course-filter').value=b.dataset.pickCourse;$('picker-dialog').close();renderList();}
  if(b.dataset.reimport)call('applyCourseChoices',{restore:[b.dataset.reimport]});
  if(b.dataset.kind){$('kind-filter').value=b.dataset.kind;renderList();}
  if(b.dataset.filter&&filterDraft){filterDraft.status=b.dataset.filter;renderFilters();}if(b.dataset.time&&filterDraft){filterDraft.time=b.dataset.time;renderFilters();}if(b.dataset.filterPlatform&&filterDraft){filterDraft.platform=b.dataset.filterPlatform;renderFilters();}
  if(b.dataset.group){const open=groupExpansion.has(b.dataset.group)?groupExpansion.get(b.dataset.group):expandDefaults(b.dataset.group);groupExpansion.set(b.dataset.group,!open);renderList();}
  if(b.dataset.offset){const n=Number(b.dataset.offset);if(!reminderDraft)reminderDraft=[...(state.settings.offsets||[72,24,3])];reminderDraft=reminderDraft.includes(n)?reminderDraft.filter(x=>x!==n):[...reminderDraft,n];renderReminders();}
  if(b.dataset.day){day=b.dataset.day;renderCalendar();}if(b.dataset.login)call('login',{platform:b.dataset.login});if(b.dataset.verifyPlatform)call('verifyPlatform',{platform:b.dataset.verifyPlatform});
  if(b.dataset.disconnect&&confirm('断开此设备的平台登录并停止采集？已保存任务及另一设备的登录不受影响。')){call('disconnect',{platform:b.dataset.disconnect});$('platform-dialog').close();}
  if(b.hasAttribute('data-year')){const year=prompt('设置学习通已连接课程的作答起始年份。跨学期课程请逐项补充日期。',String(state.settings.chaoxingYear||''));if(year!==null){if(year!==''&&!/^(20\d\d)$/.test(year))return toast('请输入四位年份');call('saveSettings',{chaoxingYear:year?Number(year):null});}}
  if(b.id==='clear-active-filters'){$('platform-filter').value='all';$('course-filter').value='all';timeFilter='all';renderList();}
});
document.addEventListener('keydown',e=>{if((e.key==='Enter'||e.key===' ')&&e.target.matches('[data-task]')){e.preventDefault();showDetail(e.target.dataset.task);}});
document.addEventListener('change',e=>{if(e.target.dataset.course&&courseDraft){courseDraft.choices.set(e.target.dataset.course,e.target.checked);renderCourseDraft();}});
$('search').addEventListener('input',renderList);
$('filter-button').onclick=openFilters;$('cancel-filters').onclick=()=>{$('filter-dialog').close();filterDraft=null;};$('reset-filters').onclick=()=>{filterDraft={platform:'all',course:'all',status:'pending',time:'all'};renderFilters();};$('apply-filters').onclick=applyFilters;
$('search-button').onclick=()=>{const hidden=$('search-toolbar').classList.toggle('hidden');if(!hidden)$('search').focus();else{$('search').value='';renderList();}};
$('health-banner').onclick=()=>$('sync-dialog').showModal();$('settings-sync-detail').onclick=()=>$('sync-dialog').showModal();$('close-sync').onclick=()=>$('sync-dialog').close();
$('course-search').addEventListener('input',renderCourseDraft);$('course-picker-button').onclick=openPicker;$('picker-search').addEventListener('input',renderPicker);$('close-picker').onclick=()=>$('picker-dialog').close();$('picker-management').onclick=()=>{$('picker-dialog').close();openCourseSelection();};
$('close-courses').onclick=saveCourseDraft;$('cancel-courses').onclick=()=>{$('course-dialog').close();courseDraft=null;};$('close-platform').onclick=()=>$('platform-dialog').close();
$('reimport-button').onclick=()=>{$('reimport-search').value='';renderReimport();$('reimport-dialog').showModal();};$('reimport-search').addEventListener('input',renderReimport);$('close-reimport').onclick=()=>$('reimport-dialog').close();
$('undo-courses').onclick=()=>{if(lastCourseUndo){call('applyCourseChoices',lastCourseUndo);lastCourseUndo=null;$('undo-bar').classList.add('hidden');}};
$('settings-back').onclick=()=>showSettingsSection('home');$('dark-mode').onchange=()=>call('saveSettings',{darkMode:$('dark-mode').checked});$('refresh').onclick=()=>call('sync');
$('prev-month').onclick=()=>{month.setMonth(month.getMonth()-1);renderCalendar();};$('next-month').onclick=()=>{month.setMonth(month.getMonth()+1);renderCalendar();};$('calendar-today').onclick=()=>{day=dateKey(new Date());month=new Date();month.setDate(1);renderCalendar();};$('close-detail').onclick=()=>$('detail-dialog').close();
$('add-reminder').onclick=()=>{const n=Number($('custom-hours').value);if(!Number.isFinite(n)||n<=0||n>720)return toast('请输入 0 到 720 之间的提醒小时');if(!reminderDraft)reminderDraft=[];reminderDraft=[...new Set([...reminderDraft,n])];$('custom-hours').value='';renderReminders();};
$('save-settings').onclick=()=>{const offsets=reminderDraft||state.settings.offsets||[];if(!offsets.length||offsets.length>12)return toast('请选择 1 到 12 个提醒时间');call('saveSettings',{offsets:[...new Set(offsets)],syncMinutes:Number($('sync-minutes').value),quietStart:$('quiet-start').value,quietEnd:$('quiet-end').value,urgentOverride:$('urgent-override').checked});reminderDraft=null;};
$('notification-permission').onclick=()=>call('notificationPermission');$('test-notification').onclick=()=>call('testNotification');$('battery-settings').onclick=()=>call('batterySettings');
for(const id of ['course-dialog','filter-dialog'])$(id).addEventListener('cancel',()=>{if(id==='course-dialog')courseDraft=null;else filterDraft=null;});
function previewAction(action,data){if(action==='applyCourseChoices'){const all=[...state.courses,...(state.removedCourses||[])];for(const c of all){if(data.choices&&Object.prototype.hasOwnProperty.call(data.choices,c.key)&&!c.removed)c.enabled=data.choices[c.key];if((data.remove||[]).includes(c.key))c.removed=true;if((data.restore||[]).includes(c.key))c.removed=false;}state.courses=all.filter(c=>!c.removed);state.removedCourses=all.filter(c=>c.removed);render();verifyCourseChange();return;}if(action==='getState')return render();if(action==='updateTask'){Object.assign(state.tasks.find(t=>t.id===data.id),data);render();return toast('预览中已更新');}if(action==='saveSettings'){Object.assign(state.settings,data);render();return toast('预览中已保存');}if(action==='toggleCourse'){state.courses.find(c=>c.key===data.key).enabled=data.enabled;render();return;}if(action==='toggleCourses'){state.courses.filter(c=>data.keys.includes(c.key)).forEach(c=>c.enabled=data.enabled);render();return;}toast('此功能需要在安卓 App 内使用');}
if(!window.Native){$('preview-banner').classList.remove('hidden');const now=Date.now();state={...state,notificationEnabled:false,courses:[{key:'cx-ds',title:'数据结构与算法',platform:'chaoxing'},{key:'mooc-math',title:'高等数学',platform:'mooc'},{key:'cx-eng',title:'大学英语',platform:'chaoxing'}],connections:[{platform:'mooc',status:'ok',message:'示例：已同步 1 门课程',lastSuccess:now-3600000},{platform:'chaoxing',status:'ok',message:'示例：已同步 2 门课程',lastSuccess:now-1800000}],tasks:[{id:'demo1',platform:'chaoxing',courseKey:'cx-ds',courseTitle:'数据结构与算法',title:'第三章 · 栈与队列练习',kind:'homework',phase:'submit',due:now+3*3600000,status:'pending',lastSeen:now-1800000},{id:'demo2',platform:'mooc',courseKey:'mooc-math',courseTitle:'高等数学',title:'第二周单元测验',kind:'quiz',phase:'submit',due:now+25*3600000,status:'pending',lastSeen:now-3600000},{id:'demo3',platform:'mooc',courseKey:'mooc-math',courseTitle:'高等数学',title:'函数与极限 · 同伴互评',kind:'homework',phase:'peerreview',due:now+3*86400000,status:'unknown',lastSeen:now-3600000},{id:'demo4',platform:'chaoxing',courseKey:'cx-eng',courseTitle:'大学英语',title:'Unit 2 阅读练习',kind:'homework',phase:'submit',due:now-5*3600000,status:'pending',lastSeen:now-1800000},{id:'demo5',platform:'chaoxing',courseKey:'cx-ds',courseTitle:'数据结构与算法',title:'实验报告 · 链表实现',kind:'homework',phase:'submit',due:null,rawDeadline:'10-15 18:00（年份待确认）',status:'unknown',lastSeen:now-1800000}]};render();}else{call('getState');}

if(window.Native)setInterval(()=>call('getState'),30000);
