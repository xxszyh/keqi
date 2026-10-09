package com.keqi.reminder;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Student catalog reader shared by connection verification and later synchronization. */
public final class ChaoxingCourses {
    public static final class Catalog {
        public final JSONArray courses;public final boolean empty;public final String warning;
        Catalog(JSONArray courses,boolean empty,String warning){this.courses=courses;this.empty=empty;this.warning=warning;}
    }
    private static String parameter(String url,String... names){
        try{String query=new URI(url).getRawQuery();if(query!=null)for(String pair:query.split("&")){String[] p=pair.split("=",2);if(p.length==2)for(String name:names)if(URLDecoder.decode(p[0],"UTF-8").equalsIgnoreCase(name))return URLDecoder.decode(p[1],"UTF-8");}}catch(Exception ignored){}return "";
    }
    private static String value(Element card,String... names){for(Element input:card.select("input[value]"))for(String name:names)if(input.id().equalsIgnoreCase(name)||input.attr("name").equalsIgnoreCase(name))return input.attr("value");return "";}
    private static boolean numeric(String value){return value.matches("[1-9]\\d*");}
    private static final String NAME_ELEMENTS=".course-name,.courseName,.course-title,.courseTitle,[data-course-name],h3,h2";
    public static boolean hasName(String name){
        name=name==null?"":name.trim();
        return !name.isEmpty()&&!name.matches("(?:课程\\s*[:：#]?\\s*)?\\d+")&&!Arrays.asList("课程名称待更新","课程","封面","课程封面","进入课程","学习课程","查看课程").contains(name);
    }
    public static String displayName(String name){return hasName(name)?name.trim():"课程名称待更新";}
    private static boolean singleEnrollment(Element root){
        Set<String> courses=new HashSet<>(),classes=new HashSet<>();
        Pattern courseQuery=Pattern.compile("(?i)[?&]courseid=(\\d+)"),classQuery=Pattern.compile("(?i)[?&](?:clazzid|classid)=(\\d+)");
        for(Element e:root.select("[href],[data],[data-url],[onclick],input[value]")){
            for(String attr:new String[]{"href","data","data-url","onclick"}){
                Matcher c=courseQuery.matcher(e.attr(attr));while(c.find())courses.add(c.group(1));
                Matcher k=classQuery.matcher(e.attr(attr));while(k.find())classes.add(k.group(1));
            }
            if(e.tagName().equals("input"))for(String attr:new String[]{"id","name"}){
                if(numeric(e.attr("value"))&&e.attr(attr).equalsIgnoreCase("courseId"))courses.add(e.attr("value"));
                if(numeric(e.attr("value"))&&(e.attr(attr).equalsIgnoreCase("classId")||e.attr(attr).equalsIgnoreCase("clazzId")))classes.add(e.attr("value"));
            }
            if(courses.size()>1||classes.size()>1)return false;
        }
        return true;
    }
    private static Element card(Element link){
        Element named=null;
        for(Element e=link;e!=null&&!e.tagName().equals("body")&&!e.tagName().equals("html")&&!e.tagName().equals("#root");e=e.parent()){
            if(!singleEnrollment(e))break;
            if(e.tagName().equals("li"))return e;
            for(String css:e.classNames())if(css.matches("(?i)(?:course|course[-_]?item|course[-_]?card|course[-_]?info)"))return e;
            if(named==null&&e!=link&&!e.select(NAME_ELEMENTS).isEmpty())named=e;
        }
        if(named!=null)return named;
        Element parent=link.parent();return parent!=null&&!Arrays.asList("body","html","#root").contains(parent.tagName())&&singleEnrollment(parent)?parent:link;
    }
    private static String named(Element root){
        for(String selector:new String[]{".course-name,.courseName,.course-title,.courseTitle,[data-course-name]","h3,h2"})
            for(Element e:root.select(selector))for(String name:new String[]{e.attr("data-course-name"),e.attr("title"),e.text()})if(hasName(name))return name.trim();
        return "";
    }
    private static String title(Element link,Element card){
        String name=named(link);if(hasName(name))return name;
        if(hasName(link.attr("title")))return link.attr("title").trim();
        name=named(card);if(hasName(name))return name;
        for(Element anchor:card.select("a[title]"))if(hasName(anchor.attr("title")))return anchor.attr("title").trim();
        return displayName(link.text());
    }
    private static String source(String raw,String entry){
        try{
            if(raw.startsWith("https%3A")||raw.startsWith("http%3A"))raw=URLDecoder.decode(raw,"UTF-8");
            URI u=new URI(entry).resolve(raw);String host=u.getHost();if(host==null||u.getUserInfo()!=null)return "";host=host.toLowerCase(Locale.ROOT);
            if(!(host.equals("chaoxing.com")||host.endsWith(".chaoxing.com")))return "";
            if("http".equalsIgnoreCase(u.getScheme())&&(u.getPort()==-1||u.getPort()==80)){
                String authority=u.getRawAuthority();if(authority.endsWith(":80"))authority=authority.substring(0,authority.length()-3);
                return "https://"+authority+u.getRawPath()+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
            }
            return "https".equalsIgnoreCase(u.getScheme())&&(u.getPort()==-1||u.getPort()==443)?u.toString():"";
        }catch(Exception ignored){return "";}
    }
    private static void add(Map<String,JSONObject> records,Element link,String raw,String entry) throws JSONException {
        String url=source(raw,entry);if(url.isEmpty())return;
        try{String path=new URI(url).getPath();if(!(path.endsWith("/stucoursemiddle")||path.endsWith("/studentcourse")||path.endsWith("/studentstudy")))return;}catch(Exception e){return;}
        String course=parameter(url,"courseid"),clazz=parameter(url,"clazzid","classid"),cpi=parameter(url,"cpi");Element card=card(link);
        if(cpi.isEmpty())cpi=value(card,"cpi");if(!numeric(course)||!numeric(clazz))return;
        if(!numeric(cpi))cpi="";String identity=course+":"+clazz;
        String name=title(link,card);JSONObject old=records.get(identity);
        if(old!=null){
            if(!hasName(old.optString("title"))&&hasName(name))old.put("title",name);
            if(!old.optString("cpi").isEmpty()||cpi.isEmpty())return;
            if(hasName(old.optString("title")))name=old.getString("title");
        }
        records.put(identity,new JSONObject().put("key","chaoxing:"+identity+":"+(cpi.isEmpty()?"pending":cpi)).put("platform","chaoxing").put("title",name).put("courseId",course).put("classId",clazz).put("cpi",cpi).put("url",url));
    }
    public static Catalog parse(String html,String entry) throws JSONException {
        Document document=Jsoup.parse(html,entry);Map<String,JSONObject> records=new LinkedHashMap<>();
        Pattern embedded=Pattern.compile("(?:https?://[^\\s\"'<>]+|(?:/?(?:visit/|mycourse/))?(?:stucoursemiddle|studentcourse|studentstudy)\\?[^\\s\"'<>]+)");
        for(Element e:document.select("[href],[data],[data-url],[onclick]"))for(String attr:new String[]{"href","data","data-url","onclick"})if(e.hasAttr(attr)){
            String raw=e.attr(attr);add(records,e,raw,entry);Matcher urls=embedded.matcher(raw);while(urls.find())add(records,e,urls.group(),entry);
        }
        // Older personal homepages keep enrollment ids in hidden fields on each card.
        for(Element input:document.select("input[name],input[id]"))if(input.attr("name").equalsIgnoreCase("courseId")||input.id().equalsIgnoreCase("courseId")){
            Element card=card(input);String course=value(card,"courseId"),clazz=value(card,"classId","clazzId"),cpi=value(card,"cpi");
            if(!numeric(course)||!numeric(clazz))continue;
            add(records,input,"/mycourse/studentcourse?courseId="+course+"&clazzid="+clazz+(numeric(cpi)?"&cpi="+cpi:""),entry);
        }
        int incomplete=0,unnamed=0;for(JSONObject c:records.values()){if(c.optString("cpi").isEmpty())incomplete++;if(!hasName(c.optString("title")))unnamed++;}
        boolean empty=document.text().matches("(?s).*(暂无课程|没有课程|还没有.*课程).*");
        boolean unresolved=document.select(".pagination,.pager,[onclick*=loadMore],.folder,.folder-item").size()>0;
        String warning=incomplete>0?"有 "+incomplete+" 门课程的作业入口信息待读取，课程选择已保留":"";
        if(unnamed>0)warning+=(warning.isEmpty()?"":"；")+"有 "+unnamed+" 门课程名称待读取，请刷新课程信息";
        if(unresolved)warning+=(warning.isEmpty()?"":"；")+"课程分页或文件夹范围需核对";
        return new Catalog(new JSONArray(records.values()),empty,warning);
    }
}
