package com.keqi.reminder;

import android.content.Context;
import android.os.*;
import android.webkit.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Fresh protected-page verification using the same browser session that performed login. */
public final class ChaoxingBrowser {
    public static final String SNAPSHOT="(function(){const root=document.documentElement.cloneNode(true);root.querySelectorAll('input[type=password]').forEach(e=>e.removeAttribute('value'));return JSON.stringify({url:location.href,html:root.outerHTML});})()";
    public interface Callback {void done(Authentication.Session session,Exception error);}
    public static Runnable read(WebView web,Callback callback){
        Handler handler=new Handler(Looper.getMainLooper());AtomicBoolean ended=new AtomicBoolean();
        Runnable[] poll={null};Callback finish=(session,error)->{if(ended.compareAndSet(false,true)){handler.removeCallbacks(poll[0]);callback.done(session,error);}};
        poll[0]=new Runnable(){int ticks=0,stable=0;String previous="";
            public void run(){
                if(ended.get())return;if(++ticks>28){finish.done(null,new java.io.IOException("课程页已打开，但未读取到可验证的课程信息，请重开课程首页后重试"));return;}
                try{
                    if(web.getProgress()<100){handler.postDelayed(this,1000);return;}
                    String current=web.getUrl();if(current==null||current.equals("about:blank")){handler.postDelayed(this,1000);return;}
                    if(current.startsWith("https://passport2.chaoxing.com/")){finish.done(null,new Http.LoginRequired());return;}
                    String path=java.net.URI.create(current).getPath();
                    if(!Http.trusted(current,"chaoxing")||!(path.endsWith("/visit/interaction")||path.endsWith("/courselistdata"))){handler.postDelayed(this,1000);return;}
                    web.evaluateJavascript(SNAPSHOT,result->{if(ended.get())return;
                        try{
                            Object raw=new JSONTokener(result).nextValue();if(!(raw instanceof String))throw new JSONException("页面尚未就绪");
                            JSONObject snapshot=new JSONObject((String)raw);if(snapshot.getString("html").length()>4000000)throw new java.io.IOException("课程页面内容过大");
                            String cookies=CookieManager.getInstance().getCookie(snapshot.getString("url"));
                            Authentication.Session session=Authentication.fromBrowser(result,cookies);
                            String signature=session.courses.toString();stable=signature.equals(previous)?stable+1:0;previous=signature;
                            if(stable>=2&&(session.courses.length()>0||session.empty||ticks>=8)){CookieManager.getInstance().flush();finish.done(session,null);return;}
                            web.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight)",null);handler.postDelayed(this,1000);
                        }catch(Http.LoginRequired e){finish.done(null,e);}
                        catch(IllegalArgumentException|JSONException e){handler.postDelayed(this,1000);}
                        catch(Exception e){finish.done(null,e);}
                    });
                }catch(Exception e){finish.done(null,e);}
            }
        };
        // Refresh the protected resource: cached DOM plus a stale uid cookie is not proof.
        web.loadUrl(LoginNavigation.CHAOXING_HOME,Collections.singletonMap("Cache-Control","no-cache"));handler.postDelayed(poll[0],1500);
        return ()->{ended.set(true);handler.removeCallbacks(poll[0]);};
    }
    public static Authentication.Session discover(Context context,String agent) throws Exception {
        Handler handler=new Handler(Looper.getMainLooper());CountDownLatch latch=new CountDownLatch(1);
        Authentication.Session[] result={null};Exception[] error={null};WebView[] view={null};Runnable[] cancel={null};AtomicBoolean closed=new AtomicBoolean();
        Runnable cleanup=()->{if(closed.compareAndSet(false,true)){if(cancel[0]!=null)cancel[0].run();if(view[0]!=null){view[0].stopLoading();view[0].destroy();view[0]=null;}latch.countDown();}};
        handler.post(()->{if(closed.get())return;try{
            WebView web=new WebView(context);view[0]=web;web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
            web.getSettings().setUseWideViewPort(true);web.getSettings().setLoadWithOverviewMode(true);
            String defaultAgent=web.getSettings().getUserAgentString().replace("; wv","").replace("Version/4.0 ","");web.getSettings().setUserAgentString(agent.isEmpty()?defaultAgent:agent);
            web.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1600,android.view.View.MeasureSpec.EXACTLY));web.layout(0,0,1080,1600);
            CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
            web.setWebViewClient(new WebViewClient(){
                public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){String target=LoginNavigation.browserTarget(r.getUrl().toString(),"chaoxing",r.isForMainFrame());if(target==null)return true;if(!target.equals(r.getUrl().toString())){v.loadUrl(target);return true;}return false;}
                public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();error[0]=new java.io.IOException("课程页面证书验证失败");cleanup.run();}
            });
            cancel[0]=read(web,(session,e)->{result[0]=session;error[0]=e;cleanup.run();});
        }catch(Exception e){error[0]=e;cleanup.run();}});
        try{if(!latch.await(35,TimeUnit.SECONDS))throw new java.io.IOException("课程页面验证超时，请稍后重试");}finally{handler.post(cleanup);}
        if(error[0]!=null)throw error[0];if(result[0]==null)throw new java.io.IOException("课程页面未读取，请重新同步");return result[0];
    }
}
