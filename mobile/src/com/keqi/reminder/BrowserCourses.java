package com.keqi.reminder;

import android.content.Context;
import android.os.*;
import android.webkit.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Reads the user's official course page DOM in a separate WebView, with no native bridge. */
public final class BrowserCourses {
    public static final String SCRIPT="JSON.stringify(Array.from(document.querySelectorAll('a[href]')).map(a=>({url:a.href,title:(a.getAttribute('title')||a.innerText||a.querySelector('img')?.alt||'').trim()})).filter(x=>/\\/(?:spoc\\/)?learn\\//.test(x.url)&&/[?&]tid=\\d+/.test(x.url)&&!/#.*(?:forum|detail)/i.test(x.url)))";
    private static final String SCAN="(function(){const items=JSON.parse("+SCRIPT+");const a=Array.from(document.querySelectorAll('a,button,[role=button],.ux-pager_btn')).filter(e=>e.getClientRects().length>0);const next=a.find(e=>/^(下一页|下页|Next|›|»|>)$/i.test(e.innerText.trim())||/pager.*next|pageNext|nextPage/.test(e.className));const disabled=next&&(next.matches(':disabled')||next.getAttribute('aria-disabled')==='true'||/disabled|z-dis/.test(next.className));return JSON.stringify({items:items,next:!!next&&!disabled,pagination:!!document.querySelector('.ux-pager,.pagination,.pager'),empty:/暂无课程|还没有.*课程|没有参加|暂无.*课程|未参加.*课程/.test(document.body.innerText),height:document.body.scrollHeight});})()";
    private static final String NEXT="(function(){const a=Array.from(document.querySelectorAll('a,button,[role=button],.ux-pager_btn'));const e=a.find(e=>e.getClientRects().length>0&&(/^(下一页|下页|Next|›|»|>)$/i.test(e.innerText.trim())||/pager.*next|pageNext|nextPage/.test(e.className)));if(e){e.click();return true;}return false;})()";
    public static JSONArray parse(String encoded) throws Exception {
        Object decoded=new JSONTokener(encoded).nextValue();if(!(decoded instanceof String))return new JSONArray();return new JSONArray((String)decoded);
    }
    public static final class Discovery {
        public final JSONArray courses;public final String warning;
        Discovery(JSONArray courses,String warning){this.courses=courses;this.warning=warning;}
    }
    public static Discovery discover(Context context,String url) throws Exception {
        if(!Http.trusted(url,"mooc"))throw new java.io.IOException("课程发现页面地址无效，请重新连接");
        Handler handler=new Handler(Looper.getMainLooper());CountDownLatch latch=new CountDownLatch(1);
        Map<String,JSONObject> records=new LinkedHashMap<>();Exception[] error={null};WebView[] view={null};String[] warning={""};
        Runnable cleanup=()->{if(view[0]!=null){WebView v=view[0];view[0]=null;v.stopLoading();v.destroy();}latch.countDown();};
        handler.post(()->{
            try{
                WebView web=new WebView(context);view[0]=web;web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
                web.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1600,android.view.View.MeasureSpec.EXACTLY));web.layout(0,0,1080,1600);
                web.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36");
                web.setWebViewClient(new WebViewClient(){
                    public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !Http.trusted(r.getUrl().toString(),"mooc");}
                    public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){h.cancel();error[0]=new java.io.IOException("平台页面 TLS 验证失败");cleanup.run();}
                });web.loadUrl(url);
                LinkedHashSet<String> scopes=new LinkedHashSet<>();scopes.add(url);
                // Both personal-course tabs are independent lists on MOOC.
                if(url.contains("/home.htm")){String base=url.split("#")[0];scopes.add(base+"#/home/course");scopes.add(base+"#/home/spocCourse");}
                Iterator<String> scope=scopes.iterator();scope.next();
                Runnable poll=new Runnable(){int ticks=0,stable=0,pages=0,scopeTicks=0;String last="",lastPage="";
                    private void nextScope(){
                        if(scope.hasNext()){stable=0;pages=0;scopeTicks=0;last="";lastPage="";web.loadUrl(scope.next());handler.postDelayed(this,2500);}else cleanup.run();
                    }
                    public void run(){
                        if(view[0]!=web)return;
                        web.evaluateJavascript(SCAN,result->{
                            if(view[0]!=web)return;
                            try{
                                Object decoded=new JSONTokener(result).nextValue();if(!(decoded instanceof String))throw new JSONException("页面未就绪");
                                JSONObject scan=new JSONObject((String)decoded);JSONArray items=scan.getJSONArray("items");
                                for(int i=0;i<items.length();i++){JSONObject c=items.getJSONObject(i);records.put(c.getString("url").split("#")[0],c);}
                                String signature=items.toString()+":"+scan.optInt("height");stable=signature.equals(last)?stable+1:0;last=signature;ticks++;scopeTicks++;
                                if(ticks>=70){warning[0]="课程发现达到时间上限，分页覆盖需核实";cleanup.run();return;}
                                if(scopeTicks>=18&&items.length()==0){warning[0]="部分课程标签未发现课程，请核对 MOOC 与 SPOC 数量";nextScope();return;}
                                if(stable>=3&&(items.length()>0||scan.optBoolean("empty"))){
                                    if(scan.optBoolean("next")){
                                        if(signature.equals(lastPage)||++pages>30){warning[0]="课程下一页未加载，保留已发现课程";nextScope();return;}
                                        lastPage=signature;stable=0;web.evaluateJavascript(NEXT,null);handler.postDelayed(this,2200);return;
                                    }
                                    if(scan.optBoolean("pagination"))warning[0]="已读取可识别分页，请首次连接时核对课程数量";
                                    nextScope();return;
                                }
                                web.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight)",null);handler.postDelayed(this,1000);
                            }catch(Exception e){if(++ticks>=15){error[0]=e;cleanup.run();}else handler.postDelayed(this,1000);}
                        });
                    }
                };handler.postDelayed(poll,2500);
            }catch(Exception e){error[0]=e;cleanup.run();}
        });
        try{if(!latch.await(100,TimeUnit.SECONDS))throw new java.io.IOException("课程页面加载超时");}finally{handler.post(cleanup);}
        if(error[0]!=null&&records.isEmpty())throw error[0];return new Discovery(new JSONArray(records.values()),warning[0]);
    }
    public static JSONArray canonical(JSONArray raw) throws Exception {
        JSONArray courses=new JSONArray();Set<String> seen=new HashSet<>();java.util.regex.Pattern p=java.util.regex.Pattern.compile("/(spoc/)?learn/([^/?#]+)");
        for(int i=0;i<raw.length();i++){JSONObject c=raw.getJSONObject(i);String url=c.optString("url").split("#")[0];if(!Http.trusted(url,"mooc"))continue;
            java.util.regex.Matcher m=p.matcher(url);String term=Collectors.query(url,"tid");if(!m.find()||!term.matches("\\d+"))continue;
            String key="mooc:"+m.group(2)+":"+term;if(!seen.add(key))continue;
            courses.put(new JSONObject().put("key",key).put("platform","mooc").put("title",c.optString("title").isEmpty()?m.group(2):c.getString("title")).put("termId",term).put("schoolCourseId",m.group(2)).put("url",url));
        }return courses;
    }
}
