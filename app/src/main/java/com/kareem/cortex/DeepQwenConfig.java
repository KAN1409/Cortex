package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Optional self-hosted Qwen3.5-4B vLLM endpoint. Disabled until explicitly configured. */
public final class DeepQwenConfig {
    public static final String MODEL="Qwen/Qwen3.5-4B";
    private static final String PREF="cortex_deep_qwen";
    private static final String K_ENABLED="enabled",K_BASE_URL="base_url",K_TOKEN="bearer_token_enc",K_IV="bearer_token_iv";
    private static final String KEY_ALIAS="cortex_deep_qwen_token";
    private DeepQwenConfig(){}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public static boolean enabled(Context c){return c!=null&&p(c).getBoolean(K_ENABLED,false)&&!baseUrl(c).isEmpty();}
    public static String baseUrl(Context c){return c==null?"":cleanUrl(p(c).getString(K_BASE_URL,""));}
    public static boolean tokenConfigured(Context c){return c!=null&&!p(c).getString(K_TOKEN,"").isEmpty()&&!p(c).getString(K_IV,"").isEmpty();}
    public static String bearerToken(Context c){
        if(c==null)return"";try{String data=p(c).getString(K_TOKEN,""),iv=p(c).getString(K_IV,"");if(data==null||data.isEmpty()||iv==null||iv.isEmpty())return"";Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),StandardCharsets.UTF_8).trim();}catch(Throwable e){clearToken(c);return"";}
    }

    public static void save(Context c,boolean enabled,String baseUrl,String bearerToken)throws Exception{
        if(c==null)return;SharedPreferences.Editor e=p(c).edit().putBoolean(K_ENABLED,enabled).putString(K_BASE_URL,cleanUrl(baseUrl));String token=clean(bearerToken);
        if(token.isEmpty()){e.remove(K_TOKEN).remove(K_IV).apply();return;}
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));e.putString(K_TOKEN,Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString(K_IV,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).apply();
    }
    public static void clearToken(Context c){if(c!=null)p(c).edit().remove(K_TOKEN).remove(K_IV).apply();}

    private static SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);java.security.Key existing=ks.getKey(KEY_ALIAS,null);if(existing instanceof SecretKey)return(SecretKey)existing;KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return kg.generateKey();}
    private static String cleanUrl(String s){String x=clean(s);while(x.endsWith("/"))x=x.substring(0,x.length()-1);return x;}
    private static String clean(String s){return s==null?"":s.trim();}
}
