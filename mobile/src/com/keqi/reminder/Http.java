package com.keqi.reminder;

import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** HTTPS only. Cookies are never forwarded to an unrelated redirect host. */
public final class Http {
    public static final class LoginRequired extends IOException {public LoginRequired(){super("登录已失效，请重新连接平台");}}
    public static final class BrowserRequired extends IOException {public BrowserRequired(){super("平台页面尚未就绪，需要用登录浏览器继续读取（HTTP 202）");}}
    public static final class Page {public final String url,html;Page(String url,String html){this.url=url;this.html=html;}}
    public static IOException responseError(int status,String platform){
        if(status==202&&platform.equals("chaoxing"))return new BrowserRequired();
        if(status==401||(status==403&&!platform.equals("chaoxing")))return new LoginRequired();
        if(status==403)return new IOException("平台暂未允许读取该页面，请核对课程权限或稍后重试");
        return status==200?null:new IOException("平台请求失败（HTTP "+status+"）");
    }
    public static boolean trusted(String url,String platform){
        try{URI u=new URI(url);String h=u.getHost();if(h==null||u.getUserInfo()!=null||!(u.getPort()==-1||u.getPort()==443))return false;h=h.toLowerCase(java.util.Locale.ROOT);return "https".equalsIgnoreCase(u.getScheme())&&(platform.equals("mooc")?(h.equals("icourse163.org")||h.endsWith(".icourse163.org")):(platform.equals("chaoxing")&&(h.equals("chaoxing.com")||h.endsWith(".chaoxing.com"))));}catch(Exception e){return false;}
    }
    public static String redirectTarget(String from,String location,String platform) throws IOException {
        if(location==null||location.isEmpty())throw new IOException("平台跳转缺少页面地址");
        String next=LoginNavigation.target(new URL(new URL(from),location).toString(),platform);
        if(next==null)throw new IOException("平台返回了不支持的页面地址");
        URI u=URI.create(next);String path=u.getPath();
        if(u.getHost().startsWith("passport")||u.getHost().equals("reg.163.com")||u.getHost().endsWith(".reg.163.com")||path.equals("/login")||path.equals("/mlogin")||path.startsWith("/login/"))throw new LoginRequired();
        if(!trusted(next,platform))throw new IOException("平台返回了不支持的页面地址");
        return next;
    }
    public static String get(String url,String platform) throws Exception{return request(url,platform,null,null);}
    public static Page getPage(String url,String platform) throws Exception{return requestPage(url,platform,null,null);}
    public static Page getPage(String url,String platform,String agent,String referer) throws Exception{return requestPage(url,platform,null,null,agent,referer);}
    public static String request(String url,String platform,String body,String contentType) throws Exception {
        return requestPage(url,platform,body,contentType).html;
    }
    private static Page requestPage(String url,String platform,String body,String contentType) throws Exception {
        return requestPage(url,platform,body,contentType,"","");
    }
    private static Page requestPage(String url,String platform,String body,String contentType,String agent,String referer) throws Exception {
        for(int redirect=0;redirect<5;redirect++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(!trusted(url,platform))throw new IOException("平台返回了不支持的页面地址");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent",agent==null||agent.isEmpty()?"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36":agent);
            c.setRequestProperty("Referer",trusted(referer,platform)?referer:platform.equals("mooc")?"https://www.icourse163.org/":LoginNavigation.CHAOXING_HOME);
            String cookies=CookieManager.getInstance().getCookie(url);if(cookies!=null)c.setRequestProperty("Cookie",cookies);
            if(platform.equals("mooc")&&body!=null&&new URI(url).getPath().startsWith("/web/j/")){
                java.util.regex.Matcher token=java.util.regex.Pattern.compile("(?:^|;\\s*)NTESSTUDYSI=([^;]+)").matcher(cookies==null?"":cookies);
                c.setRequestProperty("edu-script-token",token.find()?token.group(1):"");
            }
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type",contentType);try(OutputStream os=c.getOutputStream()){os.write(body.getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();
            for(java.util.Map.Entry<String,java.util.List<String>> h:c.getHeaderFields().entrySet())if(h.getKey()!=null&&h.getKey().equalsIgnoreCase("Set-Cookie"))for(String value:h.getValue())CookieManager.getInstance().setCookie(url,value);
            if(code>=300&&code<400){String location=c.getHeaderField("Location");c.disconnect();url=redirectTarget(url,location,platform);body=null;continue;}
            IOException responseError=responseError(code,platform);if(responseError!=null){c.disconnect();throw responseError;}
            ByteArrayOutputStream data=new ByteArrayOutputStream();try(InputStream stream=c.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){data.write(buffer,0,n);if(data.size()>12000000)throw new IOException("平台响应过大");}}finally{c.disconnect();}
            String text=data.toString("UTF-8");
            if((text.contains("passport2.chaoxing.com/login")&&text.contains("用户登录"))||text.contains("请先登录"))throw new LoginRequired();
            return new Page(url,text);
        }
        throw new IOException("平台重定向次数过多");
    }
    public static String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){throw new IllegalStateException(e);}}
}
