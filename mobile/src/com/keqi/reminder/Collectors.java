package com.keqi.reminder;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Protocol/parsing adapted from MIT Xuexitong-mcp and MOOC_reminder; see THIRD_PARTY.md. */
public final class Collectors {
    public static String query(String url,String name){try{String q=new URI(url).getRawQuery();if(q!=null)for(String part:q.split("&")){String[] kv=part.split("=",2);if(URLDecoder.decode(kv[0],"UTF-8").equals(name)&&kv.length==2)return URLDecoder.decode(kv[1],"UTF-8");}}catch(Exception ignored){}return "";}
    public static final class PageLinks {
        public final List<String> urls=new ArrayList<>();public boolean unresolved;
    }
    public static JSONObject moocCourse(JSONObject record,int scope) throws Exception {
        JSONObject term=record.optJSONObject("termPanel"),school=record.optJSONObject("schoolPanel");
        if(term==null||school==null)return null;
        String course=record.optString("id",record.optString("courseId","")),tid=term.optString("id",term.optString("termId",""));
        String shortName=school.optString("shortName","");if(shortName.isEmpty())shortName=school.optString("schoolSN","");
        if(!course.matches("[1-9]\\d*")||!tid.matches("[1-9]\\d*")||shortName.isEmpty())return null;
        String id=shortName+"-"+course;String path=scope==2||record.optInt("mode",0)!=0?"/spoc/learn/":"/learn/";
        String encoded=URLEncoder.encode(id,"UTF-8").replace("+","%20");
        return new JSONObject().put("key","mooc:"+id+":"+tid).put("platform","mooc").put("title",record.optString("name","课程 "+course)).put("termId",tid).put("schoolCourseId",id).put("url","https://www.icourse163.org"+path+encoded+"?tid="+tid);
    }
    public static PageLinks pageLinks(String html,String entry) throws Exception {
        Document doc=Jsoup.parse(html,entry);PageLinks pages=new PageLinks();URI origin=new URI(entry);Set<String> seen=new HashSet<>();
        for(Element a:doc.select(".pagination a,.pager a,.pageNext,.nextPage,a[rel=next]")){
            if(a.hasClass("disabled")||a.attr("aria-disabled").equals("true")||a.parent()!=null&&a.parent().hasClass("disabled"))continue;
            String link=a.absUrl("href");if(link.isEmpty()||link.contains("#")){if(a.text().matches(".*(下一页|\\d+|Next).*"))pages.unresolved=true;continue;}
            URI u=new URI(link);
            if("https".equals(u.getScheme())&&Objects.equals(origin.getHost(),u.getHost())&&Objects.equals(origin.getPath(),u.getPath())){if(seen.add(link)&&!entry.equals(link))pages.urls.add(link);}
            else if(a.text().matches(".*(下一页|\\d+|Next).*"))pages.unresolved=true;
        }
        if(!doc.select(".pagination,.pager,.pageNext,.nextPage").isEmpty()&&pages.urls.isEmpty()&&!doc.select(".pagination a.disabled,.pagination .disabled,.pager .disabled").isEmpty())return pages;
        if(!doc.select(".pagination,.pager").isEmpty()&&pages.urls.isEmpty()&&doc.select("a[href]").isEmpty())pages.unresolved=true;
        return pages;
    }
    public static JSONArray chaoxingCourses(String html) throws Exception {
        return ChaoxingCourses.parse(html,"https://mooc1.chaoxing.com/visit/interaction").courses;
    }
    public static JSONArray chaoxingList(String html,JSONObject course,String account) throws Exception {
        JSONArray tasks=new JSONArray();Document doc=Jsoup.parse(html,"https://mooc1.chaoxing.com/");
        for(Element li:doc.select("li[data]")){
            String link=li.attr("data");if(link.matches("(?i)^(?:https?%3A|%2F).*"))link=URLDecoder.decode(link,"UTF-8");if(link.startsWith("http://"))link="https://"+link.substring(7);if(link.startsWith("/"))link="https://mooc1.chaoxing.com"+link;
            String work=query(link,"workId");if(work.isEmpty())work=query(link,"taskrefId");if(work.isEmpty())work=query(link,"workid");if(work.isEmpty())continue;
            Element name=li.selectFirst("p.overHidden2,.work-name,.workName");Element status=li.selectFirst("p.status,.status");
            String title=name!=null?name.text():li.attr("aria-label");if(title.isEmpty())title="作业 "+work;
            String text=status!=null?status.text():li.text();
            tasks.put(task("chaoxing",account,course,work,"submit",title,"homework",link,Core.status(text),new Core.ParsedTime(null,"unknown"),""));
        }
        return tasks;
    }
    public static void chaoxingDetail(JSONObject task,String html,Integer year) throws Exception {
        String text=Jsoup.parse(html).text();
        String date="(?:\\d{4}[-年/])?\\d{1,2}[-月/]\\d{1,2}日?\\s+\\d{1,2}[:：]\\d{2}";
        Matcher m=Pattern.compile("(?:作答时间|作业时间|答题时间)[:：]?\\s*("+date+")\\s*(?:至|到|~|—)\\s*("+date+")").matcher(text);
        Core.ParsedTime result;String raw;
        if(m.find()){raw=m.group(2);result=Core.parseRangeEnd(m.group(1).replace('月','-'),raw.replace('月','-'),year);}
        else{m=Pattern.compile("(?:截止时间|结束时间)[:：]?\\s*("+date+")").matcher(text);if(m.find()){raw=m.group(1);result=Core.parseTime(raw,year);}else{raw="";result=new Core.ParsedTime(null,"unknown");}}
        task.put("due",result.at==null?JSONObject.NULL:result.at).put("rawDeadline",raw).put("precision",result.precision).put("deadlineObserved",true);
    }
    public static JSONObject task(String platform,String account,JSONObject course,String sourceId,String phase,String title,String kind,String url,String status,Core.ParsedTime time,String raw) throws Exception {
        return new JSONObject().put("id",Core.hash(platform+"|"+account+"|"+course.getString("key")+"|"+sourceId+"|"+phase))
            .put("platform",platform).put("courseKey",course.getString("key")).put("courseTitle",course.getString("title"))
            .put("sourceId",sourceId).put("phase",phase).put("title",title).put("kind",kind).put("sourceUrl",url)
            .put("status",status).put("due",time.at==null?JSONObject.NULL:time.at).put("precision",time.precision).put("rawDeadline",raw).put("deadlineObserved",true);
    }
    private static Object first(JSONObject a,JSONObject b,String... keys){for(String k:keys){Object v=a.opt(k);if(v==null||v==JSONObject.NULL)v=b.opt(k);if(v!=null&&v!=JSONObject.NULL)return v;}return null;}
    public static final class ParseBatch {public final JSONArray tasks=new JSONArray();public final List<String> warnings=new ArrayList<>();}
    public static ParseBatch moocBatch(JSONObject data,JSONObject course,String account) throws Exception {
        ParseBatch batch=new ParseBatch();walk(data,course,account,batch.tasks,new HashSet<>(),batch.warnings,0);return batch;
    }
    public static JSONArray moocTasks(JSONObject data,JSONObject course,String account) throws Exception {
        return moocBatch(data,course,account).tasks;
    }
    private static void walk(Object value,JSONObject course,String account,JSONArray out,Set<String> seen,List<String> warnings,int depth) throws Exception {
        if(depth>24)throw new IllegalArgumentException("课程数据嵌套过深");
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)walk(a.get(i),course,account,out,seen,warnings,depth+1);return;}
        if(!(value instanceof JSONObject))return;JSONObject node=(JSONObject)value;JSONObject nested=node.optJSONObject("test");if(nested==null)nested=new JSONObject();
        String name=node.optString("name",node.optString("title",node.optString("unitName","")));
        Object type=first(node,nested,"contentType","type");String t=type==null?"":String.valueOf(type);
        boolean assessed=Arrays.asList("2","3","6").contains(t)||t.isEmpty()&&name.matches("(?i).*(作业|测验|考试|测试|quiz|exam|homework).*" )&&!node.has("lessons")&&!node.has("chapters")&&!node.has("units");
        Object source=first(node,nested,"id","jobId","quizId","testId","homeworkId");
        if(assessed&&source==null&&warnings.size()<5)warnings.add("部分任务缺少稳定编号，未能纳入提醒");
        if(assessed&&source!=null&&name.isEmpty())name=nested.optString("name","课程任务 "+source);
        if(assessed&&!name.isEmpty()&&source!=null){
            String id=String.valueOf(source);if(seen.add(id)){
                String kind=t.equals("6")||name.contains("考试")?"exam":t.equals("2")?"quiz":"homework";
                Object raw=first(node,nested,"submitEndTime","deadline","endTime","examEndTime","testEndTime","homeworkEndTime","jobDeadline","closeTime");
                Core.ParsedTime time=Core.parseTime(raw==null?"":String.valueOf(raw),null);
                String status="unknown";Object score=first(node,nested,"studentScore","userScore","finalMark");
                Object attempts=first(node,nested,"usedTryCount");Object state=first(node,nested,"submitStatus","finishStatus","statusText","statusName");
                if(state instanceof String)status=Core.status((String)state);
                // Generic score/mark fields may be the available points or a default
                // zero. Only explicit personal results override an unknown state.
                if(status.equals("unknown")&&score instanceof Number&&((Number)score).doubleValue()>=0)status="done";
                if(attempts instanceof Number&&((Number)attempts).intValue()>0&&(t.equals("3")||t.equals("6")))status="done";
                // Absence of personal completion evidence remains unknown, including peer review.
                String url=course.getString("url")+(kind.equals("exam")?"#/learn/exam":"#/learn/testlist");
                out.put(task("mooc",account,course,id,"submit",name,kind,url,status,time,raw==null?"":String.valueOf(raw)));
                Object peer=first(node,nested,"evaluateEnd","evaluationEndTime");
                if(peer!=null){Core.ParsedTime pt=Core.parseTime(String.valueOf(peer),null);String peerStatus="unknown";Object explicit=first(node,nested,"peerReviewStatus","evaluationStatusText");if(explicit instanceof String)peerStatus=Core.status((String)explicit);out.put(task("mooc",account,course,id,"peerreview",name,kind,url,peerStatus,pt,String.valueOf(peer)));}
            }
        }
        Iterator<String> keys=node.keys();while(keys.hasNext()){String k=keys.next();if(k.equals("test"))continue;Object child=node.opt(k);if(child instanceof JSONObject||child instanceof JSONArray)walk(child,course,account,out,seen,warnings,depth+1);else if(child instanceof String&&((String)child).length()>50){String s=((String)child).trim();try{if(s.startsWith("{"))walk(new JSONObject(s),course,account,out,seen,warnings,depth+1);else if(s.startsWith("["))walk(new JSONArray(s),course,account,out,seen,warnings,depth+1);}catch(JSONException ignored){if(warnings.size()<5)warnings.add("课程内嵌数据未解析，请核对任务范围");}}}
    }
}
