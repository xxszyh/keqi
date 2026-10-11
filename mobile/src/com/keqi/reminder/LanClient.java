package com.keqi.reminder;

import android.content.Context;
import android.net.*;
import android.net.nsd.*;
import org.json.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native LAN transport. Certificate pinning is scoped to this Adapter only. */
public final class LanClient {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final String SECRET="lan.device";
    public static final String SERVICE="_keqi-sync._tcp.";
    public static JSONObject parse(String text) throws Exception {
        JSONObject pairing=new JSONObject(text);if(pairing.getInt("protocol")!=Workspace.PROTOCOL||!pairing.getString("workspace").matches("[a-fA-F0-9-]{36}")||!pairing.getString("fingerprint").matches("[a-f0-9]{64}")||pairing.getString("secret").length()<22||pairing.getString("secret").length()>150)throw new IllegalArgumentException("配对信息无效，请扫描电脑上的课期二维码");
        validateAddress(pairing.getString("address"),pairing.getInt("port"));return pairing;
    }
    private static InetAddress validateAddress(String address,int port) throws Exception {
        if(!address.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")||port<1||port>65535)throw new IllegalArgumentException("请使用电脑的局域网 IPv4 地址和端口");InetAddress ip=InetAddress.getByName(address);if(!LocalAddress.allowed(ip))throw new IllegalArgumentException("连接地址不属于局域网");return ip;
    }
    private static Network localNetwork(Context context) {
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);for(Network n:manager.getAllNetworks()){NetworkCapabilities c=manager.getNetworkCapabilities(n);if(c!=null&&(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return n;}return null;
    }
    private static String fingerprint(X509Certificate certificate) throws Exception {byte[] digest=MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
    public static JSONObject request(Context context,JSONObject connection,String path,JSONObject body,String token) throws Exception {
        validateAddress(connection.getString("address"),connection.getInt("port"));String pin=connection.getString("fingerprint");Network network=localNetwork(context);if(network==null)throw new IOException("等待局域网连接，请连接电脑所在 Wi-Fi");
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,new TrustManager[]{new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a) throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException("Client not supported");}public void checkServerTrusted(X509Certificate[] certs,String auth) throws java.security.cert.CertificateException{try{if(certs.length==0||!MessageDigest.isEqual(pin.getBytes("US-ASCII"),fingerprint(certs[0]).getBytes("US-ASCII")))throw new java.security.cert.CertificateException("配对电脑身份不匹配");certs[0].checkValidity();}catch(Exception e){throw new java.security.cert.CertificateException("配对电脑证书无法核对",e);}}}},new SecureRandom());
        URL url=new URL("https://"+connection.getString("address")+":"+connection.getInt("port")+"/v1/"+path);HttpsURLConnection http=(HttpsURLConnection)network.openConnection(url);http.setSSLSocketFactory(ssl.getSocketFactory());
        // The dynamic host's exact certificate is the enrolled identity, independent of DHCP IP.
        http.setHostnameVerifier((host,session)->{try{return fingerprint((X509Certificate)session.getPeerCertificates()[0]).equals(pin);}catch(Exception e){return false;}});http.setInstanceFollowRedirects(false);http.setConnectTimeout(7000);http.setReadTimeout(15000);http.setRequestMethod("POST");http.setDoOutput(true);http.setRequestProperty("Content-Type","application/json;charset=UTF-8");if(!token.isEmpty())http.setRequestProperty("Authorization","Bearer "+token);
        try{byte[] payload=body.toString().getBytes("UTF-8");if(payload.length>2000000)throw new IllegalArgumentException("同步批次过大");try(OutputStream out=http.getOutputStream()){out.write(payload);}int status=http.getResponseCode();InputStream input=status==200?http.getInputStream():http.getErrorStream();if(input==null)throw new IOException("电脑未返回同步结果");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=input){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>16000000)throw new IOException("同步数据过大，请先导出备份");}}JSONObject result=new JSONObject(bytes.toString("UTF-8"));if(status!=200)throw new IllegalArgumentException(result.optString("error","同步暂未成功"));return result;}finally{http.disconnect();}
    }
    public static void pair(Context context,String text) throws Exception {
        JSONObject pairing=parse(text),body;try(Store store=new Store(context)){Workspace w=Collaboration.load(store);JSONObject prior=store.meta("lan.connection");String previous=store.meta("lan.history").optString("workspace");if(!previous.isEmpty()&&!previous.equals(pairing.getString("workspace")))throw new IllegalArgumentException("此手机已有另一个工作区的数据，请保留备份；首版暂不支持直接切换工作区");if(prior.has("workspace")&&!prior.getString("workspace").equals(pairing.getString("workspace")))throw new IllegalArgumentException("请先导出数据并解除旧电脑关联，再连接其他工作区");body=new JSONObject().put("secret",pairing.getString("secret")).put("device",w.device()).put("name",android.os.Build.MODEL);}
        JSONObject result=request(context,pairing,"pair",body,"");long timeout=System.currentTimeMillis()+90000;
        while(result.optBoolean("pending")&&System.currentTimeMillis()<timeout){Thread.sleep(1000);body.put("request",result.getString("request"));result=request(context,pairing,"pair",body,"");}
        if(!result.has("token"))throw new IllegalArgumentException("请在电脑确认手机连接后重新配对");String oldToken=new Secrets(context).get(SECRET);new Secrets(context).put(SECRET,result.getString("token"));pairing.remove("secret");pairing.remove("expires");
        try(Store store=new Store(context)){SQLiteJoin(store,pairing);}catch(Exception e){new Secrets(context).put(SECRET,oldToken);throw e;}
        sync(context);
    }
    private static void SQLiteJoin(Store store,JSONObject pairing) throws Exception {
        Collaboration.transact(store,w->{JSONObject prior=store.meta("lan.connection");if(prior.has("workspace")&&!prior.getString("workspace").equals(pairing.getString("workspace")))throw new IllegalArgumentException("请先导出数据并解除旧电脑关联，再连接其他工作区");
            JSONObject local=store.meta("collaborationLocal");w.join(pairing.getString("workspace"));JSONObject migrated=new JSONObject();Iterator<String> keys=local.keys();while(keys.hasNext()){String key=keys.next();migrated.put(w.canonical(key),local.get(key));}store.putMeta("collaborationLocal",migrated);store.putMeta("lan.connection",pairing);store.putMeta("lan.history",new JSONObject().put("workspace",pairing.getString("workspace")));store.putMeta("lan.retry",new JSONObject());return null;});
    }
    private static void discover(Context context,JSONObject connection) throws Exception {
        CountDownLatch found=new CountDownLatch(1);NsdManager nsd=context.getSystemService(NsdManager.class);String workspace=connection.getString("workspace");
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String type){}
            public void onDiscoveryStopped(String type){}
            public void onStartDiscoveryFailed(String type,int error){found.countDown();}
            public void onStopDiscoveryFailed(String type,int error){}
            public void onServiceLost(NsdServiceInfo info){}
            public void onServiceFound(NsdServiceInfo info){if(!info.getServiceName().startsWith("Keqi-"+workspace.substring(0,8)))return;nsd.resolveService(info,new NsdManager.ResolveListener(){public void onResolveFailed(NsdServiceInfo s,int e){}public void onServiceResolved(NsdServiceInfo s){try{String a=s.getHost().getHostAddress();validateAddress(a,s.getPort());connection.put("address",a).put("port",s.getPort());found.countDown();}catch(Exception ignored){}}});}
        };
        try{nsd.discoverServices(SERVICE,NsdManager.PROTOCOL_DNS_SD,listener);found.await(4,TimeUnit.SECONDS);}finally{try{nsd.stopServiceDiscovery(listener);}catch(Exception ignored){}}
    }
    public static void sync(Context context) {
        if(Thread.currentThread().isInterrupted()||!BUSY.compareAndSet(false,true))return;
        try{JSONObject connection;try(Store store=new Store(context)){connection=store.meta("lan.connection");if(!connection.has("fingerprint"))return;JSONObject retry=store.meta("lan.retry");if(retry.optBoolean("needsAction")||retry.optLong("at")>System.currentTimeMillis())return;}
            String token=new Secrets(context).get(SECRET);if(token.isEmpty())throw new IllegalArgumentException("需要重新配对电脑");Workspace snapshot;try(Store store=new Store(context)){snapshot=Collaboration.load(store);}
            JSONObject head;try{head=request(context,connection,"head",new JSONObject(),token);}catch(IOException e){discover(context,connection);head=request(context,connection,"head",new JSONObject(),token);}
            JSONArray observations=snapshot.observations();JSONArray pending=snapshot.pending();int offset=0;
            do{JSONArray batch=new JSONArray();int batchBytes=0;for(;offset<pending.length()&&batch.length()<128;offset++){JSONObject op=pending.getJSONObject(offset);int bytes=op.toString().getBytes("UTF-8").length;if(batchBytes+bytes>1000000)break;batch.put(op);batchBytes+=bytes;}JSONObject body=new JSONObject().put("operations",batch).put("observations",observations).put("cursor",snapshot.save().optLong("cursor",-1)).put("epoch",snapshot.save().optString("remoteEpoch"));JSONObject received=request(context,connection,"exchange",body,token);
                try(Store store=new Store(context)){final JSONArray sent=observations;Collaboration.transact(store,w->{w.observationsAcknowledged(sent);w.receive(received);return null;});snapshot=Collaboration.load(store);store.putMeta("lan.connection",connection);store.putMeta("lan.retry",new JSONObject());store.putMeta("lan.status",new JSONObject().put("lastSuccess",System.currentTimeMillis()).put("message","局域网同步完成"));}observations=new JSONArray();
            }while(offset<pending.length());Notifications.rebuild(context);
        }catch(Exception e){try(Store store=new Store(context)){JSONObject retry=store.meta("lan.retry");int attempts=retry.optInt("attempts")+1;store.putMeta("lan.retry",new JSONObject().put("attempts",attempts).put("at",System.currentTimeMillis()+Math.min(900000L,30000L*(1L<<Math.min(attempts-1,5)))+new java.security.SecureRandom().nextInt(5000)).put("needsAction",e instanceof IllegalArgumentException||e instanceof SSLException));String message=e instanceof IllegalArgumentException?e.getMessage():e instanceof SSLException?"电脑身份无法核对，请检查配对或重新扫码":"等待电脑上线或局域网连接";JSONObject state=store.meta("lan.status");state.put("message",message);store.putMeta("lan.status",state);}catch(Exception ignored){}}finally{BUSY.set(false);}
    }
    public static void unlink(Context context) throws Exception {new Secrets(context).remove(SECRET);try(Store store=new Store(context)){store.putMeta("lan.connection",new JSONObject());store.putMeta("lan.status",new JSONObject());}}
}
