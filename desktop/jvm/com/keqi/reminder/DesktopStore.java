package com.keqi.reminder;

import java.nio.file.*;
import java.sql.*;
import org.json.*;

/** Single JVM writer. Task, operation and cursor updates commit together. */
public final class DesktopStore implements AutoCloseable {
    private final Connection db;
    public interface Mutation<T>{T run(Workspace workspace) throws Exception;}
    public DesktopStore(Path folder) throws Exception {
        Files.createDirectories(folder);Class.forName("org.sqlite.JDBC");db=DriverManager.getConnection("jdbc:sqlite:"+folder.resolve("keqi.db"));
        try(Statement s=db.createStatement()){s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA busy_timeout=5000");s.execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");}
    }
    public synchronized JSONObject meta(String key) throws Exception {try(PreparedStatement p=db.prepareStatement("SELECT value FROM meta WHERE key=?")){p.setString(1,key);try(ResultSet r=p.executeQuery()){return r.next()?new JSONObject(r.getString(1)):new JSONObject();}}}
    public synchronized void meta(String key,JSONObject value) throws Exception {try(PreparedStatement p=db.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)")){p.setString(1,key);p.setString(2,value.toString());p.executeUpdate();}}
    public synchronized <T> T transact(Mutation<T> mutation) throws Exception {
        db.setAutoCommit(false);try{Workspace w=new Workspace(meta("workspace"));T result=mutation.run(w);meta("workspace",w.save());db.commit();return result;}catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    public synchronized void close() throws SQLException {db.close();}
}
