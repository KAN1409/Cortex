package com.kareem.cortex.rebuild;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Fresh Cortex perception boundary for Cortex Relay.
 *
 * This service authenticates the installed Relay package, preserves exact grounded payloads and
 * ACKs durable delivery. It deliberately performs no relevance, priority, memory, situation or
 * action reasoning. Evidence first; cognition happens after intake.
 */
public final class CortexLocalBusService extends Service {
    public static final String ACTION_COMPAT = "com.kareem.cortex.LOCAL_BUS_V1";
    public static final String ACTION_REBUILD = "com.kareem.cortex.rebuild.LOCAL_BUS_V1";
    public static final String RELAY_PACKAGE = "com.kareem.secondbrain";
    public static final String CONNECTOR_ID = "second_brain";
    public static final String PROTOCOL_V1 = "CORTEX_INGEST_V1";
    public static final String PROTOCOL_V2 = "CORTEX_SIGNAL_V2";
    public static final String SCHEMA_V2 = "CORTEX_RELAY_SIGNAL_V2";

    private static final int MSG_HELLO = 1;
    private static final int MSG_INGEST_V1 = 2;
    private static final int MSG_INGEST_V2 = 20;
    private static final int MSG_ACK = 100;
    private static final int MSG_ERROR = 101;

    private static final String KEY_CONNECTOR_ID = "connector_id";
    private static final String KEY_RELAY_CAPABILITIES_JSON = "relay_capabilities_json";
    private static final String KEY_EVENT_JSON = "event_json";
    private static final String KEY_EVENT_ID = "event_id";
    private static final String KEY_STATUS = "status";
    private static final String KEY_DETAIL = "detail";
    private static final String KEY_SIGNAL_ID = "signal_id";
    private static final String KEY_SELECTED_PROTOCOL = "selected_protocol";

    private static final String PREFS = "relay_identity_v1";
    private static final String PREF_RELAY_CERT = "relay_signer_sha256";
    private static final int MAX_EVENT_BYTES = 160 * 1024;

    private CortexDb db;
    private Messenger messenger;

    @Override public void onCreate() {
        super.onCreate();
        db = new CortexDb(getApplicationContext());
        messenger = new Messenger(new android.os.Handler(getMainLooper(), this::handle));
    }

    @Override public IBinder onBind(Intent intent) {
        String action = intent == null ? "" : clean(intent.getAction());
        if (!ACTION_COMPAT.equals(action) && !ACTION_REBUILD.equals(action)) return null;
        return messenger.getBinder();
    }

    @Override public void onDestroy() {
        try { db.close(); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private boolean handle(Message message) {
        if (message == null) return true;
        String eventId = "";
        try {
            int uid = message.sendingUid;
            if (message.what == MSG_HELLO) {
                authenticate(uid, true);
                handleHello(message);
                return true;
            }

            authenticate(uid, false);
            String raw = message.data == null ? "" : clean(message.data.getString(KEY_EVENT_JSON));
            if (raw.isEmpty()) throw new InvalidEvent("EMPTY_EVENT", "event_json is required");
            if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_EVENT_BYTES) {
                throw new InvalidEvent("PAYLOAD_TOO_LARGE", "event_json exceeds Cortex intake limit");
            }
            JSONObject json = new JSONObject(raw);
            eventId = clean(json.optString(KEY_EVENT_ID));
            if (eventId.isEmpty()) throw new InvalidEvent("MISSING_EVENT_ID", "event_id is required");

            CortexDb.RelayEnvelope envelope;
            if (message.what == MSG_INGEST_V1) {
                envelope = parseV1(json, raw);
            } else if (message.what == MSG_INGEST_V2) {
                envelope = parseV2(json, raw);
            } else {
                sendError(message.replyTo, eventId, "UNKNOWN_MESSAGE", "Unsupported Local Bus message: " + message.what);
                return true;
            }

            CortexDb.IngestResult result = db.ingestRelay(envelope);
            sendAck(message.replyTo, eventId, result.duplicate ? "DUPLICATE_ACCEPTED" : "ACCEPTED", result.evidenceId, null);
        } catch (IdentityFailure identity) {
            sendError(message.replyTo, eventId, "IDENTITY_MISMATCH", identity.getMessage());
        } catch (InvalidEvent invalid) {
            sendError(message.replyTo, eventId, "INVALID_EVENT", invalid.code + ": " + invalid.getMessage());
        } catch (Throwable failure) {
            // No payload text is logged or returned. Keep errors structural and bounded.
            sendError(message.replyTo, eventId, "INGEST_FAILED", failure.getClass().getSimpleName());
        }
        return true;
    }

    private void handleHello(Message message) throws Exception {
        Bundle data = message.data == null ? Bundle.EMPTY : message.data;
        String connector = clean(data.getString(KEY_CONNECTOR_ID));
        if (!CONNECTOR_ID.equals(connector)) throw new IdentityFailure("Unexpected connector_id");

        String selected = PROTOCOL_V1;
        String advertised = clean(data.getString(KEY_RELAY_CAPABILITIES_JSON));
        if (advertisedContains(advertised, PROTOCOL_V2)) selected = PROTOCOL_V2;
        Bundle extra = new Bundle();
        extra.putString(KEY_SELECTED_PROTOCOL, selected);
        sendAck(message.replyTo, "", "READY", 0L, extra);
    }

    private CortexDb.RelayEnvelope parseV1(JSONObject json, String raw) throws Exception {
        requireEquals(PROTOCOL_V1, json.optString("protocol"), "protocol");
        requireEquals(CONNECTOR_ID, json.optString("connector_id"), "connector_id");
        requireEquals("NOTIFICATION", json.optString("source_type"), "source_type");

        String eventId = required(json, KEY_EVENT_ID);
        String sourcePackage = clean(json.optString("source_package"));
        long occurredAt = json.optLong("occurred_at", 0L);
        String summary = displaySummary(
                clean(json.optString("title")),
                clean(json.optString("text")),
                clean(json.optString("expanded_text")),
                clean(json.optString("conversation_title"))
        );
        JSONObject metadata = json.optJSONObject("metadata");
        String quality = qualityFromMetadata(metadata).toString();
        return new CortexDb.RelayEnvelope(eventId, CONNECTOR_ID, PROTOCOL_V1, "NOTIFICATION",
                sourcePackage, occurredAt, summary, raw, quality);
    }

    private CortexDb.RelayEnvelope parseV2(JSONObject json, String raw) throws Exception {
        requireEquals(PROTOCOL_V2, json.optString("protocol"), "protocol");
        requireEquals(SCHEMA_V2, json.optString("schema"), "schema");
        requireEquals(CONNECTOR_ID, json.optString("connector_id"), "connector_id");

        String eventId = required(json, KEY_EVENT_ID);
        JSONObject source = json.optJSONObject("source");
        if (source == null) throw new InvalidEvent("MISSING_SOURCE", "source object is required");
        requireEquals("NOTIFICATION", source.optString("type"), "source.type");
        String sourcePackage = clean(source.optString("package"));
        long occurredAt = json.optLong("occurred_at", 0L);

        JSONObject semantic = json.optJSONObject("semantic");
        String summary = summaryFromSemantic(semantic);
        JSONObject quality = qualityFromSemantic(semantic);
        return new CortexDb.RelayEnvelope(eventId, CONNECTOR_ID, PROTOCOL_V2, "NOTIFICATION",
                sourcePackage, occurredAt, summary, raw, quality.toString());
    }

    /** Mechanical display summary only; no personal importance or task inference. */
    private static String summaryFromSemantic(JSONObject semantic) {
        if (semantic == null) return "Relay notification evidence";
        JSONObject content = semantic.optJSONObject("content");
        if (content != null) {
            String summary = displaySummary(
                    nullableString(content, "title"),
                    nullableString(content, "text"),
                    nullableString(content, "expanded_text"),
                    nullableString(content, "conversation_title")
            );
            if (!summary.isEmpty()) return summary;
        }
        JSONObject typed = semantic.optJSONObject("semantic");
        if (typed != null) {
            for (String key : new String[]{"latest_text","preview","context_text","text","status_text","event_text","alert_text","call_text","display_subject"}) {
                String value = nullableString(typed, key);
                if (!value.isEmpty()) return clip(value, 900);
            }
        }
        return "Relay notification evidence";
    }

    /** Preserve Relay-supplied provenance/quality only. Cortex does not manufacture a quality score here. */
    private static JSONObject qualityFromSemantic(JSONObject semantic) {
        JSONObject out = new JSONObject();
        if (semantic == null) return out;
        copyIfPresent(semantic, out, "provenance");
        copyIfPresent(semantic, out, "evidence_quality");
        copyIfPresent(semantic, out, "quality");
        copyIfPresent(semantic, out, "entities");
        copyIfPresent(semantic, out, "meaningful_change");
        copyIfPresent(semantic, out, "change_reason");
        copyIfPresent(semantic, out, "conversation_identity");
        copyIfPresent(semantic, out, "conversation_identity_basis");
        copyIfPresent(semantic, out, "logical_signal_id");
        return out;
    }

    private static JSONObject qualityFromMetadata(JSONObject metadata) {
        JSONObject out = new JSONObject();
        if (metadata == null) return out;
        JSONObject semantic = metadata.optJSONObject("relay_semantic_v2");
        if (semantic != null) return qualityFromSemantic(semantic);
        copyIfPresent(metadata, out, "relay_normalization");
        copyIfPresent(metadata, out, "relay_entities");
        return out;
    }

    private void authenticate(int uid, boolean allowPin) throws Exception {
        if (uid <= 0) throw new IdentityFailure("Missing Binder sender UID");
        PackageManager pm = getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        boolean packageMatches = false;
        if (packages != null) {
            for (String item : packages) if (RELAY_PACKAGE.equals(item)) { packageMatches = true; break; }
        }
        if (!packageMatches) throw new IdentityFailure("Sender UID is not Cortex Relay");

        String current = currentSignerSha256(pm, RELAY_PACKAGE);
        if (current.isEmpty()) throw new IdentityFailure("Relay signer unavailable");
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String pinned = clean(prefs.getString(PREF_RELAY_CERT, ""));
        if (pinned.isEmpty()) {
            if (!allowPin) throw new IdentityFailure("Relay identity has not completed authenticated HELLO");
            if (!prefs.edit().putString(PREF_RELAY_CERT, current).commit()) {
                throw new IdentityFailure("Could not pin Relay signer");
            }
            return;
        }
        if (!constantTimeEquals(pinned, current)) throw new IdentityFailure("Relay signer changed");
    }

    private static String currentSignerSha256(PackageManager pm, String packageName) throws Exception {
        PackageInfo info;
        Signature[] signatures;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        } else {
            //noinspection deprecation
            info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            //noinspection deprecation
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) return "";
        Set<String> digests = new LinkedHashSet<>();
        for (Signature signature : signatures) {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            digests.add(hex(hash));
        }
        // Multiple current signers are represented deterministically; changing any signer changes the pin.
        return String.join("+", digests);
    }

    private void sendAck(Messenger target, String eventId, String status, long evidenceId, Bundle extra) {
        if (target == null) return;
        try {
            Message reply = Message.obtain(null, MSG_ACK);
            Bundle data = extra == null ? new Bundle() : new Bundle(extra);
            data.putString(KEY_EVENT_ID, clean(eventId));
            data.putString(KEY_STATUS, clean(status));
            if (evidenceId > 0) data.putLong(KEY_SIGNAL_ID, evidenceId);
            reply.data = data;
            target.send(reply);
        } catch (Throwable ignored) {}
    }

    private void sendError(Messenger target, String eventId, String status, String detail) {
        if (target == null) return;
        try {
            Message reply = Message.obtain(null, MSG_ERROR);
            Bundle data = new Bundle();
            data.putString(KEY_EVENT_ID, clean(eventId));
            data.putString(KEY_STATUS, clean(status));
            data.putString(KEY_DETAIL, clip(clean(detail), 300));
            reply.data = data;
            target.send(reply);
        } catch (Throwable ignored) {}
    }

    private static boolean advertisedContains(String raw, String value) {
        try {
            JSONArray array = new JSONArray(raw);
            for (int i=0;i<array.length();i++) if (value.equals(array.optString(i))) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static String displaySummary(String title, String text, String expanded, String conversation) {
        LinkedHashSet<String> pieces = new LinkedHashSet<>();
        addPiece(pieces, conversation);
        addPiece(pieces, title);
        addPiece(pieces, text);
        if (pieces.size() < 2) addPiece(pieces, expanded);
        return clip(String.join(" · ", pieces), 1200);
    }

    private static void addPiece(Set<String> pieces, String value) {
        String clean = clip(clean(value).replaceAll("\\s+", " "), 700);
        if (!clean.isEmpty()) pieces.add(clean);
    }

    private static String nullableString(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) return "";
        return clean(object.optString(key));
    }

    private static void copyIfPresent(JSONObject source, JSONObject target, String key) {
        if (source != null && source.has(key)) {
            try { target.put(key, source.isNull(key) ? JSONObject.NULL : source.opt(key)); } catch (Throwable ignored) {}
        }
    }

    private static String required(JSONObject object, String key) throws InvalidEvent {
        String value = clean(object.optString(key));
        if (value.isEmpty()) throw new InvalidEvent("MISSING_" + key.toUpperCase(Locale.ROOT), key + " is required");
        return value;
    }

    private static void requireEquals(String expected, String actual, String field) throws InvalidEvent {
        if (!expected.equals(clean(actual))) throw new InvalidEvent("INVALID_" + field.toUpperCase(Locale.ROOT).replace('.','_'), field + " mismatch");
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String clip(String value, int max) {
        String clean = clean(value);
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static final class IdentityFailure extends Exception {
        IdentityFailure(String message) { super(message); }
    }
    private static final class InvalidEvent extends Exception {
        final String code;
        InvalidEvent(String code, String message) { super(message); this.code=code; }
    }
}