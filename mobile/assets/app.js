'use strict';
const $ = id => document.getElementById(id);
const labels = {mooc:'中国大学 MOOC',chaoxing:'学习通'};
const phases = {submit:'提交',peerreview:'互评',exam:'考试'};
const kinds = {homework:'作业',quiz:'测验',exam:'考试'};
let state = {tasks:[],connections:[],courses:[],settings:{offsets:[72,24,3],syncMinutes:30,quietStart:'23:00',quietEnd:'07:00',urgentOverride:false},notificationEnabled:false};
let selectedFilter = 'pending';
let month = new Date(); month.setDate(1);
let day = dateKey(new Date());
let detailId = null;
let courseSelectionPlatform = 'all';
let toastTimer;
const tickIcon='<svg viewBox="0 0 24 24"><path d="m5 12 4 4 10-10"/></svg>';
function applyTheme(){const dark=!!state.settings?.darkMode;document.documentElement.classList.toggle('dark',dark);$('dark-mode').checked=dark;document.querySelector('meta[name=theme-color]').content=dark?'#1e2420':'#ffffff';}
function esc(s){return String(s ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function courseName(c){const name=String(c.title??'').trim();return c.platform==='chaoxing'&&(!name||/^(?:课程\s*[:：#]?\s*)?\d+$/.test(name))?'课程名称待更新':name;}
function taskCourseName(t){return courseName(state.courses.find(c=>c.key===t.courseKey&&c.platform===t.platform)||{title:t.courseTitle,platform:t.platform});}
function dateKey(d){return new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(d);}
function fmt(t){return new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(t));}
function done(t){return t.status==='done'||t.localDone;}
function due(t){return t.manualDue ?? t.due ?? null;}
function muted(t){return state.courses.find(c=>c.key===t.courseKey)?.enabled===false;}
function activeTasks(){return state.tasks.filter(t=>!muted(t));}
function classify(t){const at=due(t);if(done(t))return 'done';if(!at)return 'unknown';if(at<Date.now())return 'overdue';if(at-Date.now()<=86400000)return 'urgent';return 'pending';}
function remaining(t){const at=due(t);if(done(t))return t.localDone?'本地已处理':'平台已完成';if(!at)return '截止待确认';const hours=(at-Date.now())/3600000;if(hours<0)return '已逾期';if(hours<1)return `${Math.max(1,Math.ceil(hours*60))} 分钟内`;if(hours<24)return `${Math.ceil(hours)} 小时内`;return `${Math.ceil(hours/24)} 天后`;}
function toast(message){$('toast').textContent=message;$('toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),3300);}
function call(action,data={}){if(window.Native){Native.action(action,JSON.stringify(data));}else{previewAction(action,data);}}
window.onNativeState = payload => {state=typeof payload==='string'?JSON.parse(payload):payload;render();};
window.onNativeMessage = message => toast(message);
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
  $('course-scope-summary').textContent=state.courses.length?`选择课程 · ${visibleCourses.length} / ${state.courses.length} 门`:'同步后可选择要显示的课程';
  renderList();renderCalendar();renderConnections();
}
function deadlineText(t){const at=due(t);if(!at)return '日期待确认';const today=dateKey(new Date());const key=dateKey(new Date(at));const time=new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(at));return key===today?'今天 '+time+' 截止':fmt(at)+' 截止';}
function card(t){const category=classify(t);const old=t.manualDue==null&&t.deadlineObserved===false&&due(t);const notes=[done(t)?remaining(t):'',t.status==='unknown'&&!t.localDone?'完成状态待确认':'',old?'截止沿用旧记录 '+(t.deadlineCheckedAt?fmt(t.deadlineCheckedAt):''):(!due(t)&&t.rawDeadline?t.rawDeadline:'')].filter(Boolean);return `<article class="assignment-card ${done(t)?'is-done':''}" data-task="${esc(t.id)}" tabindex="0" role="button" aria-label="查看 ${esc(t.title)}"><button class="local-check" data-local-task="${esc(t.id)}" aria-label="${t.localDone?'取消':'标记'} ${esc(t.title)} 的人工处理状态"><span class="circle">${done(t)?tickIcon:''}</span></button><h3>${esc(t.title)}</h3><p class="course-name">${esc(taskCourseName(t))}</p><div class="card-bottom"><span><span class="platform">${labels[t.platform]||esc(t.platform)}</span><span class="kind">${t.phase==='peerreview'?'作业互评':kinds[t.kind]||'作业'}</span></span><time class="${category}">${esc(deadlineText(t))}</time></div>${notes.length?'<div class="row-state '+(old?'stale':'')+'">'+notes.map(esc).join(' · ')+'</div>':''}</article>`;}
function groupedCards(tasks){const today=dateKey(new Date()),tomorrow=dateKey(new Date(Date.now()+86400000)),week=dateKey(new Date(Date.now()+7*86400000));const groups=new Map();for(const t of tasks){const at=due(t),key=at?dateKey(new Date(at)):null;let group=!at?'日期待确认':!done(t)&&at<Date.now()?'已逾期':key<today?'之前记录':key===today?'今天 · '+new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',month:'long',day:'numeric'}).format(new Date()):key===tomorrow?'明天':key<=week?'7 天内':'之后';if(!groups.has(group))groups.set(group,[]);groups.get(group).push(t);}return [...groups].map(([title,items])=>'<section><div class="group-heading"><strong>'+esc(title)+'</strong><small>'+items.length+' 项</small></div>'+items.map(card).join('')+'</section>').join('')+'<div class="list-end">共 '+tasks.length+' 项</div>';}
function renderList(){
  const search=$('search').value.trim().toLowerCase();const pf=$('platform-filter').value,cf=$('course-filter').value,kf=$('kind-filter').value;
  let tasks=activeTasks().filter(t=>(pf==='all'||t.platform===pf)&&(cf==='all'||t.courseKey===cf)&&(kf==='all'||t.kind===kf)&&(!search||`${t.title} ${taskCourseName(t)}`.toLowerCase().includes(search)));
  tasks=tasks.filter(t=>selectedFilter==='all'||selectedFilter==='done'&&done(t)||selectedFilter==='pending'&&!done(t)&&!muted(t)||selectedFilter==='week'&&!done(t)&&!muted(t)&&due(t)>=Date.now()&&due(t)-Date.now()<=604800000||classify(t)===selectedFilter&&!muted(t));
  tasks.sort((a,b)=>(due(a)??Infinity)-(due(b)??Infinity));
  $('list-count').textContent=`${tasks.length} 项`;
  document.querySelectorAll('#status-chips button').forEach(b=>b.classList.toggle('active',b.dataset.filter===selectedFilter));
  const titles={urgent:'24 小时内截止',week:'7 天内待办',overdue:'已逾期待办',unknown:'截止待确认'};
  $('list-title').textContent=titles[selectedFilter]||'按截止时间排列';
  const noneSelected=state.courses.length>0&&state.courses.every(c=>c.enabled===false);
  document.querySelectorAll('[data-kind]').forEach(b=>{const active=b.dataset.kind===kf;b.classList.toggle('active',active);b.setAttribute('aria-pressed',active);});
  document.querySelectorAll('.metrics button').forEach(b=>b.classList.toggle('active',b.dataset.filter===selectedFilter));
  $('filter-label').textContent=({pending:'待办',all:'全部状态',done:'已完成',...titles}[selectedFilter]||'待办')+' · 筛选';
  $('assignment-list').innerHTML=tasks.length?groupedCards(tasks):noneSelected?emptyHtml('还没有选择课程','点上方「选择课程」，勾选需要做的课程。'):emptyHtml(state.tasks.length?'这里暂时没有待办':'先连接你的课程平台',state.tasks.length?'切换筛选查看其他作业和考试。':'连接学习通和 MOOC，作业和考试会集中显示在这里。',!state.tasks.length);
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
  const missing=activeTasks().filter(t=>!done(t)&&!due(t)).length;
  $('calendar-unknown').classList.toggle('hidden',!missing);$('calendar-unknown').textContent=`还有 ${missing} 项截止时间待确认，仍保留在待办列表中。`;
}
function renderConnections(){
  const names={unconfigured:'未连接',ok:'已同步',partial:'部分同步',needs_login:'需要登录',error:'同步失败'};
  $('connections').innerHTML=['mooc','chaoxing'].map(platform=>{const c=state.connections.find(c=>c.platform===platform)||{status:'unconfigured'};const courses=state.courses.filter(course=>course.platform===platform);return `<div class="connection-card"><div class="connection-title"><strong>${labels[platform]}</strong><span class="platform ${platform}">${c.connected&&c.status==='partial'&&!c.lastAttempt?'已连接':names[c.status]||'等待同步'}</span></div><p>${esc(c.message||'在平台官方页面登录后，授权本机读取你的课程。')}${c.lastSuccess?`<br>上次完整同步：${fmt(c.lastSuccess)}`:''}</p><button class="primary" data-login="${platform}">${c.status==='unconfigured'?'连接平台':'重新登录'}</button>${c.status!=='unconfigured'?`<button class="secondary" data-disconnect="${platform}">断开连接</button>`:''}${courses.length||c.connected?`<button class="secondary" data-select-platform-courses="${platform}">选择课程（${courses.length}）</button>`:''}${platform==='chaoxing'&&c.verificationUrl?'<button class="secondary" data-verify-platform="chaoxing">打开验证页面</button>':''}${platform==='chaoxing'?'<button class="text-button" data-year>确认日期年份</button>':''}</div>`;}).join('');
  $('notification-state').textContent=state.notificationEnabled?'✓ App 通知已开启':'App 通知尚未开启，提醒可能无法显示';
  const settings=state.settings||{};
  if(document.activeElement?.closest('.settings-card')===null||!document.activeElement?.closest('.settings-card')){
    $('offsets').value=(settings.offsets||[72,24,3]).join(',');$('sync-minutes').value=String(settings.syncMinutes||30);$('quiet-start').value=settings.quietStart??'23:00';$('quiet-end').value=settings.quietEnd??'07:00';$('urgent-override').checked=!!settings.urgentOverride;
  }
  $('course-settings').innerHTML=courseChoices();$('course-dialog-list').innerHTML=courseChoices(courseSelectionPlatform);filterCourseSearch();
}
function openCourseSelection(platform='all'){
  courseSelectionPlatform=platform;
  $('course-dialog-title').textContent=platform==='all'?'选择要显示的课程':`${labels[platform]} · 选择课程`;
  $('course-search').value='';$('course-dialog-list').innerHTML=courseChoices(platform);$('course-dialog').showModal();
}
function filterCourseSearch(){const query=$('course-search').value.trim().toLowerCase();$('course-dialog-list').querySelectorAll('.course-switch').forEach(row=>row.classList.toggle('hidden',!row.textContent.toLowerCase().includes(query)));}
function courseChoices(scope='all'){
  if(!state.courses.some(c=>scope==='all'||c.platform===scope))return `<p class="help">${scope==='all'?'连接平台并读取课程后，课程会显示在这里。':labels[scope]+'课程尚未读取，请完成连接或重新同步。'}</p>`;
  const bulk='<div class="course-actions"><button class="secondary" data-course-bulk="show">全选</button><button class="secondary" data-course-bulk="hide">全部隐藏</button></div>';
  return bulk+['mooc','chaoxing'].filter(platform=>scope==='all'||platform===scope).map(platform=>{const courses=state.courses.filter(c=>c.platform===platform);if(!courses.length)return '';return `<h3 class="course-group-title">${labels[platform]} <small>${courses.filter(c=>c.enabled!==false).length} / ${courses.length} 门已选</small></h3>`+courses.map(c=>`<label class="course-switch"><div>${esc(courseName(c))}<small>${c.enabled===false?'已隐藏，不提醒':'显示作业和考试并提醒'}</small></div><input type="checkbox" aria-label="显示 ${esc(courseName(c))} 的作业和考试" data-course="${esc(c.key)}" ${c.enabled===false?'':'checked'}></label>`).join('');}).join('');
}
function showPage(page){document.querySelectorAll('.page').forEach(el=>el.classList.toggle('hidden',el.id!==page+'-page'));document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));$('page-title').textContent={tasks:'课期',calendar:'日历',settings:'设置'}[page];$('search-button').classList.toggle('hidden',page!=='tasks');window.scrollTo(0,0);if(page==='calendar')renderCalendar();}
function showDetail(id){const t=state.tasks.find(t=>t.id===id);if(!t)return;detailId=id;
 $('detail-content').innerHTML=`<span class="platform ${esc(t.platform)}">${labels[t.platform]}</span><h3>${esc(t.title)}</h3><p>${esc(taskCourseName(t))} · ${phases[t.phase]||'提交'}${t.kind==='exam'&&(t.examStart||t.rawStart)?'<br>开始：'+(t.examStart?fmt(t.examStart):esc(t.rawStart)):''}<br>截止：${due(t)?fmt(due(t)):esc(t.rawDeadline||'待确认')}<br>平台状态：${{done:'已完成',pending:'待完成',unknown:'待确认'}[t.status]}${t.localDone?'<br>人工标记：已处理':''}</p>${t.manualDue==null&&t.deadlineObserved===false&&due(t)?'<p class="source-note">截止沿用旧记录'+(t.deadlineCheckedAt?'，上次核实 '+fmt(t.deadlineCheckedAt):'')+'。本轮未读取到新的完整截止。</p>':''}<p class="source-note">人工标记和补充的日期保存在本机，不会改变平台任务。互评与提交分别提醒。</p><button class="primary" id="open-source">打开平台页面</button><button class="secondary" id="toggle-done">${t.localDone?'取消人工标记':'标记已处理'}</button><button class="secondary" id="snooze">稍后 1 小时提醒</button><label>补充或修正截止时间<input type="datetime-local" id="manual-deadline"></label><button class="secondary" id="save-deadline">保存补充时间</button>${t.manualDue?'<button class="text-button" id="reset-deadline">恢复平台时间</button>':''}`;
 $('detail-dialog').showModal();$('open-source').onclick=()=>call('openSource',{id});$('toggle-done').onclick=()=>{call('updateTask',{id,localDone:!t.localDone});$('detail-dialog').close();};$('snooze').onclick=()=>{call('updateTask',{id,snoozeUntil:Date.now()+3600000});$('detail-dialog').close();};$('save-deadline').onclick=()=>{const value=$('manual-deadline').value;if(!value)return toast('请先选择日期和时间');const ms=Date.parse(value+':00+08:00');if(!Number.isFinite(ms))return toast('日期格式不正确');call('updateTask',{id,manualDue:ms});$('detail-dialog').close();};if($('reset-deadline'))$('reset-deadline').onclick=()=>{call('updateTask',{id,manualDue:null});$('detail-dialog').close();};
}
document.addEventListener('click',e=>{const b=e.target.closest('button');const local=e.target.closest('[data-local-task]');if(local){const t=state.tasks.find(t=>t.id===local.dataset.localTask);if(!t)return;if(t.status==='done'&&!t.localDone)return toast('平台已完成，无需人工标记');if(confirm(t.localDone?'取消人工已处理标记？':'仅在课期标记已处理，不会改变平台的作业或考试状态。'))call('updateTask',{id:t.id,localDone:!t.localDone});return;}const task=e.target.closest('[data-task]');if(task)return showDetail(task.dataset.task);if(!b)return;if(b.dataset.page)showPage(b.dataset.page);if(b.hasAttribute('data-go-settings'))showPage('settings');if(b.hasAttribute('data-select-courses'))openCourseSelection();if(b.dataset.selectPlatformCourses)openCourseSelection(b.dataset.selectPlatformCourses);if(b.dataset.courseBulk){const scope=b.closest('#course-dialog')?courseSelectionPlatform:'all';call('toggleCourses',{keys:state.courses.filter(c=>scope==='all'||c.platform===scope).map(c=>c.key),enabled:b.dataset.courseBulk==='show'});}if(b.dataset.kind){$('kind-filter').value=b.dataset.kind;renderList();}if(b.dataset.filter){selectedFilter=b.dataset.filter;renderList();}if(b.dataset.day){day=b.dataset.day;renderCalendar();}if(b.dataset.login)call('login',{platform:b.dataset.login});if(b.dataset.verifyPlatform)call('verifyPlatform',{platform:b.dataset.verifyPlatform});if(b.dataset.disconnect&&confirm('断开后停止采集和提醒，历史作业会从本机删除。'))call('disconnect',{platform:b.dataset.disconnect});if(b.hasAttribute('data-year')){const year=prompt('统一设置学习通已连接课程的作答起始年份，跨年结束日期会按区间处理。如有不同学期，请留空并逐项补充日期。',String(state.settings.chaoxingYear||''));if(year!==null){if(year!==''&&!/^(20\d\d)$/.test(year))return toast('请输入四位年份');call('saveSettings',{chaoxingYear:year?Number(year):null});}}});
document.addEventListener('keydown',e=>{if((e.key==='Enter'||e.key===' ')&&e.target.matches('[data-task]')){e.preventDefault();showDetail(e.target.dataset.task);}});
document.addEventListener('change',e=>{if(e.target.dataset.course)call('toggleCourse',{key:e.target.dataset.course,enabled:e.target.checked});});
for(const id of ['search','platform-filter','course-filter','kind-filter'])$(id).addEventListener(id==='search'?'input':'change',renderList);
$('filter-button').onclick=()=>{const hidden=$('filters').classList.toggle('hidden');$('filter-button').setAttribute('aria-expanded',!hidden);};
$('search-button').onclick=()=>{const hidden=$('search-toolbar').classList.toggle('hidden');if(!hidden)$('search').focus();else{$('search').value='';renderList();}};
$('health-banner').onclick=()=>$('sync-dialog').showModal();$('settings-sync-detail').onclick=()=>$('sync-dialog').showModal();$('close-sync').onclick=()=>$('sync-dialog').close();
$('course-search').addEventListener('input',filterCourseSearch);
$('dark-mode').onchange=()=>call('saveSettings',{darkMode:$('dark-mode').checked});$('refresh').onclick=()=>call('sync');$('prev-month').onclick=()=>{month.setMonth(month.getMonth()-1);renderCalendar();};$('next-month').onclick=()=>{month.setMonth(month.getMonth()+1);renderCalendar();};$('calendar-today').onclick=()=>{day=dateKey(new Date());month=new Date();month.setDate(1);renderCalendar();};$('close-detail').onclick=()=>$('detail-dialog').close();
$('save-settings').onclick=()=>{const offsets=$('offsets').value.split(/[,，\s]+/).filter(Boolean).map(Number);if(!offsets.length||offsets.some(x=>!Number.isFinite(x)||x<=0||x>720))return toast('提醒小时需在 0 到 720 之间');call('saveSettings',{offsets:[...new Set(offsets)],syncMinutes:Number($('sync-minutes').value),quietStart:$('quiet-start').value,quietEnd:$('quiet-end').value,urgentOverride:$('urgent-override').checked});};
$('notification-permission').onclick=()=>call('notificationPermission');$('test-notification').onclick=()=>call('testNotification');$('battery-settings').onclick=()=>call('batterySettings');
$('close-courses').onclick=()=>$('course-dialog').close();
function previewAction(action,data){if(action==='getState')return render();if(action==='updateTask'){Object.assign(state.tasks.find(t=>t.id===data.id),data);render();return toast('预览中已更新');}if(action==='saveSettings'){Object.assign(state.settings,data);render();return toast('预览中已保存');}if(action==='toggleCourse'){state.courses.find(c=>c.key===data.key).enabled=data.enabled;render();return;}if(action==='toggleCourses'){state.courses.filter(c=>data.keys.includes(c.key)).forEach(c=>c.enabled=data.enabled);render();return;}toast('此功能需要在安卓 App 内使用');}
if(!window.Native){$('preview-banner').classList.remove('hidden');const now=Date.now();state={...state,notificationEnabled:false,courses:[{key:'cx-ds',title:'数据结构与算法',platform:'chaoxing'},{key:'mooc-math',title:'高等数学',platform:'mooc'},{key:'cx-eng',title:'大学英语',platform:'chaoxing'}],connections:[{platform:'mooc',status:'ok',message:'示例：已同步 1 门课程',lastSuccess:now-3600000},{platform:'chaoxing',status:'ok',message:'示例：已同步 2 门课程',lastSuccess:now-1800000}],tasks:[{id:'demo1',platform:'chaoxing',courseKey:'cx-ds',courseTitle:'数据结构与算法',title:'第三章 · 栈与队列练习',kind:'homework',phase:'submit',due:now+3*3600000,status:'pending',lastSeen:now-1800000},{id:'demo2',platform:'mooc',courseKey:'mooc-math',courseTitle:'高等数学',title:'第二周单元测验',kind:'quiz',phase:'submit',due:now+25*3600000,status:'pending',lastSeen:now-3600000},{id:'demo3',platform:'mooc',courseKey:'mooc-math',courseTitle:'高等数学',title:'函数与极限 · 同伴互评',kind:'homework',phase:'peerreview',due:now+3*86400000,status:'unknown',lastSeen:now-3600000},{id:'demo4',platform:'chaoxing',courseKey:'cx-eng',courseTitle:'大学英语',title:'Unit 2 阅读练习',kind:'homework',phase:'submit',due:now-5*3600000,status:'pending',lastSeen:now-1800000},{id:'demo5',platform:'chaoxing',courseKey:'cx-ds',courseTitle:'数据结构与算法',title:'实验报告 · 链表实现',kind:'homework',phase:'submit',due:null,rawDeadline:'10-15 18:00（年份待确认）',status:'unknown',lastSeen:now-1800000}]};render();}else{call('getState');}

if(window.Native)setInterval(()=>call('getState'),30000);
