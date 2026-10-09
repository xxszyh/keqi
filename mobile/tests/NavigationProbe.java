package com.keqi.reminder;

/** CLI seam used by the public browser probe to replay the actual native routing policy. */
public final class NavigationProbe {
    public static void main(String[] args){
        if(args[0].equals("start")){System.out.println(LoginNavigation.loginStart("chaoxing",Boolean.parseBoolean(args[1])));return;}
        String target=LoginNavigation.browserTarget(args[2],"chaoxing",Boolean.parseBoolean(args[1]));
        System.out.println(target==null?"BLOCKED":target);
    }
}
