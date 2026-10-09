package com.keqi.reminder;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.io.IOException;
import java.net.URI;

/** A pending page is never evidence of zero homework or a completed synchronization. */
public final class ChaoxingReadPolicy {
    public enum Stage {CATALOG,COURSE,WORK_LIST,WORK_DETAIL,EXAM_LIST}
    public static final class InteractionRequired extends IOException {
        public InteractionRequired(){super("平台页面尚未就绪，请打开验证页面，完成平台提示后再刷新");}
    }
    public static boolean allowed(String url){
        if(!Http.trusted(url,"chaoxing"))return false;
        String path=URI.create(url).getPath().replaceAll("/+$","");
        if(path.endsWith("/exam/phone/examcode")||path.endsWith("/exam/test/examcode/examlist")||path.endsWith("/exam/phone/task-list"))return true;
        return path.endsWith("/visit/interaction")||path.endsWith("/courselistdata")||path.endsWith("/stucoursemiddle")||path.endsWith("/studentcourse")||path.endsWith("/studentstudy")||path.endsWith("/mooc2/work/list")||path.endsWith("/work")||path.endsWith("/work/task")||path.endsWith("/work/dowork")||path.endsWith("/work/doHomeWorkNew")||path.endsWith("/work/selectWorkQuestionYiPiYue")||path.endsWith("/work/selectWorkQuestion")||path.endsWith("/work/doWork")||path.endsWith("/work/viewWork");
    }
    public static boolean ready(String url,String html,Stage stage) throws Exception {
        if(!responseAllowed(url,stage))throw new IOException("平台返回了不支持的读取页面");
        Document doc=Jsoup.parse(html,url);String text=doc.body().text();
        if(html.contains("fanyaloginbycode")||html.contains("请先登录")||doc.selectFirst("button#loginBtn,button[onclick*=loginByPhone],form[action*=login]")!=null)throw new Http.LoginRequired();
        if(text.matches("(?s).*(没有此页面访问权限|无权访问|没有访问权限|课程已关闭).*"))throw new IOException("平台暂未允许读取该课程页面，请核对课程权限");
        if(stage==Stage.CATALOG){ChaoxingCourses.Catalog catalog=ChaoxingCourses.parse(html,url);return catalog.courses.length()>0||catalog.empty;}
        if(stage==Stage.COURSE){for(Element input:doc.select("input#workEnc,input[name=workEnc],input#examEnc,input[name=examEnc]"))if(!input.attr("value").trim().isEmpty())return true;}
        if(stage==Stage.EXAM_LIST&&ChaoxingExams.ready(html))return true;
        if(stage==Stage.WORK_LIST){
            for(Element li:doc.select("li[data]")){String link=li.attr("data");if(link.matches("(?i)^(?:https?%3A|%2F).*"))link=java.net.URLDecoder.decode(link,"UTF-8");if(!Collectors.query(link,"workId").isEmpty()||!Collectors.query(link,"workid").isEmpty()||!Collectors.query(link,"taskrefId").isEmpty())return true;}
            if(text.matches("(?s).*(暂无作业|没有作业|暂无数据).*"))return true;
        }
        if(stage==Stage.WORK_DETAIL&&text.matches("(?s).*(?:(?:作答时间|作业时间|答题时间|截止时间|结束时间)[:：]?\\s*\\d|题量[:：]?\\s*\\d).*"))return true;
        if(text.length()<400&&text.matches("(?s).*(请输入验证码|请完成验证|点击进行验证|拖动滑块|滑动验证).*"))throw new InteractionRequired();
        return false;
    }
    /** Only final COURSE responses may vary their route; the initiating GET stays allowlisted. */
    public static boolean responseAllowed(String url,Stage stage){
        if(allowed(url))return true;
        if(stage!=Stage.COURSE||!Http.trusted(url,"chaoxing"))return false;
        String path=URI.create(url).getPath().toLowerCase(java.util.Locale.ROOT);
        return !path.matches(".*(?:submit|save|start|begin|answer|examnotes|delete|login|passport).*" )&&path.matches(".*/(?:visit|mycourse|course)/.*");
    }
    public static String embeddedPage(String url,String html,Stage stage){
        for(Element frame:Jsoup.parse(html,url).select("iframe[src]")){
            String target=LoginNavigation.target(frame.absUrl("src"),"chaoxing");if(target==null||!allowed(target)||target.equals(url))continue;
            String path=URI.create(target).getPath();
            if(stage==Stage.WORK_DETAIL&&path.matches(".*/work/(?:task|dowork|doHomeWorkNew|selectWorkQuestionYiPiYue|selectWorkQuestion|doWork|viewWork)$"))return target;
            if(stage==Stage.WORK_LIST&&path.endsWith("/mooc2/work/list"))return target;
            if(stage==Stage.COURSE&&(path.endsWith("/studentcourse")||path.endsWith("/studentstudy")))return target;
            if(stage==Stage.CATALOG&&path.endsWith("/courselistdata"))return target;
            if(stage==Stage.EXAM_LIST&&(path.endsWith("/exam/test/examcode/examlist")||path.endsWith("/exam/phone/examcode")||path.endsWith("/exam/phone/task-list")))return target;
        }return "";
    }
    public static Http.Page fromBrowser(String encoded,String cookies,String expected,Stage stage) throws Exception {
        Object raw=new JSONTokener(encoded).nextValue();if(!(raw instanceof String))return null;
        JSONObject value=new JSONObject((String)raw);String url=value.getString("url"),html=value.getString("html");
        if(html.length()>12000000)throw new IOException("平台响应过大");
        if(!responseAllowed(url,stage))throw new IOException("平台返回了不支持的读取页面");
        if(!ConnectionPolicy.matchesChaoxingSession(cookies,html,expected))throw new Http.LoginRequired();
        return ready(url,html,stage)?new Http.Page(url,html):null;
    }
}
