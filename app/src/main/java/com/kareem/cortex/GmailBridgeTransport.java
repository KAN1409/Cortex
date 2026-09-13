package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Minimal Gmail REST transport for Cortex bridge traffic.
 * Authentication is injected; this class never stores OAuth credentials.
 */
public final class GmailBridgeTransport {
    public static final String LABEL_NAME = "Cortex/ChatGPT-Bridge";
    private static final String API = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    public interface AccessTokenProvider {
        String getAccessToken() throws Exception;
        void invalidate(String token);
    }

    private final AccessTokenProvider tokenProvider;
    private final String selfAddress;

    public GmailBridgeTransport(AccessTokenProvider tokenProvider, String selfAddress) {
        if (tokenProvider == null) throw new IllegalArgumentException("tokenProvider required");
        if (selfAddress == null || selfAddress.trim().isEmpty()) throw new IllegalArgumentException("selfAddress required");
        this.tokenProvider = tokenProvider;
        this.selfAddress = selfAddress.trim();
    }

    public SendResult sendEnvelope(JSONObject envelope) {
        ChatGptBridgeProtocol.Validation v = ChatGptBridgeProtocol.validateEnvelope(envelope);
        if (!v.ok) return SendResult.fail("INVALID_ENVELOPE: " + v.reason);
        String subject = ChatGptBridgeProtocol.subjectFor(envelope);
        String body = envelope.toString();
        String mime = buildMime(selfAddress, selfAddress, subject, body);
        String raw = base64UrlNoPadding(mime.getBytes(StandardCharsets.UTF_8));
        try {
            JSONObject response = requestJson("POST", API + "/messages/send", new JSONObject().put("raw", raw), true);
            String id = response.optString("id", "");
            if (id.isEmpty()) return SendResult.fail("GMAIL_SEND_NO_ID");
            ensureBridgeLabelAndApply(id);
            return SendResult.ok(id, response.optString("threadId", ""));
        } catch (Throwable t) {
            return SendResult.fail(t.getClass().getSimpleName() + ": " + safeMessage(t));
        }
    }

    public List<JSONObject> fetchCandidateVerdicts(long newerThanEpochMs, int maxResults) throws Exception {
        int boundedMax = Math.max(1, Math.min(maxResults, 100));
        long afterSeconds = Math.max(0L, newerThanEpochMs / 1000L);
        String query = "label:\"" + LABEL_NAME + "\" subject:CORTEX-BRIDGE after:" + afterSeconds;
        String url = API + "/messages?q=" + urlEncode(query) + "&maxResults=" + boundedMax;
        JSONObject listing = requestJson("GET", url, null, true);
        JSONArray messages = listing.optJSONArray("messages");
        ArrayList<JSONObject> out = new ArrayList<>();
        if (messages == null) return out;
        for (int i = 0; i < messages.length(); i++) {
            String id = messages.optJSONObject(i) == null ? "" : messages.optJSONObject(i).optString("id", "");
            if (id.isEmpty()) continue;
            JSONObject full = requestJson("GET", API + "/messages/" + urlEncode(id) + "?format=full", null, true);
            JSONObject envelope = extractEnvelope(full);
            if (envelope != null && ChatGptBridgeProtocol.MessageType.CHATGPT_TEST_VERDICT.name().equals(envelope.optString("messageType"))) {
                out.add(envelope);
            }
        }
        return out;
    }

    private void ensureBridgeLabelAndApply(String messageId) throws Exception {
        JSONObject labels = requestJson("GET", API + "/labels", null, true);
        JSONArray items = labels.optJSONArray("labels");
        String labelId = "";
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && LABEL_NAME.equals(item.optString("name"))) {
                    labelId = item.optString("id", "");
                    break;
                }
            }
        }
        if (labelId.isEmpty()) {
            JSONObject created = requestJson("POST", API + "/labels",
                    new JSONObject().put("name", LABEL_NAME).put("labelListVisibility", "labelShow").put("messageListVisibility", "show"), true);
            labelId = created.optString("id", "");
        }
        if (!labelId.isEmpty()) {
            requestJson("POST", API + "/messages/" + urlEncode(messageId) + "/modify",
                    new JSONObject().put("addLabelIds", new JSONArray().put(labelId)), true);
        }
    }

    private JSONObject extractEnvelope(JSONObject message) {
        try {
            JSONObject payload = message.optJSONObject("payload");
            String body = findBody(payload);
            if (body == null || body.trim().isEmpty()) return null;
            JSONObject envelope = new JSONObject(body.trim());
            ChatGptBridgeProtocol.Validation v = ChatGptBridgeProtocol.validateEnvelope(envelope);
            return v.ok ? envelope : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String findBody(JSONObject part) throws Exception {
        if (part == null) return null;
        JSONObject body = part.optJSONObject("body");
        String data = body == null ? "" : body.optString("data", "");
        String mime = part.optString("mimeType", "");
        if (!data.isEmpty() && ("text/plain".equalsIgnoreCase(mime) || mime.isEmpty())) {
            return new String(base64UrlDecode(data), StandardCharsets.UTF_8);
        }
        JSONArray parts = part.optJSONArray("parts");
        if (parts != null) {
            String htmlFallback = null;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject child = parts.optJSONObject(i);
                String found = findBody(child);
                if (found == null) continue;
                if ("text/plain".equalsIgnoreCase(child.optString("mimeType", ""))) return found;
                if (htmlFallback == null) htmlFallback = found;
            }
            return htmlFallback;
        }
        if (!data.isEmpty()) return new String(base64UrlDecode(data), StandardCharsets.UTF_8);
        return null;
    }

    private JSONObject requestJson(String method, String url, JSONObject body, boolean retryAuth) throws Exception {
        String token = tokenProvider.getAccessToken();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) { os.write(bytes); }
        }
        int code = connection.getResponseCode();
        if (code == 401 && retryAuth) {
            tokenProvider.invalidate(token);
            connection.disconnect();
            return requestJson(method, url, body, false);
        }
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = readAll(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) throw new GmailHttpException(code, text);
        return text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private static String buildMime(String from, String to, String subject, String body) {
        return "From: " + from + "\r\n" +
                "To: " + to + "\r\n" +
                "Subject: " + subject.replace("\r", " ").replace("\n", " ") + "\r\n" +
                "MIME-Version: 1.0\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n" +
                "\r\n" + body;
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String urlEncode(String value) throws Exception {
        return java.net.URLEncoder.encode(value, "UTF-8");
    }

    private static String base64UrlNoPadding(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] base64UrlDecode(String value) {
        String normalized = value.replace('-', '+').replace('_', '/');
        int pad = (4 - normalized.length() % 4) % 4;
        StringBuilder sb = new StringBuilder(normalized);
        for (int i = 0; i < pad; i++) sb.append('=');
        return Base64.getDecoder().decode(sb.toString());
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? "" : t.getMessage();
    }

    public static final class SendResult {
        public final boolean ok;
        public final String messageId;
        public final String threadId;
        public final String error;
        private SendResult(boolean ok, String messageId, String threadId, String error) {
            this.ok = ok; this.messageId = messageId; this.threadId = threadId; this.error = error;
        }
        public static SendResult ok(String messageId, String threadId) { return new SendResult(true, messageId, threadId, ""); }
        public static SendResult fail(String error) { return new SendResult(false, "", "", error == null ? "UNKNOWN" : error); }
    }

    public static final class GmailHttpException extends Exception {
        public final int statusCode;
        public GmailHttpException(int statusCode, String body) {
            super("Gmail HTTP " + statusCode + ": " + (body == null ? "" : body));
            this.statusCode = statusCode;
        }
    }
}
