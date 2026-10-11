package android.content;
import java.nio.file.Path;
public final class Context {
    public final Path directory;
    public Context(Path directory){this.directory=directory;}
    public Context getApplicationContext(){return this;}
    public <T> T getSystemService(Class<T> type){try{return type.getDeclaredConstructor().newInstance();}catch(Exception e){throw new IllegalStateException(e);}}
    public int checkSelfPermission(String permission){return 0;}
}
