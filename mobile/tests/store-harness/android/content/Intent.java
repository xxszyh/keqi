package android.content;
import android.net.Uri;
public class Intent {
    public Intent(Context context,Class<?> type){}
    public Intent setData(Uri data){return this;}
    public Intent putExtra(String key,String value){return this;}
}
