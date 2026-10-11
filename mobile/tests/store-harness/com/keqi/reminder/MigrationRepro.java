package com.keqi.reminder;
import android.content.Context;
import java.nio.file.*;
import java.util.*;
import org.json.*;
public final class MigrationRepro {
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("keqi-mobile-migration-");Context context=new Context(directory);
        try{
            try(Store old=new Store(context)){
                JSONObject course=new JSONObject().put("key","chaoxing:101:202:303").put("platform","chaoxing").put("title","样例课程").put("courseId","101").put("classId","202").put("cpi","303").put("enabled",true);
                old.putMeta("connection.chaoxing",new JSONObject().put("platform","chaoxing").put("account",UUID.randomUUID().toString()).put("sourceAccount","7").put("connected",true).put("status","ok").put("courses",new JSONArray().put(course)));
                old.updateTask(new JSONObject().put("id",Core.hash("old-task")).put("platform","chaoxing").put("sourceId","901").put("courseKey",course.getString("key")).put("courseTitle","样例课程").put("title","样例作业").put("kind","homework").put("phase","submit").put("status","pending").put("due",1900000000000L).put("version",2).put("localDone",false));
            }
            try(Store upgraded=new Store(context)){
                JSONObject snapshot=upgraded.snapshot(false);
                if(snapshot.getJSONArray("courses").length()!=1)throw new AssertionError("Upgraded phone lost its course list");
                if(!snapshot.getJSONArray("connections").getJSONObject(1).optBoolean("connected"))throw new AssertionError("Upgraded phone lost its platform binding");
                if(snapshot.getJSONArray("tasks").length()!=1)throw new AssertionError("Upgraded phone lost its homework");
                if(args.length>0)Files.write(Paths.get(args[0]),snapshot.toString().getBytes("UTF-8"));
                System.out.println("Phone upgrade snapshot preserves course, binding and homework");
            }
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(directory)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);}}
    }
}
