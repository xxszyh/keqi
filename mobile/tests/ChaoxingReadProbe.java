package com.keqi.reminder;
import org.json.*;
import java.io.*;

/** Explicit fixture DOM -> production protected-page gate -> real homework/deadline parser. */
public final class ChaoxingReadProbe {
    public static void main(String[] args) throws Exception {
        JSONObject input=new JSONObject(new BufferedReader(new InputStreamReader(System.in,"UTF-8")).readLine());
        try{
            ChaoxingReadPolicy.Stage stage=ChaoxingReadPolicy.Stage.valueOf(input.getString("stage"));
            Http.Page page=ChaoxingReadPolicy.fromBrowser(input.getString("snapshot"),input.getString("cookies"),"123",stage);
            JSONObject result=new JSONObject().put("ready",page!=null);
            if(page==null){JSONObject snapshot=new JSONObject(new JSONTokener(input.getString("snapshot")).nextValue().toString());result.put("next",ChaoxingReadPolicy.embeddedPage(snapshot.getString("url"),snapshot.getString("html"),stage));}
            if(page!=null){
                if(stage==ChaoxingReadPolicy.Stage.WORK_LIST)result.put("tasks",Collectors.chaoxingList(page.html,new JSONObject().put("key","fixture-course").put("title","物理"),"fixture-account"));
                if(stage==ChaoxingReadPolicy.Stage.WORK_DETAIL){JSONObject task=new JSONObject();Collectors.chaoxingDetail(task,page.html,null);result.put("task",task);}
                if(stage==ChaoxingReadPolicy.Stage.EXAM_LIST)result.put("tasks",ChaoxingExams.parse(page.html,page.url,new JSONArray().put(new JSONObject().put("key","fixture-course").put("title","物理").put("courseId","1").put("classId","2")),"fixture-account",null).tasks);
            }
            System.out.println(result);
        }catch(Exception e){System.out.println(new JSONObject().put("ready",false).put("error",e.getClass().getSimpleName()));}
    }
}
