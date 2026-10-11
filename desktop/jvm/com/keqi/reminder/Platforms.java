package com.keqi.reminder;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Desktop platform reading reuses the production mobile parsers and read-only exam policy. */
public final class Platforms {
    private final DesktopStore store;
    public Platforms(DesktopStore store){this.store=store;}
    public JSONObject connect(String platform) throws Exception {
        Http.Page page=Http.getPage(LoginNavigation.verificationPage(platform),platform);JSONObject session=Http.transport.read(new JSONObject().put("platform",platform).put("url",page.url).put("stage","cookies"));
        String cookies=session.optString("cookies"),account=ConnectionPolicy.account(platform,cookies,page.html);ConnectionPolicy.validate(platform,cookies,page.url,0,account);
        JSONObject c=new JSONObject().put("platform",platform).put("sourceAccount",account).put("generation",UUID.randomUUID().toString()).put("connected",true).put("status","ok").put("message","官方账号已验证，正在读取任务");store.meta("connection."+platform,c);return c;
    }
    private boolean active(String platform,JSONObject captured) throws Exception {
        JSONObject current=store.meta("connection."+platform);return current.optBoolean("connected")&&current.optString("sourceAccount").equals(captured.optString("sourceAccount"))&&current.optString("generation").equals(captured.optString("generation"));
    }
    private void observeIfActive(String platform,JSONObject captured,JSONArray courses,JSONArray tasks) throws Exception {
        store.transact(w->{if(!active(platform,captured))throw new InterruptedException("平台连接已改变");w.observe(platform,captured.getString("sourceAccount"),courses,tasks,false,true);return null;});
    }
    private void statusIfActive(String platform,JSONObject captured) throws Exception {
        store.transact(w->{if(active(platform,captured))store.meta("connection."+platform,captured);return null;});
    }
    public void read(String platform) throws Exception {
        JSONObject c=store.meta("connection."+platform);if(!c.optBoolean("connected"))return;List<String> warnings=new ArrayList<>();JSONArray courses=new JSONArray(),tasks=new JSONArray();String account=c.getString("sourceAccount");
        try {
            Http.Page home=Http.getPage(LoginNavigation.verificationPage(platform),platform);JSONObject cookieResult=Http.transport.read(new JSONObject().put("platform",platform).put("url",home.url).put("stage","cookies"));String cookies=cookieResult.optString("cookies");String verified=ConnectionPolicy.account(platform,cookies,home.html);ConnectionPolicy.validate(platform,cookies,home.url,0,verified);if(!account.equals(verified))throw new Http.LoginRequired();
            if(platform.equals("chaoxing")){
                ChaoxingCourses.Catalog catalog=ChaoxingCourses.parse(home.html,home.url);courses=catalog.courses;
                if(courses.length()==0&&!catalog.empty){String fid=ConnectionPolicy.fid(home.html);if(fid.isEmpty())throw new java.io.IOException("课程目录尚未就绪，请在登录窗口打开课程首页");String url="https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata?courseType=1&courseFid="+Http.enc(fid)+"&courseTeacherid=0&courseOrderBy=0";courses=ChaoxingCourses.parse(Http.getPage(url,platform).html,url).courses;}
                if(!catalog.warning.isEmpty())warnings.add(catalog.warning);final JSONArray catalogCourses=courses;observeIfActive(platform,c,catalogCourses,new JSONArray());
                Workspace before=new Workspace(store.meta("workspace"));TaskService service=new TaskService(before,true);Map<String,JSONObject> prefs=new HashMap<>();JSONArray all=service.courses();for(int i=0;i<all.length();i++){JSONObject p=all.getJSONObject(i);if(p.optString("sourceAccount").equals(account)&&p.optString("platform").equals(platform))prefs.put(p.optString("sourceKey"),p);}
                Integer year=store.meta("settings").isNull("chaoxingYear")?null:store.meta("settings").getInt("chaoxingYear");
                ChaoxingTaskReader.Result result=ChaoxingTaskReader.read((url,stage)->Http.page(url,platform,null,null,stage.name()),courses,account,year,key->{JSONObject pref=prefs.get(key);return pref==null||!pref.optBoolean("removed")&&pref.optBoolean("enabled",true);},warnings,()->Thread.sleep(250));tasks=result.tasks;courses=result.courses;warnings=result.warnings;
                c.put("message",result.message(service.tasks(false),key->true));
            }else{
                Matcher token=Pattern.compile("(?:^|;\\s*)NTESSTUDYSI=([^;]+)").matcher(cookies);if(!token.find())throw new Http.LoginRequired();String csrf=token.group(1);
                MoocCatalog.Batch enrollment=MoocCatalog.read((scope,p)->{Thread.sleep(250);return new JSONObject(Http.request("https://www.icourse163.org/web/j/learnerCourseRpcBean.getMyLearnedCoursePanelList.rpc?csrfKey="+Http.enc(csrf),"mooc","type=30&p="+p+"&psize=8&courseType="+scope,"application/x-www-form-urlencoded;charset=UTF-8"));});courses=enrollment.courses;warnings.addAll(enrollment.warnings);
                final JSONArray catalogCourses=courses;observeIfActive(platform,c,catalogCourses,new JSONArray());
                Workspace before=new Workspace(store.meta("workspace"));
                for(int i=0;i<courses.length();i++){JSONObject course=courses.getJSONObject(i),pref=before.get(before.canonical(course.getString("key")));if(pref!=null&&pref.optBoolean("removed"))continue;
                    try{JSONObject dto=null;String[] methods={"getLastLearnedMocTermDto","getMocTermDto","getMocTermDto"};for(int k=0;k<methods.length;k++){try{String body=k==0?new JSONObject().put("termId",Long.parseLong(course.getString("termId"))).toString():"termId="+Http.enc(course.getString("termId"))+(k==1?"&gatewayType=3":"");JSONObject candidate=new JSONObject(Http.request("https://www.icourse163.org/web/j/courseBean."+methods[k]+".rpc?csrfKey="+Http.enc(csrf),"mooc",body,k==0?"application/json;charset=UTF-8":"application/x-www-form-urlencoded;charset=UTF-8"));if(candidate.optInt("code")>=0&&!candidate.isNull("result")){dto=candidate;break;}}catch(Http.LoginRequired e){throw e;}catch(Exception e){if(k==2)throw e;}}
                        if(dto==null)throw new java.io.IOException("课程数据尚未就绪");Collectors.ParseBatch batch=Collectors.moocBatch(dto,course,account);for(int j=0;j<batch.tasks.length();j++)tasks.put(batch.tasks.get(j));warnings.addAll(batch.warnings);
                    }catch(Http.LoginRequired e){throw e;}catch(Exception e){warnings.add(course.optString("title")+"：课程读取未完成");}Thread.sleep(250);
                }c.put("message","本轮读取 "+tasks.length()+" 项任务"+(warnings.isEmpty()?"":"；部分课程待重试"));
            }
            final JSONArray foundCourses=courses,foundTasks=tasks;long now=System.currentTimeMillis();for(int i=0;i<tasks.length();i++)tasks.getJSONObject(i).put("lastSeen",now).put("deadlineCheckedAt",now);
            observeIfActive(platform,c,foundCourses,foundTasks);c.put("status",warnings.isEmpty()?"ok":"partial").put("lastAttempt",now);if(warnings.isEmpty())c.put("lastSuccess",now);statusIfActive(platform,c);
        }catch(Exception e){c.put("status",e instanceof Http.LoginRequired?"needs_login":"error").put("message",e instanceof Http.LoginRequired?e.getMessage():"本轮平台读取未完成，请重试或打开官方登录页").put("lastAttempt",System.currentTimeMillis());statusIfActive(platform,c);throw e;}
    }
}
