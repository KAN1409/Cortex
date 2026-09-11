package com.kareem.cortex;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Bidirectional transport between Cortex and the private ChatGPT teacher relay.
 *
 * No OpenAI model API is called here. Cortex uploads a compact layered Context Pack and pulls a
 * bounded Policy Pack. The teacher is connected to the FINAL JUDGMENT policy boundary: it can
 * teach how Cortex ranks and interrupts, but it cannot rewrite evidence/knowledge or execute.
 */
public final class CortexChatGptBridge {
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 12_000;
    private CortexChatGptBridge() {}

    public static boolean sync(Context context) throws Exception {
        Context app = context.getApplicationContext();
        if (!CortexChatGptBridgeConfig.enabled(app)) return false;
        String endpoint = CortexChatGptBridgeConfig.endpoint(app);
        String token = CortexChatGptBridgeConfig.token(app);
        VaultDb db = new VaultDb(app);
        try {
            JSONObject contextPack = buildContextPack(app, db);
            request("POST", endpoint + "/device/context", token, contextPack.toString());
            String response = request("GET", endpoint + "/device/policy", token, null);
            JSONObject root = new JSONObject(response);
            JSONObject policy = root.optJSONObject("policy");
            if (policy != null) CortexPersonalPolicy.save(app, policy);
            CortexChatGptBridgeConfig.record(app, true, "");
            return true;
        } catch (Exception e) {
            CortexChatGptBridgeConfig.record(app, false,
                    e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            throw e;
        } finally {
            try { db.close(); } catch (Throwable ignored) {}
        }
    }

    static JSONObject buildContextPack(Context app, VaultDb db) throws Exception {
        return CortexTeacherContextNormalizer.apply(CortexTeacherContext.build(app, db));
    }

    private static String request(String method, String url, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String text = read(in);
            if (code < 200 || code >= 300) {
                throw new IOException("Bridge HTTP " + code + (text.isEmpty() ? "" : " · " + clip(text, 240)));
            }
            return text;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] x = new byte[8192];
        int n, total = 0;
        while ((n = in.read(x)) > 0 && total < 1_000_000) {
            b.write(x, 0, n);
            total += n;
        }
        return b.toString(StandardCharsets.UTF_8.name());
    }

    private static String clip(String s, int n) {
        String x = safe(s).replaceAll("\\s+", " ").trim();
        return x.length() <= n ? x : x.substring(0, n) + "…";
    }
    private static String safe(String s) { return s == null ? "" : s; }
}
