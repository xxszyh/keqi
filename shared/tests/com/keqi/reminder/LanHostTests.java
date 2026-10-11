package com.keqi.reminder;

import org.json.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;

/** A real HTTPS server and SQLite file; no user account or device credentials used. */
public final class LanHostTests {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static JSONObject post(JSONObject pairing,String path,JSONObject body,String token,String pin) throws Exception {
        SSLContext ssl=SSLContext.getInstance("TLS");
        ssl.init(null,new TrustManager[]{new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String a){throw new UnsupportedOperationException();}
            public void checkServerTrusted(X509Certificate[] c,String a) throws java.security.cert.CertificateException {
                try{StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(c[0].getEncoded()))hex.append(String.format(Locale.ROOT,"%02x",b));if(!hex.toString().equals(pin))throw new java.security.cert.CertificateException("wrong pinned host");c[0].checkValidity();}catch(Exception e){throw new java.security.cert.CertificateException(e);}
            }
        }},new SecureRandom());
        HttpsURLConnection http=(HttpsURLConnection)new URL("https://127.0.0.1:"+pairing.getInt("port")+"/v1/"+path).openConnection();
        http.setSSLSocketFactory(ssl.getSocketFactory());http.setHostnameVerifier((host,session)->host.equals("127.0.0.1"));
        http.setConnectTimeout(3000);http.setReadTimeout(5000);http.setRequestMethod("POST");http.setDoOutput(true);
        http.setRequestProperty("Content-Type","application/json");if(!token.isEmpty())http.setRequestProperty("Authorization","Bearer "+token);
        try{try(OutputStream out=http.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}int code=http.getResponseCode();InputStream in=code==200?http.getInputStream():http.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream input=in){byte[] buf=new byte[4096];int n;while((n=input.read(buf))!=-1)out.write(buf,0,n);}return new JSONObject(out.toString("UTF-8")).put("http",code);}finally{http.disconnect();}
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("keqi.test.loopback","true");Path folder=Files.createTempDirectory("keqi-lan-test-");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try(DesktopStore store=new DesktopStore(folder);LanHost lan=new LanHost(store)){
            store.transact(w->null);lan.start("127.0.0.1",folder,"temporary-test-password");JSONObject pairing=lan.pairing();String pin=pairing.getString("fingerprint");
            check(post(pairing,"head",new JSONObject(),"",pin).getInt("http")==403,"unpaired devices cannot read tasks");
            try{post(pairing,"head",new JSONObject(),"",new String(new char[64]).replace('\0','0'));throw new AssertionError("wrong certificate accepted");}catch(SSLException expected){checks++;}
            Workspace phone=new Workspace(new JSONObject());phone.join(pairing.getString("workspace"));JSONObject request=new JSONObject().put("device",phone.device()).put("name","Anonymous test phone").put("secret",pairing.getString("secret"));
            check(post(pairing,"pair",new JSONObject(request.toString()).put("secret","incorrect-code"),"",pin).getInt("http")==403,"wrong pairing code rejected");
            JSONObject pending=post(pairing,"pair",request,"",pin);check(pending.getBoolean("pending")&&!pending.has("token"),"pair waits for explicit computer approval");
            lan.approve(pending.getString("request"),true);request.put("request",pending.getString("request"));String token=post(pairing,"pair",request,"",pin).getString("token");
            new TaskService(phone,false).command("createTask",new JSONObject().put("title","Offline phone task"));JSONArray ops=phone.pending();JSONObject upload=new JSONObject().put("operations",ops).put("cursor",-1);
            JSONObject first=post(pairing,"exchange",upload,token,pin);check(first.getInt("http")==200&&first.getJSONArray("entities").length()==1,"authenticated upload commits and returns tasks");long revision=first.getLong("revision");check(post(pairing,"exchange",upload,token,pin).getLong("revision")==revision,"retry after lost reply is idempotent");
            JSONObject corrupt=new JSONObject(ops.getJSONObject(0).toString()).put("counter",999);check(post(pairing,"exchange",new JSONObject().put("operations",new JSONArray().put(corrupt)),token,pin).getInt("http")==400,"invalid operation rejected");
            check(store.transact(w->w.list("manual").length())==1,"rejected upload leaves database unchanged");
            try{store.transact(w->{w.create(new JSONObject().put("title","must roll back"),true);throw new IOException("simulated write failure");});}catch(IOException expected){checks++;}
            check(store.transact(w->w.list("manual").length())==1,"write failure rolls back content and revision together");
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            Future<?> writer=pool.submit(()->{try{store.transact(w->{w.create(new JSONObject().put("title","delayed transaction"),true);entered.countDown();release.await(3,TimeUnit.SECONDS);return null;});}catch(Exception e){throw new RuntimeException(e);}});
            entered.await(3,TimeUnit.SECONDS);Future<Long> reader=pool.submit(()->store.transact(w->w.revision()));check(!reader.isDone(),"reader cannot observe a half-committed revision");release.countDown();writer.get();check(reader.get()==revision+1,"reader receives the committed revision");
            lan.revoke(phone.device());check(post(pairing,"head",new JSONObject(),token,pin).getInt("http")==403,"revoked token immediately loses read access");
        }finally{pool.shutdownNow();try(java.util.stream.Stream<Path> files=Files.walk(folder)){for(Path file:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(file);}}
        System.out.println("HTTPS / SQLite sync checks passed: "+checks);
    }
}
