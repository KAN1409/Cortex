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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Provider-resilient Cortex cognition boundary.
 *
 * Primary: Groq openai/gpt-oss-120b with strict JSON-schema constrained decoding.
 * Fallback: the existing Gemini brain implementation.
 *
 * Obvious TEST_META / PRODUCT_FEEDBACK stays local so provider availability can never promote
 * test chatter into personal state. All provider outputs still pass grounding and capture-policy
 * safety rails before they are allowed to change Cortex product state.
 */
public final class CortexBrainRouter {
    private static final String GROQ_MODEL = "openai/gpt-oss-120b";
    private static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 40_000;

    private CortexBrainRouter() {}

    public static BrainIntakeEngine.Decision understand(Context context,
                                                         CortexDb.AttachmentEvidence evidence,
                                                         String transcript,
                                                         String contextJson) throws Exception {
        String clean = clean(transcript);
        if (clean.isEmpty()) throw new IllegalArgumentException("Transcript required for brain intake");

        BrainIntakeEngine.Decision local = obviousCapturePolicy(clean);
        if (local != null) return local;

        String prompt = prompt(evidence, clean, contextJson);
        String groqKey = GroqKeyStore.get(context);
        Exception groqError = null;

        if (groqKey != null && !groqKey.trim().isEmpty()) {
            try {
                return callGroq(groqKey.trim(), prompt, clean);
            } catch (Exception e) {
                groqError = e;
            }
        } else {
            groqError = new IllegalStateException("Groq key not configured");
        }

        try {
            BrainIntakeEngine.Decision fallback = BrainIntakeEngine.understand(context, evidence, clean, contextJson);
            if (fallback.provider == null || fallback.provider.trim().isEmpty() || "gemini".equals(fallback.provider)) {
                fallback.provider = "gemini-fallback";
            }
            if (fallback.reason == null || fallback.reason.trim().isEmpty()) {
                fallback.reason = "Gemini fallback applied after Groq primary was unavailable.";
            }
            return fallback;
        } catch (Exception geminiError) {
            String g = compact(message(groqError), 180);
            String m = compact(message(geminiError), 180);
            IOException combined = new IOException("Cortex brain providers failed · Groq primary: " + g + " · Gemini fallback: " + m);
            combined.addSuppressed(groqError);
            combined.addSuppressed(geminiError);
            throw combined;
        }
    }

    private static BrainIntakeEngine.Decision callGroq(String key, String prompt, String transcript) throws Exception {
        JSONObject request = new JSONObject();
        request.put("model", GROQ_MODEL);
        request.put("reasoning_effort", "medium");
        request.put("reasoning_format", "hidden");
        request.put("max_completion_tokens", 1600);
        request.put("messages", new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("content", prompt)));

        JSONObject jsonSchema = new JSONObject();
        jsonSchema.put("name", "cortex_brain_decision");
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", decisionSchema());
        request.put("response_format", new JSONObject()
                .put("type", "json_schema")
                .put("json_schema", jsonSchema));

        HttpURLConnection c = (HttpURLConnection) new URL(GROQ_ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        try (OutputStream out = c.getOutputStream()) {
            out.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = c.getResponseCode();
        String response = read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        c.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("Groq brain HTTP " + code + ": " + compact(response, 360));
        }

        JSONObject root = new JSONObject(response);
        JSONArray choices = root.optJSONArray("choices");
        JSONObject choice = choices == null || choices.length() == 0 ? null : choices.optJSONObject(0);
        JSONObject message = choice == null ? null : choice.optJSONObject("message");
        String content = message == null ? "" : message.optString("content", "").trim();
        if (content.isEmpty()) throw new IOException("Groq GPT-OSS brain returned no structured decision");

        JSONObject decisionJson = new JSONObject(content);
        BrainIntakeEngine.Decision d = BrainIntakeEngine.Decision.from(decisionJson, GROQ_MODEL);
        d.provider = "groq";
        d.rawProviderResponse = response;
        enforceGrounding(d, transcript);
        enforceCapturePolicy(d, transcript);
        d.rawDecisionJson = d.toPolicyJson(decisionJson).toString();
        if (!d.memoryCreate && !d.situationCreate && d.entities.isEmpty() && !d.feedbackCreate) d.evidenceOnly = true;
        return d;
    }

    private static String prompt(CortexDb.AttachmentEvidence evidence, String clean, String contextJson) {
        ZonedDateTime now = ZonedDateTime.now();
        return "You are Cortex Brain, the single cognition layer for grounded phone evidence. " +
                "Determine what this capture means for the user's real current state. Do not invent history, people, deadlines, facts or urgency. " +
                "The response is constrained by a strict JSON schema, so fill every field. Use empty strings and false when a field does not apply.\n\n" +
                "CURRENT_LOCAL_TIME: " + now + "\n" +
                "TIME_ZONE: " + ZoneId.systemDefault().getId() + "\n" +
                "EVIDENCE_ID: " + evidence.id + "\n" +
                "EVIDENCE_KIND: " + evidence.kind + "\n" +
                "CAPTURE_TEXT:\n" + clean + "\n\n" +
                "CURRENT_CORTEX_STATE_JSON:\n" + (contextJson == null ? "{}" : contextJson) + "\n\n" +
                "POLICY:\n" +
                "- PERSONAL = real life/work facts, plans, commitments, preferences, people, projects or current state.\n" +
                "- TEST_META = testing transcription/capture/Cortex/model. Never create Memory, Situation or World. surface CAPTURE_HISTORY_ONLY, retention SHORT.\n" +
                "- PRODUCT_FEEDBACK = reports about Cortex/Relay/ASR/app behavior or requested product changes. Never create personal Memory/Situation/World. surface CAPTURE_HISTORY_ONLY, retention STANDARD.\n" +
                "- TRANSIENT = filler with no durable/current-state meaning. Never create Memory/Situation/World. surface HIDDEN, retention SHORT.\n" +
                "- Memory is only durable information genuinely worth recalling later. Do not store every capture.\n" +
                "- Situation is only an unresolved live state: action/commitment/reminder/deadline/waiting/decision/ongoing issue.\n" +
                "- A time-bound action like 'I need to send an email urgently at 3pm' is a PERSONAL Situation and normally needs_attention.\n" +
                "- Reuse an existing Situation canonical_key only when CURRENT_CORTEX_STATE_JSON clearly represents the same issue.\n" +
                "- World entities require an explicit named PERSON, PROJECT, ORGANIZATION, or genuinely durable TOPIC. Never promote generic nouns, times, filenames or UI text.\n" +
                "- World entity names must appear in the capture text.\n" +
                "- needs_attention only when evidence supports action now/soon; otherwise watching or quiet.\n" +
                "- Titles and summaries must be concise and grounded in the capture.";
    }

    private static JSONObject decisionSchema() throws Exception {
        JSONObject capturePolicy = objectSchema(
                props(
                        "class", enumString("PERSONAL", "TEST_META", "PRODUCT_FEEDBACK", "TRANSIENT"),
                        "surface", enumString("NORMAL", "CAPTURE_HISTORY_ONLY", "HIDDEN"),
                        "retention", enumString("DURABLE", "STANDARD", "SHORT")
                ), "class", "surface", "retention");

        JSONObject memory = objectSchema(
                props("create", type("boolean"), "title", type("string"), "body", type("string")),
                "create", "title", "body");

        JSONObject situation = objectSchema(
                props(
                        "create", type("boolean"),
                        "canonical_key", type("string"),
                        "title", type("string"),
                        "summary", type("string"),
                        "attention", enumString("quiet", "watching", "needs_attention")
                ), "create", "canonical_key", "title", "summary", "attention");

        JSONObject entity = objectSchema(
                props(
                        "type", enumString("PERSON", "PROJECT", "ORGANIZATION", "TOPIC"),
                        "canonical_key", type("string"),
                        "name", type("string"),
                        "summary", type("string")
                ), "type", "canonical_key", "name", "summary");

        JSONObject feedback = objectSchema(
                props(
                        "create", type("boolean"),
                        "category", enumString("ASR", "CAPTURE", "RELAY", "BRAIN", "UI", "OTHER"),
                        "summary", type("string")
                ), "create", "category", "summary");

        JSONObject rootProps = new JSONObject();
        rootProps.put("capture_policy", capturePolicy);
        rootProps.put("memory", memory);
        rootProps.put("situation", situation);
        rootProps.put("world_entities", new JSONObject().put("type", "array").put("items", entity));
        rootProps.put("product_feedback", feedback);
        rootProps.put("evidence_only", type("boolean"));
        rootProps.put("reason", type("string"));
        return objectSchema(rootProps, "capture_policy", "memory", "situation", "world_entities", "product_feedback", "evidence_only", "reason");
    }

    private static JSONObject type(String type) throws Exception {
        return new JSONObject().put("type", type);
    }

    private static JSONObject enumString(String... values) throws Exception {
        JSONArray a = new JSONArray();
        for (String value : values) a.put(value);
        return new JSONObject().put("type", "string").put("enum", a);
    }

    private static JSONObject props(Object... pairs) throws Exception {
        JSONObject p = new JSONObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) p.put((String) pairs[i], pairs[i + 1]);
        return p;
    }

    private static JSONObject objectSchema(JSONObject properties, String... required) throws Exception {
        JSONArray r = new JSONArray();
        for (String x : required) r.put(x);
        return new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", r)
                .put("additionalProperties", false);
    }

    private static BrainIntakeEngine.Decision obviousCapturePolicy(String transcript) throws Exception {
        String t = norm(transcript);
        boolean feedback = containsAny(t,
                "transcription wrong", "transcription is wrong", "transcript wrong", "asr wrong", "didn't work", "doesn't work",
                "not working", "stuck", "bug", "cortex should", "relay should", "app should",
                "الترانسكريبت غلط", "الترانسكريبت مش", "الترانسكريبشن غلط", "مش شغال", "ما اشتغلش", "معلق", "علّق", "صلح الكورتكس", "صلح الابلكيشن");
        boolean test = containsAny(t,
                "test transcription", "test the transcription", "transcription test", "test uh transcription",
                "we're gonna test", "we are going to test", "just testing", "this is a test", "test cortex", "test capture", "test asr",
                "testing cortex capture", "testing the transcription",
                "نجرب الترانسكريبت", "نجرب الترانسكريبشن", "تجربة الترانسكريبت", "اختبار الترانسكريبت", "بنجرب الترانسكريبت", "نجرب الكورتكس", "تست للترانسكريبت", "تست للترانسكريبشن");
        if (!feedback && !test) return null;

        BrainIntakeEngine.Decision d = new BrainIntakeEngine.Decision();
        d.provider = "policy";
        d.model = "capture-policy-v1";
        d.evidenceOnly = true;
        d.surface = "CAPTURE_HISTORY_ONLY";
        d.memoryCreate = false;
        d.situationCreate = false;
        d.entities.clear();
        if (feedback) {
            d.captureClass = "PRODUCT_FEEDBACK";
            d.retention = "STANDARD";
            d.feedbackCreate = true;
            d.feedbackCategory = "OTHER";
            d.feedbackSummary = compact(transcript, 240);
            d.reason = "Explicit product-feedback wording matched Cortex capture policy.";
        } else {
            d.captureClass = "TEST_META";
            d.retention = "SHORT";
            d.feedbackCreate = false;
            d.reason = "Explicit test/meta wording matched Cortex capture policy.";
        }
        d.rawDecisionJson = d.toPolicyJson(new JSONObject()).toString();
        return d;
    }

    private static void enforceGrounding(BrainIntakeEngine.Decision d, String transcript) {
        ArrayList<BrainIntakeEngine.Entity> grounded = new ArrayList<>();
        String hay = norm(transcript);
        for (BrainIntakeEngine.Entity e : d.entities) {
            String name = norm(e.name);
            if (name.length() >= 2 && hay.contains(name)) grounded.add(e);
        }
        d.entities.clear();
        d.entities.addAll(grounded);
    }

    private static void enforceCapturePolicy(BrainIntakeEngine.Decision d, String transcript) {
        String t = norm(transcript);
        boolean feedback = containsAny(t,
                "transcription wrong", "transcription is wrong", "transcript wrong", "asr wrong", "didn't work", "doesn't work",
                "not working", "stuck", "bug", "cortex should", "relay should", "app should",
                "الترانسكريبت غلط", "الترانسكريبت مش", "الترانسكريبشن غلط", "مش شغال", "ما اشتغلش", "معلق", "علّق", "صلح الكورتكس", "صلح الابلكيشن");
        boolean test = containsAny(t,
                "test transcription", "test the transcription", "transcription test", "test uh transcription",
                "we're gonna test", "we are going to test", "just testing", "this is a test", "test cortex", "test capture", "test asr",
                "testing cortex capture", "testing the transcription",
                "نجرب الترانسكريبت", "نجرب الترانسكريبشن", "تجربة الترانسكريبت", "اختبار الترانسكريبت", "بنجرب الترانسكريبت", "نجرب الكورتكس", "تست للترانسكريبت", "تست للترانسكريبشن");
        if (feedback) {
            d.captureClass = "PRODUCT_FEEDBACK";
            d.surface = "CAPTURE_HISTORY_ONLY";
            d.retention = "STANDARD";
            d.memoryCreate = false;
            d.situationCreate = false;
            d.entities.clear();
            d.evidenceOnly = true;
            if (!d.feedbackCreate) {
                d.feedbackCreate = true;
                d.feedbackCategory = "OTHER";
                d.feedbackSummary = compact(transcript, 240);
            }
        } else if (test) {
            d.captureClass = "TEST_META";
            d.surface = "CAPTURE_HISTORY_ONLY";
            d.retention = "SHORT";
            d.memoryCreate = false;
            d.situationCreate = false;
            d.entities.clear();
            d.feedbackCreate = false;
            d.evidenceOnly = true;
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

    private static String message(Throwable t) {
        if (t == null) return "unknown";
        String m = t.getMessage();
        return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m;
    }

    private static boolean containsAny(String s, String... xs) {
        for (String x : xs) if (s.contains(norm(x))) return true;
        return false;
    }

    private static String compact(String value, int max) {
        String x = clean(value).replaceAll("\\s+", " ");
        return x.length() <= max ? x : x.substring(0, max) + "…";
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String norm(String s) {
        return clean(s).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
