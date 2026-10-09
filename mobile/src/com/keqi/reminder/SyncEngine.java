package com.keqi.reminder;

import android.content.Context;
import android.webkit.CookieManager;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

public final class SyncEngine {
    public static final AtomicBoolean BUSY=new AtomicBoolean(false);
    public static void sync(Context context){
        sync(context,()->{});
    }
    public static void sync(Context context,Runnable stateChanged){
        if(!BUSY.compareAndSet(false,true)){stateChanged.run();return;}
        stateChanged.run();
        try(Store store=new Store(context)){
            for(String platform:new String[]{"chaoxing","mooc"}){
                if(Thread.currentThread().isInterrupted())break;
                JSONObject config=store.meta("connection."+platform);if(!config.optBoolean("connected"))continue;
                try{
                    if(!config.optString("status").equals("needs_login"))Sessions.restoreIfEmpty(context,platform);
                    Authentication.Session session;
                    try{session=Authentication.verify(platform);}
                    catch(Exception e){if(e instanceof InterruptedException||Thread.currentThread().isInterrupted()||!platform.equals("chaoxing"))throw e;session=ChaoxingBrowser.discover(context,config.optString("browserAgent"));}
                    if(config.has("sourceAccount")&&!session.account.equals(config.optString("sourceAccount")))throw new Http.LoginRequired();
                    if(platform.equals("chaoxing"))chaoxing(context,store,config,session,stateChanged);else mooc(context,store,config);
                    JSONObject latest=store.meta("connection."+platform);if(latest.optBoolean("connected")&&latest.optString("account").equals(config.optString("account")))Sessions.save(context,platform);
                }catch(Exception e){
                    if(e instanceof InterruptedException||Thread.currentThread().isInterrupted()){Thread.currentThread().interrupt();break;}
                    try{String state=e instanceof Http.LoginRequired?"needs_login":"error";
                        String message=e instanceof Http.LoginRequired?"登录失效，请在设置中重新连接":safeError(e);
                        if(store.connectionError(platform,config.optString("account"),state,message))Notifications.post(context,"sync."+platform,"课程同步需要处理",(platform.equals("mooc")?"MOOC":"学习通")+"："+message);
                    }catch(Exception ignored){}
                }
            }
            CookieManager.getInstance().flush();
            Notifications.rebuild(context);
        }finally{BUSY.set(false);stateChanged.run();}
    }
    static String safeError(Exception e){String m=e.getMessage();if(m==null)return "同步失败，请稍后重试";if(m.matches("(?s).*https?://.*"))return "平台连接失败，请检查网络或重新登录";return m.length()>160?m.substring(0,160):m;}
    private static String url(String root,JSONObject params) throws Exception {StringBuilder b=new StringBuilder(root).append('?');Iterator<String> keys=params.keys();while(keys.hasNext()){String k=keys.next();b.append(Http.enc(k)).append('=').append(Http.enc(params.get(k).toString())).append('&');}return b.toString();}
    private static String account(JSONObject config){return config.optString("account","local");}
    private static List<String> pages(ChaoxingPageBrowser reader,ChaoxingReadPolicy.Stage stage,String entry,String label,List<String> warnings) throws Exception {
        List<String> out=new ArrayList<>();Set<String> visited=new HashSet<>();ArrayDeque<String> pending=new ArrayDeque<>();pending.add(entry);
        while(!pending.isEmpty()){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();String next=pending.remove();if(!visited.add(next))continue;
            if(visited.size()>50){warnings.add(label+"：分页超过读取上限");break;}
            try{Http.Page page=reader.get(next,stage);String html=page.html;out.add(html);Collectors.PageLinks links=Collectors.pageLinks(html,page.url);if(links.unresolved)warnings.add(label+"：有分页入口未识别，请核对数量");for(String link:links.urls)if(!visited.contains(link))pending.add(link);}
            catch(Http.LoginRequired e){throw e;}catch(InterruptedException e){throw e;}catch(Exception e){if(out.isEmpty())throw e;warnings.add(label+"：部分分页读取失败");}
            if(!pending.isEmpty())Thread.sleep(300);
        }
        return out;
    }
    private static void chaoxing(Context context,Store store,JSONObject config,Authentication.Session session,Runnable stateChanged) throws Exception {
        try(ChaoxingPageBrowser reader=new ChaoxingPageBrowser(context,config.optString("browserAgent"),session.account)){
        List<String> warnings=new ArrayList<>();if(!session.warning.isEmpty())warnings.add(session.warning);
        store.verificationPage("chaoxing",account(config),"");
        JSONArray discovered=session.courses;
        if(discovered.length()==0&&!session.empty){
            if(session.fid.isEmpty())throw new java.io.IOException("登录已验证，但课程列表暂未识别，请重开课程首页后重新连接");
            List<String> coursePages=pages(reader,ChaoxingReadPolicy.Stage.CATALOG,url("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata",new JSONObject().put("courseType",1).put("courseFid",session.fid).put("courseTeacherid",0).put("courseOrderBy",0)),"课程列表",warnings);
            Map<String,JSONObject> courseMap=new LinkedHashMap<>();for(String page:coursePages){JSONArray found=Collectors.chaoxingCourses(page);for(int i=0;i<found.length();i++)courseMap.put(found.getJSONObject(i).getString("key"),found.getJSONObject(i));}
            discovered=new JSONArray(courseMap.values());if(discovered.length()==0&&!String.join("",coursePages).matches("(?s).*(暂无课程|没有课程|noCourse).*"))throw new java.io.IOException("未识别到课程列表，需检查平台页面变化");
        }
        JSONArray courses=CourseSelection.merge(config.optJSONArray("courses"),discovered);
        // Publish catalog choices before slower homework requests; a later failure keeps them.
        store.ingest("chaoxing",account(config),new JSONArray(),courses,"partial","已读取 "+courses.length()+" 门课程，可选择要显示的课程；正在同步作业和考试");stateChanged.run();
        Integer year=store.settings().isNull("chaoxingYear")?null:store.settings().getInt("chaoxingYear");
        ChaoxingTaskReader.Result result=ChaoxingTaskReader.read(reader::get,courses,account(config),year,store::enabled,warnings,()->Thread.sleep(300));
        store.verificationPage("chaoxing",account(config),result.verification.isEmpty()?reader.verificationTarget():result.verification);
        store.ingest("chaoxing",account(config),result.tasks,result.courses,result.status(),result.message(store.tasks(),store::enabled));
        }catch(ChaoxingReadPolicy.InteractionRequired e){store.verificationPage("chaoxing",account(config),LoginNavigation.CHAOXING_HOME);throw e;}
    }
    private static void mooc(Context context,Store store,JSONObject config) throws Exception {
        String cookies=CookieManager.getInstance().getCookie("https://www.icourse163.org/");Matcher csrf=Pattern.compile("(?:^|;\\s*)NTESSTUDYSI=([^;]+)").matcher(cookies==null?"":cookies);if(!csrf.find())throw new Http.LoginRequired();String token=csrf.group(1);
        List<String> warnings=new ArrayList<>();JSONArray discovered;boolean apiComplete=false;
        try{MoocCourses.Batch batch=MoocCourses.read();discovered=batch.courses;warnings.addAll(batch.warnings);apiComplete=batch.warnings.isEmpty();if(!apiComplete&&discovered.length()==0)throw new java.io.IOException("课程接口未读取完成");}
        catch(Http.LoginRequired e){throw e;}catch(InterruptedException e){throw e;}catch(Exception e){warnings.add("MOOC 课程接口未完成，改用页面发现");try{BrowserCourses.Discovery scan=BrowserCourses.discover(context,config.optString("discoveryUrl","https://www.icourse163.org/home.htm#/home/course"));discovered=BrowserCourses.canonical(scan.courses);if(!scan.warning.isEmpty())warnings.add(scan.warning);}catch(InterruptedException interrupted){throw interrupted;}catch(Exception failure){discovered=new JSONArray();warnings.add("自动发现课程未完成，暂同步已连接课程");}}
        JSONArray old=config.optJSONArray("courses");Map<String,JSONObject> records=new LinkedHashMap<>();if(old!=null)for(int i=0;i<old.length();i++)records.put(old.getJSONObject(i).getString("key"),old.getJSONObject(i));for(int i=0;i<discovered.length();i++)records.put(discovered.getJSONObject(i).getString("key"),discovered.getJSONObject(i));
        JSONArray courses=new JSONArray(records.values());if(courses.length()==0&&!apiComplete)throw new java.io.IOException("没有发现 MOOC 课程，请重新连接并进入“我的课程”页");
        if(discovered.length()==0&&!apiComplete)warnings.add("本轮未发现课程范围，需在平台“我的课程”页确认");
        JSONArray tasks=new JSONArray();
        for(int i=0;i<courses.length();i++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();JSONObject co=courses.getJSONObject(i);
            try{
                JSONObject dto=null;
                String[] methods={"getLastLearnedMocTermDto","getMocTermDto","getMocTermDto"};
                for(int k=0;k<methods.length;k++){
                    try{String endpoint="https://www.icourse163.org/web/j/courseBean."+methods[k]+".rpc?csrfKey="+Http.enc(token);
                        String body=k==0?new JSONObject().put("termId",Long.parseLong(co.getString("termId"))).toString():"termId="+Http.enc(co.getString("termId"))+(k==1?"&gatewayType=3":"");
                        JSONObject candidate=new JSONObject(Http.request(endpoint,"mooc",body,k==0?"application/json;charset=UTF-8":"application/x-www-form-urlencoded;charset=UTF-8"));
                        if(candidate.optInt("code",0)<0)continue;Object result=candidate.opt("result");if(result==null||result==JSONObject.NULL)continue;dto=candidate;break;
                    }catch(Http.LoginRequired e){throw e;}catch(Exception e){if(k==2)throw e;}
                }
                if(dto==null)throw new java.io.IOException("平台未返回课程数据，请重新登录");Collectors.ParseBatch batch=Collectors.moocBatch(dto,co,account(config));JSONArray items=batch.tasks;for(String warning:batch.warnings)warnings.add(co.getString("title")+"："+warning);for(int j=0;j<items.length();j++)tasks.put(items.get(j));
            }catch(Http.LoginRequired e){throw e;}catch(InterruptedException e){throw e;}catch(Exception e){warnings.add(co.getString("title")+"："+safeError(e));}
            Thread.sleep(300);
        }
        store.ingest("mooc",account(config),tasks,courses,warnings.isEmpty()?"ok":"partial",warnings.isEmpty()?"已同步 "+courses.length()+" 门已发现课程，请核对课程范围":String.join("；",warnings.subList(0,Math.min(5,warnings.size()))));
    }
}
