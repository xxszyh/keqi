package com.keqi.reminder;

import java.net.URI;

/** Allows platform login navigation and upgrades legacy first-party HTTP callbacks. */
public final class LoginNavigation {
    // This is the protected course homepage the user reaches after logging in with Edge.
    public static final String CHAOXING_HOME="https://mooc1-1.chaoxing.com/visit/interaction";
    public static String verificationPage(String platform){return platform.equals("mooc")?"https://www.icourse163.org/home.htm":CHAOXING_HOME;}
    public static String browserTarget(String address,String platform,boolean mainFrame){
        // Embedded platform frames retain normal WebView behavior and cookie scoping.
        // They cannot access any native bridge, and TLS/cleartext policy is unchanged.
        if(!mainFrame)return address;
        String trustedTarget=target(address,platform);if(trustedTarget!=null)return trustedTarget;
        if(platform.equals("chaoxing"))try{
            java.net.URL url=new java.net.URL(address);String host=url.getHost();
            if("https".equalsIgnoreCase(url.getProtocol())&&url.getUserInfo()==null&&(url.getPort()==-1||url.getPort()==443)&&host.matches("[A-Za-z0-9.-]+"))return address;
        }catch(Exception ignored){}return null;
    }
    public static String loginStart(String platform,boolean desktop){
        if(platform.equals("mooc"))return "https://www.icourse163.org/";
        return "https://passport2.chaoxing.com/"+(desktop?"login":"mlogin")+"?fid=&newversion=true&refer=https%3A%2F%2Fmooc1-1.chaoxing.com%2Fvisit%2Finteraction";
    }
    public static String target(String address,String platform){
        try{
            URI u=new URI(address);String host=u.getHost();if(host==null||u.getUserInfo()!=null)return null;host=host.toLowerCase(java.util.Locale.ROOT);
            // HDU's post-login personal-home alias was returned as HTTP in the screenshots.
            // Enter the working official homepage with its own platform cookies instead;
            // do not transfer school query tokens or relax the app's cleartext/TLS policy.
            String path=u.getRawPath();boolean schoolHome=path==null||path.isEmpty()||path.equals("/")||path.equals("/base")||path.equals("/base/");
            if(platform.equals("chaoxing")&&host.equals("i.mooc.hdu.edu.cn")&&schoolHome&&
                (("http".equalsIgnoreCase(u.getScheme())&&(u.getPort()==-1||u.getPort()==80))||
                 ("https".equalsIgnoreCase(u.getScheme())&&(u.getPort()==-1||u.getPort()==443))))return CHAOXING_HOME;
            boolean allowed=platform.equals("chaoxing")?(host.equals("chaoxing.com")||host.endsWith(".chaoxing.com")):(host.equals("icourse163.org")||host.endsWith(".icourse163.org")||host.equals("163.com")||host.endsWith(".163.com"));
            if(!allowed)return null;
            if("https".equalsIgnoreCase(u.getScheme())&&(u.getPort()==-1||u.getPort()==443))return address;
            if("http".equalsIgnoreCase(u.getScheme())&&(u.getPort()==-1||u.getPort()==80)){
                String authority=u.getRawAuthority();if(authority.endsWith(":80"))authority=authority.substring(0,authority.length()-3);
                return "https://"+authority+(u.getRawPath()==null?"":u.getRawPath())+(u.getRawQuery()==null?"":"?"+u.getRawQuery())+(u.getRawFragment()==null?"":"#"+u.getRawFragment());
            }
        }catch(Exception ignored){}return null;
    }
}
