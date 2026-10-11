package android.database.sqlite;
import android.content.Context;
import android.database.Cursor;
public abstract class SQLiteOpenHelper implements AutoCloseable {
    private final Context context;
    private final String name;
    private SQLiteDatabase db;
    protected SQLiteOpenHelper(Context context,String name,Object factory,int version){this.context=context;this.name=name;}
    public void setWriteAheadLoggingEnabled(boolean enabled){}
    public synchronized SQLiteDatabase getWritableDatabase(){if(db==null){db=new SQLiteDatabase(context.directory.resolve(name).toString());try(Cursor c=db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='meta'",null)){c.moveToFirst();if(c.getInt(0)==0)onCreate(db);}}return db;}
    public SQLiteDatabase getReadableDatabase(){return getWritableDatabase();}
    public abstract void onCreate(SQLiteDatabase db);
    public abstract void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion);
    public void close(){if(db!=null)db.close();}
}
