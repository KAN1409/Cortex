package com.kareem.cortex.rebuild;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Port of the proven Cortex Gemini voice path with a hard network deadline. */
public final class GeminiAudioTranscriber {
    private static final String MODEL = "gemini-3.6-flash";
    public static final long MAX_SAFE_INLINE_BYTES = 8_000_000L;
    private static final long HARD_TIMEOUT_MS = 45_000L;
    private static final String PROMPT = "Transcribe this audio verbatim. Speech may switch between Egyptian Arabic and English. Preserve Egyptian Arabic as spoken, preserve every spoken English word in Latin letters, and do not translate, summarize, paraphrase, or convert Egyptian Arabic to Modern Standard Arabic. Return only the transcript text.";
    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cortex-gemini-timeout"); t.setDaemon(true); return t;
    });

    private GeminiAudioTranscriber() {}

    public static TranscriptResult transcribe(Context context, File audio) throws Exception {
        if (audio == null || !audio.exists() || audio.length() == 0) throw new IllegalArgumentException("Missing audio file");
        if (audio.length() > MAX_SAFE_INLINE_BYTES) throw new IOException("Gemini inline audio skipped for memory safety");
        String key = GeminiKeyStore.get(context);
        if (key.isEmpty()) throw new IllegalStateException("Gemini API key not configured");

        byte[] bytes = readBytesBounded(audio, MAX_SAFE_INLINE_BYTES);
        String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        bytes = null;
        JSONObject inline = new JSONObject().put("mimeType", mime(audio)).put("data", b64);
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text", PROMPT))
                .put(new JSONObject().put("inlineData", inline));
        JSONArray contents = new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts));
        JSONObject cfg = new JSONObject().put("temperature", 0).put("maxOutputTokens", 2048);
        JSONObject req = new JSONObject().put("contents", contents).put("generationConfig", cfg);

        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL +
                ":generateContent?key=" + java.net.URLEncoder.encode(key, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        AtomicBoolean forcedTimeout = new AtomicBoolean(false);
        ScheduledFuture<?> guard = TIMEOUTS.schedule(() -> {
            forcedTimeout.set(true);
            try { c.disconnect(); } catch (Throwable ignored) {}
        }, HARD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(12_000);
            c.setReadTimeout(40_000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(req.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            String body = read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
            if (forcedTimeout.get()) throw new SocketTimeoutException("Gemini ASR hard timeout");
            if (code < 200 || code >= 300) throw new IOException("Gemini ASR HTTP " + code + ": " + compact(body));

            JSONObject root = new JSONObject(body);
            String text = extractText(root).trim()
                    .replaceAll("^```(?:text)?\\s*", "")
                    .replaceAll("```$", "")
                    .replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) throw new IOException("Gemini returned an empty transcript");

            long duration = duration(audio);
            TranscriptResult r = new TranscriptResult();
            r.text = text;
            r.rawTranscript = text;
            r.providerMergedTranscript = text;
            r.engine = MODEL + "+audio";
            r.version = "gemini-audio-v4-safe-inline-hard-timeout";
            r.durationMs = duration;
            r.processedDurationMs = duration;
            r.coverage = duration > 0 ? 1.0 : 0.0;
            r.language = detectLanguage(text);
            r.rawProviderResponse = body;
            return r;
        } catch (IOException e) {
            if (forcedTimeout.get()) throw new SocketTimeoutException("Gemini ASR hard timeout");
            throw e;
        } finally {
            guard.cancel(false);
            try { c.disconnect(); } catch (Throwable ignored) {}
        }
    }

    private static String extractText(JSONObject root) {
        JSONArray cs = root.optJSONArray("candidates");
        if (cs == null || cs.length() == 0) return "";
        JSONObject candidate = cs.optJSONObject(0);
        JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            String t = p == null ? "" : p.optString("text", "");
            if (!t.isEmpty()) { if (b.length() > 0) b.append(' '); b.append(t); }
        }
        return b.toString();
    }

    private static byte[] readBytesBounded(File f, long max) throws Exception {
        long len = f.length();
        if (len <= 0 || len > max) throw new IOException("Audio file outside safe inline size");
        try (InputStream in = new BufferedInputStream(new FileInputStream(f));
             ByteArrayOutputStream b = new ByteArrayOutputStream((int)Math.min(len, max))) {
            byte[] buf = new byte[65_536]; long total = 0;
            for (int n; (n = in.read(buf)) != -1;) {
                total += n;
                if (total > max) throw new IOException("Audio grew beyond safe inline size while reading");
                b.write(buf, 0, n);
            }
            return b.toByteArray();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream x = in; ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = x.read(buf)) != -1;) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    private static long duration(File f) {
        MediaMetadataRetriever m = null;
        try {
            m = new MediaMetadataRetriever(); m.setDataSource(f.getAbsolutePath());
            String d = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? 0 : Long.parseLong(d);
        } catch (Throwable e) { return 0; }
        finally { if (m != null) try { m.release(); } catch (Throwable ignored) {} }
    }

    private static String mime(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".m4a") || n.endsWith(".mp4")) return "audio/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".ogg")) return "audio/ogg";
        return "audio/wav";
    }

    private static String detectLanguage(String s) {
        int ar = 0, la = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x0600 && ch <= 0x06ff) ar++;
            else if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) la++;
        }
        if (ar > 0 && la > 0) return "Arabic+English";
        if (ar > 0) return "Arabic";
        if (la > 0) return "English";
        return "auto";
    }

    private static String compact(String s) {
        if (s == null) return "";
        String x = s.replaceAll("\\s+", " ").trim();
        return x.length() > 500 ? x.substring(0, 500) + "…" : x;
    }
}
