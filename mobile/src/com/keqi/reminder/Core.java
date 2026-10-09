package com.keqi.reminder;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/** Pure business rules, shared by collectors and scheduling, independently tested. */
public final class Core {
    public static final TimeZone CN=TimeZone.getTimeZone("GMT+08:00");
    public static String status(String text) {
        if(text.matches("(?s).*(未提交|待提交|未完成|未交|未做|待做|未作答).*"))return "pending";
        if(text.matches("(?s).*(已提交|已完成|待批阅|已批阅|已批改|已作答).*"))return "done";
        return "unknown";
    }
    public static final class ParsedTime {
        public final Long at; public final String precision;
        ParsedTime(Long at,String precision){this.at=at;this.precision=precision;}
    }
    public static ParsedTime parseTime(String raw,Integer year) {
        if(raw==null||raw.trim().isEmpty())return new ParsedTime(null,"unknown");
        String value=raw.trim().replace("年","-").replace("月","-").replace("日","").replace('/','-').replace('：',':');
        if(value.matches("\\d{10,13}")){
            long n=Long.parseLong(value);if(n<100000000000L)n*=1000;
            return n>=946684800000L&&n<7258118400000L?new ParsedTime(n,"exact"):new ParsedTime(null,"unknown");
        }
        if(value.matches("\\d{4}-\\d{1,2}-\\d{1,2}"))return new ParsedTime(null,"date_only");
        boolean supplied=false;
        if(value.matches("\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?")){
            if(year==null)return new ParsedTime(null,"year_missing");
            value=year+"-"+value; supplied=true;
        }
        String[] formats={"yyyy-M-d HH:mm:ss","yyyy-M-d HH:mm","yyyy-M-d'T'HH:mm:ssXXX","yyyy-M-d'T'HH:mm:ss.SSSXXX"};
        for(String pattern:formats){
            SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.ROOT);f.setTimeZone(CN);f.setLenient(false);
            ParsePosition pos=new ParsePosition(0);Date d=f.parse(value,pos);
            if(d!=null&&pos.getIndex()==value.length())return new ParsedTime(d.getTime(),supplied?"year_confirmed":"exact");
        }
        return new ParsedTime(null,"unknown");
    }
    public static ParsedTime parseRangeEnd(String start,String end,Integer year){
        String begin=start.replace('年','-').replace('月','-').replace("日","").replace('/','-');
        String finish=end.replace('年','-').replace('月','-').replace("日","").replace('/','-');
        Integer endYear=year;Matcher dated=Pattern.compile("^(\\d{4})-(\\d{1,2}-\\d{1,2}.*)").matcher(begin);
        if(dated.matches()){endYear=Integer.valueOf(dated.group(1));begin=dated.group(2);}
        if(endYear!=null&&begin.matches("\\d{1,2}-\\d{1,2}.*")&&finish.matches("\\d{1,2}-\\d{1,2}.*")){
            int s=Integer.parseInt(begin.split("[- ]")[0])*100+Integer.parseInt(begin.split("[- ]")[1]);
            int e=Integer.parseInt(finish.split("[- ]")[0])*100+Integer.parseInt(finish.split("[- ]")[1]);
            if(e<s)endYear=endYear+1;
        }
        return parseTime(finish,endYear);
    }
    public static double mostUrgent(long due,long now,double[] offsets){
        double result=Double.POSITIVE_INFINITY;
        if(due<=now)return -1;
        for(double h:offsets)if(due-now<=h*3600000&&h<result)result=h;
        return Double.isInfinite(result)?-1:result;
    }
    public static boolean currentThreshold(long due,long now,double[] offsets,double requested){return Double.compare(mostUrgent(due,now,offsets),requested)==0;}
    public static boolean quiet(long now,String start,String end){
        if(start.isEmpty()||end.isEmpty()||start.equals(end))return false;
        SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.ROOT);f.setTimeZone(CN);String s=f.format(new Date(now));
        return start.compareTo(end)<0?s.compareTo(start)>=0&&s.compareTo(end)<0:s.compareTo(start)>=0||s.compareTo(end)<0;
    }
    public static long quietEnd(long now,String end){
        Calendar c=Calendar.getInstance(CN);c.setTimeInMillis(now);String[] parts=end.split(":");
        c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(parts[0]));c.set(Calendar.MINUTE,Integer.parseInt(parts[1]));c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        if(c.getTimeInMillis()<=now)c.add(Calendar.DATE,1);return c.getTimeInMillis();
    }
    public static String hash(String text){
        try{byte[] data=java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(int i=0;i<16;i++)b.append(String.format(Locale.ROOT,"%02x",data[i]));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}
    }
}
