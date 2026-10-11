package com.keqi.reminder;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Production reminder calculation must not read the whole workspace per task. */
public final class NotificationScaleRepro {
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("keqi-notification-scale-");Context context=new Context(directory);
        try{
            try(Store store=new Store(context)){
                JSONArray courses=new JSONArray();
                for(int i=0;i<42;i++)courses.put(new JSONObject().put("key","chaoxing:"+i+":202:303").put("title","示例课程 "+i).put("platform","chaoxing").put("enabled",i!=0).put("removed",i==1));
                store.putMeta("connection.chaoxing",new JSONObject().put("sourceAccount","7").put("connected",true).put("courses",courses));
                for(int i=0;i<594;i++)store.updateTask(new JSONObject().put("id",Core.hash("task"+i)).put("platform","chaoxing").put("sourceId",String.valueOf(i)).put("courseKey","chaoxing:"+(i%42)+":202:303").put("title","示例作业 "+i).put("kind","homework").put("phase","submit").put("due",i<42?System.currentTimeMillis()+10*86400000L:JSONObject.NULL).put("status",i==2?"done":"pending").put("localDone",i==3));
                // Legacy task lacking a stable source ID still obeys its disabled course.
                store.updateTask(new JSONObject().put("id","legacy").put("platform","chaoxing").put("courseKey","chaoxing:0:202:303").put("title","旧记录").put("due",System.currentTimeMillis()+10*86400000L).put("status","pending"));
                store.snapshot(false);
            }
            SQLiteDatabase.workspaceReads=0;
            Notifications.rebuild(context);
            if(SQLiteDatabase.workspaceReads>4)throw new AssertionError("Reminder rebuild blocks startup with repeated full-workspace reads: "+SQLiteDatabase.workspaceReads);
            try(Store store=new Store(context)){
                // Disabled/removed courses, platform completion and local completion cancel alarms.
                if(store.pendingNotices().length()!=38*3)throw new AssertionError("Course or completion choices were not respected");
            }
            System.out.println("Reminder rebuild reads one catalog; legacy, removed and completed tasks keep their alarm rules");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(directory)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);}}
    }
}
