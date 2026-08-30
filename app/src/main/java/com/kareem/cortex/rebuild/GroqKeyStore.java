package com.kareem.cortex.rebuild;

import android.content.Context;

public final class GroqKeyStore {
    private static final String NAME = "groq_api_key";
    private GroqKeyStore() {}
    public static boolean has(Context c) { return SecureApiKeyStore.has(c, NAME); }
    public static String get(Context c) { return SecureApiKeyStore.get(c, NAME); }
    public static void save(Context c, String key) throws Exception { SecureApiKeyStore.save(c, NAME, key); }
    public static void clear(Context c) { SecureApiKeyStore.clear(c, NAME); }
}
