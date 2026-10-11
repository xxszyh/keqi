package com.keqi.reminder;

import java.net.*;

public final class LocalAddress {
    public static boolean allowed(InetAddress address){if(!(address instanceof Inet4Address))return false;byte[] bytes=address.getAddress();int first=bytes[0]&255,second=bytes[1]&255;return address.isSiteLocalAddress()||address.isLinkLocalAddress()||first==100&&second>=64&&second<=127;}
}
