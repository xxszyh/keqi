package com.keqi.reminder;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

public final class SyncJob extends JobService {
    private Future<?> work;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    public boolean onStartJob(JobParameters params){work=executor.submit(()->{try{SyncEngine.sync(getApplicationContext());}finally{jobFinished(params,false);}});return true;}
    public boolean onStopJob(JobParameters params){if(work!=null)work.cancel(true);return true;}
    public void onDestroy(){executor.shutdownNow();super.onDestroy();}
    public static void schedule(Context context){
        int minutes;try(Store store=new Store(context)){minutes=store.settings().optInt("syncMinutes",30);}
        JobInfo info=new JobInfo.Builder(4101,new ComponentName(context,SyncJob.class)).setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(Math.max(15,minutes)*60000L).setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();
        context.getSystemService(JobScheduler.class).schedule(info);
    }
}
