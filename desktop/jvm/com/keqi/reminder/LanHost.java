package com.keqi.reminder;

import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import javax.jmdns.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Paired-device HTTPS transport; only explicitly selected local interfaces are bound. */
public final class LanHost implements AutoCloseable {
    public static final String SERVICE="_keqi-sync._tcp.local.";
    private final DesktopStore store;
    private HttpsServer server;
    private JmDNS discovery;
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private final Map<String,long[]> attempts=new LinkedHashMap<>();
    private final Map<String,JSONObject> requests=new LinkedHashMap<>();
    private String secret="",fingerprint="",address="";
    private long expires;
    private final SecureRandom random=new SecureRandom();
    public LanHost(DesktopStore store){this.store=store;}
    public static String digest(String text) throws Exception {return hex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();}
    private String random(int size){byte[] bytes=new byte[size];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static JSONArray interfaces() throws Exception {
        JSONArray out=new JSONArray();Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){NetworkInterface n=interfaces.nextElement();if(!n.isUp()||n.isLoopback()||n.isVirtual())continue;Enumeration<InetAddress> addresses=n.getInetAddresses();while(addresses.hasMoreElements()){InetAddress a=addresses.nextElement();if(LocalAddress.allowed(a))out.put(new JSONObject().put("address",a.getHostAddress()).put("name",n.getDisplayName()));}}return out;
    }
    public synchronized void start(String selected,Path directory,String password) throws Exception {
        if(selected.equals(address)&&server!=null)return;closeTransport();InetAddress bind=InetAddress.getByName(selected);boolean found=false;JSONArray choices=interfaces();for(int i=0;i<choices.length();i++)if(choices.getJSONObject(i).getString("address").equals(selected))found=true;
        if(!found&&!Boolean.getBoolean("keqi.test.loopback"))throw new IllegalArgumentException("请选择本机局域网网卡");
        Path key=directory.resolve("host.p12");if(!Files.exists(key)){
            String tool=Paths.get(System.getProperty("java.home"),"bin","keytool.exe").toString();ProcessBuilder pb=new ProcessBuilder(tool,"-genkeypair","-alias","keqi","-keyalg","RSA","-keysize","2048","-validity","3650","-dname","CN=Keqi Local Sync","-storetype","PKCS12","-keystore",key.toString(),"-storepass:env","KEQI_TLS_PASS","-keypass:env","KEQI_TLS_PASS");pb.environment().put("KEQI_TLS_PASS",password);pb.redirectErrorStream(true);Process process=pb.start();try(InputStream in=process.getInputStream()){while(in.read()!=-1){/* Keytool output must not enter the UI protocol. */}}if(process.waitFor()!=0)throw new IOException("无法生成局域网连接证书");
        }
        KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(key)){keys.load(in,password.toCharArray());}fingerprint=hex(MessageDigest.getInstance("SHA-256").digest(keys.getCertificate("keqi").getEncoded()));
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,password.toCharArray());SSLContext tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),null,random);
        int preferred=store.meta("lan.settings").optInt("port",0);try{server=HttpsServer.create(new InetSocketAddress(bind,preferred),12);}catch(BindException e){server=HttpsServer.create(new InetSocketAddress(bind,0),12);}server.setHttpsConfigurator(new HttpsConfigurator(tls));server.setExecutor(workers);server.createContext("/v1/",this::handle);server.start();address=selected;
        store.meta("lan.settings",new JSONObject().put("address",selected).put("port",server.getAddress().getPort()));
        try{discovery=JmDNS.create(bind,"Keqi");Workspace w=new Workspace(store.meta("workspace"));discovery.registerService(ServiceInfo.create(SERVICE,"Keqi-"+w.id().substring(0,8),server.getAddress().getPort(),0,0,Collections.singletonMap("workspace",w.id())));}catch(IOException e){if(discovery!=null)discovery.close();discovery=null;}
    }
    public synchronized JSONObject pairing() throws Exception {
        if(server==null)throw new IllegalArgumentException("请先选择局域网连接地址");secret=random(24);expires=System.currentTimeMillis()+300000;requests.clear();Workspace w=new Workspace(store.meta("workspace"));
        return new JSONObject().put("protocol",Workspace.PROTOCOL).put("workspace",w.id()).put("host",w.id()).put("address",address).put("port",server.getAddress().getPort()).put("fingerprint",fingerprint).put("secret",secret).put("expires",expires);
    }
    public synchronized JSONObject status() throws Exception {
        JSONArray pending=new JSONArray();long now=System.currentTimeMillis();for(JSONObject request:requests.values())if(!request.has("token")&&request.optLong("expires")>now)pending.put(new JSONObject().put("request",request.getString("request")).put("name",request.getString("name")));
        JSONArray devices=new JSONArray();JSONObject records=store.meta("lan.devices");Iterator<String> ids=records.keys();while(ids.hasNext()){String id=ids.next();JSONObject d=records.getJSONObject(id);devices.put(new JSONObject().put("id",id).put("name",d.optString("name")).put("revoked",d.optBoolean("revoked")));}
        return new JSONObject().put("running",server!=null).put("address",address).put("port",server==null?0:server.getAddress().getPort()).put("discovery",discovery!=null).put("interfaces",interfaces()).put("requests",pending).put("devices",devices);
    }
    public synchronized void approve(String requestId,boolean accepted) throws Exception {
        JSONObject request=requests.get(requestId);if(request==null||request.optLong("expires")<=System.currentTimeMillis())throw new IllegalArgumentException("配对申请已失效，请重新扫码");
        if(!accepted){requests.remove(requestId);return;}String token=random(32),device=request.getString("device");JSONObject records=store.meta("lan.devices");records.put(device,new JSONObject().put("name",request.getString("name")).put("hash",digest(token)).put("revoked",false));store.meta("lan.devices",records);request.put("token",token);secret="";
    }
    public synchronized void revoke(String device) throws Exception {JSONObject records=store.meta("lan.devices");if(records.has(device)){records.getJSONObject(device).put("revoked",true);store.meta("lan.devices",records);}}
    private String authorize(String token) throws Exception {if(token==null||token.length()>200)throw new SecurityException("需要配对");String hash=digest(token);JSONObject records=store.meta("lan.devices");Iterator<String> ids=records.keys();while(ids.hasNext()){String id=ids.next();JSONObject d=records.getJSONObject(id);if(!d.optBoolean("revoked")&&MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),d.optString("hash").getBytes(StandardCharsets.US_ASCII)))return id;}throw new SecurityException("设备授权已失效或被撤销");}
    private synchronized JSONObject pair(JSONObject body) throws Exception {
        long now=System.currentTimeMillis();String provided=body.getString("secret");if(now>expires)throw new SecurityException("配对已过期");
        String requestId=body.optString("request");if(!requestId.isEmpty()){JSONObject pending=requests.get(requestId);if(pending==null||!pending.optString("secret").equals(provided))throw new SecurityException("配对申请无效");if(pending.has("token")){requests.remove(requestId);return new JSONObject().put("token",pending.getString("token"));}return new JSONObject().put("pending",true).put("request",requestId);}
        if(secret.isEmpty()||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.US_ASCII),provided.getBytes(StandardCharsets.US_ASCII)))throw new SecurityException("配对码无效");
        if(requests.size()>=3)throw new SecurityException("配对申请过多，请重新生成二维码");String device=body.getString("device"),name=body.optString("name","Android");if(!device.matches("[a-fA-F0-9-]{36}")||name.length()>80)throw new IllegalArgumentException("设备资料无效");requestId=random(18);requests.put(requestId,new JSONObject().put("request",requestId).put("secret",provided).put("device",device).put("name",name).put("expires",expires));return new JSONObject().put("pending",true).put("request",requestId);
    }
    private synchronized void limitPair(String address) {
        long now=System.currentTimeMillis();long[] window=attempts.get(address);if(window==null||now-window[0]>60000){if(attempts.size()>=100)attempts.remove(attempts.keySet().iterator().next());window=new long[]{now,0};attempts.put(address,window);}if(++window[1]>120)throw new SecurityException("配对请求过于频繁，请稍后重试");
    }
    private void handle(HttpExchange request) throws IOException {
        int code=200;JSONObject result;try{
            if(!request.getRequestMethod().equals("POST"))throw new IllegalArgumentException("只接受结构化同步请求");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=request.getRequestBody()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>2000000)throw new IllegalArgumentException("同步批次过大");}}JSONObject body=new JSONObject(bytes.toString("UTF-8"));String path=request.getRequestURI().getPath();
            if(path.equals("/v1/pair")){limitPair(request.getRemoteAddress().getAddress().getHostAddress());result=pair(body);}
            else{
                String token=request.getRequestHeaders().getFirst("Authorization");if(token!=null&&token.startsWith("Bearer "))token=token.substring(7);final String auth=token;String device=authorize(auth);
                result=store.transact(w->{authorize(auth);if(path.equals("/v1/head"))return new JSONObject().put("epoch",w.epoch()).put("revision",w.revision());
                    if(path.equals("/v1/exchange")){JSONArray operations=body.optJSONArray("operations"),observations=body.optJSONArray("observations");if(operations!=null&&operations.length()>128||observations!=null&&observations.length()>1000)throw new IllegalArgumentException("同步批次过大");
                        if(operations!=null)for(int i=0;i<operations.length();i++){JSONObject op=operations.getJSONObject(i);if(!device.equals(op.getString("device")))throw new SecurityException("操作设备不匹配");w.apply(op,true);}
                        if(observations!=null)for(int i=0;i<observations.length();i++)w.acceptObservation(observations.getJSONObject(i),device);
                        String epoch=body.optString("epoch");long cursor=body.optLong("cursor",-1);if(!epoch.isEmpty()&&!epoch.equals(w.epoch()))throw new IllegalArgumentException("主机已恢复备份，请重新核对并配对");return w.snapshot(cursor);
                    }throw new IllegalArgumentException("同步入口不存在");});
            }
        }catch(SecurityException e){code=403;result=new JSONObject();try{result.put("error",e.getMessage());}catch(JSONException ignored){}}
        catch(Exception e){code=400;result=new JSONObject();try{result.put("error",e instanceof IllegalArgumentException?e.getMessage():"同步暂未提交，请稍后重试");}catch(JSONException ignored){}}
        byte[] content=result.toString().getBytes(StandardCharsets.UTF_8);request.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");request.getResponseHeaders().set("Cache-Control","no-store");request.sendResponseHeaders(code,content.length);try(OutputStream out=request.getResponseBody()){out.write(content);}finally{request.close();}
    }
    private synchronized void closeTransport() throws IOException {if(server!=null){server.stop(0);server=null;}if(discovery!=null){discovery.close();discovery=null;}address="";secret="";requests.clear();}
    public synchronized void close() throws IOException {closeTransport();workers.shutdownNow();}
}
