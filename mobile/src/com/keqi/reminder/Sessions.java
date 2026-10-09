package com.keqi.reminder;

import android.content.Context;
import android.webkit.*;
import org.json.*;
import java.net.URI;
import java.util.*;

/** Encrypted cookie fallback and explicit removal for a disconnected platform. */
public final class Sessions {
    private static String[] origins(String platform){return platform.equals("mooc")?new String[]{"https://www.icourse163.org/","https://passport.163.com/","https://reg.163.com/"}:new String[]{"https://i.chaoxing.com/","https://mooc1-1.chaoxing.com/","https://mooc1.chaoxing.com/","https://mooc2-ans.chaoxing.com/","https://passport2.chaoxing.com/"};}
    public static void save(Context context,String platform) throws Exception {
        JSONObject snapshot=new JSONObject();CookieManager manager=CookieManager.getInstance();
        for(String origin:origins(platform)){String value=manager.getCookie(origin);if(value!=null)snapshot.put(origin,value);}
        new Secrets(context).put(platform,snapshot.toString());manager.flush();
    }
    public static void restoreIfEmpty(Context context,String platform) throws Exception {
        String value=new Secrets(context).get(platform);if(value.isEmpty())return;
        JSONObject snapshot=new JSONObject(value);CookieManager manager=CookieManager.getInstance();
        // Never replace newer cookies or a logout response received from the site.
        for(String origin:origins(platform))if(manager.getCookie(origin)==null&&snapshot.has(origin)){
            for(String pair:snapshot.getString(origin).split(";\\s*"))if(pair.contains("="))manager.setCookie(origin,pair+"; Path=/; Secure; HttpOnly");
        }
        manager.flush();
    }
    public static void clear(Context context,String platform) throws Exception {
        CookieManager manager=CookieManager.getInstance();
        for(String origin:origins(platform)){
            String value=manager.getCookie(origin);if(value==null)continue;
            String host=new URI(origin).getHost();String parent=host.endsWith(".chaoxing.com")?"chaoxing.com":host.endsWith(".icourse163.org")?"icourse163.org":"163.com";
            for(String pair:value.split(";\\s*")){
                int at=pair.indexOf('=');if(at<1)continue;String cookie=pair.substring(0,at)+"=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure";
                manager.setCookie(origin,cookie);manager.setCookie(origin,cookie+"; Domain="+host);manager.setCookie(origin,cookie+"; Domain=."+parent);
            }
        }
        manager.flush();new Secrets(context).remove(platform);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->WebStorage.getInstance().getOrigins(values->{for(Object name:values.keySet()){String origin=String.valueOf(name);if(Http.trusted(origin,platform))WebStorage.getInstance().deleteOrigin(origin);}}));
    }
}
