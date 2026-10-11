package com.keqi.reminder;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Session snapshots encrypted with a key bound to this Android installation. */
public final class Secrets {
    private final Context context;
    public Secrets(Context context){this.context=context.getApplicationContext();}
    private static synchronized javax.crypto.SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("keqi.sessions")){
            KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("keqi.sessions",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey("keqi.sessions",null);
    }
    public String protect(String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes("UTF-8")),Base64.NO_WRAP);
    }
    public String unprotect(String value) throws Exception {
        if(value.isEmpty())return "";
        String[] parts=value.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),"UTF-8");
    }
    public void put(String name,String value) throws Exception {context.getSharedPreferences("session_vault",0).edit().putString(name,protect(value)).commit();}
    public String get(String name) throws Exception {return unprotect(context.getSharedPreferences("session_vault",0).getString(name,""));}
    public void remove(String name){context.getSharedPreferences("session_vault",0).edit().remove(name).commit();}
}
