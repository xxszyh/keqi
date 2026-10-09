package com.keqi.reminder;

import org.json.*;
import java.io.IOException;
import java.util.*;

public final class ChaoxingExamTests {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static JSONObject co(String key,String cid,String clazz) throws Exception{return new JSONObject().put("key",key).put("platform","chaoxing").put("title","概率论与数理统计").put("courseId",cid).put("classId",clazz).put("cpi","3");}
    private static String card(String id,String cid,String clazz,String title,String status,String time){return "<ul class='ks_list'><li data='/exam-ans/exam/phone/start?taskrefId="+id+"&courseId="+cid+"&classId="+clazz+"'><dl><dt>"+title+"</dt><dd>"+time+"</dd></dl><span class='ks_state'>"+status+"</span></li></ul>";}
    public static void main(String[] args) throws Exception {
        JSONObject course=co("cx:1:2","1","2");JSONArray courses=new JSONArray().put(course);
        String html=card("8","1","2","期中考试","未完成","2026-10-09 08:00 至 2026-10-15 18:00");
        JSONObject exam=ChaoxingExams.parse(html,ChaoxingExams.MOBILE,courses,"account",null).tasks.getJSONObject(0);
        check(exam.getString("kind").equals("exam")&&exam.getString("phase").equals("exam"),"exam metadata becomes an exam task");
        check(exam.getString("courseTitle").equals("概率论与数理统计")&&exam.getString("courseKey").equals("cx:1:2"),"exam maps to the exact enrolled course and class");
        check(exam.getLong("due")==Core.parseTime("2026-10-15 18:00",null).at&&exam.getLong("examStart")==Core.parseTime("2026-10-09 08:00",null).at,"exam end is deadline; start stays separate");
        check(exam.getString("status").equals("pending")&&exam.getString("sourceUrl").equals(ChaoxingExams.MOBILE),"start-exam link is never stored as the open-source action");
        JSONObject work=Collectors.task("chaoxing","account",course,"8","submit","作业","homework","","pending",new Core.ParsedTime(null,"unknown"),"");
        check(!exam.getString("id").equals(work.getString("id")),"homework and exam IDs cannot collide");
        String refer=java.net.URLEncoder.encode("https://mooc1.chaoxing.com/exam/test?courseId=1&classId=2&examId=8","UTF-8");
        String table="<table class='dataTable'><tr class='dataTr'><td>1</td><td>期中考试</td><td>2026-10-09 08:00 至 2026-10-15 18:00</td><td>120分钟</td><td>已结束</td><td>待批阅</td><td></td><td>在线</td><td><a onclick=\"go('https://mooc1.chaoxing.com/examnotes?refer="+refer+"')\">查看</a></td></tr></table>";
        JSONObject row=ChaoxingExams.parse(table,ChaoxingExams.TABLE,courses,"account",null).tasks.getJSONObject(0);
        check(row.getString("id").equals(exam.getString("id"))&&row.getString("status").equals("done"),"table refer resolves identity and answer state independently of exam closure");
        String twice=table.replace(refer,java.net.URLEncoder.encode(refer,"UTF-8"));
        check(ChaoxingExams.parse(twice,ChaoxingExams.TABLE,courses,"account",null).tasks.getJSONObject(0).getString("id").equals(exam.getString("id")),"double encoded refer keeps the stable exam and course mapping");
        JSONObject lineBreaks=ChaoxingExams.parse(html.replace(" 至 ","<br>"),ChaoxingExams.MOBILE,courses,"account",null).tasks.getJSONObject(0);
        check(lineBreaks.getLong("due")==exam.getLong("due"),"two explicit dates separated by a table line break retain the end deadline");
        JSONObject ended=ChaoxingExams.parse(card("9","1","2","期末考试","未作答","已结束"),ChaoxingExams.MOBILE,courses,"account",null).tasks.getJSONObject(0);
        check(ended.getString("status").equals("pending")&&ended.isNull("due"),"closed exam is not automatically completed and no deadline is invented");
        JSONObject startOnly=ChaoxingExams.parse(card("10","1","2","考试","未开始","开始时间：2026-10-20 10:00"),ChaoxingExams.MOBILE,courses,"account",null).tasks.getJSONObject(0);
        check(startOnly.isNull("due")&&!startOnly.getBoolean("deadlineObserved"),"a start time is never mistaken for a deadline or evidence to erase an old deadline");
        JSONObject yearless=ChaoxingExams.parse(card("11","1","2","跨年考试","未完成","12-31 18:00 至 01-02 18:00"),ChaoxingExams.MOBILE,courses,"account",null).tasks.getJSONObject(0);
        check(yearless.isNull("due")&&yearless.getString("precision").equals("year_missing"),"missing exam year remains unconfirmed");
        JSONObject confirmed=ChaoxingExams.parse(card("11","1","2","跨年考试","未完成","12-31 18:00 至 01-02 18:00"),ChaoxingExams.MOBILE,courses,"account",2026).tasks.getJSONObject(0);
        check(confirmed.getLong("due")==Core.parseTime("2027-01-02 18:00",null).at,"confirmed exam year handles rollover");
        courses.put(co("cx:1:4","1","4"));JSONObject ambiguous=ChaoxingExams.parse(card("12","1","","考试","未完成",""),ChaoxingExams.MOBILE,courses,"account",null).tasks.getJSONObject(0);
        check(ambiguous.getString("courseKey").equals("chaoxing:unlinked-exams")&&courses.length()==3,"ambiguous exam goes into its own selectable named group");
        check(ChaoxingExams.parse(card("","1","2","考试","未完成",""),ChaoxingExams.MOBILE,courses,"account",null).warnings.size()==1,"missing stable identity is reported rather than silently fabricated");
        check(!ChaoxingExams.ready("<h1>考试</h1><p>加载中</p>")&&ChaoxingExams.ready("<p>暂无考试</p>"),"pending exam page cannot prove zero exams");
        List<String> requests=new ArrayList<>();ChaoxingRequests.Reader reader=(url,stage)->{requests.add(url);if(stage==ChaoxingReadPolicy.Stage.EXAM_LIST)return new Http.Page(url,url.equals(ChaoxingExams.MOBILE)?html:table);throw new IOException("平台返回了不支持的读取页面");};
        ChaoxingTaskReader.Result result=ChaoxingTaskReader.read(reader,new JSONArray().put(course),"account",null,key->true,new ArrayList<>(),()->{});
        check(result.tasks.length()==1&&result.tasks.getJSONObject(0).getString("kind").equals("exam"),"production sync reads and deduplicates exams even when course homework fails");
        check(requests.stream().noneMatch(u->u.contains("/start?")||u.contains("examnotes")),"production sync never fetches an exam-start or answer page");
        check(result.message(new JSONArray(),key->true).contains("0 项作业、1 项考试")&&result.status().equals("partial"),"partial report retains fresh exam counts and actual homework failures");
        requests.clear();ChaoxingTaskReader.Result hidden=ChaoxingTaskReader.read(reader,new JSONArray().put(course),"account",null,key->false,new ArrayList<>(),()->{});
        check(hidden.tasks.length()==0&&requests.stream().noneMatch(u->u.contains("stucoursemiddle")),"hidden course exams and homework share the saved selection");
        JSONArray cached=new JSONArray().put(work).put(exam);check(result.message(cached,key->true).contains("1 项旧记录本轮未更新"),"cached homework is distinguished from the fresh exam in the actual sync report");
        List<String> called=new ArrayList<>();
        ChaoxingRequests.Reader detailFail=(url,stage)->{called.add(url);if(stage==ChaoxingReadPolicy.Stage.EXAM_LIST)return new Http.Page(url,"<p>暂无考试</p>");if(stage==ChaoxingReadPolicy.Stage.COURSE)return new Http.Page(url,"<input id='workEnc' value='fixture'><input id='enc' value='fixture'>");if(stage==ChaoxingReadPolicy.Stage.WORK_LIST)return new Http.Page(url,"<li data='https://mooc1.chaoxing.com/mooc-ans/work/task?workId=8'><p class='overHidden2'>第二章作业</p><p class='status'>未提交</p></li>");throw new IOException("平台返回了不支持的读取页面");};
        ChaoxingTaskReader.Result withWork=ChaoxingTaskReader.read(detailFail,new JSONArray().put(course),"account",null,key->true,new ArrayList<>(),()->{});
        check(withWork.tasks.length()==1&&!withWork.tasks.getJSONObject(0).getBoolean("deadlineObserved")&&withWork.message(new JSONArray(),key->true).contains("1 项作业、0 项考试"),"actual production flow reports the list as read despite a detail failure, preserving its previous deadline");
        check(withWork.warnings.get(0).contains("作业列表已更新，详情未读取"),"partial read identifies the failing stage instead of denying that any homework was read");
        String alternate="https://mooc1.chaoxing.com/mooc-ans/mycourse/stu?courseid=1&clazzid=2";
        check(ChaoxingReadPolicy.ready(alternate,"<input id='workEnc' value='fixture'>",ChaoxingReadPolicy.Stage.COURSE),"a redirected course view with protected entry metadata remains readable");
        check(!ChaoxingReadPolicy.allowed(alternate)&&!ChaoxingReadPolicy.responseAllowed("https://mooc1.chaoxing.com/mycourse/startExam",ChaoxingReadPolicy.Stage.COURSE),"relaxed final course proof cannot initiate arbitrary or exam-start requests");
        boolean examStartRejected=false;try{ChaoxingRequests.get("https://mooc1.chaoxing.com/exam-ans/exam/phone/start?taskrefId=8",ChaoxingReadPolicy.Stage.EXAM_LIST,reader,reader);}catch(IOException e){examStartRejected=true;}
        check(examStartRejected,"exam start URLs are rejected before any transport runs");
        System.out.println("Passed "+checks+" exam parsing, selection and partial-sync checks.");
    }
}
