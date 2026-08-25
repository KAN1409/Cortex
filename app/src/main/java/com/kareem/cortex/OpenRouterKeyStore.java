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

/** Encrypted OpenRouter API key storage backed by Android Keystore. */
public final class OpenRouterKeyStore {
    private static final String PREFS="cortex_secure";
    private static final String VALUE="openrouter_api_key";
    private static final String IV="openrouter_api_key_iv";
    private static final String ALIAS="cortex_openrouter_api_key";

    private OpenRouterKeyStore(){}

    public static boolean has(Context context){return !get(context).isEmpty();}

    public static String get(Context context){
        try{
            SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            String data=p.getString(VALUE,"");
            String iv=p.getString(IV,"");
            if(data==null||data.isEmpty()||iv==null||iv.isEmpty())return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),StandardCharsets.UTF_8).trim();
        }catch(Exception e){clear(context);return "";}
    }

    public static void save(Context context,String apiKey)throws Exception{
        String value=apiKey==null?"":apiKey.trim();
        if(value.isEmpty()){clear(context);return;}
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString(VALUE,Base64.encodeToString(encrypted,Base64.NO_WRAP))
                .putString(IV,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).apply();
    }

    public static void clear(Context context){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(VALUE).remove(IV).apply();
    }

    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        java.security.Key existing=ks.getKey(ALIAS,null);
        if(existing instanceof SecretKey)return (SecretKey)existing;
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }
}
