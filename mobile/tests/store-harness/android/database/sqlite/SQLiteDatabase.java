package android.database.sqlite;
import android.content.ContentValues;
import android.database.Cursor;
import java.sql.*;
import java.util.*;
public final class SQLiteDatabase {
    public static final int CONFLICT_REPLACE=5;
    private final Connection db;
    private final Deque<Boolean> transactions=new ArrayDeque<>();
    private boolean failed;
    public static int workspaceReads;
    public SQLiteDatabase(String path){try{Class.forName("org.sqlite.JDBC");db=DriverManager.getConnection("jdbc:sqlite:"+path);}catch(Exception e){throw new IllegalStateException(e);}}
    public void execSQL(String sql){try(Statement s=db.createStatement()){s.execute(sql);}catch(SQLException e){throw new IllegalStateException(e);}}
    public Cursor rawQuery(String sql,String[] args){if(args!=null&&args.length==1&&args[0].equals("collaboration"))workspaceReads++;try{PreparedStatement p=db.prepareStatement(sql);if(args!=null)for(int i=0;i<args.length;i++)p.setString(i+1,args[i]);return new Cursor(p.executeQuery());}catch(SQLException e){throw new IllegalStateException(e);}}
    public long insertWithOnConflict(String table,String ignored,ContentValues values,int conflict){
        List<String> keys=new ArrayList<>(values.keySet());String slots=String.join(",",Collections.nCopies(keys.size(),"?"));
        try(PreparedStatement p=db.prepareStatement("INSERT OR REPLACE INTO "+table+"("+String.join(",",keys)+") VALUES("+slots+")")){for(int i=0;i<keys.size();i++)p.setObject(i+1,values.get(keys.get(i)));return p.executeUpdate();}catch(SQLException e){throw new IllegalStateException(e);}
    }
    public int update(String table,ContentValues values,String where,String[] args){try{List<String> keys=new ArrayList<>(values.keySet()),parts=new ArrayList<>();for(String key:keys)parts.add(key+"=?");try(PreparedStatement p=db.prepareStatement("UPDATE "+table+" SET "+String.join(",",parts)+" WHERE "+where)){int i=1;for(String key:keys)p.setObject(i++,values.get(key));for(String arg:args)p.setString(i++,arg);return p.executeUpdate();}}catch(SQLException e){throw new IllegalStateException(e);}}
    public void beginTransaction(){try{if(transactions.isEmpty()){db.setAutoCommit(false);failed=false;}transactions.push(false);}catch(SQLException e){throw new IllegalStateException(e);}}
    public void setTransactionSuccessful(){transactions.pop();transactions.push(true);}
    public void endTransaction(){try{if(!transactions.pop())failed=true;if(transactions.isEmpty()){if(failed)db.rollback();else db.commit();db.setAutoCommit(true);}}catch(SQLException e){throw new IllegalStateException(e);}}
    public void close(){try{db.close();}catch(SQLException e){throw new IllegalStateException(e);}}
}
