package com.keqi.reminder;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.util.*;
import java.util.function.Predicate;

/** One synchronization of list metadata. Independent exam sources survive homework failures. */
public final class ChaoxingTaskReader {
    public interface Pause {void run() throws InterruptedException;}
    public static final class Result {
        public final JSONArray courses,tasks=new JSONArray();public final List<String> warnings;
        public String verification="";
        Result(JSONArray courses,List<String> warnings){this.courses=courses;this.warnings=new ArrayList<>(warnings);}
        public String status(){return warnings.isEmpty()?"ok":"partial";}
        public String message(JSONArray old,Predicate<String> enabled) throws Exception {
            Set<String> fresh=new HashSet<>();int exams=0,works=0,cached=0;
            for(int i=0;i<tasks.length();i++){JSONObject t=tasks.getJSONObject(i);fresh.add(t.getString("id"));if(t.optString("kind").equals("exam"))exams++;else works++;}
            for(int i=0;i<old.length();i++){JSONObject t=old.getJSONObject(i);if(t.optString("platform").equals("chaoxing")&&enabled.test(t.optString("courseKey"))&&!fresh.contains(t.optString("id")))cached++;}
            String message="本轮读取 "+works+" 项作业、"+exams+" 项考试";
            if(cached>0)message+="；"+cached+" 项旧记录本轮未更新，保留供查看";
            if(!warnings.isEmpty())message+="；部分内容未更新："+String.join("；",warnings.subList(0,Math.min(3,warnings.size())))+(warnings.size()>3?"（共 "+warnings.size()+" 项提示）":"");
            return message;
        }
    }
    private static String hidden(String html,String name){Element e=Jsoup.parse(html).selectFirst("input[id="+name+"],input[name="+name+"]");return e==null?"":e.attr("value");}
    private static String url(String root,JSONObject params) throws Exception {StringBuilder b=new StringBuilder(root).append('?');Iterator<String> keys=params.keys();while(keys.hasNext()){String k=keys.next();b.append(Http.enc(k)).append('=').append(Http.enc(params.get(k).toString())).append('&');}return b.toString();}
    private static String error(Exception e){String m=e.getMessage();if(m==null||m.matches("(?s).*https?://.*"))return "平台页面未能读取，请稍后刷新";return m.length()>100?m.substring(0,100):m;}
    private static void interrupted() throws InterruptedException {if(Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private static List<Http.Page> pages(ChaoxingRequests.Reader reader,String entry,ChaoxingReadPolicy.Stage stage,String label,Result out,Pause pause) throws Exception {
        List<Http.Page> result=new ArrayList<>();Set<String> seen=new HashSet<>();ArrayDeque<String> todo=new ArrayDeque<>();todo.add(entry);
        while(!todo.isEmpty()){
            interrupted();String next=todo.remove();if(!seen.add(next))continue;if(seen.size()>50){out.warnings.add(label+"：分页超过读取上限");break;}
            try{Http.Page page=reader.read(next,stage);result.add(page);Collectors.PageLinks links=Collectors.pageLinks(page.html,page.url);if(links.unresolved)out.warnings.add(label+"：有分页未读取，请核对数量");for(String link:links.urls)if(!seen.contains(link))todo.add(link);}
            catch(Http.LoginRequired e){throw e;}catch(InterruptedException e){throw e;}catch(Exception e){if(result.isEmpty())throw e;out.warnings.add(label+"：部分分页未读取");}
            if(!todo.isEmpty())pause.run();
        }return result;
    }
    public static Result read(ChaoxingRequests.Reader reader,JSONArray courses,String account,Integer year,Predicate<String> enabled,List<String> warnings,Pause pause) throws Exception {
        Result out=new Result(courses,warnings);Map<String,JSONObject> exams=new LinkedHashMap<>();
        // These are independent list views. No metadata request can start a timed exam.
        for(String source:new String[]{ChaoxingExams.MOBILE,ChaoxingExams.TABLE}){
            interrupted();String label=source.equals(ChaoxingExams.MOBILE)?"课程考试列表":"考试安排列表";
            try{for(Http.Page page:pages(reader,source,ChaoxingReadPolicy.Stage.EXAM_LIST,label,out,pause)){
                Collectors.ParseBatch batch=ChaoxingExams.parse(page.html,page.url,courses,account,year);out.warnings.addAll(batch.warnings);
                for(int j=0;j<batch.tasks.length();j++){JSONObject exam=batch.tasks.getJSONObject(j);if(!enabled.test(exam.getString("courseKey")))continue;String id=exam.getString("id");JSONObject previous=exams.get(id);
                    if(previous!=null&&!exam.optBoolean("deadlineObserved"))for(String k:new String[]{"due","rawDeadline","precision","deadlineObserved","examStart","rawStart"})if(previous.has(k))exam.put(k,previous.get(k));
                    if(previous!=null&&exam.optString("status").equals("unknown")&&!previous.optString("status").equals("unknown"))exam.put("status",previous.getString("status"));
                    exams.put(id,exam);
                }
            }}catch(Http.LoginRequired e){throw e;}catch(InterruptedException e){throw e;}catch(Exception e){out.warnings.add(label+"："+error(e));if(e instanceof ChaoxingReadPolicy.InteractionRequired&&out.verification.isEmpty())out.verification=source;}
            pause.run();
        }
        for(JSONObject exam:exams.values())out.tasks.put(exam);
        for(int i=0;i<courses.length();i++){
            interrupted();JSONObject co=courses.getJSONObject(i);if(co.optBoolean("examOnly")||!enabled.test(co.getString("key")))continue;
            String label=CourseSelection.displayTitle("chaoxing",co.optString("title")),stage="课程入口";
            try{
                if(co.optString("cpi").isEmpty()&&co.has("url")){String cpi=hidden(reader.read(co.getString("url"),ChaoxingReadPolicy.Stage.COURSE).html,"cpi");if(cpi.matches("[1-9]\\d*"))co.put("cpi",cpi);}
                if(co.optString("cpi").isEmpty())throw new java.io.IOException("课程入口信息未读取");
                String middle=reader.read(url("https://mooc1.chaoxing.com/visit/stucoursemiddle",new JSONObject().put("courseid",co.getString("courseId")).put("clazzid",co.getString("classId")).put("cpi",co.getString("cpi")).put("ismooc2",1).put("v",2)),ChaoxingReadPolicy.Stage.COURSE).html;
                stage="作业列表";String workEnc=hidden(middle,"workEnc");if(workEnc.isEmpty())throw new java.io.IOException("作业入口信息未读取");
                String workList=url("https://mooc1.chaoxing.com/mooc2/work/list",new JSONObject().put("courseId",co.getString("courseId")).put("classId",co.getString("classId")).put("cpi",co.getString("cpi")).put("ut","s").put("openc",hidden(middle,"openc")).put("enc",workEnc).put("t",System.currentTimeMillis()).put("stuenc",hidden(middle,"enc")).put("isdisplaytable",2));
                Map<String,JSONObject> works=new LinkedHashMap<>();
                for(Http.Page page:pages(reader,workList,ChaoxingReadPolicy.Stage.WORK_LIST,label+"作业列表",out,pause)){
                    JSONArray batch=Collectors.chaoxingList(page.html,co,account);
                    for(int j=0;j<batch.length();j++){JSONObject task=batch.getJSONObject(j);works.put(task.getString("id"),task);}
                }
                for(JSONObject task:works.values()){
                    interrupted();try{Collectors.chaoxingDetail(task,reader.read(task.getString("sourceUrl"),ChaoxingReadPolicy.Stage.WORK_DETAIL).html,year);}
                    catch(Http.LoginRequired e){throw e;}catch(InterruptedException e){throw e;}catch(Exception e){task.put("deadlineObserved",false);out.warnings.add(label+" · “"+task.optString("title")+"”：作业列表已更新，详情未读取，保留旧截止时间");if(e instanceof ChaoxingReadPolicy.InteractionRequired&&out.verification.isEmpty())out.verification=co.optString("url",task.optString("sourceUrl"));}
                    out.tasks.put(task);pause.run();
                }
            }catch(Http.LoginRequired e){throw e;}catch(InterruptedException e){throw e;}catch(Exception e){out.warnings.add(label+" · "+stage+"："+error(e));if(e instanceof ChaoxingReadPolicy.InteractionRequired&&out.verification.isEmpty())out.verification=co.optString("url");}
            pause.run();
        }
        return out;
    }
}
