package com.keqi.reminder;

import java.io.*;
import java.net.*;
import org.json.*;
import java.util.*;

/** Electron owns official browser sessions; this JVM Adapter requests bounded page results. */
public final class Http {
    public interface Transport {JSONObject read(JSONObject request) throws Exception;}
    public static Transport transport;
    public static final class LoginRequired extends IOException {public LoginRequired(){super("登录已失效，请重新连接平台");}}
    public static final class BrowserRequired extends IOException {public BrowserRequired(){super("需要浏览器续读");}}
    public static final class Page {public final String url,html;public Page(String url,String html){this.url=url;this.html=html;}}
    public static String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){throw new IllegalArgumentException(e);}}
    public static boolean trusted(String address,String platform){try{URI u=new URI(address);String h=u.getHost();if(h==null||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443||!u.getScheme().equals("https"))return false;return platform.equals("mooc")?(h.equals("icourse163.org")||h.endsWith(".icourse163.org")):platform.equals("chaoxing")&&(h.equals("chaoxing.com")||h.endsWith(".chaoxing.com"));}catch(Exception e){return false;}}
    public static Page page(String url,String platform,String body,String contentType,String stage) throws Exception {
        if(!trusted(url,platform))throw new IOException("平台页面地址无效");JSONObject request=new JSONObject().put("platform",platform).put("url",url).put("body",body==null?JSONObject.NULL:body).put("contentType",contentType==null?"":contentType).put("stage",stage);
        ChaoxingReadPolicy.Stage policy=null;try{if(platform.equals("chaoxing"))policy=ChaoxingReadPolicy.Stage.valueOf(stage);}catch(IllegalArgumentException ignored){}
        Set<String> followed=new HashSet<>();
        for(int attempt=0;attempt<12;attempt++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();JSONObject result=transport.read(request);
            if(result.optInt("status")==401)throw new LoginRequired();if(result.optInt("status")!=200)throw new IOException("平台读取暂未完成");if(!trusted(result.getString("url"),platform))throw new LoginRequired();Page page=new Page(result.getString("url"),result.getString("html"));if(policy==null||ChaoxingReadPolicy.ready(page.url,page.html,policy))return page;
            String embedded=ChaoxingReadPolicy.embeddedPage(page.url,page.html,policy);if(!embedded.isEmpty()&&followed.size()<3&&followed.add(embedded))request.put("url",embedded).put("keepPage",false);else request.put("keepPage",true);
        }throw new ChaoxingReadPolicy.InteractionRequired();
    }

    public static Page getPage(String url,String platform) throws Exception {return page(url,platform,null,null,"page");}
    public static String request(String url,String platform,String body,String type) throws Exception {return page(url,platform,body,type,"rpc").html;}
}
