package com.keqi.reminder;

import android.content.Context;
import android.os.*;
import android.webkit.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Ordinary WebView navigation, using the saved login session and no native bridge. */
public final class ChaoxingPageBrowser implements AutoCloseable {
    public static final String SNAPSHOT="(function(){const root=document.documentElement.cloneNode(true);const clean=e=>e.querySelectorAll('input[type=password]').forEach(x=>x.removeAttribute('value'));clean(root);let count=0;const append=(doc,depth)=>{if(depth>2)return;doc.querySelectorAll('iframe').forEach(frame=>{try{const child=frame.contentDocument;if(!child||!child.body||count>=8)return;const copy=child.body.cloneNode(true);clean(copy);root.querySelector('body').appendChild(copy);count++;append(child,depth+1);}catch(e){}});};append(document,1);return JSON.stringify({url:location.href,html:root.outerHTML});})()";
    private final Context context;private final String agent,account;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed=new AtomicBoolean();
    private WebView web;private Attempt active;private boolean interactionBlocked;
    private long spent;private String effectiveAgent,pendingTarget="";
    private static final class Attempt {
        final String url;final ChaoxingReadPolicy.Stage stage;final CountDownLatch latch=new CountDownLatch(1);
        volatile boolean done;boolean started;Http.Page result;Exception error;Runnable poll;
        final Set<String> followed=new HashSet<>();
        Attempt(String url,ChaoxingReadPolicy.Stage stage){this.url=url;this.stage=stage;}
    }
    public ChaoxingPageBrowser(Context context,String agent,String account){this.context=context.getApplicationContext();this.agent=agent;this.account=account;}
    private void finish(Attempt a,Http.Page page,Exception error){
        if(a.done)return;a.done=true;a.result=page;a.error=error;if(a.poll!=null)handler.removeCallbacks(a.poll);if(web!=null)web.stopLoading();a.latch.countDown();
    }
    private void initialize(){
        if(web!=null)return;web=new WebView(context);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setMediaPlaybackRequiresUserGesture(true);
        effectiveAgent=agent.isEmpty()?s.getUserAgentString().replace("; wv","").replace("Version/4.0 ",""):agent;s.setUserAgentString(effectiveAgent);
        web.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1600,android.view.View.MeasureSpec.EXACTLY));web.layout(0,0,1080,1600);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return navigate(r.getUrl().toString(),r.isForMainFrame());}
            public boolean shouldOverrideUrlLoading(WebView v,String url){return navigate(url,true);}
            private boolean navigate(String url,boolean main){
                if(!main)return !(url.startsWith("https://")||url.equals("about:blank"));
                try{String target=Http.redirectTarget(active.url,url,"chaoxing");if(!target.equals(url)){web.loadUrl(target);return true;}return false;}
                catch(Exception e){if(active!=null)finish(active,null,e);return true;}
            }
            public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){if(active!=null&&Http.trusted(url,"chaoxing"))active.started=true;}
            public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();if(active!=null)finish(active,null,new java.io.IOException("平台页面证书验证失败"));}
            public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()&&active!=null)finish(active,null,new java.io.IOException("平台页面加载失败，请检查网络后刷新"));}
            public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame()&&active!=null){java.io.IOException error=Http.responseError(response.getStatusCode(),"chaoxing");if(error!=null&&!(error instanceof Http.BrowserRequired))finish(active,null,error);}}
        });
    }
    public Http.Page get(String url,ChaoxingReadPolicy.Stage stage) throws Exception {
        return ChaoxingRequests.get(url,stage,(address,kind)->Http.getPage(address,"chaoxing",effectiveAgent==null?agent:effectiveAgent,LoginNavigation.CHAOXING_HOME),this::read);
    }
    public String verificationTarget(){return pendingTarget;}
    private Http.Page read(String url,ChaoxingReadPolicy.Stage stage) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("页面读取必须在后台执行");
        if(closed.get())throw new InterruptedException();if(interactionBlocked||spent>=90000){if(pendingTarget.isEmpty())pendingTarget=url;throw new ChaoxingReadPolicy.InteractionRequired();}
        Attempt a=new Attempt(url,stage);long started=SystemClock.elapsedRealtime();
        handler.post(()->{if(closed.get()||a.done)return;try{
            initialize();active=a;a.followed.add(url);
            a.poll=new Runnable(){int ticks=0,readyTicks=0;String previous="";
                public void run(){
                    if(closed.get()||a.done||active!=a)return;
                    if(++ticks>28){finish(a,null,new ChaoxingReadPolicy.InteractionRequired());return;}
                    if(!a.started||web.getProgress()<100){handler.postDelayed(this,1000);return;}
                    String address=web.getUrl();if(address==null||!ChaoxingReadPolicy.responseAllowed(address,stage)){handler.postDelayed(this,1000);return;}
                    web.evaluateJavascript(SNAPSHOT,encoded->{if(closed.get()||a.done||active!=a)return;try{
                        Object raw=new JSONTokener(encoded).nextValue();if(!(raw instanceof String)){handler.postDelayed(this,1000);return;}
                        JSONObject snapshot=new JSONObject((String)raw);String current=snapshot.getString("url");
                        Http.Page page=ChaoxingReadPolicy.fromBrowser(encoded,CookieManager.getInstance().getCookie(current),account,stage);
                        if(page!=null){readyTicks=current.equals(previous)?readyTicks+1:1;previous=current;if(readyTicks>=2){CookieManager.getInstance().flush();finish(a,page,null);return;}}
                        else{
                            readyTicks=0;previous="";String frame=ChaoxingReadPolicy.embeddedPage(current,snapshot.getString("html"),stage);
                            if(!frame.isEmpty()&&a.followed.size()<4&&a.followed.add(frame))web.loadUrl(frame,Collections.singletonMap("Referer",current));
                            else if(ticks==8||ticks==16)web.reload();
                        }
                        handler.postDelayed(this,1000);
                    }catch(JSONException e){handler.postDelayed(this,1000);}catch(Exception e){finish(a,null,e);}});
                }
            };
            Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-cache");headers.put("Referer",LoginNavigation.CHAOXING_HOME);web.loadUrl(url,headers);handler.postDelayed(a.poll,1000);
        }catch(Exception e){finish(a,null,e);}});
        try{if(!a.latch.await(35,TimeUnit.SECONDS)){interactionBlocked=true;if(pendingTarget.isEmpty())pendingTarget=url;throw new ChaoxingReadPolicy.InteractionRequired();}}
        finally{spent+=SystemClock.elapsedRealtime()-started;handler.post(()->{if(!a.done)finish(a,null,new InterruptedException());});}
        if(a.error!=null){if(a.error instanceof ChaoxingReadPolicy.InteractionRequired){interactionBlocked=true;if(pendingTarget.isEmpty())pendingTarget=url;}throw a.error;}
        if(a.result==null)throw new ChaoxingReadPolicy.InteractionRequired();return a.result;
    }
    public void close(){closed.set(true);handler.post(()->{if(active!=null&&!active.done)finish(active,null,new InterruptedException());if(web!=null){web.stopLoading();web.destroy();web=null;}});}
}
