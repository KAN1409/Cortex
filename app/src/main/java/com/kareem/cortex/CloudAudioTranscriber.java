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

/** Cloud-only ASR client. Provider credentials remain server-side. */
public final class CloudAudioTranscriber {
    public interface Callback {
        void ok(TranscriptResult result);
        void fail(Exception error);
    }

    public static final class RetryableException extends Exception {
        public RetryableException(String message) { super(message); }
        public RetryableException(String message, Throwable cause) { super(message, cause); }
    }

    private static final String ENDPOINT = "https://kareemabdelaziz.com/ai/transcribe.php";
    private static final int MAX_REDIRECTS = 3;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private CloudAudioTranscriber() {}

    public static void transcribe(Context context, File audioFile, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                callback.ok(transcribeBlocking(audioFile));
            } catch (Exception e) {
                callback.fail(e);
            }
        });
    }

    static TranscriptResult transcribeBlocking(File audioFile) throws Exception {
        if (audioFile == null || !audioFile.exists() || !audioFile.isFile()) {
            throw new IllegalArgumentException("Audio file is missing");
        }
        if (audioFile.length() <= 0) {
            throw new IllegalArgumentException("Audio file is empty");
        }
        if (audioFile.length() > 25L * 1024L * 1024L) {
            throw new IllegalArgumentException("Audio file exceeds the 25 MB cloud transcription limit");
        }
        return postAudio(new URL(ENDPOINT), audioFile, 0);
    }

    private static TranscriptResult postAudio(URL endpoint, File audioFile, int redirectCount) throws Exception {
        HttpURLConnection connection = null;
        String boundary = "----CortexPrimeVoice" + UUID.randomUUID().toString().replace("-", "");
        try {
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setInstanceFollowRedirects(false); // We must preserve POST + body across 307/308.
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(180_000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("X-Cortex-Client", "android-cloud-voice-v2");

            try (OutputStream raw = connection.getOutputStream()) {
                writeTextPart(raw, boundary, "mode", "ar-EG+en-codeswitch-auto");
                writeTextPart(raw, boundary, "languages", "ar,en");
                writeFilePart(raw, boundary, "audio", audioFile, mimeFor(audioFile));
                raw.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                raw.flush();
            }

            int code = connection.getResponseCode();
            if (code == 301 || code == 302 || code == 307 || code == 308) {
                if (redirectCount >= MAX_REDIRECTS) {
                    throw new IllegalStateException("Cortex transcription backend redirect loop (HTTP " + code + ")");
                }
                String location = connection.getHeaderField("Location");
                if (location == null || location.trim().isEmpty()) {
                    throw new IllegalStateException("Cortex transcription backend HTTP " + code + " without Location header");
                }
                URL next = new URL(endpoint, location.trim());
                validateRedirect(endpoint, next);
                connection.disconnect();
                connection = null;
                return postAudio(next, audioFile, redirectCount + 1);
            }

            String body = readBody(code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (code < 200 || code >= 300) {
                String message = "Cortex transcription backend HTTP " + code +
                        (body.isEmpty() ? "" : ": " + body);
                if (code == 408 || code == 425 || code == 429 || code >= 500) {
                    throw new RetryableException(message);
                }
                throw new IllegalStateException(message);
            }

            JSONObject json = new JSONObject(body);
            if (!json.optBoolean("ok", true)) {
                String message = json.optString("error", "Cloud transcription failed");
                if (json.optBoolean("retryable", false)) throw new RetryableException(message);
                throw new IllegalStateException(message);
            }

            String text = json.optString("transcript", json.optString("text", "")).trim();
            if (text.isEmpty()) throw new IllegalStateException("Cloud transcription returned no text");

            TranscriptResult result = new TranscriptResult();
            result.text = text;
            result.engine = json.optString("engine", "gpt-transcribe_cloud");
            result.version = json.optString("version", "cloud-v2");
            result.language = json.optString("language", "ar-EG+en-codeswitch-auto");
            result.durationMs = json.optLong("duration_ms", 0L);

            JSONArray segments = json.optJSONArray("segments");
            if (segments != null) {
                for (int i = 0; i < segments.length(); i++) {
                    JSONObject s = segments.optJSONObject(i);
                    if (s == null) continue;
                    String segmentText = s.optString("text", "").trim();
                    if (segmentText.isEmpty()) continue;
                    long startMs = s.optLong("start_ms", 0L);
                    long endMs = s.optLong("end_ms", startMs);
                    float confidence = (float) s.optDouble("confidence", 0.0);
                    result.segments.add(new TranscriptResult.Segment(startMs, endMs, segmentText, confidence));
                }
            }
            return result;
        } catch (RetryableException e) {
            throw e;
        } catch (java.net.SocketTimeoutException | java.net.ConnectException | java.net.UnknownHostException e) {
            throw new RetryableException("Cloud transcription unavailable: " + e.getMessage(), e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void validateRedirect(URL from, URL to) {
        if (!"https".equalsIgnoreCase(to.getProtocol())) {
            throw new SecurityException("Refusing non-HTTPS transcription redirect");
        }
        String fromHost = normalizeHost(from.getHost());
        String toHost = normalizeHost(to.getHost());
        if (!fromHost.equals(toHost)) {
            throw new SecurityException("Refusing transcription redirect to another host: " + to.getHost());
        }
    }

    private static String normalizeHost(String host) {
        String h = host == null ? "" : host.toLowerCase(Locale.US);
        return h.startsWith("www.") ? h.substring(4) : h;
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
        if (n.endsWith(".webm")) return "audio/webm";
        return "application/octet-stream";
    }
}
