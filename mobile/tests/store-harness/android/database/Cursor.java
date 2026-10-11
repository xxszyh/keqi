package android.database;
import java.sql.*;
public final class Cursor implements AutoCloseable {
    private final ResultSet result;
    public Cursor(ResultSet result){this.result=result;}
    public boolean moveToFirst(){return moveToNext();}
    public boolean moveToNext(){try{return result.next();}catch(SQLException e){throw new IllegalStateException(e);}}
    public String getString(int i){try{return result.getString(i+1);}catch(SQLException e){throw new IllegalStateException(e);}}
    public int getInt(int i){try{return result.getInt(i+1);}catch(SQLException e){throw new IllegalStateException(e);}}
    public long getLong(int i){try{return result.getLong(i+1);}catch(SQLException e){throw new IllegalStateException(e);}}
    public void close(){try{Statement statement=result.getStatement();result.close();statement.close();}catch(SQLException e){throw new IllegalStateException(e);}}
}
