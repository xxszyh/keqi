package com.keqi.reminder;

import org.json.*;
import java.util.*;

/** The same task commands back both interfaces. Platform fields remain read-only. */
public final class TaskService {
    private final Workspace workspace;
    private final boolean host;
    public TaskService(Workspace workspace,boolean host){this.workspace=workspace;this.host=host;}
    public JSONObject command(String action,JSONObject command) throws JSONException {
        String id=command.optString("id"),type="manual";JSONObject existing=id.isEmpty()?null:workspace.get(id);
        if(existing!=null){id=existing.getString("id");type=existing.getString("entityType");}
        switch(action){
            case "createTask":{
                JSONObject fields=fields(command);JSONObject rule=command.optJSONObject("repeat");String created;
                if(rule==null)created=workspace.create(fields,host);else{Repeats.validate(rule);created=UUID.randomUUID().toString();JSONObject template=new JSONObject(fields.toString());template.remove("manualDue");workspace.edit(created,"series",new JSONObject().put("rule",rule).put("template",template).put("paused",false),host);Repeats.materialize(workspace,System.currentTimeMillis()-86400000L,System.currentTimeMillis()+30*86400000L,host);}
                return new JSONObject().put("id",created);
            }
            case "resolveConflict":
                if(existing==null)throw new IllegalArgumentException("没有找到记录");JSONObject resolution=command.optJSONObject("values");workspace.edit(id,type,resolution==null?fields(command):resolution,command.optJSONObject("bases"),host);break;
            case "updateTask":
                if(existing==null)throw new IllegalArgumentException("没有找到任务");workspace.edit(id,type,fields(command),command.optJSONObject("bases"),host);break;
            case "deleteTask":case "restoreTask":
                if(existing==null||!type.equals("manual"))throw new IllegalArgumentException("只能删除或恢复手动待办");workspace.edit(id,type,new JSONObject().put("deleted",action.equals("deleteTask")),host);break;
            case "addSubtask":
                JSONObject parent=workspace.get(command.getString("parent"));if(parent==null||!Arrays.asList("manual","platform").contains(parent.getString("entityType")))throw new IllegalArgumentException("父任务不存在");
                workspace.edit(UUID.randomUUID().toString(),"subtask",new JSONObject().put("parent",parent.getString("id")).put("title",command.getString("title")).put("done",false).put("position",System.currentTimeMillis()),host);break;
            case "updateSubtask":
                if(existing==null||!type.equals("subtask"))throw new IllegalArgumentException("没有找到步骤");JSONObject step=new JSONObject();for(String k:new String[]{"title","done","position","deleted"})if(command.has(k))step.put(k,command.get(k));workspace.edit(id,type,step,command.optJSONObject("bases"),host);break;
            case "reorderSubtasks":{
                JSONArray ids=command.getJSONArray("ids");if(ids.length()>500)throw new IllegalArgumentException("步骤过多");for(int i=0;i<ids.length();i++){JSONObject child=workspace.get(ids.getString(i));if(child==null||!child.optString("parent").equals(command.getString("parent")))throw new IllegalArgumentException("步骤列表已变化");workspace.edit(child.getString("id"),"subtask",new JSONObject().put("position",i),host);}break;
            }
            case "materializeRange":long from=command.getLong("from"),until=command.getLong("until");if(from<946684800000L||until>7258118400000L||until<from||until-from>366*86400000L)throw new IllegalArgumentException("生成范围不超过一年");Repeats.materialize(workspace,from,until,host);break;
            case "changeSeries":
                if(existing==null||!type.equals("series"))throw new IllegalArgumentException("重复系列不存在");JSONObject change=new JSONObject();if(command.has("paused"))change.put("paused",command.getBoolean("paused"));if(command.has("deleted"))change.put("deleted",command.getBoolean("deleted"));
                if(command.has("rule")){JSONObject rule=new JSONObject(command.getJSONObject("rule").toString());Repeats.validate(rule);long cutoff=java.time.LocalDateTime.parse(rule.getString("start")).atZone(java.time.ZoneId.of(rule.optString("timezone","Asia/Shanghai"))).toInstant().toEpochMilli();JSONObject previous=new JSONObject(existing.getJSONObject("rule").toString());previous.put("until",cutoff-1);rule.put("previous",previous).put("segment",Core.hash(workspace.nextOperationId()));change.put("rule",rule);}workspace.edit(id,type,change,command.optJSONObject("bases"),host);break;
            case "batchTasks":
                JSONArray ids=command.getJSONArray("ids");if(ids.length()>500)throw new IllegalArgumentException("一次最多编辑 500 项");String operation=command.getString("operation");
                if(!Arrays.asList("updateTask","deleteTask","restoreTask").contains(operation))throw new IllegalArgumentException("批量动作无效");for(int i=0;i<ids.length();i++){JSONObject single=new JSONObject(command.optJSONObject("values")==null?"{}":command.getJSONObject("values").toString()).put("id",ids.getString(i));command(operation,single);}break;
            case "applyCourseChoices":{
                JSONObject choices=command.optJSONObject("choices");if(choices!=null){Iterator<String> keys=choices.keys();while(keys.hasNext()){String key=keys.next();editCourse(key,new JSONObject().put("enabled",choices.getBoolean(key)));}}
                for(String k:new String[]{"remove","restore"}){JSONArray keys=command.optJSONArray(k);if(keys!=null)for(int i=0;i<keys.length();i++)editCourse(keys.getString(i),new JSONObject().put("removed",k.equals("remove")));}break;
            }
            default:throw new IllegalArgumentException("不支持的任务操作");
        }
        return new JSONObject();
    }
    private void editCourse(String id,JSONObject change) throws JSONException {JSONObject c=workspace.get(id);if(c==null||!c.optString("entityType").equals("course"))throw new IllegalArgumentException("课程列表已变化，请重新打开");workspace.edit(c.getString("id"),"course",change,host);}
    private JSONObject fields(JSONObject command) throws JSONException {JSONObject values=new JSONObject();for(String key:new String[]{"title","manualDue","localDone","snoozeUntil","note","priority","tags","courseKey","courseTitle"})if(command.has(key))values.put(key,command.get(key));return values;}
    public JSONArray courses() throws JSONException {return workspace.list("course");}
    public JSONArray tasks(boolean trash) throws JSONException {
        JSONArray result=new JSONArray(),tasks=workspace.list("manual","platform"),steps=workspace.list("subtask"),courses=courses();
        Map<String,JSONObject> series=new HashMap<>();JSONArray allSeries=workspace.list("series");for(int i=0;i<allSeries.length();i++)series.put(allSeries.getJSONObject(i).getString("id"),allSeries.getJSONObject(i));
        Map<String,JSONObject> catalog=new HashMap<>();for(int i=0;i<courses.length();i++)catalog.put(courses.getJSONObject(i).getString("id"),courses.getJSONObject(i));
        for(int i=0;i<tasks.length();i++){JSONObject t=tasks.getJSONObject(i);if(t.optBoolean("deleted")!=trash||!trash&&t.optBoolean("retired"))continue;if(t.getString("entityType").equals("manual"))t.put("platform","manual").put("kind","todo").put("status","pending");
            JSONObject rule=series.get(t.optString("seriesId"));if(!trash&&rule!=null&&t.optLong("originalDue")>System.currentTimeMillis()){
                boolean invalid=!Repeats.validInstance(t,rule),edited=t.getJSONObject("fieldVersions").length()>1;boolean inactive=invalid||rule.optBoolean("paused")||rule.optBoolean("deleted")||rule.getJSONArray("conflicts").length()>0;t.put("reminderSuppressed",inactive);if(invalid&&!edited)continue;if(invalid)t.put("seriesReplaced",true);
            }
            String course=t.optString("courseKey");JSONObject c=catalog.get(course);if(!trash&&c!=null&&c.optBoolean("removed"))continue;if(c!=null)t.put("courseTitle",c.optString("title"));
            if(!t.has("courseTitle"))t.put("courseTitle","个人事务");if(!t.has("due"))t.put("due",JSONObject.NULL);if(!t.has("localDone"))t.put("localDone",false);if(!t.has("title"))t.put("title","待办");
            JSONArray children=new JSONArray();for(int j=0;j<steps.length();j++){JSONObject child=steps.getJSONObject(j);if(child.optString("parent").equals(t.getString("id"))&&!child.optBoolean("deleted"))children.put(child);}t.put("subtasks",children);result.put(t);
        }return result;
    }
}
