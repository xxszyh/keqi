package com.keqi.reminder;

import android.webkit.CookieManager;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Reads the same paginated enrollment RPC used by the current personal course cards. */
public final class MoocCourses {
    public static final String ENDPOINT="https://www.icourse163.org/web/j/learnerCourseRpcBean.getMyLearnedCoursePanelList.rpc";
    public static final class Batch {public final JSONArray courses=new JSONArray();public final List<String> warnings=new ArrayList<>();}
    public interface Requester {JSONObject page(int scope,int page) throws Exception;}
    public static Batch read() throws Exception {
        String cookies=CookieManager.getInstance().getCookie("https://www.icourse163.org/");Matcher token=Pattern.compile("(?:^|;\\s*)NTESSTUDYSI=([^;]+)").matcher(cookies==null?"":cookies);if(!token.find())throw new Http.LoginRequired();
        String csrf=token.group(1);
        return read((scope,page)->{
            if(page>1||scope==2)Thread.sleep(250);
            String body="type=30&p="+page+"&psize=8&courseType="+scope;
            return new JSONObject(Http.request(ENDPOINT+"?csrfKey="+Http.enc(csrf),"mooc",body,"application/x-www-form-urlencoded;charset=UTF-8"));
        });
    }
    public static Batch read(Requester requester) throws Exception {
        MoocCatalog.Batch found=MoocCatalog.read(requester::page);Batch out=new Batch();for(int i=0;i<found.courses.length();i++)out.courses.put(found.courses.get(i));out.warnings.addAll(found.warnings);return out;
    }
}
