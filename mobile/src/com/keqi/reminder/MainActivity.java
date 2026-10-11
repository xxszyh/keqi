package com.keqi.reminder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.*;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.FrameLayout;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService collector=Executors.newSingleThreadExecutor();
    private final ExecutorService lanWorker=Executors.newSingleThreadExecutor();
    private final Handler lanTimer=new Handler(Looper.getMainLooper());
    private final Runnable pollLan=new Runnable(){public void run(){startLan();lanTimer.postDelayed(this,30000);}};
    private final java.util.concurrent.atomic.AtomicBoolean lanRequested=new java.util.concurrent.atomic.AtomicBoolean();
    private void startLan(){if(!lanWorker.isShutdown()&&lanRequested.compareAndSet(false,true))lanWorker.execute(()->{try{LanClient.sync(getApplicationContext());}finally{lanRequested.set(false);refresh();}});}
    private void pairLan(String text){message("正在申请配对，请在电脑上确认手机连接");lanWorker.execute(()->{try{LanClient.pair(getApplicationContext(),text);message("电脑配对成功");}catch(Exception e){message(e instanceof IllegalArgumentException?e.getMessage():"配对失败，请核对网络和电脑二维码");}refresh();});}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==7001&&result==RESULT_OK&&data!=null)pairLan(data.getStringExtra("pairing"));}
    private final java.util.concurrent.atomic.AtomicBoolean syncRequested=new java.util.concurrent.atomic.AtomicBoolean(false);
    protected void onCreate(Bundle saved){
        super.onCreate(saved);
        Notifications.create(this);web=new WebView(this);try(Store store=new Store(this)){applyAppearance(store.settings().optBoolean("darkMode",false));}web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(false);web.getSettings().setAllowFileAccess(true);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebChromeClient(new WebChromeClient(){
            public boolean onJsConfirm(WebView v,String url,String text,JsResult result){
                new AlertDialog.Builder(MainActivity.this).setMessage(text).setPositiveButton("确定",(d,w)->result.confirm()).setNegativeButton("取消",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;
            }
            public boolean onJsPrompt(WebView v,String url,String text,String value,JsPromptResult result){
                EditText input=new EditText(MainActivity.this);input.setText(value);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                new AlertDialog.Builder(MainActivity.this).setMessage(text).setView(input).setPositiveButton("保存",(d,w)->result.confirm(input.getText().toString())).setNegativeButton("取消",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !r.getUrl().toString().startsWith("file:///android_asset/");}
            public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){String url=r.getUrl().toString();return url.startsWith("file:///android_asset/")?null:new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}
            public void onPageFinished(WebView v,String url){refresh();}
        });FrameLayout host=new FrameLayout(this);host.addView(web,new FrameLayout.LayoutParams(-1,-1));
        host.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(host);web.loadUrl("file:///android_asset/index.html");SyncJob.schedule(this);worker.execute(()->{Notifications.rebuild(getApplicationContext());});
    }
    protected void onPause(){lanTimer.removeCallbacks(pollLan);super.onPause();}
    protected void onResume(){super.onResume();lanTimer.removeCallbacks(pollLan);lanTimer.post(pollLan);refresh();worker.execute(()->{Notifications.rebuild(getApplicationContext());try(Store store=new Store(this)){boolean needed=false;for(String p:new String[]{"mooc","chaoxing"}){JSONObject c=store.meta("connection."+p);if(c.optBoolean("connected")&&System.currentTimeMillis()-c.optLong("lastAttempt",0)>store.settings().optInt("syncMinutes",30)*60000L)needed=true;}if(needed)startSync();}});}
    protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("Native");web.destroy();web=null;}lanTimer.removeCallbacks(pollLan);lanWorker.shutdownNow();worker.shutdown();collector.shutdown();super.onDestroy();}
    private void startSync(){
        if(collector.isShutdown()||!syncRequested.compareAndSet(false,true))return;
        collector.execute(()->{try{SyncEngine.sync(getApplicationContext(),this::refresh);message("本轮检测已完成，请查看平台同步状态");}finally{syncRequested.set(false);refresh();}});
    }
    public void onBackPressed(){if(web==null){super.onBackPressed();return;}web.evaluateJavascript("window.handleBack?window.handleBack():false",handled->{if(!"true".equals(handled)){if(web!=null&&web.canGoBack())web.goBack();else MainActivity.super.onBackPressed();}});}
    private void message(String text){runOnUiThread(()->{if(web!=null)web.evaluateJavascript("if(window.onNativeMessage)window.onNativeMessage("+JSONObject.quote(text)+")",null);});}
    private void applyAppearance(boolean dark){
        int color=dark?0xff1e2420:0xffffffff;getWindow().setStatusBarColor(color);getWindow().setNavigationBarColor(color);if(web!=null)web.setBackgroundColor(color);
        android.view.View decor=getWindow().getDecorView();int flags=decor.getSystemUiVisibility();int light=android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(dark?flags&~light:flags|light);
    }
    private void refresh(){if(worker.isShutdown())return;worker.execute(()->{try(Store store=new Store(this)){String data=store.snapshot(SyncEngine.BUSY.get()).toString();boolean dark=store.settings().optBoolean("darkMode",false);runOnUiThread(()->{if(web!=null){applyAppearance(dark);web.evaluateJavascript("if(window.onNativeState)window.onNativeState("+data+")",null);}});}catch(Exception e){message("读取本机数据失败");}});}
    private final class Bridge {
        @JavascriptInterface public void action(String action,String payload){
            if(action.equals("scanPairing")){runOnUiThread(()->startActivityForResult(new Intent(MainActivity.this,ScanActivity.class),7001));return;}
            if(action.equals("pairComputer")){try{pairLan(new JSONObject(payload).getString("text"));}catch(Exception e){message("配对文字格式不正确");}return;}
            if(action.equals("lanSync")){try(Store store=new Store(MainActivity.this)){store.putMeta("lan.retry",new JSONObject());}startLan();return;}
            if(action.equals("login")){try{String p=new JSONObject(payload).getString("platform");if(!Arrays.asList("mooc","chaoxing").contains(p))return;runOnUiThread(()->startActivity(new Intent(MainActivity.this,LoginActivity.class).putExtra("platform",p)));}catch(Exception ignored){}return;}
            if(action.equals("notificationPermission")){runOnUiThread(()->{
                boolean missing=Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED;
                boolean asked=getPreferences(0).getBoolean("notificationAsked",false);
                if(missing&&(!asked||shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))){getPreferences(0).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);}
                else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
            });return;}
            if(action.equals("batterySettings")){runOnUiThread(()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));return;}
            worker.execute(()->{
                try(Store store=new Store(MainActivity.this)){
                    JSONObject data=new JSONObject(payload);
                    switch(action){
                        case "getState":break;
                        case "createTask":case "deleteTask":case "restoreTask":case "addSubtask":case "updateSubtask":case "reorderSubtasks":case "materializeRange":case "changeSeries":case "batchTasks":case "resolveConflict":
                            Collaboration.command(store,action,data);Notifications.rebuild(MainActivity.this);message("任务已保存");break;
                        case "unlinkComputer":LanClient.unlink(getApplicationContext());message("已解除电脑关联，本机任务仍保留");break;
                        case "updateLanAddress":
                            JSONObject lan=store.meta("lan.connection");if(!lan.has("fingerprint"))throw new IllegalArgumentException("请先配对电脑");lan.put("address",data.getString("address")).put("port",data.getInt("port"));store.putMeta("lan.connection",lan);break;
                        case "sync":
                            startSync();break;
                        case "testNotification":message(Notifications.post(MainActivity.this,"test","课期通知测试","你可以在这里收到作业和考试截止提醒。")?"测试通知已提交给手机系统":"请先开启 App 通知权限");break;
                        case "saveSettings":
                            JSONObject settings=store.settings();Iterator<String> keys=data.keys();
                            while(keys.hasNext()){String k=keys.next();if(!Arrays.asList("offsets","syncMinutes","quietStart","quietEnd","urgentOverride","chaoxingYear","darkMode").contains(k))continue;
                                if(k.equals("darkMode"))data.getBoolean(k);
                                if(k.equals("offsets")){JSONArray offsets=data.getJSONArray(k);if(offsets.length()<1||offsets.length()>12)throw new IllegalArgumentException("提醒档数应为 1～12");for(int i=0;i<offsets.length();i++)if(!Double.isFinite(offsets.getDouble(i))||offsets.getDouble(i)<=0||offsets.getDouble(i)>720)throw new IllegalArgumentException("提醒小时范围为 0～720");}
                                if(k.equals("syncMinutes")&&!Arrays.asList(30,60,180).contains(data.getInt(k)))throw new IllegalArgumentException("不支持的检测间隔");
                                if((k.equals("quietStart")||k.equals("quietEnd"))&&!data.getString(k).matches("(?:[01]\\d|2[0-3]):[0-5]\\d|"))throw new IllegalArgumentException("免打扰时间格式不正确");
                                if(k.equals("chaoxingYear")&&!data.isNull(k)&&(data.getInt(k)<2000||data.getInt(k)>2100))throw new IllegalArgumentException("年份不正确");
                                settings.put(k,data.get(k));
                            }store.putMeta("settings",settings);if(data.length()!=1||!data.has("darkMode")){SyncJob.schedule(MainActivity.this);Notifications.rebuild(MainActivity.this);}message("设置已保存" );
                            if(data.has("chaoxingYear")&&store.meta("connection.chaoxing").optBoolean("connected")){message("年份已保存，正在重新读取截止时间");startSync();}break;
                        case "updateTask":
                            Collaboration.command(store,"updateTask",data);Notifications.rebuild(MainActivity.this);message("已更新本机记录");break;
                        case "toggleCourse":
                            store.enableCourse(data.getString("key"),data.getBoolean("enabled"));Notifications.rebuild(MainActivity.this);break;
                        case "toggleCourses":
                            JSONArray courseKeys=data.getJSONArray("keys");Set<String> selected=new HashSet<>();for(int i=0;i<courseKeys.length();i++)selected.add(courseKeys.getString(i));
                            store.enableCourses(selected,data.getBoolean("enabled"));Notifications.rebuild(MainActivity.this);break;
                        case "applyCourseChoices":{
                            Set<String> remove=new HashSet<>(),restore=new HashSet<>();JSONArray removing=data.optJSONArray("remove"),restoring=data.optJSONArray("restore");
                            if(removing!=null)for(int i=0;i<removing.length();i++)remove.add(removing.getString(i));if(restoring!=null)for(int i=0;i<restoring.length();i++)restore.add(restoring.getString(i));
                            Collaboration.command(store,"applyCourseChoices",data);Notifications.rebuild(MainActivity.this);message("课程设置已保存");break;
                        }
                        case "disconnect":
                            String platform=data.getString("platform");if(!Arrays.asList("mooc","chaoxing").contains(platform))throw new IllegalArgumentException("不支持的平台");store.disconnect(platform);Sessions.clear(MainActivity.this,platform);Notifications.rebuild(MainActivity.this);message("此设备已断开平台登录，任务仍保留");break;
                        case "openSource":
                            JSONObject source=store.task(data.getString("id"));if(source==null)return;String url=source.getString("sourceUrl");if(!Http.trusted(url,source.getString("platform")))throw new IllegalArgumentException("来源链接无效");runOnUiThread(()->startActivity(new Intent(MainActivity.this,LoginActivity.class).putExtra("platform",source.optString("platform")).putExtra("sourceUrl",url)));break;
                        case "verifyPlatform":
                            String verifyPlatform=data.getString("platform");if(!verifyPlatform.equals("chaoxing"))return;
                            String verifyUrl=store.meta("connection.chaoxing").optString("verificationUrl");if(!ChaoxingReadPolicy.allowed(verifyUrl))throw new IllegalArgumentException("验证页面尚未准备好，请先刷新");
                            runOnUiThread(()->startActivity(new Intent(MainActivity.this,LoginActivity.class).putExtra("platform","chaoxing").putExtra("sourceUrl",verifyUrl)));break;
                        default:throw new IllegalArgumentException("不支持的操作");
                    }
                }catch(Exception e){message(e instanceof IllegalArgumentException?e.getMessage():"操作失败，请重试");}
                if(!action.equals("getState"))startLan();
                refresh();
            });
        }
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);refresh();worker.execute(()->Notifications.rebuild(MainActivity.this));}
}
