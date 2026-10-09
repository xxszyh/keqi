package com.keqi.reminder;

import org.json.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Replays the video's acceptance states and exercises the production enrollment reader. */
public final class ConnectionTests {
    private static int checks=0;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static boolean rejected(String platform,String cookies,String account){try{ConnectionPolicy.validate(platform,cookies,"",0,account);return false;}catch(IllegalArgumentException e){return true;}}
    static JSONObject course(int id,int mode) throws Exception {
        return new JSONObject().put("id",id).put("name","课程 "+id).put("mode",mode).put("schoolPanel",new JSONObject().put("shortName","SCHOOL")).put("termPanel",new JSONObject().put("id",id+1000));
    }
    static JSONArray courses(int first,int count,int mode) throws Exception {JSONArray result=new JSONArray();for(int i=0;i<count;i++)result.put(course(first+i,mode));return result;}
    static JSONObject page(JSONArray courses,int count) throws Exception {return new JSONObject().put("code",0).put("result",new JSONObject().put("list",courses).put("query",new JSONObject().put("totlePageCount",count)));}
    public static void main(String[] args) throws Exception {
        check(!rejected("mooc","NTESSTUDYSI=fixture","123"),"authenticated MOOC connection rejected when link count is zero");
        System.out.println("PASS: authenticated MOOC connection does not depend on link count");
        check(rejected("chaoxing","JSESSIONID=anonymous; route=fixture",""),"anonymous Chaoxing cookies incorrectly accepted");
        System.out.println("PASS: anonymous Chaoxing cookies cannot complete connection");
        check(rejected("mooc","NTESSTUDYSI=anonymous",""),"MOOC CSRF cookie alone cannot prove authentication");
        check(rejected("chaoxing","","123"),"missing session rejected");
        check(ConnectionPolicy.account("mooc","NTESSTUDYSI=fixture","<script>window.webUser = {\"roles\":[1],\"id\":123};</script>").equals("123"),"account comes from server-rendered signed-in page");
        check(ConnectionPolicy.account("mooc","NTESSTUDYSI=anonymous","<script>window.webUser=null;</script>").isEmpty(),"guest user rejected");
        check(ConnectionPolicy.account("mooc","NTESSTUDYSI=fixture","webUser={\"id\":0}").isEmpty(),"zero account rejected");
        check(ConnectionPolicy.account("chaoxing","JSESSIONID=fixture; _uid=123; route=fixture","<script>var fid='456';</script>").equals("123"),"protected Chaoxing account accepted");
        check(ConnectionPolicy.account("chaoxing","UID=123","<input id='fid' value='456'>").equals("123"),"hidden school id accepted");
        check(ConnectionPolicy.account("chaoxing","_uid=123","<input id='fid' value='456'><button id='loginBtn'>登录</button>").isEmpty(),"login form rejected even with stale uid cookie");
        check(ConnectionPolicy.account("chaoxing","_uid=123","<button onclick='hasAgreePolicy(loginByPhoneAndPwd)'>登录</button><input id='fid' value='456'>").isEmpty(),"mobile login form rejected");
        check(ConnectionPolicy.account("chaoxing","JSESSIONID=anonymous","var fid='456';").isEmpty(),"school id is not account authentication");
        check(ConnectionPolicy.account("chaoxing","_uid=123","页面无法访问").isEmpty(),"uid without protected page rejected");
        String authenticatedCatalog="<div class='course'><a href='/visit/stucoursemiddle?courseid=1001&amp;clazzid=2001&amp;cpi=3001'><span class='course-name' title='概率论与数理统计'>概率论与数理统计</span></a></div>";
        check(ConnectionPolicy.account("chaoxing","_uid=123; JSESSIONID=fixture",authenticatedCatalog).equals("123"),"signed-in course catalog must complete connection when old profile uid/fid declarations are absent");
        check(ConnectionPolicy.account("chaoxing","JSESSIONID=guest",authenticatedCatalog).isEmpty(),"course links without a scoped account cookie cannot authorize a connection");
        check(ConnectionPolicy.account("chaoxing","_uid=123",authenticatedCatalog+"<button id='loginBtn'>登录</button>").isEmpty(),"public course links on a login form do not authorize a connection");
        check(ConnectionPolicy.account("chaoxing","_uid=123",authenticatedCatalog+"<script>var uid='456';</script>").isEmpty(),"course links cannot override a mismatched signed-in account");
        check(ConnectionPolicy.account("chaoxing","UID=456; _uid=123",authenticatedCatalog+"<script>var uid='123';</script>").equals("123"),"current Chaoxing _uid takes priority over a stale legacy UID alias");
        check(ConnectionPolicy.account("chaoxing","UID=123; _uid=0",authenticatedCatalog).isEmpty(),"an explicit guest _uid cannot be promoted by a stale legacy UID cookie");
        String modernCatalog="<li class='course-item'><a title='大学物理' href='https://mooc1-1.chaoxing.com/mycourse/studentcourse?cpi=3002&amp;clazzid=2002&amp;courseId=1002&amp;enc=a%2Bb+c'>封面</a></li>";
        ChaoxingCourses.Catalog modern=ChaoxingCourses.parse(modernCatalog,"https://mooc1-1.chaoxing.com/visit/interaction");
        check(modern.courses.length()==1&&modern.courses.getJSONObject(0).getString("title").equals("大学物理"),"personal homepage studentcourse cards and reordered mixed-case queries recognized");
        check(modern.courses.getJSONObject(0).getString("url").endsWith("enc=a%2Bb+c"),"catalog retains the signed source query exactly");
        String hiddenCatalog="<li><input name='courseId' value='1003'><input name='classId' value='2003'><input name='cpi' value='3003'><a title='数字逻辑电路' href='javascript:void(0)'>封面</a></li>";
        check(ChaoxingCourses.parse(hiddenCatalog,"https://mooc1-1.chaoxing.com/visit/interaction").courses.getJSONObject(0).getString("title").equals("数字逻辑电路"),"hidden enrollment fields read without clicking or entering each course");
        String missingCpi="<li><input name='courseId' value='1004'><input name='classId' value='2004'><a title='待补入口的课' href='javascript:void(0)'>封面</a></li>";
        ChaoxingCourses.Catalog incompleteCatalog=ChaoxingCourses.parse(missingCpi,"https://mooc1-1.chaoxing.com/visit/interaction");
        check(incompleteCatalog.courses.length()==1&&incompleteCatalog.courses.getJSONObject(0).getString("cpi").isEmpty()&&!incompleteCatalog.warning.isEmpty(),"missing CPI retains the real course choice and reports incomplete homework coverage without inventing an id");
        check(ChaoxingCourses.parse(modernCatalog+modernCatalog,"https://mooc1-1.chaoxing.com/visit/interaction").courses.length()==1,"repeated cover/title links do not create duplicate course switches");
        String splitNameCatalog="<div class='course'><div class='cover'><a href='/mycourse/studentcourse?courseId=255733792&amp;clazzid=2009&amp;cpi=3009'><img></a></div><h3><a href='/mycourse/studentcourse?courseId=255733792&amp;clazzid=2009&amp;cpi=3009' title='形势与政策3'>形势与政策3</a></h3></div>";
        String splitName=ChaoxingCourses.parse(splitNameCatalog,"https://mooc1-1.chaoxing.com/visit/interaction").courses.getJSONObject(0).getString("title");
        check(splitName.equals("形势与政策3"),"course choice must display the course name instead of an enrollment code; got: "+splitName);
        String siblingNameCatalog="<div class='catalog'><div class='entry'><div class='cover'><a href='/visit/stucoursemiddle?courseid=1010&amp;clazzid=2010&amp;cpi=3010'><img></a></div><span class='course-name' title='概率论与数理统计'>概率论…</span><p>教师甲</p></div><div class='entry'><div class='cover'><a href='/visit/stucoursemiddle?courseid=1011&amp;clazzid=2011&amp;cpi=3011'><img></a></div><span class='course-name' title='数字逻辑电路'>数字逻辑电路</span></div></div>";
        JSONArray siblingNames=ChaoxingCourses.parse(siblingNameCatalog,"https://mooc1-1.chaoxing.com/visit/interaction").courses;
        check(siblingNames.getJSONObject(0).getString("title").equals("概率论与数理统计")&&siblingNames.getJSONObject(1).getString("title").equals("数字逻辑电路"),"course-name siblings belong to their own enrollment, not another card or teacher");
        String camelName="<li><input name='courseId' value='1012'><input name='classId' value='2012'><input name='cpi' value='3012'><span class='courseName' title='工程复变函数和积分变换 I'>工程复变函数…</span></li>";
        check(ChaoxingCourses.parse(camelName,"https://mooc1-1.chaoxing.com/visit/interaction").courses.getJSONObject(0).getString("title").equals("工程复变函数和积分变换 I"),"hidden enrollment cards retain full camel-case course names");
        String lateName="<div class='cover'><a href='/mycourse/studentcourse?courseId=1013&amp;clazzid=2013&amp;cpi=3013'><img></a></div><a href='/mycourse/studentcourse?courseId=1013&amp;clazzid=2013&amp;cpi=3013' title='大学生与法'>大学生与法</a>";
        check(ChaoxingCourses.parse(lateName,"https://mooc1-1.chaoxing.com/visit/interaction").courses.getJSONObject(0).getString("title").equals("大学生与法"),"a later named link upgrades an earlier unnamed cover for the same enrollment");
        ChaoxingCourses.Catalog noName=ChaoxingCourses.parse("<li><a href='/mycourse/studentcourse?courseId=1014&amp;clazzid=2014&amp;cpi=3014'><img></a></li>","https://mooc1-1.chaoxing.com/visit/interaction");
        check(noName.courses.getJSONObject(0).getString("title").equals("课程名称待更新")&&!noName.warning.isEmpty(),"missing names report pending metadata instead of displaying course codes");
        String coverClass="<div class='entry'><div class='course-cover'><a href='/mycourse/studentcourse?courseId=1015&amp;clazzid=2015&amp;cpi=3015'><img></a></div><span class='course-name' title='现代数字电子技术基础实验'>现代数字电子技术基础实验</span></div>";
        check(ChaoxingCourses.parse(coverClass,"https://mooc1-1.chaoxing.com/visit/interaction").courses.getJSONObject(0).getString("title").equals("现代数字电子技术基础实验"),"a course-cover wrapper does not hide its sibling course name");
        JSONArray isolated=ChaoxingCourses.parse("<a href='/mycourse/studentcourse?courseId=1016&amp;clazzid=2016&amp;cpi=3016'><img></a>"+splitNameCatalog,"https://mooc1-1.chaoxing.com/visit/interaction").courses;
        check(isolated.getJSONObject(0).getString("title").equals("课程名称待更新")&&isolated.getJSONObject(1).getString("title").equals("形势与政策3"),"an unnamed enrollment never borrows another course's name from the page body");
        check(ChaoxingCourses.parse("<a href='https://chaoxing.com.evil.example/mycourse/studentcourse?courseId=1&clazzid=2&cpi=3'>课程</a>","https://mooc1-1.chaoxing.com/visit/interaction").courses.length()==0,"course resources stay on actual platform HTTPS hosts");
        check(ChaoxingCourses.parse("<a href='/mycourse/studentcourse?courseId=1&cpi=3'>缺少班级</a>","https://mooc1-1.chaoxing.com/visit/interaction").courses.length()==0,"course name and course id alone cannot fabricate an enrollment");
        JSONObject browserPage=new JSONObject().put("url","https://mooc1-1.chaoxing.com/visit/interaction").put("html",modernCatalog);
        Authentication.Session live=Authentication.fromBrowser(JSONObject.quote(browserPage.toString()),"_uid=123; JSESSIONID=fixture");
        check(live.account.equals("123")&&live.fid.isEmpty()&&live.courses.length()==1,"fresh login-browser snapshot verifies and returns selectable courses without old profile fields");
        boolean anonymousBrowser=false;try{Authentication.fromBrowser(JSONObject.quote(browserPage.toString()),"JSESSIONID=guest");}catch(IllegalArgumentException e){anonymousBrowser=true;}check(anonymousBrowser,"fresh browser snapshot without account session is rejected");
        browserPage.put("url","https://passport2.chaoxing.com/login");boolean loginBrowser=false;try{Authentication.fromBrowser(JSONObject.quote(browserPage.toString()),"_uid=123");}catch(Http.LoginRequired e){loginBrowser=true;}check(loginBrowser,"browser redirect to login rejected even if stale uid and course DOM are present");
        browserPage.put("url","https://identity.school.example/visit/interaction");boolean externalBrowser=false;try{Authentication.fromBrowser(JSONObject.quote(browserPage.toString()),"_uid=123");}catch(Http.LoginRequired e){externalBrowser=true;}check(externalBrowser,"external browser page cannot complete a native connection");
        check(ConnectionPolicy.account("chaoxing","_uid=123","<script>var userId = '123';</script>").equals("123"),"protected course-home account does not depend on optional school metadata");
        check(ConnectionPolicy.account("chaoxing","_uid=123","<input id='userId' value='123'>").equals("123"),"server-rendered course-home user input verifies the current account");
        check(ConnectionPolicy.account("chaoxing","_uid=123","<script>var uid='456';var fid='789';</script>").isEmpty(),"stale cookie cannot replace a different server-rendered user");
        check(ConnectionPolicy.account("chaoxing","JSESSIONID=guest","var userId='123';").isEmpty(),"server id needs a scoped matching platform session");
        check(ConnectionPolicy.account("chaoxing","_uid=123","var userId='123';<button id='loginBtn'>登录</button>").isEmpty(),"login form with apparent user id remains anonymous");
        check(ConnectionPolicy.fid("<script>const fid = '456';</script>").equals("456"),"modern school metadata declaration recognized");
        String callback="http://i.chaoxing.com:80/base?enc=a%2Bb+c&refer=https%3A%2F%2Fi.chaoxing.com#tab";
        check("https://i.chaoxing.com/base?enc=a%2Bb+c&refer=https%3A%2F%2Fi.chaoxing.com#tab".equals(LoginNavigation.target(callback,"chaoxing")),"legacy HTTPS upgrade preserves signed query");
        check("https://passport2.chaoxing.com/mlogin".equals(LoginNavigation.target("https://passport2.chaoxing.com/mlogin","chaoxing")),"official mobile login allowed");
        String courseHome="https://mooc1-1.chaoxing.com/visit/interaction";
        check(courseHome.equals(LoginNavigation.browserTarget("http://i.mooc.hdu.edu.cn","chaoxing",true)),"HDU callback must reach the official course home confirmed in Edge");
        check(courseHome.equals(LoginNavigation.browserTarget("http://i.mooc.hdu.edu.cn:80/base?ticket=fixture%2Btoken#tab","chaoxing",true)),"school return can use the official session without forwarding school-specific query tokens");
        check(LoginNavigation.target("http://i.mooc.hdu.edu.cn","mooc")==null,"school callback belongs to Chaoxing, not China MOOC");
        for(String address:new String[]{"http://i.mooc.hdu.edu.cn.evil.example/","http://user@i.mooc.hdu.edu.cn/","http://i.mooc.hdu.edu.cn:8080/","http://other.hdu.edu.cn/","http://i.mooc.hdu.edu.cn/unrelated"})check(LoginNavigation.browserTarget(address,"chaoxing",true)==null,"only the observed school home callback may be remapped: "+address);
        check(courseHome.equals(Http.redirectTarget("https://i.chaoxing.com/base","http://i.mooc.hdu.edu.cn","chaoxing")),"native validation follows the same school return policy as the login browser");
        check("https://i.chaoxing.com/base?enc=a%2Bb".equals(Http.redirectTarget("https://mooc1.chaoxing.com/","http://i.chaoxing.com/base?enc=a%2Bb","chaoxing")),"native first-party legacy redirects upgrade to HTTPS without changing signed data");
        check("https://mooc1.chaoxing.com/visit/home".equals(Http.redirectTarget("https://mooc1.chaoxing.com/visit/interaction","home","chaoxing")),"relative native redirect resolved against its own page");
        boolean guestRedirect=false;try{Http.redirectTarget(courseHome,"https://passport2.chaoxing.com/login?refer=fixture","chaoxing");}catch(Http.LoginRequired e){guestRedirect=true;}check(guestRedirect,"anonymous official course-home redirect requires login");
        boolean moocGuest=false;try{Http.redirectTarget("https://www.icourse163.org/home.htm","https://passport.163.com/login","mooc");}catch(Http.LoginRequired e){moocGuest=true;}check(moocGuest,"expired MOOC session retains its reconnect state when redirected to NetEase passport");
        check(!(Http.responseError(403,"chaoxing") instanceof Http.LoginRequired),"a forbidden or closed Chaoxing course does not invalidate a verified whole-platform connection");
        check(Http.responseError(401,"chaoxing") instanceof Http.LoginRequired,"actual unauthorized Chaoxing session still requires login");
        check(Http.responseError(403,"mooc") instanceof Http.LoginRequired,"existing MOOC reconnect behavior preserved");
        IOException pendingRead=Http.responseError(202,"chaoxing");
        check(pendingRead!=null&&pendingRead.getClass().getSimpleName().equals("BrowserRequired"),"Chaoxing HTTP 202 must trigger the logged-in browser read path instead of a generic request failure; got: "+pendingRead);
        for(String address:new String[]{"http://i.mooc.hdu.edu.cn/","https://user@i.chaoxing.com/","https://i.chaoxing.com:8080/","https://chaoxing.com.evil.example/"})check(!Http.trusted(address,"chaoxing"),"native requests stay on scoped, standard-port HTTPS platform hosts: "+address);
        check(LoginNavigation.target("https://webzj.reg.163.com/login","mooc")!=null,"NetEase authentication frame allowed");
        check("https://identity.school.example/sso".equals(LoginNavigation.browserTarget("https://identity.school.example/sso","chaoxing",true)),"official browser can follow HTTPS school identity redirect");
        check("about:blank".equals(LoginNavigation.browserTarget("about:blank","chaoxing",false)),"blank embedded auth frame must not be mistaken for blocked main navigation");
        for(String address:new String[]{"http://identity.school.example/sso","https://user@identity.school.example/sso","https://identity.school.example:8080/sso","intent://login","about:blank"})check(LoginNavigation.browserTarget(address,"chaoxing",true)==null,"login browser must reject unsupported main navigation: "+address);
        check(LoginNavigation.loginStart("chaoxing",false).startsWith("https://passport2.chaoxing.com/mlogin?"),"mobile login starts directly at passport instead of home redirect");
        check(LoginNavigation.loginStart("chaoxing",true).startsWith("https://passport2.chaoxing.com/login?"),"desktop alternative uses official login entry");
        for(boolean desktop:new boolean[]{false,true})check(java.net.URLDecoder.decode(LoginNavigation.loginStart("chaoxing",desktop),"UTF-8").endsWith("refer="+courseHome),"both login modes return to the working protected course home");
        check(LoginNavigation.verificationPage("chaoxing").equals(courseHome),"complete connection and background sync verify the official course home");
        check(LoginNavigation.verificationPage("mooc").equals("https://www.icourse163.org/home.htm"),"MOOC verification entry unchanged");
        for(String address:new String[]{"https://chaoxing.com.evil.example/","https://evil.example/","https://chaoxing.com@evil.example/","https://user@i.chaoxing.com/","http://i.chaoxing.com:8080/","javascript:alert(1)","intent://login"})check(LoginNavigation.target(address,"chaoxing")==null,"unexpected navigation accepted: "+address);
        JSONObject normal=Collectors.moocCourse(course(1,0),1),spoc=Collectors.moocCourse(course(2,20),2);
        check(normal.getString("url").equals("https://www.icourse163.org/learn/SCHOOL-1?tid=1001"),"MOOC course URL matches personal card data");
        check(spoc.getString("url").equals("https://www.icourse163.org/spoc/learn/SCHOOL-2?tid=1002"),"SPOC course URL matches personal card data");
        JSONObject fallback=course(1,0);fallback.getJSONObject("schoolPanel").put("shortName","").put("schoolSN","TEST");
        check(Collectors.moocCourse(fallback,1).getString("schoolCourseId").equals("TEST-1"),"schoolSN used when shortName is empty");
        JSONObject missing=course(1,0);missing.remove("termPanel");check(Collectors.moocCourse(missing,1)==null,"missing term cannot create fake course");
        JSONObject secondTerm=course(1,0);secondTerm.getJSONObject("termPanel").put("id",9999);
        check(!Collectors.moocCourse(secondTerm,1).getString("key").equals(normal.getString("key")),"different semesters remain separate");
        MoocCourses.Batch video=MoocCourses.read((scope,p)->page(courses(scope==1?1:20,scope==1?5:4,scope==1?0:20),1));
        check(video.courses.length()==9&&video.warnings.isEmpty(),"five MOOC and four SPOC course cards read without anchor links");
        List<String> calls=new ArrayList<>();
        MoocCourses.Batch paginated=MoocCourses.read((scope,p)->{calls.add(scope+":"+p);return page(scope==2?courses(20,4,20):p==1?courses(1,8,0):courses(9,1,0),scope==1?2:1);});
        check(paginated.courses.length()==13&&paginated.warnings.isEmpty()&&calls.equals(Arrays.asList("1:1","1:2","2:1")),"both scopes and all enrollment pages read");
        MoocCourses.Batch partial=MoocCourses.read((scope,p)->{if(scope==2)throw new IOException("fixture network failure");return page(courses(1,5,0),1);});
        check(partial.courses.length()==5&&!partial.warnings.isEmpty(),"SPOC failure preserves successful MOOC discovery and reports incomplete coverage");
        MoocCourses.Batch lostPage=MoocCourses.read((scope,p)->{if(p==2)throw new IOException("fixture timeout");return page(scope==1?courses(1,8,0):courses(20,4,20),scope==1?2:1);});
        check(lostPage.courses.length()==12&&!lostPage.warnings.isEmpty(),"failed later page preserves earlier courses and checks other scope");
        MoocCourses.Batch repeat=MoocCourses.read((scope,p)->page(scope==1?courses(1,8,0):new JSONArray(),scope==1?3:0));
        check(repeat.courses.length()==8&&!repeat.warnings.isEmpty(),"repeating pagination terminates without reporting complete coverage");
        MoocCourses.Batch empty=MoocCourses.read((scope,p)->page(new JSONArray(),0));
        check(empty.courses.length()==0&&empty.warnings.isEmpty(),"authenticated empty course list is a valid result");
        MoocCourses.Batch unknown=MoocCourses.read((scope,p)->new JSONObject().put("code",0).put("result",JSONObject.NULL));
        check(unknown.courses.length()==0&&!unknown.warnings.isEmpty(),"unrecognized response is not a successful empty list");
        MoocCourses.Batch incomplete=MoocCourses.read((scope,p)->page(scope==1?new JSONArray().put(missing):new JSONArray(),1));
        check(!incomplete.warnings.isEmpty(),"missing course identity reports incomplete coverage");
        boolean expired=false;try{MoocCourses.read((scope,p)->{throw new Http.LoginRequired();});}catch(Http.LoginRequired e){expired=true;}check(expired,"expired session propagated to reconnect state");
        boolean loginCode=false;try{MoocCourses.read((scope,p)->new JSONObject().put("code",1));}catch(Http.LoginRequired e){loginCode=true;}check(loginCode,"official RPC login-required code propagates to reconnect state");
        boolean interrupted=false;try{MoocCourses.read((scope,p)->{throw new InterruptedException();});}catch(InterruptedException e){interrupted=true;}check(interrupted,"canceled sync is not reported as successful");
        if(args.length>0)for(String platform:new String[]{"mooc","chaoxing"}){
            Path file=Paths.get(args[0],"anonymous-"+platform+".html");
            if(Files.exists(file))check(ConnectionPolicy.account(platform,"NTESSTUDYSI=anonymous; _uid=123",new String(Files.readAllBytes(file),"UTF-8")).isEmpty(),"actual anonymous platform HTML cannot establish connection");
        }
        if(args.length>0){Path file=Paths.get(args[0],"anonymous-courses.json");if(Files.exists(file)){
            JSONObject actual=new JSONObject(new String(Files.readAllBytes(file),"UTF-8"));
            MoocCourses.Batch guest=MoocCourses.read((scope,p)->actual);
            check(!guest.warnings.isEmpty(),"actual anonymous code-zero response is not a complete course list");
        }}
        if(args.length>1){Path file=Paths.get(args[1],"anonymous-interaction.html");if(Files.exists(file))check(ConnectionPolicy.account("chaoxing","_uid=123",new String(Files.readAllBytes(file),"UTF-8")).isEmpty(),"actual anonymous course-home redirect cannot complete connection, even with stale uid");}
        System.out.println("Passed "+checks+" connection/catalog checks.");
    }
}
