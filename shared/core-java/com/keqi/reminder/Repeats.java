package com.keqi.reminder;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.json.*;

/** Calendar recurrence: an instance's identity follows its original slot, never completion time. */
public final class Repeats {
    public static void validate(JSONObject rule) throws JSONException {
        if(!Arrays.asList("daily","weekly","monthly").contains(rule.getString("frequency")))throw new IllegalArgumentException("重复周期无效");
        int interval=rule.optInt("interval",1);if(interval<1||interval>365)throw new IllegalArgumentException("重复间隔应为 1～365");
        try{ZoneId.of(rule.optString("timezone","Asia/Shanghai"));LocalDateTime start=LocalDateTime.parse(rule.getString("start"));if(start.getYear()<2000||start.getYear()>2199)throw new IllegalArgumentException("日期范围无效");}catch(Exception e){throw new IllegalArgumentException("重复起始日期或时区无效");}
        if(rule.has("until")&&(rule.getLong("until")<946684800000L||rule.getLong("until")>7258118400000L))throw new IllegalArgumentException("结束日期范围无效");
        if(rule.has("weekdays")){JSONArray days=rule.getJSONArray("weekdays");if(days.length()==0||days.length()>7)throw new IllegalArgumentException("请选择星期");for(int i=0;i<days.length();i++)if(days.getInt(i)<1||days.getInt(i)>7)throw new IllegalArgumentException("星期无效");}
        if(!Arrays.asList("last","skip").contains(rule.optString("shortMonth","last")))throw new IllegalArgumentException("短月规则无效");
    }
    public static JSONArray instances(String series,JSONObject rule,long from,long until) throws JSONException {
        JSONArray earlier=rule.has("previous")?instances(series,rule.getJSONObject("previous"),from,until):new JSONArray();validate(rule);ZoneId zone=ZoneId.of(rule.optString("timezone","Asia/Shanghai"));LocalDateTime start=LocalDateTime.parse(rule.getString("start"));if(start.getYear()<2000||start.getYear()>2199)throw new IllegalArgumentException("日期范围无效");int interval=rule.optInt("interval",1);String frequency=rule.getString("frequency"),segment=rule.optString("segment","initial");
        LocalDate end=Instant.ofEpochMilli(until).atZone(zone).toLocalDate().plusDays(1),lower=Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1);
        LocalDate date=start.toLocalDate().isAfter(lower)?start.toLocalDate():lower;JSONArray result=earlier;Set<Integer> days=new HashSet<>();JSONArray specified=rule.optJSONArray("weekdays");if(specified!=null)for(int i=0;i<specified.length();i++)days.add(specified.getInt(i));else days.add(start.getDayOfWeek().getValue());
        if(java.time.temporal.ChronoUnit.DAYS.between(date,end)>3660)throw new IllegalArgumentException("一次生成范围不超过十年");
        for(;!date.isAfter(end);date=date.plusDays(1)){
            boolean match=false;if(frequency.equals("daily"))match=java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(),date)%interval==0;
            if(frequency.equals("weekly")){long weeks=java.time.temporal.ChronoUnit.WEEKS.between(start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));match=weeks%interval==0&&days.contains(date.getDayOfWeek().getValue());}
            if(frequency.equals("monthly")){long months=java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(start),YearMonth.from(date));int day=start.getDayOfMonth();if(day>date.lengthOfMonth()&&rule.optString("shortMonth","last").equals("last"))day=date.lengthOfMonth();match=months%interval==0&&date.getDayOfMonth()==day;}
            if(!match)continue;LocalDateTime local=LocalDateTime.of(date,start.toLocalTime());if(local.isBefore(start))continue;java.time.zone.ZoneOffsetTransition transition=zone.getRules().getTransition(local);if(transition!=null&&transition.isGap())local=transition.getDateTimeAfter();long at=local.atZone(zone).withEarlierOffsetAtOverlap().toInstant().toEpochMilli();if(at<from||at>until||rule.has("until")&&at>rule.optLong("until"))continue;
            String slot=date.toString();result.put(new JSONObject().put("id",Core.hash(new JSONArray(Arrays.asList("repeat-v1",series,segment,slot)).toString())).put("slot",slot).put("segment",segment).put("due",at));
        }return result;
    }
    public static boolean validInstance(JSONObject task,JSONObject series) throws JSONException {
        long at=task.optLong("originalDue");if(at==0)return true;JSONArray matches=instances(series.getString("id"),series.getJSONObject("rule"),at-86400000L,at+86400000L);for(int i=0;i<matches.length();i++)if(matches.getJSONObject(i).getString("id").equals(task.getString("id")))return true;return false;
    }
    public static void materialize(Workspace workspace,long from,long until,boolean host) throws JSONException {
        JSONArray series=workspace.list("series");for(int i=0;i<series.length();i++){JSONObject s=series.getJSONObject(i);if(s.optBoolean("paused")||s.optBoolean("deleted")||s.getJSONArray("conflicts").length()>0)continue;JSONObject rule=s.getJSONObject("rule"),template=s.getJSONObject("template");JSONArray instances=instances(s.getString("id"),rule,from,until);
            for(int j=0;j<instances.length();j++){JSONObject instance=instances.getJSONObject(j);if(workspace.get(instance.getString("id"))!=null)continue;JSONObject baseline=new JSONObject(template.toString()).put("seriesId",s.getString("id")).put("slot",instance.getString("slot")).put("manualDue",instance.getLong("due")).put("originalDue",instance.getLong("due")).put("segment",instance.getString("segment"));workspace.edit(instance.getString("id"),"manual",new JSONObject().put("baseline",baseline),host);}
        }
    }
}
