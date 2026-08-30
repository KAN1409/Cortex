package com.kareem.cortex.rebuild;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The fresh Cortex cognitive boundary for deliberate captures.
 * Evidence enters here only after perception (for voice: after ASR). The model may create current
 * situations, durable memory and grounded world entities; it can also choose evidence-only.
 */
public final class BrainIntakeEngine {
    private static final String MODEL = "gemini-3.6-flash";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 40_000;

    private BrainIntakeEngine() {}

    public static Decision understand(Context context, CortexDb.AttachmentEvidence evidence,
                                      String transcript, String contextJson) throws Exception {
        String key = GeminiKeyStore.get(context);
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("Cortex brain requires the configured Gemini key");
        }
        String clean = clean(transcript);
        if (clean.isEmpty()) throw new IllegalArgumentException("Transcript required for brain intake");

        ZonedDateTime now = ZonedDateTime.now();
        String prompt = "You are the single cognition layer inside Cortex. You receive grounded phone evidence and decide what it MEANS for the user's current state. " +
                "You do not invent facts. You do not create UI cards from raw evidence. Return ONLY one JSON object matching the schema below.\n\n" +
                "CURRENT_LOCAL_TIME: " + now + "\nTIME_ZONE: " + ZoneId.systemDefault().getId() + "\n" +
                "EVIDENCE_ID: " + evidence.id + "\nEVIDENCE_KIND: " + evidence.kind + "\n" +
                "VOICE_TRANSCRIPT:\n" + clean + "\n\n" +
                "CURRENT_CORTEX_STATE_JSON:\n" + (contextJson == null ? "{}" : contextJson) + "\n\n" +
                "RULES:\n" +
                "1. The transcript is deliberate user-captured evidence, not automatically a memory or task.\n" +
                "2. Create a situation only for an unresolved current state: a request, commitment, reminder, decision needed, waiting state, deadline, ongoing issue, or meaningful change that still matters.\n" +
                "3. Create durable memory only for information worth recalling later: a durable fact, preference, decision, note, instruction, plan, or meaningful personal/work context. Do not save filler or transcription chatter as memory.\n" +
                "4. Create world entities only when an explicit named person, project, organization or durable topic is present in the transcript. Never turn numbers, generic nouns or UI fragments into entities.\n" +
                "5. A single capture may create both a memory and a situation when both are justified.\n" +
                "6. Reuse/update an existing situation when CURRENT_CORTEX_STATE_JSON clearly describes the same live issue. Use its canonical_key exactly. Otherwise create a new stable lowercase canonical_key.\n" +
                "7. attention may be needs_attention only when the evidence itself supports action now/soon. Future/nonurgent states are quiet.\n" +
                "8. If no cognitive product is justified, set evidence_only=true. The evidence remains searchable and preserved.\n" +
                "9. Keep titles short, natural and user-facing. Summary/body may paraphrase only what the transcript supports.\n\n" +
                "SCHEMA:\n" +
                "{\n" +
                "  \"memory\": {\"create\": false, \"title\": \"\", \"body\": \"\"},\n" +
                "  \"situation\": {\"create\": false, \"canonical_key\": \"\", \"title\": \"\", \"summary\": \"\", \"attention\": \"quiet\"},\n" +
                "  \"world_entities\": [{\"type\": \"PERSON|PROJECT|ORGANIZATION|TOPIC\", \"canonical_key\": \"\", \"name\": \"\", \"summary\": \"\"}],\n" +
                "  \"evidence_only\": false,\n" +
                "  \"reason\": \"brief grounded explanation\"\n" +
                "}";

        JSONObject cfg = new JSONObject();
        cfg.put("temperature", 0);
        cfg.put("maxOutputTokens", 900);
        cfg.put("responseMimeType", "application/json");
        JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
        JSONArray contents = new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts));
        JSONObject request = new JSONObject().put("contents", contents).put("generationConfig", cfg);

        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL +
                ":generateContent?key=" + java.net.URLEncoder.encode(key, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        try (OutputStream out = c.getOutputStream()) {
            out.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        String response = read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        c.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("Cortex brain HTTP " + code + ": " + compact(response, 420));
        }
        JSONObject root = new JSONObject(response);
        String modelText = extractText(root);
        if (modelText.trim().isEmpty()) throw new IOException("Cortex brain returned no decision");
        JSONObject decisionJson = parseObject(modelText);
        Decision d = Decision.from(decisionJson, MODEL);
        d.rawProviderResponse = response;
        d.rawDecisionJson = decisionJson.toString();
        enforceGrounding(d, clean);
        if (!d.memoryCreate && !d.situationCreate && d.entities.isEmpty()) d.evidenceOnly = true;
        return d;
    }

    private static void enforceGrounding(Decision d, String transcript) {
        ArrayList<Entity> grounded = new ArrayList<>();
        String hay = norm(transcript);
        for (Entity e : d.entities) {
            String name = norm(e.name);
            if (name.length() >= 2 && hay.contains(name)) grounded.add(e);
        }
        d.entities.clear();
        d.entities.addAll(grounded);
    }

    private static JSONObject parseObject(String text) throws Exception {
        String t = text.trim().replaceAll("^```(?:json)?\\s*", "").replaceAll("```$", "").trim();
        int a = t.indexOf('{'), b = t.lastIndexOf('}');
        if (a < 0 || b <= a) throw new IOException("Cortex brain returned invalid JSON");
        return new JSONObject(t.substring(a, b + 1));
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
            String text = p == null ? "" : p.optString("text", "");
            if (!text.isEmpty()) { if (b.length() > 0) b.append('\n'); b.append(text); }
        }
        return b.toString();
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream x = in; ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = x.read(buf)) != -1;) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    private static String compact(String value, int max) {
        String x = clean(value).replaceAll("\\s+", " ");
        return x.length() <= max ? x : x.substring(0, max) + "…";
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static String norm(String s) { return clean(s).toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }

    public static final class Entity {
        public final String type, canonicalKey, name, summary;
        Entity(String type, String canonicalKey, String name, String summary) {
            this.type = normalizeType(type); this.canonicalKey = key(canonicalKey, this.type + ":" + name);
            this.name = clean(name); this.summary = clean(summary);
        }
        private static String normalizeType(String t) {
            String x = clean(t).toUpperCase(Locale.ROOT);
            if (x.equals("PERSON") || x.equals("PROJECT") || x.equals("ORGANIZATION") || x.equals("TOPIC")) return x;
            return "TOPIC";
        }
    }

    public static final class Decision {
        public boolean memoryCreate, situationCreate, evidenceOnly;
        public String memoryTitle="", memoryBody="", situationKey="", situationTitle="", situationSummary="", attention="quiet", reason="", provider="gemini", model="";
        public String rawProviderResponse="", rawDecisionJson="{}";
        public final List<Entity> entities = new ArrayList<>();

        static Decision from(JSONObject j, String model) {
            Decision d = new Decision(); d.model = model;
            JSONObject m = j.optJSONObject("memory");
            if (m != null) { d.memoryCreate=m.optBoolean("create",false); d.memoryTitle=clean(m.optString("title")); d.memoryBody=clean(m.optString("body")); }
            JSONObject s = j.optJSONObject("situation");
            if (s != null) {
                d.situationCreate=s.optBoolean("create",false); d.situationTitle=clean(s.optString("title")); d.situationSummary=clean(s.optString("summary"));
                d.situationKey=key(s.optString("canonical_key"),d.situationTitle); String a=clean(s.optString("attention","quiet")).toLowerCase(Locale.ROOT);
                d.attention=a.equals("needs_attention")?"needs_attention":a.equals("watching")?"watching":"quiet";
            }
            JSONArray entities=j.optJSONArray("world_entities");
            if(entities!=null)for(int i=0;i<entities.length();i++){JSONObject e=entities.optJSONObject(i);if(e==null)continue;String name=clean(e.optString("name"));if(name.isEmpty())continue;d.entities.add(new Entity(e.optString("type"),e.optString("canonical_key"),name,e.optString("summary")));}
            d.evidenceOnly=j.optBoolean("evidence_only",false); d.reason=clean(j.optString("reason"));
            if(d.memoryCreate&&(d.memoryTitle.isEmpty()||d.memoryBody.isEmpty()))d.memoryCreate=false;
            if(d.situationCreate&&(d.situationTitle.isEmpty()||d.situationSummary.isEmpty()))d.situationCreate=false;
            return d;
        }
    }

    static String key(String proposed, String fallback) {
        String x = clean(proposed).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "");
        if (x.isEmpty()) x = clean(fallback).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "");
        if (x.length() > 96) x = x.substring(0,96);
        return x.isEmpty()?"evidence":x;
    }
}
