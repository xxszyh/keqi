package com.keqi.reminder;

import org.json.JSONObject;

/** A response belongs only to the pairing generation that initiated it. */
public final class LinkSession {
    public static void requireIntent(JSONObject current,String captured){
        if(!current.optString("id").equals(captured))throw new IllegalArgumentException("配对已取消，请重新扫描电脑二维码");
    }
    public static boolean matches(JSONObject current,JSONObject captured){
        return current.has("fingerprint")&&current.optString("fingerprint").equals(captured.optString("fingerprint"))
            &&current.optString("workspace").equals(captured.optString("workspace"))&&current.optString("link").equals(captured.optString("link"));
    }
}
