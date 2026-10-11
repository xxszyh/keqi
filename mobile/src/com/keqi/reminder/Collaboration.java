package com.keqi.reminder;

import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Atomic bridge between the existing Android database and the shared task model. */
public final class Collaboration {
    public interface Change<T>{T apply(Workspace workspace) throws Exception;}
    public static <T> T transact(Store store,Change<T> change) throws Exception {
        synchronized(store){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            Workspace workspace=load(store);T result=change.apply(workspace);store.putMeta("collaboration",workspace.save());db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}}
    }
    public static Workspace load(Store store) throws JSONException {
        synchronized(store){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{Workspace workspace=loadWithin(store);int count=workspace.pending().length();Repeats.materialize(workspace,System.currentTimeMillis()-86400000L,System.currentTimeMillis()+30*86400000L,false);if(workspace.pending().length()!=count)store.putMeta("collaboration",workspace.save());db.setTransactionSuccessful();return workspace;}finally{db.endTransaction();}}
    }
    private static Workspace loadWithin(Store store) throws JSONException {
        JSONObject saved=store.meta("collaboration");Workspace workspace=new Workspace(saved);
        if(!saved.has("device")){
            JSONObject local=new JSONObject();JSONArray raw=store.rawTasks();
            for(String platform:new String[]{"mooc","chaoxing"}){JSONObject connection=store.meta("connection."+platform);String account=connection.optString("sourceAccount");if(account.isEmpty())continue;JSONArray courses=connection.optJSONArray("courses");if(courses==null)courses=new JSONArray();JSONArray tasks=new JSONArray();for(int i=0;i<raw.length();i++)if(raw.getJSONObject(i).optString("platform").equals(platform))tasks.put(raw.getJSONObject(i));workspace.observe(platform,account,courses,tasks,true,false);
                for(int i=0;i<courses.length();i++){JSONObject c=courses.getJSONObject(i);workspace.edit(workspace.canonical(c.getString("key")),"course",new JSONObject().put("enabled",c.optBoolean("enabled",true)).put("removed",c.optBoolean("removed",false)),false);}
                for(int i=0;i<tasks.length();i++){JSONObject task=tasks.getJSONObject(i);String canonical=workspace.canonical(task.getString("id"));if(workspace.get(canonical)==null)continue;JSONObject edits=new JSONObject();for(String k:new String[]{"localDone","manualDue","snoozeUntil"})if(task.has(k))edits.put(k,task.get(k));if(edits.length()>0)workspace.edit(canonical,"platform",edits,false);local.put(canonical,new JSONObject().put("noticeId",task.getString("id")).put("version",task.optInt("version",1)).put("due",task.isNull("manualDue")?task.opt("due"):task.opt("manualDue")).put("status",task.optString("status")));}
            }
            store.putMeta("collaborationLocal",local);store.putMeta("collaboration",workspace.save());
        }return workspace;
    }
    public static JSONArray tasks(Store store) throws JSONException {
        synchronized(store){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{JSONArray result=tasksWithin(store);db.setTransactionSuccessful();return result;}finally{db.endTransaction();}}
    }
    private static JSONArray tasksWithin(Store store) throws JSONException {
        Workspace w=load(store);JSONArray tasks=new TaskService(w,false).tasks(false),raw=store.rawTasks();Set<String> known=new HashSet<>();JSONObject local=store.meta("collaborationLocal");
        for(int i=0;i<tasks.length();i++){JSONObject t=tasks.getJSONObject(i);String id=t.getString("id");known.add(id);JSONObject previous=local.optJSONObject(id);if(previous==null)previous=new JSONObject().put("noticeId",id).put("version",1);Object due=t.opt("manualDue");if(due==null||due==JSONObject.NULL)due=t.opt("due");
            if(previous.has("due")&&(!Objects.equals(String.valueOf(due),String.valueOf(previous.opt("due")))||!previous.optString("status").equals(t.optString("status"))))previous.put("version",previous.optInt("version",1)+1);
            previous.put("due",due==null?JSONObject.NULL:due).put("status",t.optString("status"));local.put(id,previous);t.put("version",previous.optInt("version",1)).put("noticeId",previous.getString("noticeId"));
        }
        for(int i=0;i<raw.length();i++){JSONObject t=raw.getJSONObject(i);if(!known.contains(w.canonical(t.getString("id")))&&w.get(t.getString("id"))==null)tasks.put(t);}
        store.putMeta("collaborationLocal",local);return tasks;
    }
    public static JSONArray courses(Store store) throws JSONException {
        Workspace w=load(store);JSONArray result=new TaskService(w,false).courses(),raw=store.rawCourses();Set<String> known=new HashSet<>();for(int i=0;i<result.length();i++)known.add(result.getJSONObject(i).getString("key"));for(int i=0;i<raw.length();i++)if(!known.contains(w.canonical(raw.getJSONObject(i).getString("key"))))result.put(raw.getJSONObject(i));return result;
    }
    public static void observe(Store store,String platform,JSONArray tasks,JSONArray courses) throws Exception {
        String account=store.meta("connection."+platform).optString("sourceAccount");if(account.isEmpty())return;transact(store,w->{w.observe(platform,account,courses,tasks,false,false);return null;});
    }
    public static void command(Store store,String action,JSONObject command) throws Exception {
        transact(store,w->{if(action.equals("updateTask")&&w.get(command.getString("id"))==null){JSONArray legacy=store.rawTasks();for(int i=0;i<legacy.length();i++){JSONObject task=legacy.getJSONObject(i);if(!task.optString("id").equals(command.getString("id")))continue;for(String field:new String[]{"manualDue","localDone","snoozeUntil","note","priority","tags"})if(command.has(field)){Workspace.validateField("platform",field,command.get(field));task.put(field,command.get(field));}if(command.has("manualDue"))task.put("version",task.optInt("version",1)+1);store.updateTask(task);return null;}throw new IllegalArgumentException("没有找到任务");}return new TaskService(w,false).command(action,command);});
    }
    public static JSONObject status(Store store) throws JSONException {
        JSONObject connection=store.meta("lan.connection"),status=store.meta("lan.status");Workspace w=load(store);int conflicts=0;JSONArray tasks=new TaskService(w,false).tasks(false);for(int i=0;i<tasks.length();i++)conflicts+=tasks.getJSONObject(i).getJSONArray("conflicts").length();
        return new JSONObject().put("paired",connection.has("fingerprint")).put("address",connection.optString("address")).put("port",connection.optInt("port")).put("pending",w.pending().length()).put("conflicts",conflicts).put("lastSuccess",status.optLong("lastSuccess")).put("message",status.optString("message",connection.has("fingerprint")?"等待局域网连接":"尚未配对电脑"));
    }
}
