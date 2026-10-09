package com.keqi.reminder;

import android.webkit.CookieManager;
import org.json.*;

/** Proves a browser session against a protected platform page before storing a connection. */
public final class Authentication {
    public static final class Session {
        public final String account,cookies,fid,url,warning;public final JSONArray courses;public final boolean empty;
        Session(String account,String cookies,String fid,String url,ChaoxingCourses.Catalog catalog){this.account=account;this.cookies=cookies;this.fid=fid;this.url=url;this.courses=catalog==null?new JSONArray():catalog.courses;this.warning=catalog==null?"":catalog.warning;this.empty=catalog!=null&&catalog.empty;}
    }
    public static Session verify(String platform) throws Exception {
        CookieManager.getInstance().flush();
        Http.Page page=Http.getPage(LoginNavigation.verificationPage(platform),platform);
        // Only the final protected response and cookies scoped to that response can prove login.
        return fromPage(platform,page,CookieManager.getInstance().getCookie(page.url));
    }
    static Session fromPage(String platform,Http.Page page,String cookies) throws Exception {
        if(!Http.trusted(page.url,platform))throw new java.io.IOException("账号验证页面地址无效");
        String account=ConnectionPolicy.account(platform,cookies,page.html);ConnectionPolicy.validate(platform,cookies,page.url,0,account);
        return new Session(account,cookies,platform.equals("chaoxing")?ConnectionPolicy.fid(page.html):"",page.url,platform.equals("chaoxing")?ChaoxingCourses.parse(page.html,page.url):null);
    }
    static Session fromBrowser(String encoded,String cookies) throws Exception {
        Object raw=new JSONTokener(encoded).nextValue();if(!(raw instanceof String))throw new java.io.IOException("课程页面尚未就绪");
        JSONObject snapshot=new JSONObject((String)raw);String url=snapshot.getString("url");
        String path=java.net.URI.create(url).getPath();if(!Http.trusted(url,"chaoxing")||!(path.endsWith("/visit/interaction")||path.endsWith("/courselistdata")))throw new Http.LoginRequired();
        return fromPage("chaoxing",new Http.Page(url,snapshot.getString("html")),cookies);
    }
}
