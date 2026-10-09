package com.keqi.reminder;
import org.json.*;
import java.util.regex.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Connection acceptance extracted from the activity so the reported flow can be replayed. */
public final class ConnectionPolicy {
    public static void validate(String platform,String cookies,String page,int discovered,String verifiedAccount){
        if(cookies==null||cookies.isEmpty()||(platform.equals("mooc")&&!cookies.contains("NTESSTUDYSI=")))throw new IllegalArgumentException("请先完成平台登录");
        if(verifiedAccount==null||!verifiedAccount.matches("[1-9]\\d*"))throw new IllegalArgumentException("尚未登录成功，请先完成平台登录，再点完成连接");
    }
    public static String fid(String html){
        Matcher m=Pattern.compile("(?:(?:var|let|const)\\s+fid\\s*=\\s*['\"]?|\"fid\"\\s*:\\s*\"?)(\\d+)").matcher(html);if(m.find())return m.group(1);
        Element e=Jsoup.parse(html).selectFirst("input#fid,input[name=fid]");return e==null?"":e.attr("value");
    }
    private static String serverUser(String html){
        Matcher m=Pattern.compile("(?:(?:var|let|const)\\s+(?:uid|userId)\\s*=\\s*['\"]?|\"(?:uid|userId)\"\\s*:\\s*\"?)([1-9]\\d*)(?=['\"\\s;,}]|$)").matcher(html);if(m.find())return m.group(1);
        Element e=Jsoup.parse(html).selectFirst("input#uid,input#userId,input[name=uid],input[name=userId]");String value=e==null?"":e.attr("value");return value.matches("[1-9]\\d*")?value:"";
    }
    private static String cookieUser(String cookies){
        for(String name:new String[]{"_uid","UID","uid"}){
            Matcher value=Pattern.compile("(?:^|;\\s*)"+name+"=([^;]*)(?:;|$)").matcher(cookies==null?"":cookies);
            if(value.find())return value.group(1).matches("[1-9]\\d*")?value.group(1):"";
        }return "";
    }
    static boolean matchesChaoxingSession(String cookies,String html,String expected){
        String user=cookieUser(cookies),server=serverUser(html);
        return expected!=null&&expected.matches("[1-9]\\d*")&&expected.equals(user)&&(server.isEmpty()||server.equals(expected));
    }
    public static String account(String platform,String cookies,String protectedHtml){
        if(protectedHtml==null)return "";
        if(platform.equals("chaoxing")){
            if(protectedHtml.contains("fanyaloginbycode")||Jsoup.parse(protectedHtml).selectFirst("button#loginBtn,button[onclick*=loginByPhone]")!=null)return "";
            String cookieUser=cookieUser(cookies),pageUser=serverUser(protectedHtml);if(cookieUser.isEmpty())return "";
            if(!pageUser.isEmpty())return pageUser.equals(cookieUser)?cookieUser:"";
            if(!fid(protectedHtml).isEmpty())return cookieUser;
            try{return ChaoxingCourses.parse(protectedHtml,"https://mooc1-1.chaoxing.com/visit/interaction").courses.length()>0?cookieUser:"";}catch(JSONException e){return "";}
        }
        Matcher user=Pattern.compile("(?:window\\.)?webUser\\s*=\\s*(\\{)").matcher(protectedHtml);
        if(user.find())try{Object value=new JSONTokener(protectedHtml.substring(user.start(1))).nextValue();if(value instanceof JSONObject){String id=((JSONObject)value).optString("id","");if(id.matches("[1-9]\\d*"))return id;}}catch(JSONException ignored){}
        return "";
    }
}
