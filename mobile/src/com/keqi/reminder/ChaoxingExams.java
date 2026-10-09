package com.keqi.reminder;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Read exam metadata from the two official list formats; never follow a start-exam link. */
public final class ChaoxingExams {
    public static final String MOBILE="https://mooc1-api.chaoxing.com/exam-ans/exam/phone/examcode";
    public static final String TABLE="https://mooc1-api.chaoxing.com/exam-ans/exam/test/examcode/examlist";
    public static boolean ready(String html){
        Document d=Jsoup.parse(html);
        return !d.select("ul.ks_list > li,tr.dataTr").isEmpty()||d.body().text().matches("(?s).*(暂无考试|没有考试|暂无数据).*" );
    }
    private static String query(String url,String... names){
        try{String raw=new URI(url).getRawQuery();if(raw!=null)for(String part:raw.split("&")){String[] kv=part.split("=",2);if(kv.length!=2)continue;String name=URLDecoder.decode(kv[0],"UTF-8");for(String key:names)if(name.equalsIgnoreCase(key))return URLDecoder.decode(kv[1],"UTF-8");}}catch(Exception ignored){}return "";
    }
    private static String refer(String value){
        try{for(int i=0;i<2&&value.matches("(?i)^(?:https?%3A|%2F).*" );i++)value=URLDecoder.decode(value,"UTF-8");}catch(Exception ignored){}return value;
    }
    private static String link(Element e,String base){
        String raw=e.attr("data");
        if(raw.isEmpty())for(Element a:e.select("a[href],a[onclick]")){
            String href=a.attr("href");if(!href.isEmpty()&&!href.startsWith("javascript:")&&!href.equals("#")){raw=href;break;}
            Matcher m=Pattern.compile("(?:go|location\\.href)\\s*\\(?\\s*[=]?\\s*['\"]([^'\"]+)['\"]").matcher(a.attr("onclick"));if(m.find()){raw=m.group(1);break;}
        }
        try{if(raw.matches("(?i)^(?:https?%3A|%2F).*"))raw=URLDecoder.decode(raw,"UTF-8");return raw.isEmpty()?"":new URI(base).resolve(raw).toString();}catch(Exception ignored){return "";}
    }
    private static String text(Element e,String selector){Element v=e.selectFirst(selector);return v==null?"":v.text().trim();}
    private static JSONObject course(JSONArray courses,String courseId,String classId) throws Exception {
        JSONObject found=null;
        for(int i=0;i<courses.length();i++){JSONObject co=courses.getJSONObject(i);if(!courseId.isEmpty()&&co.optString("courseId").equals(courseId)&&(classId.isEmpty()||co.optString("classId").equals(classId))){if(found!=null)return null;found=co;}}
        return found;
    }
    private static JSONObject unlinked(JSONArray courses) throws Exception {
        String key="chaoxing:unlinked-exams";for(int i=0;i<courses.length();i++)if(courses.getJSONObject(i).optString("key").equals(key))return courses.getJSONObject(i);
        JSONObject co=new JSONObject().put("key",key).put("platform","chaoxing").put("title","其他考试（未关联课程）").put("examOnly",true).put("enabled",true);courses.put(co);return co;
    }
    private static final String DATE="(?:\\d{4}[-年/])?\\d{1,2}[-月/]\\d{1,2}日?\\s+\\d{1,2}[:：]\\d{2}(?::\\d{2})?";
    private static void times(JSONObject task,String raw,Integer year) throws Exception {
        Matcher range=Pattern.compile("("+DATE+")\\s*(?:(?:至|到|~|～|—|–|-|--)\\s*|\\s+)("+DATE+")").matcher(raw);
        String end="",start="";Core.ParsedTime time=new Core.ParsedTime(null,"unknown");
        if(range.find()){start=range.group(1);end=range.group(2);time=Core.parseRangeEnd(start,end,year);}
        else{Matcher ending=Pattern.compile("(?:截止(?:时间)?|结束(?:时间)?|有效至)[:：]?\\s*("+DATE+")").matcher(raw);if(ending.find()){end=ending.group(1);time=Core.parseTime(end,year);}}
        task.put("due",time.at==null?JSONObject.NULL:time.at).put("precision",time.precision).put("rawDeadline",end.isEmpty()?raw:end).put("deadlineObserved",!end.isEmpty());
        Core.ParsedTime begin=Core.parseTime(start,year);task.put("examStart",begin.at==null?JSONObject.NULL:begin.at).put("rawStart",start);
    }
    public static Collectors.ParseBatch parse(String html,String base,JSONArray courses,String account,Integer year) throws Exception {
        Collectors.ParseBatch batch=new Collectors.ParseBatch();Document d=Jsoup.parse(html,base);Set<String> seen=new HashSet<>();
        for(Element row:d.select("ul.ks_list > li,tr.dataTr")){
            String title,time,status;
            if(row.tagName().equals("tr")){
                List<Element> cells=row.select("td");if(cells.size()<9){batch.warnings.add("一条考试记录格式未识别");continue;}
                title=cells.get(1).text();time=cells.get(2).text();status=cells.get(5).text();if(status.isEmpty())status=cells.get(4).text();
            }else{title=text(row,"dl dt");time=text(row,"dl dd");status=text(row,".ks_state,.status");}
            if(title.isEmpty()){batch.warnings.add("一条考试记录缺少名称");continue;}
            String address=link(row,base),refer=refer(query(address,"refer"));
            String cid=query(address,"courseId","moocId"),clazz=query(address,"classId","clazzId"),id=query(address,"taskrefId","examId","testId");
            if(!refer.isEmpty()){String c=query(refer,"courseId","moocId"),cl=query(refer,"classId","clazzId"),ex=query(refer,"examId","taskrefId","testId");if(!c.isEmpty())cid=c;if(!cl.isEmpty())clazz=cl;if(!ex.isEmpty())id=ex;}
            if(cid.isEmpty())cid=row.attr("data-courseid");if(clazz.isEmpty())clazz=row.attr("data-classid");if(id.isEmpty())id=row.attr("data-examid");
            if(id.isEmpty()){batch.warnings.add("“"+title+"”缺少稳定考试编号，未纳入提醒");continue;}
            JSONObject co=course(courses,cid,clazz);if(co==null){co=unlinked(courses);batch.warnings.add("“"+title+"”未关联到唯一课程，归入其他考试");}
            String key=co.getString("key")+"|"+id;if(!seen.add(key))continue;
            String normalized=Core.status(status);if(normalized.equals("unknown")&&status.matches("(?s).*(未开始|待考试|未参加|考试中|作答中|进行中).*"))normalized="pending";
            JSONObject task=Collectors.task("chaoxing",account,co,id,"exam",title,"exam",base,normalized,new Core.ParsedTime(null,"unknown"),"");
            task.put("rawStatus",status);times(task,time,year);batch.tasks.put(task);
        }
        return batch;
    }
}
