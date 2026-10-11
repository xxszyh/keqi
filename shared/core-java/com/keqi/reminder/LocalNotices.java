package com.keqi.reminder;

import java.util.Objects;
import org.json.*;

/** Device-local reminder identities/events are never part of shared task operations. */
public final class LocalNotices {
    public static JSONObject fromLegacy(JSONObject task) throws JSONException {
        JSONObject local=new JSONObject().put("noticeId",task.getString("id")).put("version",task.optInt("version",1))
            .put("due",task.isNull("manualDue")?task.opt("due"):task.opt("manualDue"))
            .put("sourceDue",task.opt("due")).put("status",task.optString("status"));
        for(String field:new String[]{"deadlineChanged","deadlineEventVersion"})if(task.has(field))local.put(field,task.get(field));
        return local;
    }
    public static void annotate(JSONObject task,JSONObject local) throws JSONException {
        if(!local.has("noticeId"))local.put("noticeId",task.getString("id")).put("version",1);
        Object due=task.isNull("manualDue")?task.opt("due"):task.opt("manualDue");
        boolean dateChanged=local.has("due")&&!Objects.equals(String.valueOf(due),String.valueOf(local.opt("due")));
        if(local.has("due")&&(dateChanged||!local.optString("status").equals(task.optString("status"))))local.put("version",local.optInt("version",1)+1);
        Object prior=local.opt("sourceDue"),source=task.opt("due");
        if(!task.optString("platform").equals("manual")&&prior instanceof Number&&source instanceof Number&&!Objects.equals(String.valueOf(prior),String.valueOf(source)))
            local.put("deadlineChanged",true).put("deadlineEventVersion",Math.max(local.optInt("deadlineEventVersion")+1,local.optInt("version",1)));
        local.put("due",due==null?JSONObject.NULL:due).put("sourceDue",source==null?JSONObject.NULL:source).put("status",task.optString("status"));
        task.put("version",local.optInt("version",1)).put("noticeId",local.getString("noticeId"));
        for(String field:new String[]{"deadlineChanged","deadlineEventVersion"})if(local.has(field))task.put(field,local.get(field));
    }
}
