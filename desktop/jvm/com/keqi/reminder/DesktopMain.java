package com.keqi.reminder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Versioned, bounded JSON over pipes. No shell, credentials or raw database access in the UI. */
public final class DesktopMain {
    private final PrintWriter output=new PrintWriter(new OutputStreamWriter(System.out,StandardCharsets.UTF_8),true);
    private final Map<String,CompletableFuture<JSONObject>> pages=new ConcurrentHashMap<>();
    private final ExecutorService commands=Executors.newSingleThreadExecutor(),collector=Executors.newSingleThreadExecutor();
    private final DesktopStore store;
    private final LanHost lan;
    private final Path directory;
    private volatile boolean reading=false;
    private String password="";
    private DesktopMain(Path directory) throws Exception {this.directory=directory;store=new DesktopStore(directory);lan=new LanHost(store);store.transact(w->null);Http.transport=this::readPage;}
    private synchronized void send(JSONObject response){output.println(response.toString());}
    private JSONObject readPage(JSONObject request) throws Exception {String id=UUID.randomUUID().toString();CompletableFuture<JSONObject> future=new CompletableFuture<>();pages.put(id,future);try{send(new JSONObject().put("type","readPage").put("id",id).put("data",request));return future.get(75,TimeUnit.SECONDS);}finally{pages.remove(id);}}
    private JSONObject settings() throws Exception {JSONObject s=store.meta("settings");if(!s.has("offsets"))s.put("offsets",new JSONArray(new int[]{72,24,3}));if(!s.has("syncMinutes"))s.put("syncMinutes",30);if(!s.has("quietStart"))s.put("quietStart","23:00");if(!s.has("quietEnd"))s.put("quietEnd","07:00");return s;}
    private static void validateSettings(JSONObject s) throws JSONException {
        JSONArray offsets=s.getJSONArray("offsets");if(offsets.length()<1||offsets.length()>12)throw new IllegalArgumentException("提醒档数应为 1～12");for(int i=0;i<offsets.length();i++)if(!Double.isFinite(offsets.getDouble(i))||offsets.getDouble(i)<=0||offsets.getDouble(i)>720)throw new IllegalArgumentException("提醒小时范围为 0～720");
        if(!Arrays.asList(30,60,180).contains(s.getInt("syncMinutes")))throw new IllegalArgumentException("不支持的检测间隔");for(String k:new String[]{"quietStart","quietEnd"})if(!s.getString(k).matches("(?:[01]\\d|2[0-3]):[0-5]\\d|"))throw new IllegalArgumentException("免打扰时间格式不正确");if(s.has("chaoxingYear")&&!s.isNull("chaoxingYear")&&(s.getInt("chaoxingYear")<2000||s.getInt("chaoxingYear")>2100))throw new IllegalArgumentException("年份不正确");
    }
    private JSONObject state() throws Exception {
        JSONObject snapshot=store.transact(w->{Repeats.materialize(w,System.currentTimeMillis()-86400000L,System.currentTimeMillis()+30*86400000L,true);TaskService service=new TaskService(w,true);JSONArray courses=service.courses(),visible=new JSONArray(),removed=new JSONArray();for(int i=0;i<courses.length();i++){JSONObject c=courses.getJSONObject(i);(c.optBoolean("removed")?removed:visible).put(c);}return new JSONObject().put("tasks",service.tasks(false)).put("trash",service.tasks(true)).put("courses",visible).put("removedCourses",removed).put("series",w.list("series")).put("workspace",w.id());});
        JSONArray connections=new JSONArray();for(String p:new String[]{"mooc","chaoxing"}){JSONObject c=store.meta("connection."+p);if(!c.has("platform"))c.put("platform",p).put("status","unconfigured");c.remove("sourceAccount");connections.put(c);}return snapshot.put("connections",connections).put("settings",settings()).put("syncing",reading).put("notificationEnabled",true).put("lan",lan.status());
    }
    private JSONObject command(String action,JSONObject input) throws Exception {
        switch(action){
            case "initialize":password=input.getString("password");JSONObject saved=store.meta("lan.settings");JSONArray interfaces=LanHost.interfaces();if(interfaces.length()>0){String address=saved.optString("address",interfaces.getJSONObject(0).getString("address"));try{lan.start(address,directory,password);}catch(IllegalArgumentException e){lan.start(interfaces.getJSONObject(0).getString("address"),directory,password);}}return state();
            case "getState":return state();
            case "startHost":lan.start(input.getString("address"),directory,password);return state();
            case "pairingCode":return lan.pairing();
            case "approvePairing":lan.approve(input.getString("request"),input.getBoolean("accepted"));return state();
            case "revokeDevice":lan.revoke(input.getString("id"));return state();
            case "connectPlatform":new Platforms(store).connect(input.getString("platform"));startRead();return state();
            case "sync":startRead();return state();
            case "disconnect":{String p=input.getString("platform");if(!Arrays.asList("mooc","chaoxing").contains(p))throw new IllegalArgumentException("平台无效");store.meta("connection."+p,new JSONObject().put("platform",p).put("status","unconfigured"));return state();}
            case "saveSettings":JSONObject s=settings();for(String k:new String[]{"offsets","syncMinutes","quietStart","quietEnd","urgentOverride","chaoxingYear","darkMode"})if(input.has(k))s.put(k,input.get(k));validateSettings(s);store.meta("settings",s);return state();
            case "backup":return store.transact(w->new JSONObject().put("format","keqi-data-v1").put("snapshot",w.snapshot(-1)));
            case "restoreBackup":store.transact(w->{w.importArchive(input,true);return null;});return state();
            default:store.transact(w->new TaskService(w,true).command(action,input));return state();
        }
    }
    private synchronized void startRead(){if(reading)return;reading=true;collector.submit(()->{try{for(String p:new String[]{"mooc","chaoxing"})try{new Platforms(store).read(p);}catch(Exception ignored){}}finally{reading=false;try{send(new JSONObject().put("type","state").put("data",state()));}catch(Exception ignored){}}});}
    private void accept(JSONObject request) throws Exception {
        String id=request.getString("id");if(request.optString("type").equals("pageResult")){CompletableFuture<JSONObject> f=pages.get(id);if(f!=null){if(request.has("error"))f.completeExceptionally(new IOException("平台浏览器读取未完成"));else f.complete(request.getJSONObject("data"));}return;}
        commands.submit(()->{try{JSONObject result=command(request.getString("action"),request.optJSONObject("data")==null?new JSONObject():request.getJSONObject("data"));send(new JSONObject().put("type","result").put("id",id).put("data",result));}catch(Exception e){try{send(new JSONObject().put("type","result").put("id",id).put("error",e instanceof IllegalArgumentException||e instanceof Http.LoginRequired?e.getMessage():"操作未完成，请重试"));}catch(JSONException ignored){}}});
    }
    public static void main(String[] args) throws Exception {
        DesktopMain host=new DesktopMain(Paths.get(args[0]));try(BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){String line;while((line=input.readLine())!=null){if(line.length()>2000000)throw new IOException("IPC message too large");host.accept(new JSONObject(line));}}finally{host.collector.shutdownNow();host.commands.shutdownNow();host.lan.close();host.store.close();}
    }
}
