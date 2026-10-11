package android.app;
public class NotificationManager {
    public static final int IMPORTANCE_NONE=0,IMPORTANCE_DEFAULT=3;
    public NotificationChannel getNotificationChannel(String id){return null;}
    public boolean areNotificationsEnabled(){return true;}
    public void createNotificationChannel(NotificationChannel channel){}
    public void notify(int id,Notification notification){}
}
