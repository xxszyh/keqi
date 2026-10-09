package com.keqi.reminder;
import android.content.*;
public final class AlarmReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){String key=intent.getStringExtra("key");if(key==null)return;PendingResult result=goAsync();new Thread(()->{try{Notifications.deliver(context,key);}finally{result.finish();}},"keqi-notice").start();}
}
