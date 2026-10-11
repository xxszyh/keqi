package android.app;
import android.content.*;
public class PendingIntent {
    public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;
    public static PendingIntent getActivity(Context c,int request,Intent intent,int flags){return new PendingIntent();}
    public static PendingIntent getBroadcast(Context c,int request,Intent intent,int flags){return new PendingIntent();}
}
