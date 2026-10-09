package com.keqi.reminder;

import org.json.*;
import java.io.IOException;

/** Screenshot regression: production HTTP classification -> browser continuation -> data parser. */
public final class ChaoxingReadTests {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static String snapshot(String url,String html) throws Exception{return JSONObject.quote(new JSONObject().put("url",url).put("html",html).toString());}
    public static void main(String[] args) throws Exception {
        String courseUrl="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=1&clazzid=2&cpi=3";
        String listUrl="https://mooc1.chaoxing.com/mooc-ans/mooc2/work/list?courseId=1&classId=2&cpi=3";
        String detailUrl="https://mooc1.chaoxing.com/mooc-ans/work/doHomeWorkNew?workId=8";
        String course="<input id='courseId' value='1'><input id='cpi' value='3'><input id='workEnc' value='fixture-enc'>";
        String list="<li data='"+detailUrl+"'><p class='overHidden2'>第三章作业</p><p class='status'>未提交</p></li>";
        String detail="<h1>作业详情</h1>作答时间：2026-10-09 08:00 至 2026-10-15 18:00";
        String redirected="https://mooc1.chaoxing.com/mooc-ans/mycourse/studentcourse/?courseid=1&clazzid=2&cpi=3";
        Http.Page redirectedPage=ChaoxingRequests.get(courseUrl,ChaoxingReadPolicy.Stage.COURSE,(url,stage)->new Http.Page(redirected,course),(url,stage)->{throw new AssertionError("ready protected redirect must not need another browser request");});
        check(redirectedPage.html.contains("fixture-enc"),"a real course body after an official redirect must not report an unsupported read page");
        int[] nativeCalls={0},browserCalls={0};
        ChaoxingRequests.Reader native202=(url,stage)->{nativeCalls[0]++;throw Http.responseError(202,"chaoxing");};
        ChaoxingRequests.Reader browser=(url,stage)->{browserCalls[0]++;String html=stage==ChaoxingReadPolicy.Stage.COURSE?course:stage==ChaoxingReadPolicy.Stage.WORK_LIST?list:detail;return ChaoxingReadPolicy.fromBrowser(snapshot(url,html),"_uid=123; JSESSIONID=fixture","123",stage);};
        Http.Page middle=ChaoxingRequests.get(courseUrl,ChaoxingReadPolicy.Stage.COURSE,native202,browser);
        check(middle.html.contains("fixture-enc")&&nativeCalls[0]==1&&browserCalls[0]==1,"HTTP 202 resumes once in the login browser and yields the real course entry");
        JSONObject co=new JSONObject().put("key","chaoxing:1:2:3").put("title","金工实习");
        Http.Page works=ChaoxingRequests.get(listUrl,ChaoxingReadPolicy.Stage.WORK_LIST,native202,browser);
        JSONArray tasks=Collectors.chaoxingList(works.html,co,"fixture-account");
        check(tasks.length()==1&&tasks.getJSONObject(0).getString("title").equals("第三章作业"),"a 202 work-list continuation feeds the actual homework parser");
        JSONObject task=tasks.getJSONObject(0);Collectors.chaoxingDetail(task,ChaoxingRequests.get(detailUrl,ChaoxingReadPolicy.Stage.WORK_DETAIL,native202,browser).html,null);
        check(task.getLong("due")==Core.parseTime("2026-10-15 18:00",null).at&&task.getBoolean("deadlineObserved"),"a 202 detail continuation recovers the actual deadline instead of unknown metadata");
        check(nativeCalls[0]==3&&browserCalls[0]==3,"entry, list and detail use the same continuation seam");
        ChaoxingRequests.get(courseUrl,ChaoxingReadPolicy.Stage.COURSE,(url,stage)->new Http.Page(url,course),browser);
        check(browserCalls[0]==3,"an ordinary ready HTTP 200 page does not open an extra browser");
        ChaoxingRequests.get(listUrl,ChaoxingReadPolicy.Stage.WORK_LIST,(url,stage)->new Http.Page(url,"<p>正在加载</p>"),browser);
        check(browserCalls[0]==4,"an unfinished HTTP 200 page also continues instead of claiming zero homework");
        check(!ChaoxingReadPolicy.ready(listUrl,"<p>页面加载中</p>",ChaoxingReadPolicy.Stage.WORK_LIST),"a pending 202 document cannot prove an empty work list");
        check(!ChaoxingReadPolicy.ready(courseUrl,"<input id='workEnc' value=''>",ChaoxingReadPolicy.Stage.COURSE),"an unpopulated entry field remains pending");
        check(!ChaoxingReadPolicy.ready(detailUrl,"<p>请稍候</p>",ChaoxingReadPolicy.Stage.WORK_DETAIL),"a detail loading page cannot erase an old deadline");
        check(!ChaoxingReadPolicy.ready(detailUrl,"<title>作业详情</title><h1>作业详情</h1><p>详情加载中</p>",ChaoxingReadPolicy.Stage.WORK_DETAIL),"a static work-detail heading is not proof that deadline metadata finished loading");
        check(ChaoxingReadPolicy.ready(listUrl,"<p>暂无作业</p>",ChaoxingReadPolicy.Stage.WORK_LIST),"an explicit real empty list remains supported");
        boolean pending=false;try{ChaoxingRequests.get(listUrl,ChaoxingReadPolicy.Stage.WORK_LIST,native202,(url,stage)->null);}catch(ChaoxingReadPolicy.InteractionRequired e){pending=true;}
        check(pending,"an unresolved browser response is partial synchronization, never a successful empty response");
        boolean login=false;try{ChaoxingRequests.get(courseUrl,ChaoxingReadPolicy.Stage.COURSE,(url,stage)->{throw Http.responseError(401,"chaoxing");},browser);}catch(Http.LoginRequired e){login=true;}
        check(login&&browserCalls[0]==4,"actual unauthorized responses retain the reconnect flow without browser retries");
        boolean forbidden=false;try{ChaoxingRequests.get(courseUrl,ChaoxingReadPolicy.Stage.COURSE,(url,stage)->{throw Http.responseError(403,"chaoxing");},browser);}catch(IOException e){forbidden=!(e instanceof Http.LoginRequired);}
        check(forbidden&&browserCalls[0]==4,"course permission failures remain local and do not trigger a retry loop");
        check(!(Http.responseError(202,"mooc") instanceof Http.BrowserRequired),"Chaoxing continuation does not change the MOOC response policy");
        for(String url:new String[]{"https://evil.example/work","http://mooc1.chaoxing.com/work","https://user@mooc1.chaoxing.com/work","https://mooc1.chaoxing.com/work/submit"}){
            boolean rejected=false;try{ChaoxingRequests.get(url,ChaoxingReadPolicy.Stage.WORK_DETAIL,native202,browser);}catch(IOException e){rejected=true;}
            check(rejected,"continuation rejects unsupported or non-read-only page: "+url);
        }
        boolean stale=false;try{ChaoxingReadPolicy.fromBrowser(snapshot(courseUrl,course),"_uid=456","123",ChaoxingReadPolicy.Stage.COURSE);}catch(Http.LoginRequired e){stale=true;}
        check(stale,"browser continuation must belong to the verified source account");
        boolean mismatch=false;try{ChaoxingReadPolicy.fromBrowser(snapshot(courseUrl,course+"<script>var uid='456';</script>"),"_uid=123","123",ChaoxingReadPolicy.Stage.COURSE);}catch(Http.LoginRequired e){mismatch=true;}
        check(mismatch,"a conflicting server-rendered account cannot be accepted by a scoped cookie alone");
        boolean loginPage=false;try{ChaoxingReadPolicy.fromBrowser(snapshot(courseUrl,"<button id='loginBtn'>登录</button>"),"_uid=123","123",ChaoxingReadPolicy.Stage.COURSE);}catch(Http.LoginRequired e){loginPage=true;}
        check(loginPage,"a login form cannot pass as a course page after a 202 response");
        boolean interaction=false;try{ChaoxingReadPolicy.ready(courseUrl,"<p>请完成验证，拖动滑块</p>",ChaoxingReadPolicy.Stage.COURSE);}catch(ChaoxingReadPolicy.InteractionRequired e){interaction=true;}
        check(interaction,"interactive verification is handed back to the user instead of being automated");
        check(ChaoxingReadPolicy.fromBrowser(snapshot(listUrl,"<p>正在加载</p>"),"_uid=123","123",ChaoxingReadPolicy.Stage.WORK_LIST)==null,"the real browser snapshot seam distinguishes waiting from ready");
        check(ChaoxingReadPolicy.embeddedPage(detailUrl,"<iframe src='/mooc-ans/work/selectWorkQuestionYiPiYue?workId=8'></iframe>",ChaoxingReadPolicy.Stage.WORK_DETAIL).equals("https://mooc1.chaoxing.com/mooc-ans/work/selectWorkQuestionYiPiYue?workId=8"),"the actually displayed protected work frame can be read as an ordinary page");
        check(ChaoxingReadPolicy.embeddedPage(detailUrl,"<iframe src='https://evil.example/work/task?workId=8'></iframe><iframe src='/work/submit'></iframe>",ChaoxingReadPolicy.Stage.WORK_DETAIL).isEmpty(),"unrelated or submission frames cannot enter the read continuation");
        System.out.println("Passed "+checks+" HTTP 202 continuation / protected page checks.");
    }
}
