package com.keqi.reminder;

import android.webkit.CookieManager;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Reads the same paginated enrollment RPC used by the current personal course cards. */
public final class MoocCourses {
    public static final String ENDPOINT="https://www.icourse163.org/web/j/learnerCourseRpcBean.getMyLearnedCoursePanelList.rpc";
    public static final class Batch {public final JSONArray courses=new JSONArray();public final List<String> warnings=new ArrayList<>();}
    public interface Requester {JSONObject page(int scope,int page) throws Exception;}
    public static Batch read() throws Exception {
        String cookies=CookieManager.getInstance().getCookie("https://www.icourse163.org/");Matcher token=Pattern.compile("(?:^|;\\s*)NTESSTUDYSI=([^;]+)").matcher(cookies==null?"":cookies);if(!token.find())throw new Http.LoginRequired();
        String csrf=token.group(1);
        return read((scope,page)->{
            if(page>1||scope==2)Thread.sleep(250);
            String body="type=30&p="+page+"&psize=8&courseType="+scope;
            return new JSONObject(Http.request(ENDPOINT+"?csrfKey="+Http.enc(csrf),"mooc",body,"application/x-www-form-urlencoded;charset=UTF-8"));
        });
    }
    public static Batch read(Requester requester) throws Exception {
        Batch out=new Batch();Set<String> seen=new HashSet<>();
        for(int scope:new int[]{1,2}){
            try{
            Set<String> pages=new HashSet<>();
            for(int page=1;page<=100;page++){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                JSONObject response=requester.page(scope,page);
                int code=response.optInt("code",Integer.MIN_VALUE);if(code==1)throw new Http.LoginRequired();
                if(code!=0)throw new IOException("MOOC 课程接口拒绝请求，请重新登录后重试");
                JSONObject result=response.optJSONObject("result");if(result==null)result=response.optJSONObject("data");if(result==null)throw new IOException("MOOC 课程列表结构未识别");
                JSONArray records=result.optJSONArray("list");if(records==null)records=result.optJSONArray("result");if(records==null)throw new IOException("MOOC 未返回课程列表");
                if(records.length()>0&&!pages.add(records.toString())){out.warnings.add("MOOC 课程分页重复，保留已读取课程");break;}
                for(int i=0;i<records.length();i++){
                    JSONObject course=Collectors.moocCourse(records.getJSONObject(i),scope);
                    if(course==null){out.warnings.add("一门 MOOC 课程缺少学期编号或学校标识");continue;}
                    if(seen.add(course.getString("key")))out.courses.put(course);
                }
                JSONObject paging=result.optJSONObject("query");if(paging==null)paging=result.optJSONObject("pagination");
                int total=paging==null?-1:paging.optInt("totlePageCount",paging.optInt("totalPageCount",-1));
                if(records.length()==0||total>=0&&page>=total||total<0&&records.length()<8)break;
                if(page==100)out.warnings.add("MOOC 课程分页达到读取上限");
            }
            }catch(Http.LoginRequired e){throw e;}catch(InterruptedException e){throw e;}catch(Exception e){out.warnings.add((scope==1?"MOOC":"SPOC")+" 课程列表未读取完整，请检查网络或重新连接");}
        }return out;
    }
}
