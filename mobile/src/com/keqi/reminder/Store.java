package com.keqi.reminder;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    private final Context context;
    public Store(Context context){super(context,"keqi.db",null,1);this.context=context.getApplicationContext();setWriteAheadLoggingEnabled(true);}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY,platform TEXT NOT NULL,data TEXT NOT NULL)");db.execSQL("CREATE TABLE notices(key TEXT PRIMARY KEY,task_id TEXT,version INTEGER,kind TEXT,due INTEGER,state TEXT NOT NULL)");db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");}
    public void onUpgrade(SQLiteDatabase db,int a,int b){throw new IllegalStateException("Unsupported database version");}
    public synchronized JSONObject meta(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?",new String[]{key})){return c.moveToFirst()?new JSONObject(c.getString(0)):new JSONObject();}catch(JSONException e){throw new IllegalStateException("本机设置数据损坏",e);}}
    public synchronized void putMeta(String key,JSONObject data){ContentValues v=new ContentValues();v.put("key",key);v.put("value",data.toString());getWritableDatabase().insertWithOnConflict("meta",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public JSONObject settings(){JSONObject s=meta("settings");try{if(!s.has("offsets"))s.put("offsets",new JSONArray(new double[]{72,24,3}));if(!s.has("syncMinutes"))s.put("syncMinutes",30);if(!s.has("quietStart"))s.put("quietStart","23:00");if(!s.has("quietEnd"))s.put("quietEnd","07:00");}catch(JSONException e){throw new IllegalStateException(e);}return s;}
    public synchronized JSONArray tasks(){JSONArray arr=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM tasks",null)){while(c.moveToNext())arr.put(new JSONObject(c.getString(0)));return arr;}catch(JSONException e){throw new IllegalStateException(e);}}
    public synchronized JSONObject task(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM tasks WHERE id=?",new String[]{id})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}catch(JSONException e){throw new IllegalStateException(e);}}
    public synchronized void updateTask(JSONObject task){ContentValues v=new ContentValues();v.put("id",task.optString("id"));v.put("platform",task.optString("platform"));v.put("data",task.toString());getWritableDatabase().insertWithOnConflict("tasks",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public synchronized void patchTask(JSONObject patch) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            JSONObject task=task(patch.getString("id"));if(task==null)throw new IllegalArgumentException("没有找到这份作业");
            for(String k:new String[]{"localDone","manualDue","snoozeUntil"})if(patch.has(k)){
                if(k.equals("localDone"))task.put(k,patch.getBoolean(k));
                else if(patch.isNull(k))task.put(k,JSONObject.NULL);
                else{long n=patch.getLong(k);if(n<946684800000L||n>7258118400000L)throw new IllegalArgumentException("日期范围不正确");task.put(k,n);}
            }
            task.put("version",task.optInt("version",1)+1);updateTask(task);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void ingest(String platform,String expectedAccount,JSONArray incoming,JSONArray courses,String status,String message) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            JSONObject active=meta("connection."+platform);
            if(!active.optBoolean("connected")||!active.optString("account").equals(expectedAccount))return;
            for(int i=0;i<incoming.length();i++){
                JSONObject fresh=incoming.getJSONObject(i),old=task(fresh.getString("id"));
                long now=System.currentTimeMillis();fresh.put("lastSeen",now);
                if(fresh.optBoolean("deadlineObserved",true))fresh.put("deadlineCheckedAt",now);
                else if(old!=null)fresh.put("deadlineCheckedAt",old.optLong("deadlineCheckedAt",old.optLong("lastSeen")));
                if(old!=null){
                    for(String key:new String[]{"manualDue","localDone","snoozeUntil","firstSeen"})if(old.has(key))fresh.put(key,old.get(key));
                    if(!fresh.optBoolean("deadlineObserved",true))for(String key:new String[]{"due","rawDeadline","precision"})if(old.has(key))fresh.put(key,old.get(key));
                    boolean changed=!Objects.equals(old.opt("due"),fresh.opt("due"))||!old.optString("status").equals(fresh.optString("status"));
                    fresh.put("version",old.optInt("version",1)+(changed?1:0));
                    if(changed&&!Objects.equals(old.opt("due"),fresh.opt("due"))&&!fresh.isNull("due")&&!old.isNull("due"))fresh.put("deadlineChanged",true).put("deadlineEventVersion",fresh.getInt("version"));
                    else if(old.optBoolean("deadlineChanged"))fresh.put("deadlineChanged",true).put("deadlineEventVersion",old.optInt("deadlineEventVersion",old.optInt("version",1)));
                }else{fresh.put("version",1);fresh.put("firstSeen",now);}
                updateTask(fresh);
            }
            JSONObject connection=meta("connection."+platform);connection.put("platform",platform).put("status",status).put("message",message).put("lastAttempt",System.currentTimeMillis());
            if(status.equals("ok"))connection.put("lastSuccess",System.currentTimeMillis());
            JSONArray mergedCourses=CourseSelection.merge(connection.optJSONArray("courses"),courses);
            connection.put("courses",mergedCourses);putMeta("connection."+platform,connection);
            // Rename even cached/hidden homework without changing reminder versions or dates.
            JSONArray renamed=CourseSelection.refreshTaskTitles(tasks(),mergedCourses);
            for(int i=0;i<renamed.length();i++)updateTask(renamed.getJSONObject(i));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public JSONArray courses(){JSONArray result=new JSONArray();for(String p:new String[]{"mooc","chaoxing"}){JSONArray cs=meta("connection."+p).optJSONArray("courses");if(cs!=null)for(int i=0;i<cs.length();i++)result.put(cs.opt(i));}return result;}
    public synchronized void enableCourse(String key,boolean enabled) throws JSONException {
        enableCourses(Collections.singleton(key),enabled);
    }
    public synchronized void enableCourses(Set<String> keys,boolean enabled) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{for(String p:new String[]{"mooc","chaoxing"}){JSONObject c=meta("connection."+p);JSONArray courses=c.optJSONArray("courses");if(courses==null)continue;for(int i=0;i<courses.length();i++)if(keys.contains(courses.getJSONObject(i).getString("key")))courses.getJSONObject(i).put("enabled",enabled);putMeta("connection."+p,c);}db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public synchronized boolean connectionError(String platform,String expectedAccount,String state,String message) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{JSONObject c=meta("connection."+platform);if(!c.optBoolean("connected")||!c.optString("account").equals(expectedAccount))return false;boolean changed=!c.optString("status").equals(state);c.put("status",state).put("message",message).put("lastAttempt",System.currentTimeMillis());putMeta("connection."+platform,c);db.setTransactionSuccessful();return changed;}finally{db.endTransaction();}
    }
    public synchronized void verificationPage(String platform,String expectedAccount,String url) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            JSONObject c=meta("connection."+platform);if(!c.optBoolean("connected")||!c.optString("account").equals(expectedAccount))return;
            if(url.isEmpty())c.remove("verificationUrl");else if(platform.equals("chaoxing")&&ChaoxingReadPolicy.allowed(url))c.put("verificationUrl",url);
            putMeta("connection."+platform,c);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public boolean enabled(String courseKey){JSONArray cs=courses();for(int i=0;i<cs.length();i++){JSONObject c=cs.optJSONObject(i);if(c!=null&&c.optString("key").equals(courseKey))return c.optBoolean("enabled",true);}return true;}
    public synchronized void notice(String key,String taskId,int version,String kind,long due,String state){ContentValues v=new ContentValues();v.put("key",key);v.put("task_id",taskId);v.put("version",version);v.put("kind",kind);v.put("due",due);v.put("state",state);getWritableDatabase().insertWithOnConflict("notices",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public String noticeState(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT state FROM notices WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):"";}}
    public synchronized void noticeState(String key,String state){ContentValues v=new ContentValues();v.put("state",state);getWritableDatabase().update("notices",v,"key=?",new String[]{key});}
    public JSONArray pendingNotices(){JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT key,task_id,version,kind,due FROM notices WHERE state='pending'",null)){while(c.moveToNext())out.put(new JSONObject().put("key",c.getString(0)).put("taskId",c.getString(1)).put("version",c.getInt(2)).put("kind",c.getString(3)).put("at",c.getLong(4)));return out;}catch(JSONException e){throw new IllegalStateException(e);}}
    public synchronized void disconnect(String platform){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("tasks","platform=?",new String[]{platform});JSONObject c=new JSONObject().put("platform",platform).put("status","unconfigured");putMeta("connection."+platform,c);db.setTransactionSuccessful();}catch(JSONException e){throw new IllegalStateException(e);}finally{db.endTransaction();}}
    public JSONObject snapshot(boolean syncing) throws JSONException {
        JSONArray connections=new JSONArray();for(String p:new String[]{"mooc","chaoxing"}){JSONObject c=meta("connection."+p);if(!c.has("platform"))c.put("platform",p).put("status","unconfigured");connections.put(c);}
        return new JSONObject().put("tasks",tasks()).put("connections",connections).put("courses",courses()).put("settings",settings()).put("syncing",syncing).put("notificationEnabled",Notifications.enabled(context));
    }
}
