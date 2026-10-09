package com.keqi.reminder;

import org.json.*;
import java.util.*;

/** Fresh course metadata never overwrites the user's persistent display/reminder choice. */
public final class CourseSelection {
    public static JSONArray merge(JSONArray previous,JSONArray incoming) throws JSONException {
        Map<String,JSONObject> merged=new LinkedHashMap<>();
        if(previous!=null)for(int i=0;i<previous.length();i++){JSONObject c=previous.getJSONObject(i);merged.put(c.getString("key"),c);}
        for(int i=0;i<incoming.length();i++){
            JSONObject c=incoming.getJSONObject(i),old=merged.get(c.getString("key"));
            if(old==null&&c.optString("platform").equals("chaoxing")&&!c.optString("courseId").isEmpty()&&!c.optString("classId").isEmpty()){
                for(JSONObject prior:merged.values())if(prior.optString("platform").equals("chaoxing")&&prior.optString("courseId").equals(c.optString("courseId"))&&prior.optString("classId").equals(c.optString("classId"))){old=prior;c.put("key",prior.getString("key"));break;}
            }
            if(old!=null){
                c.put("enabled",old.optBoolean("enabled",true));
                if(c.optString("platform").equals("chaoxing")&&!ChaoxingCourses.hasName(c.optString("title"))&&ChaoxingCourses.hasName(old.optString("title")))c.put("title",old.getString("title"));
            }
            merged.put(c.getString("key"),c);
        }
        return new JSONArray(merged.values());
    }
    public static String displayTitle(String platform,String title){return platform.equals("chaoxing")?ChaoxingCourses.displayName(title):title;}
    public static JSONArray refreshTaskTitles(JSONArray tasks,JSONArray courses) throws JSONException {
        Map<String,JSONObject> names=new HashMap<>();for(int i=0;i<courses.length();i++){JSONObject course=courses.getJSONObject(i);names.put(course.getString("key"),course);}
        JSONArray changed=new JSONArray();
        for(int i=0;i<tasks.length();i++){
            JSONObject task=tasks.getJSONObject(i),course=names.get(task.optString("courseKey"));
            if(course==null||!course.optString("platform").equals(task.optString("platform")))continue;
            String name=displayTitle(course.optString("platform"),course.optString("title"));
            if(!name.equals(task.optString("courseTitle"))){task.put("courseTitle",name);changed.put(task);}
        }
        return changed;
    }
}
