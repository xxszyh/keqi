package com.keqi.reminder;

import android.app.*;
import android.os.Bundle;
import android.net.Uri;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Official login browser. There is deliberately no JavascriptInterface on this WebView. */
public final class LoginActivity extends Activity {
    private WebView web;private String platform;private boolean sourceView;
    private TextView pageInfo;
    private String mobileAgent;private boolean desktopLogin;
    private Runnable cancelCourseRead;
    private static final String DESKTOP_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    protected void onCreate(Bundle saved){
        super.onCreate(saved);platform=getIntent().getStringExtra("platform");if(!Arrays.asList("mooc","chaoxing").contains(platform)){finish();return;}
        String source=getIntent().getStringExtra("sourceUrl");boolean viewOnly=source!=null&&Http.trusted(source,platform);
        sourceView=viewOnly;
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(0xfff8f8fc);
        TextView hint=new TextView(this);hint.setText(viewOnly?"这是平台官方页面，返回课期后会更新提醒。":"在平台页面完成登录，再点完成连接。课程会自动读取，无需逐门打开。换账号请先断开连接。");hint.setPadding(20,16,20,8);hint.setTextSize(13);layout.addView(hint);
        Button button=new Button(this);button.setText(viewOnly?"返回课期":"完成连接");layout.addView(button);button.setOnClickListener(v->{if(viewOnly){try(Store store=new Store(this)){JSONObject c=store.meta("connection."+platform);try{c.put("lastAttempt",0);store.putMeta("connection."+platform,c);}catch(JSONException ignored){}}finish();}else complete(button);});
        web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        pageInfo=new TextView(this);pageInfo.setTextSize(11);pageInfo.setPadding(20,4,20,8);pageInfo.setText("正在打开平台页面…");layout.addView(pageInfo);
        LinearLayout actions=new LinearLayout(this);Button reload=new Button(this);reload.setText(sourceView?"刷新页面":"重开登录页");actions.addView(reload,new LinearLayout.LayoutParams(0,-2,1));reload.setOnClickListener(v->{if(sourceView)web.reload();else openLogin();});Button home=new Button(this);home.setText(platform.equals("mooc")?"我的课程":"学习通首页");actions.addView(home,new LinearLayout.LayoutParams(0,-2,1));home.setOnClickListener(v->web.loadUrl(platform.equals("mooc")?"https://www.icourse163.org/home.htm#/home/course":"https://i.chaoxing.com/"));
        if(platform.equals("chaoxing")&&!sourceView){home.setText("重置登录");home.setOnClickListener(v->{web.stopLoading();home.setEnabled(false);button.setEnabled(false);pageInfo.setText("正在清理学习通登录状态…");worker.execute(()->{try{Sessions.clear(getApplicationContext(),"chaoxing");try(Store store=new Store(getApplicationContext())){JSONObject config=store.meta("connection.chaoxing");store.connectionError("chaoxing",config.optString("account"),"needs_login","登录状态已重置，请重新连接");}runOnUiThread(()->{if(isFinishing()||isDestroyed())return;home.setEnabled(true);button.setEnabled(true);openLogin();Toast.makeText(this,"学习通登录状态已重置，请重新登录",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;home.setEnabled(true);button.setEnabled(true);pageInfo.setText("重置登录未完成，请重开页面后重试");});}});});}
        if(platform.equals("chaoxing")&&!sourceView){Button mode=new Button(this);mode.setText("电脑登录");actions.addView(mode,new LinearLayout.LayoutParams(0,-2,1));mode.setOnClickListener(v->{desktopLogin=!desktopLogin;mode.setText(desktopLogin?"手机登录":"电脑登录");openLogin();});}layout.addView(actions);
        web.getSettings().setUseWideViewPort(true);web.getSettings().setLoadWithOverviewMode(true);web.getSettings().setSupportZoom(true);web.getSettings().setBuiltInZoomControls(true);web.getSettings().setDisplayZoomControls(false);web.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        mobileAgent=web.getSettings().getUserAgentString().replace("; wv","").replace("Version/4.0 ","");
        web.getSettings().setUserAgentString(platform.equals("mooc")?DESKTOP_AGENT:mobileAgent);
        if(sourceView&&platform.equals("chaoxing")){try(Store store=new Store(this)){String savedAgent=store.meta("connection.chaoxing").optString("browserAgent");if(!savedAgent.isEmpty())web.getSettings().setUserAgentString(savedAgent);}}
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient(){
            public boolean onJsAlert(WebView v,String url,String message,JsResult result){new AlertDialog.Builder(LoginActivity.this).setMessage(message).setPositiveButton("确定",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;}
            public boolean onJsConfirm(WebView v,String url,String message,JsResult result){new AlertDialog.Builder(LoginActivity.this).setMessage(message).setPositiveButton("确定",(d,w)->result.confirm()).setNegativeButton("取消",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
            public boolean onJsPrompt(WebView v,String url,String message,String value,JsPromptResult result){EditText input=new EditText(LoginActivity.this);input.setText(value);new AlertDialog.Builder(LoginActivity.this).setMessage(message).setView(input).setPositiveButton("确定",(d,w)->result.confirm(input.getText().toString())).setNegativeButton("取消",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
            public boolean onConsoleMessage(ConsoleMessage m){if(m.messageLevel()==ConsoleMessage.MessageLevel.ERROR){String type=m.message().contains("ReferenceError")?"ReferenceError":m.message().contains("TypeError")?"TypeError":"脚本或资源错误";pageInfo.setText("登录页出现 "+type+"（第 "+m.lineNumber()+" 行），可刷新后重试");}return true;}
        });
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return navigate(v,r.getUrl().toString(),r.isForMainFrame());}
            public boolean shouldOverrideUrlLoading(WebView v,String url){return navigate(v,url,true);}
            private boolean navigate(WebView v,String address,boolean mainFrame){String target=LoginNavigation.browserTarget(address,platform,mainFrame);if(target==null){Uri uri=Uri.parse(address);String destination=uri.getScheme()==null?"未知类型":uri.getScheme()+":"+(uri.getHost()==null?"":"//"+uri.getHost());pageInfo.setText("未打开跳转："+destination+"。可点重开登录页或切换登录方式");return true;}if(!target.equals(address)){v.loadUrl(target);return true;}return false;}
            public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){Uri uri=Uri.parse(url);pageInfo.setText("平台页面："+uri.getHost());}
            public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){if(r.isForMainFrame())pageInfo.setText("页面加载失败（"+error.getErrorCode()+"），请检查网络或刷新");}
            public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame())pageInfo.setText("平台返回 HTTP "+response.getStatusCode()+"，请稍后重试");}
            public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();Toast.makeText(LoginActivity.this,"平台证书验证失败，请检查网络",Toast.LENGTH_LONG).show();}
        });layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));layout.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});setContentView(layout);if(viewOnly)web.loadUrl(source);else openLogin();
    }
    private void openLogin(){web.stopLoading();web.clearHistory();web.getSettings().setUserAgentString(platform.equals("mooc")||desktopLogin?DESKTOP_AGENT:mobileAgent);pageInfo.setText("正在打开官方登录页…");web.loadUrl(LoginNavigation.loginStart(platform,desktopLogin));}
    private void complete(Button button){
        if(platform.equals("chaoxing")){
            button.setEnabled(false);button.setText("正在验证并读取课程…");pageInfo.setText("正在用当前登录会话重新读取课程首页…");
            cancelCourseRead=ChaoxingBrowser.read(web,(session,error)->{cancelCourseRead=null;if(isFinishing()||isDestroyed())return;
                if(error!=null){connectionFailure(button,error);return;}
                saveConnection(button,session,session.courses,web.getUrl(),web.getSettings().getUserAgentString());
            });return;
        }
        button.setEnabled(false);button.setText("正在验证登录…");String script=platform.equals("mooc")?BrowserCourses.SCRIPT:"JSON.stringify([])";String current=web.getUrl();
        web.evaluateJavascript(script,result->{
            if(isFinishing()||isDestroyed()||worker.isShutdown())return;
            worker.execute(()->{try{
                Authentication.Session session=Authentication.verify(platform);JSONArray observed=new JSONArray();
                try{observed=BrowserCourses.canonical(BrowserCourses.parse(result));}catch(Exception ignored){}
                saveConnection(button,session,observed,current,"");
            }catch(Exception e){runOnUiThread(()->connectionFailure(button,e));}});
        });
    }
    private void saveConnection(Button button,Authentication.Session session,JSONArray observed,String current,String agent){
        if(worker.isShutdown())return;
        worker.execute(()->{try(Store store=new Store(this)){
                String account=session.account;
                String cookies=session.cookies;
                ConnectionPolicy.validate(platform,cookies,current,observed.length(),account);
                JSONObject config=store.meta("connection."+platform);
                if(config.has("sourceAccount")&&!account.equals(config.optString("sourceAccount"))){store.disconnect(platform);config=new JSONObject();}
                if(observed.length()>0)config.put("courses",CourseSelection.merge(config.optJSONArray("courses"),observed));
                if(platform.equals("mooc")){
                    config.put("discoveryUrl","https://www.icourse163.org/home.htm?userId="+account+"#/home/course");
                }
                if(platform.equals("chaoxing"))config.put("browserAgent",agent);
                if(!config.has("account"))config.put("account",UUID.randomUUID().toString());
                Sessions.save(this,platform);
                String message=platform.equals("chaoxing")&&observed.length()>0?"已连接并读取 "+observed.length()+" 门课程，可在“选择课程”中调整；作业正在同步":"登录已验证，等待读取课程";
                config.put("sourceAccount",account).put("platform",platform).put("connected",true).put("status","partial").put("lastAttempt",0).put("message",message);store.putMeta("connection."+platform,config);
                SyncJob.schedule(this);runOnUiThread(()->{if(!isFinishing()){Toast.makeText(this,"登录已验证，返回后会开始同步",Toast.LENGTH_LONG).show();finish();}});
            }catch(Exception e){runOnUiThread(()->connectionFailure(button,e));}});
    }
    private void connectionFailure(Button button,Exception e){if(isFinishing()||isDestroyed())return;String message=e instanceof Http.LoginRequired?"平台会话已失效，请重新登录后连接":e instanceof IllegalArgumentException?e.getMessage():e instanceof java.io.IOException?SyncEngine.safeError(e):"课程连接未完成，请重开课程首页后重试";pageInfo.setText(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();button.setText("完成连接");button.setEnabled(true);}
    public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    protected void onPause(){if(sourceView){try(Store store=new Store(this)){JSONObject c=store.meta("connection."+platform);try{c.put("lastAttempt",0);store.putMeta("connection."+platform,c);}catch(JSONException ignored){}}}super.onPause();}
    protected void onDestroy(){if(cancelCourseRead!=null)cancelCourseRead.run();worker.shutdownNow();if(web!=null)web.destroy();super.onDestroy();}
}
