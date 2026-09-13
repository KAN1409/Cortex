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

/** Stores the optional Gmail app password encrypted by Android Keystore. */
public final class GmailAppPasswordStore {
    private static final String PREFS="gmail_bridge_secret";
    private static final String K_ALIAS="cortex_gmail_bridge_app_password_v1";
    private static final String K_CT="ciphertext";
    private static final String K_IV="iv";
    private static final String K_EMAIL="email";

    private GmailAppPasswordStore() {}

    public static boolean has(Context context, String email) {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        return !p.getString(K_CT,"").isEmpty() && email!=null && email.equalsIgnoreCase(p.getString(K_EMAIL,""));
    }

    public static void save(Context context,String email,String appPassword) throws Exception {
        if(email==null||email.trim().isEmpty()) throw new IllegalArgumentException("email required");
        String normalized=appPassword==null?"":appPassword.replace(" ","").trim();
        if(normalized.length()!=16) throw new IllegalArgumentException("Google app password must be 16 characters");
        SecretKey key=getOrCreateKey();
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,key);
        byte[] encrypted=c.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString(K_EMAIL,email.trim())
                .putString(K_CT,Base64.encodeToString(encrypted,Base64.NO_WRAP))
                .putString(K_IV,Base64.encodeToString(c.getIV(),Base64.NO_WRAP))
                .apply();
    }

    public static String load(Context context,String email) throws Exception {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!has(context,email)) throw new IllegalStateException("app password not configured for selected Gmail account");
        byte[] encrypted=Base64.decode(p.getString(K_CT,""),Base64.NO_WRAP);
        byte[] iv=Base64.decode(p.getString(K_IV,""),Base64.NO_WRAP);
        SecretKey key=getOrCreateKey();
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
        return new String(c.doFinal(encrypted),StandardCharsets.UTF_8);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry existing=ks.getEntry(K_ALIAS,null);
        if(existing instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry)existing).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(K_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }
}
