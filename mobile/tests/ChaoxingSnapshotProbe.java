package com.keqi.reminder;
import org.json.*;
import java.io.*;

/** Browser-fixture seam: production protected DOM -> session -> selectable courses. */
public final class ChaoxingSnapshotProbe {
    public static void main(String[] args) throws Exception {
        JSONObject input=new JSONObject(new BufferedReader(new InputStreamReader(System.in,"UTF-8")).readLine());
        try{Authentication.Session result=Authentication.fromBrowser(input.getString("snapshot"),input.getString("cookies"));System.out.println(new JSONObject().put("accepted",true).put("courses",result.courses).put("warning",result.warning));}
        catch(Exception e){System.out.println(new JSONObject().put("accepted",false).put("reason",e.getClass().getSimpleName()));}
    }
}
