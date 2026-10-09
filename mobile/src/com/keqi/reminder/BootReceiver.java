package com.keqi.reminder;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){PendingResult result=goAsync();new Thread(()->{try{SyncJob.schedule(context);Notifications.rebuild(context);}finally{result.finish();}},"keqi-restore").start();}
}
