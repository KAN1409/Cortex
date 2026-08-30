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
import java.util.List;
import java.util.Locale;

/**
 * Single cognition boundary for deliberate captures.
 *
 * Perception (ASR/OCR/file parsing) produces evidence. This layer decides whether that evidence
 * changes the user's durable Memory, current Situations, World model, product feedback, or remains
 * evidence-only. Test/meta chatter is explicitly prevented from becoming personal memory/state.
 */
public final class BrainIntakeEngine {
    private static final String MODEL = "gemini-3.6-flash";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 40_000;

    private BrainIntakeEngine() {}

    public static Decision understand(Context context, CortexDb.AttachmentEvidence evidence,
                                      String transcript, String contextJson) throws Exception {
        String clean = clean(transcript);
        if (clean.isEmpty()) throw new IllegalArgumentException("Transcript required for brain intake");

        Decision hard = obviousCapturePolicy(clean);
        if (hard != null) return hard;

        String key = GeminiKeyStore.get(context);
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("Cortex brain requires the configured Gemini key");
        }

        ZonedDateTime now = ZonedDateTime.now();
        String prompt =
                "You are the single cognition layer inside Cortex. You receive grounded deliberate capture evidence and decide what it MEANS for the user's current state. " +
                "You must separate personal cognition from test/meta chatter and product feedback. Do not invent facts. Return ONLY one JSON object.\n\n" +
                "CURRENT_LOCAL_TIME: " + now + "\nTIME_ZONE: " + ZoneId.systemDefault().getId() + "\n" +
                "EVIDENCE_ID: " + evidence.id + "\nEVIDENCE_KIND: " + evidence.kind + "\n" +
                "CAPTURE_TEXT:\n" + clean + "\n\n" +
                "CURRENT_CORTEX_STATE_JSON:\n" + (contextJson == null ? "{}" : contextJson) + "\n\n" +
                "CAPTURE CLASS PARAMETERS:\n" +
                "- PERSONAL: content about the user's real life/work, plans, preferences, commitments, facts, people, projects or current state.\n" +
                "- TEST_META: the user is testing transcription, capture, Cortex, the app, the model, or merely saying test/filler to verify the system.\n" +
                "- PRODUCT_FEEDBACK: the user reports that Cortex/Relay/ASR/app behavior is wrong, broken, slow, inaccurate, missing, or requests a product change.\n" +
                "- TRANSIENT: conversational filler with no durable or current-state meaning.\n\n" +
                "ROUTING PARAMETERS:\n" +
                "1. TEST_META must NEVER create Memory, Situation, or World entities. Set evidence_only=true, surface=CAPTURE_HISTORY_ONLY, retention=SHORT.\n" +
                "2. TRANSIENT must NEVER create Memory, Situation, or World entities. Set evidence_only=true, surface=HIDDEN, retention=SHORT.\n" +
                "3. PRODUCT_FEEDBACK must NEVER become personal Memory/Situation/World merely because it mentions Cortex. It may create product_feedback. Use surface=CAPTURE_HISTORY_ONLY, retention=STANDARD.\n" +
                "4. PERSONAL may create durable Memory only for information worth recalling later: durable fact, preference, decision, note, instruction, plan, or meaningful personal/work context.\n" +
                "5. PERSONAL may create a Situation only for an unresolved current state: request, commitment, reminder, decision needed, waiting state, deadline, ongoing issue, or meaningful change that still matters.\n" +
                "6. World entities require an explicit named PERSON, PROJECT, ORGANIZATION, or durable TOPIC. Never promote numbers, generic nouns, UI fragments, filenames, or transcription artifacts.\n" +
                "7. A PERSONAL capture can create both Memory and Situation when both are justified.\n" +
                "8. Reuse/update an existing Situation when CURRENT_CORTEX_STATE_JSON clearly describes the same live issue. Use its canonical_key exactly; otherwise create a stable lowercase key.\n" +
                "9. attention=needs_attention only when evidence supports action now/soon. Future/nonurgent state is quiet or watching.\n" +
                "10. If PERSONAL evidence has no justified product state, evidence_only=true and surface=CAPTURE_HISTORY_ONLY.\n" +
                "11. Keep titles short, natural and user-facing. Summary/body may paraphrase only what the evidence supports.\n\n" +
                "SCHEMA:\n" +
                "{\n" +
                "  \"capture_policy\": {\"class\": \"PERSONAL|TEST_META|PRODUCT_FEEDBACK|TRANSIENT\", \"surface\": \"NORMAL|CAPTURE_HISTORY_ONLY|HIDDEN\", \"retention\": \"DURABLE|STANDARD|SHORT\"},\n" +
                "  \"memory\": {\"create\": false, \"title\": \"\", \"body\": \"\"},\n" +
                "  \"situation\": {\"create\": false, \"canonical_key\": \"\", \"title\": \"\", \"summary\": \"\", \"attention\": \"quiet|watching|needs_attention\"},\n" +
                "  \"world_entities\": [{\"type\": \"PERSON|PROJECT|ORGANIZATION|TOPIC\", \"canonical_key\": \"\", \"name\": \"\", \"summary\": \"\"}],\n" +
                "  \"product_feedback\": {\"create\": false, \"category\": \"ASR|CAPTURE|RELAY|BRAIN|UI|OTHER\", \"summary\": \"\"},\n" +
                "  \"evidence_only\": false,\n" +
                "  \"reason\": \"brief grounded explanation\"\n" +
                "}";

        JSONObject cfg = new JSONObject();
        cfg.put("temperature", 0);
        cfg.put("maxOutputTokens", 1000);
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
        enforceGrounding(d, clean);
        enforceCapturePolicy(d, clean);
        d.rawDecisionJson = d.toPolicyJson(decisionJson).toString();
        if (!d.memoryCreate && !d.situationCreate && d.entities.isEmpty() && !d.feedbackCreate) d.evidenceOnly = true;
        return d;
    }

    private static Decision obviousCapturePolicy(String transcript) throws Exception {
        String t = norm(transcript);
        boolean feedback = containsAny(t,
                "transcription wrong","transcription is wrong","transcript wrong","asr wrong","didn't work","doesn't work",
                "not working","stuck","bug","cortex should","relay should","app should",
                "الترانسكريبت غلط","الترانسكريبت مش","الترانسكريبشن غلط","مش شغال","ما اشتغلش","معلق","علّق","صلح الكورتكس","صلح الابلكيشن");
        boolean test = containsAny(t,
                "test transcription","test the transcription","transcription test","test uh transcription",
                "we're gonna test","we are going to test","just testing","this is a test","test cortex","test capture","test asr",
                "testing cortex capture","testing the transcription",
                "نجرب الترانسكريبت","نجرب الترانسكريبشن","تجربة الترانسكريبت","اختبار الترانسكريبت","بنجرب الترانسكريبت","نجرب الكورتكس","تست للترانسكريبت","تست للترانسكريبشن");
        if (!feedback && !test) return null;

        Decision d = new Decision();
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

    /** Hard safety rail: obvious test/meta or product-feedback captures cannot accidentally become
     * personal state even if the model over-promotes them. */
    private static void enforceCapturePolicy(Decision d, String transcript) {
        String t = norm(transcript);
        boolean feedback = containsAny(t,
                "transcription wrong","transcription is wrong","transcript wrong","asr wrong","didn't work","doesn't work",
                "not working","stuck","bug","cortex should","relay should","app should",
                "الترانسكريبت غلط","الترانسكريبت مش","الترانسكريبشن غلط","مش شغال","ما اشتغلش","معلق","علّق","صلح الكورتكس","صلح الابلكيشن");
        boolean test = containsAny(t,
                "test transcription","test the transcription","transcription test","test uh transcription",
                "we're gonna test","we are going to test","just testing","this is a test","test cortex","test capture","test asr",
                "testing cortex capture","testing the transcription",
                "نجرب الترانسكريبت","نجرب الترانسكريبشن","تجربة الترانسكريبت","اختبار الترانسكريبت","بنجرب الترانسكريبت","نجرب الكورتكس","تست للترانسكريبت","تست للترانسكريبشن");
        if (feedback) {
            d.captureClass="PRODUCT_FEEDBACK"; d.surface="CAPTURE_HISTORY_ONLY"; d.retention="STANDARD";
            d.memoryCreate=false; d.situationCreate=false; d.entities.clear(); d.evidenceOnly=true;
            if(!d.feedbackCreate){d.feedbackCreate=true;d.feedbackCategory="OTHER";d.feedbackSummary=compact(transcript,240);}
        } else if (test) {
            d.captureClass="TEST_META"; d.surface="CAPTURE_HISTORY_ONLY"; d.retention="SHORT";
            d.memoryCreate=false; d.situationCreate=false; d.entities.clear(); d.feedbackCreate=false; d.evidenceOnly=true;
        }
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

    private static boolean containsAny(String s,String... xs){for(String x:xs)if(s.contains(norm(x)))return true;return false;}
    private static String compact(String value, int max) {String x=clean(value).replaceAll("\\s+"," ");return x.length()<=max?x:x.substring(0,max)+"…";}
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
        public boolean memoryCreate, situationCreate, evidenceOnly, feedbackCreate;
        public String memoryTitle="", memoryBody="", situationKey="", situationTitle="", situationSummary="", attention="quiet", reason="", provider="gemini", model="";
        public String captureClass="PERSONAL", surface="NORMAL", retention="STANDARD", feedbackCategory="", feedbackSummary="";
        public String rawProviderResponse="", rawDecisionJson="{}";
        public final List<Entity> entities = new ArrayList<>();

        static Decision from(JSONObject j, String model) {
            Decision d = new Decision(); d.model = model;
            JSONObject p=j.optJSONObject("capture_policy");
            if(p!=null){d.captureClass=policyClass(p.optString("class"));d.surface=surface(p.optString("surface"));d.retention=retention(p.optString("retention"));}
            JSONObject m = j.optJSONObject("memory");
            if (m != null) { d.memoryCreate=m.optBoolean("create",false); d.memoryTitle=clean(m.optString("title")); d.memoryBody=clean(m.optString("body")); }
            JSONObject s = j.optJSONObject("situation");
            if (s != null) {
                d.situationCreate=s.optBoolean("create",false); d.situationTitle=clean(s.optString("title")); d.situationSummary=clean(s.optString("summary"));
                d.situationKey=key(s.optString("canonical_key"),d.situationTitle); String a=clean(s.optString("attention","quiet")).toLowerCase(Locale.ROOT);
                d.attention=a.equals("needs_attention")?"needs_attention":a.equals("watching")?"watching":"quiet";
            }
            JSONArray es=j.optJSONArray("world_entities");
            if(es!=null)for(int i=0;i<es.length();i++){JSONObject e=es.optJSONObject(i);if(e==null)continue;String name=clean(e.optString("name"));if(!name.isEmpty())d.entities.add(new Entity(e.optString("type"),e.optString("canonical_key"),name,e.optString("summary")));}
            JSONObject f=j.optJSONObject("product_feedback");
            if(f!=null){d.feedbackCreate=f.optBoolean("create",false);d.feedbackCategory=feedbackCategory(f.optString("category"));d.feedbackSummary=clean(f.optString("summary"));}
            d.evidenceOnly=j.optBoolean("evidence_only",false); d.reason=clean(j.optString("reason"));
            if(d.memoryCreate&&(d.memoryTitle.isEmpty()||d.memoryBody.isEmpty()))d.memoryCreate=false;
            if(d.situationCreate&&(d.situationTitle.isEmpty()||d.situationSummary.isEmpty()))d.situationCreate=false;
            if(!d.memoryCreate&&!d.situationCreate&&d.entities.isEmpty()&&!d.feedbackCreate)d.evidenceOnly=true;
            return d;
        }

        JSONObject toPolicyJson(JSONObject original) throws Exception {
            JSONObject out=new JSONObject(original.toString());
            JSONObject p=new JSONObject();p.put("class",captureClass);p.put("surface",surface);p.put("retention",retention);out.put("capture_policy",p);
            JSONObject m=new JSONObject();m.put("create",memoryCreate);m.put("title",memoryTitle);m.put("body",memoryBody);out.put("memory",m);
            JSONObject s=new JSONObject();s.put("create",situationCreate);s.put("canonical_key",situationKey);s.put("title",situationTitle);s.put("summary",situationSummary);s.put("attention",attention);out.put("situation",s);
            JSONArray es=new JSONArray();for(Entity e:entities){JSONObject j=new JSONObject();j.put("type",e.type);j.put("canonical_key",e.canonicalKey);j.put("name",e.name);j.put("summary",e.summary);es.put(j);}out.put("world_entities",es);
            JSONObject f=new JSONObject();f.put("create",feedbackCreate);f.put("category",feedbackCategory);f.put("summary",feedbackSummary);out.put("product_feedback",f);
            out.put("evidence_only",evidenceOnly);out.put("reason",reason);return out;
        }

        private static String policyClass(String x){String v=clean(x).toUpperCase(Locale.ROOT);return v.equals("TEST_META")||v.equals("PRODUCT_FEEDBACK")||v.equals("TRANSIENT")?v:"PERSONAL";}
        private static String surface(String x){String v=clean(x).toUpperCase(Locale.ROOT);return v.equals("CAPTURE_HISTORY_ONLY")||v.equals("HIDDEN")?v:"NORMAL";}
        private static String retention(String x){String v=clean(x).toUpperCase(Locale.ROOT);return v.equals("DURABLE")||v.equals("SHORT")?v:"STANDARD";}
        private static String feedbackCategory(String x){String v=clean(x).toUpperCase(Locale.ROOT);return v.equals("ASR")||v.equals("CAPTURE")||v.equals("RELAY")||v.equals("BRAIN")||v.equals("UI")?v:"OTHER";}
    }

    private static String key(String preferred,String fallback){String x=clean(preferred);if(x.isEmpty())x=clean(fallback);x=x.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\p{L}]+","_").replaceAll("^_+|_+$","");return x.isEmpty()?"item":x;}
}
