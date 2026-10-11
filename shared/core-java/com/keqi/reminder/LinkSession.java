package com.keqi.reminder;

import org.json.JSONObject;

/** A response belongs only to the pairing generation that initiated it. */
public final class LinkSession {
    public static boolean matches(JSONObject current,JSONObject captured){
        return current.has("fingerprint")&&current.optString("fingerprint").equals(captured.optString("fingerprint"))
            &&current.optString("workspace").equals(captured.optString("workspace"))&&current.optString("link").equals(captured.optString("link"));
    }
}
