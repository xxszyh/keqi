package android.app;
import android.content.Context;
public class Notification {
    public static class BigTextStyle {public BigTextStyle bigText(String text){return this;}}
    public static class Builder {
        public Builder(Context context,String channel){}
        public Builder setSmallIcon(int icon){return this;}
        public Builder setContentTitle(String text){return this;}
        public Builder setContentText(String text){return this;}
        public Builder setStyle(BigTextStyle style){return this;}
        public Builder setContentIntent(PendingIntent intent){return this;}
        public Builder setAutoCancel(boolean cancel){return this;}
        public Notification build(){return new Notification();}
    }
}
