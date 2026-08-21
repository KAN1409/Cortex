package com.kareem.cortex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Direct Groq Whisper ASR client. The user's API key stays encrypted on-device. */
public final class CloudAudioTranscriber {
    public interface Callback {
        void ok(TranscriptResult result);
        void fail(Exception error);
    }

    public static final class RetryableException extends Exception {
        public RetryableException(String message) { super(message); }
        public RetryableException(String message, Throwable cause) { super(message, cause); }
    }

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String MODEL = "whisper-large-v3";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private CloudAudioTranscriber() {}

    public static void transcribe(Context context, File audioFile, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                callback.ok(transcribeBlocking(app, audioFile));
            } catch (Exception e) {
                callback.fail(e);
            }
        });
    }

    static TranscriptResult transcribeBlocking(Context context, File audioFile) throws Exception {
        if (audioFile == null || !audioFile.exists() || !audioFile.isFile()) {
            throw new IllegalArgumentException("Audio file is missing");
        }
        if (audioFile.length() <= 0) {
            throw new IllegalArgumentException("Audio file is empty");
        }
        if (audioFile.length() > 25L * 1024L * 1024L) {
            throw new IllegalArgumentException("Audio file exceeds Groq free-tier 25 MB limit");
        }

        String apiKey = GroqKeyStore.load(context);
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("Groq API key is not configured. Open Cortex ASR settings first.");
        }
        return postAudio(audioFile, apiKey);
    }

    private static TranscriptResult postAudio(File audioFile, String apiKey) throws Exception {
        HttpURLConnection connection = null;
        String boundary = "----CortexGroqVoice" + UUID.randomUUID().toString().replace("-", "");
        try {
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(180_000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("User-Agent", "CortexPrime/1.0.23 Android");

            try (OutputStream raw = connection.getOutputStream()) {
                writeTextPart(raw, boundary, "model", MODEL);
                writeTextPart(raw, boundary, "prompt",
                        "Egyptian Arabic mixed naturally with English words and technical terms. " +
                        "Keep Arabic speech in Arabic script and English terms in Latin letters. " +
                        "Cortex Prime, transcription, ASR, Android, code-switching.");
                writeTextPart(raw, boundary, "response_format", "verbose_json");
                writeTextPart(raw, boundary, "timestamp_granularities[]", "segment");
                writeTextPart(raw, boundary, "temperature", "0");
                // Deliberately do not force a single language. The recording can switch Arabic <-> English.
                writeFilePart(raw, boundary, "file", audioFile, mimeFor(audioFile));
                raw.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                raw.flush();
            }

            int code = connection.getResponseCode();
            String body = readBody(code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (code < 200 || code >= 300) {
                String detail = groqError(body);
                if (code == 401 || code == 403) {
                    throw new IllegalStateException("Groq API key rejected (HTTP " + code + "). Open Cortex ASR settings and replace the key.");
                }
                if (code == 413) {
                    throw new IllegalStateException("Groq rejected this audio because the upload is too large for the current plan.");
                }
                String message = "Groq transcription HTTP " + code + (detail.isEmpty() ? "" : ": " + detail);
                if (code == 408 || code == 425 || code == 429 || code >= 500) {
                    throw new RetryableException(message);
                }
                throw new IllegalStateException(message);
            }

            JSONObject json = new JSONObject(body);
            String text = json.optString("text", "").trim();
            if (text.isEmpty()) throw new IllegalStateException("Groq Whisper returned no text");

            TranscriptResult result = new TranscriptResult();
            result.text = text;
            result.engine = "groq_whisper_large_v3";
            result.version = "groq-direct-v1";
            String language = json.optString("language", "").trim();
            result.language = language.isEmpty() ? "ar+en-auto" : language + "+code-switch-auto";
            result.durationMs = Math.max(0L, Math.round(json.optDouble("duration", 0.0) * 1000.0));

            JSONArray segments = json.optJSONArray("segments");
            if (segments != null) {
                for (int i = 0; i < segments.length(); i++) {
                    JSONObject s = segments.optJSONObject(i);
                    if (s == null) continue;
                    String segmentText = s.optString("text", "").trim();
                    if (segmentText.isEmpty()) continue;
                    long startMs = Math.max(0L, Math.round(s.optDouble("start", 0.0) * 1000.0));
                    long endMs = Math.max(startMs, Math.round(s.optDouble("end", startMs / 1000.0) * 1000.0));
                    double avgLogProb = s.optDouble("avg_logprob", Double.NaN);
                    float confidence = 0.0f;
                    if (!Double.isNaN(avgLogProb)) {
                        confidence = (float) Math.max(0.0, Math.min(1.0, Math.exp(avgLogProb)));
                    }
                    result.segments.add(new TranscriptResult.Segment(startMs, endMs, segmentText, confidence));
                }
            }
            return result;
        } catch (RetryableException e) {
            throw e;
        } catch (java.net.SocketTimeoutException | java.net.ConnectException | java.net.UnknownHostException e) {
            throw new RetryableException("Groq transcription unavailable: " + e.getMessage(), e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String groqError(String body) {
        if (body == null || body.trim().isEmpty()) return "";
        try {
            JSONObject json = new JSONObject(body);
            JSONObject error = json.optJSONObject("error");
            if (error != null) return error.optString("message", "").trim();
            return json.optString("message", "").trim();
        } catch (Exception ignored) {
            String clean = body.replace('\n', ' ').replace('\r', ' ').trim();
            return clean.length() > 240 ? clean.substring(0, 240) + "…" : clean;
        }
    }

    private static void writeTextPart(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(OutputStream out, String boundary, String name, File file, String mime) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + safeFilename(file.getName()) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String readBody(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        }
    }

    private static String safeFilename(String name) {
        return name == null ? "cortex.wav" : name.replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private static String mimeFor(File file) {
        String n = file.getName().toLowerCase(Locale.US);
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".ogg")) return "audio/ogg";
        if (n.endsWith(".webm")) return "audio/webm";
        return "application/octet-stream";
    }
}
