package com.keqi.reminder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Notifications {
    private static final String CHANNEL="deadlines";
    public static boolean enabled(Context c){NotificationManager manager=c.getSystemService(NotificationManager.class);NotificationChannel channel=manager.getNotificationChannel(CHANNEL);return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&manager.areNotificationsEnabled()&&(channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);}
    public static void create(Context c){NotificationChannel channel=new NotificationChannel(CHANNEL,"作业、考试截止与同步提醒",NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription("临近截止、截止变更和登录失效提醒");c.getSystemService(NotificationManager.class).createNotificationChannel(channel);}
    public static boolean post(Context c,String key,String title,String content){
        create(c);if(!enabled(c))return false;
        Intent open=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_my_calendar).setContentTitle(title).setContentText(content).setStyle(new Notification.BigTextStyle().bigText(content)).setContentIntent(pi).setAutoCancel(true).build();
        c.getSystemService(NotificationManager.class).notify(key.hashCode(),n);return true;
    }
    private static PendingIntent alarmIntent(Context c,String key){Intent intent=new Intent(c,AlarmReceiver.class).setData(Uri.parse("keqi://notice/"+key)).putExtra("key",key);return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static void schedule(Context c,String key,long at){c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,Math.max(System.currentTimeMillis()+1000,at),alarmIntent(c,key));}
    public static long due(JSONObject t){return !t.isNull("manualDue")?t.optLong("manualDue"):t.optLong("due",0);}
    private static boolean actionable(Store store,JSONObject task){return !task.optString("status").equals("done")&&!task.optBoolean("localDone")&&store.enabled(task.optString("courseKey"));}
    public static synchronized void rebuild(Context c){
        try(Store store=new Store(c)){
            JSONArray pending=store.pendingNotices();for(int i=0;i<pending.length();i++){String key=pending.getJSONObject(i).getString("key");c.getSystemService(AlarmManager.class).cancel(alarmIntent(c,key));store.noticeState(key,"cancelled");}
            JSONObject settings=store.settings();JSONArray offsets=settings.getJSONArray("offsets");double[] hours=new double[offsets.length()];for(int i=0;i<hours.length;i++)hours[i]=offsets.getDouble(i);
            long now=System.currentTimeMillis();JSONArray tasks=store.tasks();
            for(int i=0;i<tasks.length();i++){
                JSONObject t=tasks.getJSONObject(i);long due=due(t);if(!actionable(store,t)||due<=now)continue;
                int version=t.optInt("version",1);String id=t.getString("id");double urgent=Core.mostUrgent(due,now,hours);
                for(double offset:hours){long at=due-(long)(offset*3600000);if(at<=now&&Double.compare(offset,urgent)!=0)continue;
                    String kind="remind:"+offset;String key=Core.hash(id+":"+version+":"+kind);
                    at=Math.max(now+1000,Math.max(at,t.optLong("snoozeUntil",0)));if(at>=due)continue;
                    register(c,store,key,id,version,kind,at);
                }
                if(t.optBoolean("deadlineChanged")){String key=Core.hash(id+":"+t.optInt("deadlineEventVersion",version)+":change");register(c,store,key,id,version,"change",now+1000);}
            }
        }catch(Exception e){throw new IllegalStateException("无法安排本地提醒",e);}
    }
    private static void register(Context c,Store store,String key,String id,int version,String kind,long at){
        if(store.noticeState(key).equals("posted"))return;
        store.notice(key,id,version,kind,at,"pending");store.noticeState(key,"pending");schedule(c,key,at);
    }
    public static synchronized void deliver(Context c,String key){
        try(Store store=new Store(c)){
            if(!store.noticeState(key).equals("pending"))return;
            JSONObject found=null;JSONArray notices=store.pendingNotices();for(int i=0;i<notices.length();i++)if(notices.getJSONObject(i).getString("key").equals(key)){found=notices.getJSONObject(i);break;}if(found==null)return;
            JSONObject t=store.task(found.getString("taskId"));long now=System.currentTimeMillis();
            if(t==null||t.optInt("version",1)!=found.getInt("version")||!actionable(store,t)||due(t)<=now){store.noticeState(key,"cancelled");return;}
            JSONObject prefs=store.settings();long due=due(t);
            String kind=found.getString("kind");
            if(kind.startsWith("remind:")){
                double requested=Double.parseDouble(kind.substring(7));long threshold=due-(long)(requested*3600000);
                if(now<threshold){schedule(c,key,threshold);return;}
                JSONArray a=prefs.getJSONArray("offsets");double[] offsets=new double[a.length()];for(int i=0;i<offsets.length;i++)offsets[i]=a.getDouble(i);
                if(!Core.currentThreshold(due,now,offsets,requested)){store.noticeState(key,"cancelled");return;}
            }
            if(Core.quiet(now,prefs.optString("quietStart"),prefs.optString("quietEnd"))&&!(prefs.optBoolean("urgentOverride")&&due-now<=10800000)){
                long later=Core.quietEnd(now,prefs.getString("quietEnd"));if(later<due)schedule(c,key,later);else store.noticeState(key,"cancelled");return;
            }
            if(t.optLong("snoozeUntil",0)>now){schedule(c,key,t.getLong("snoozeUntil"));return;}
            String label=t.optString("kind").equals("exam")?"考试":t.optString("kind").equals("quiz")?"测验":t.optString("phase").equals("peerreview")?"作业互评":"作业提交";
            SimpleDateFormat df=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);df.setTimeZone(Core.CN);
            long mins=Math.max(1,(due-now)/60000);String remaining=mins>=60?(long)Math.ceil(mins/60.0)+" 小时":"约 "+mins+" 分钟";
            String title=found.getString("kind").equals("change")?label+"截止时间变更":label+"提醒 · "+remaining;
            String body=CourseSelection.displayTitle(t.optString("platform"),t.optString("courseTitle"))+" · "+t.optString("title")+"\n"+df.format(new Date(due))+" 截止";
            if(t.optString("status").equals("unknown"))body+="\n平台完成状态待确认，请核实。";
            if(now-t.optLong("deadlineCheckedAt",t.optLong("lastSeen",0))>7200000)body+="\n基于上次同步信息，打开 App 更新。";
            if(post(c,key,title,body))store.noticeState(key,"posted");
            else if(now+1800000<due)schedule(c,key,now+1800000);
        }catch(Exception ignored){/* Failed deliveries remain pending and are rebuilt on next sync/open. */}
    }
}
