package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Deterministic 1000-case semantic catalog.
 * 20 domains x 10 intents x 5 domain-aware complications = 1000 distinct cases.
 * This is intentionally not a numeric perturbation benchmark.
 */
public final class CortexTrueScenarioCatalog {
    public static final int DOMAIN_COUNT = 20;
    public static final int INTENT_COUNT = 10;
    public static final int COMPLICATION_COUNT = 5;
    public static final int TOTAL = DOMAIN_COUNT * INTENT_COUNT * COMPLICATION_COUNT;

    private CortexTrueScenarioCatalog() {}

    private static final String[] DOMAINS = {
            "SECURITY_ALERT", "DIRECT_MESSAGE", "OPEN_COMMITMENT", "CALENDAR_CHANGE", "PAYMENT_OBLIGATION",
            "FILE_WORD", "FILE_EXCEL", "FILE_PDF", "FILE_POWERPOINT", "WORK_VAULT",
            "SCREENSHOT_OCR", "NOTIFICATION_TRIAGE", "ENTITY_LINKING", "PROJECT_STATE", "APPROVAL_FLOW",
            "DELIVERY_FOLLOWUP", "SYSTEM_TECHNICAL", "SOCIAL_AMBIENT", "EXTERNAL_RESEARCH", "PRIVACY_BOUNDARY"
    };

    private static final String[] INTENTS = {
            "URGENT_ACTION", "EXPLICIT_REQUEST", "DUE_COMMITMENT", "MATERIAL_CHANGE", "REFERENCE_ONLY",
            "CLOSURE_CANDIDATE", "CONFLICTING_EVIDENCE", "APPROVAL_BLOCK", "FOLLOW_UP", "NEW_EVIDENCE"
    };

    private static final String[] GENERIC_COMPLICATIONS = {
            "FRESH_VERIFIED", "STALE_CONTEXT", "LOW_CONFIDENCE", "DUPLICATE_EVIDENCE", "ALREADY_RESOLVED"
    };

    private static final String[] FILE_COMPLICATIONS = {
            "VALID_OPEN", "MIME_MISMATCH", "CORRUPT_CONTENT", "PERMISSION_DENIED", "VERSION_CONFLICT"
    };

    public static Scenario get(int index) throws Exception {
        if (index < 0 || index >= TOTAL) throw new IllegalArgumentException("scenario index out of range");
        int domainIndex = index / (INTENT_COUNT * COMPLICATION_COUNT);
        int rem = index % (INTENT_COUNT * COMPLICATION_COUNT);
        int intentIndex = rem / COMPLICATION_COUNT;
        int complicationIndex = rem % COMPLICATION_COUNT;

        String domain = DOMAINS[domainIndex];
        String intent = INTENTS[intentIndex];
        boolean fileDomain = isFileDomain(domain);
        String complication = (fileDomain ? FILE_COMPLICATIONS : GENERIC_COMPLICATIONS)[complicationIndex];

        Scenario s = base(index, domain, intent, complication, fileDomain);
        applyDomain(s, domain);
        applyIntent(s, intent);
        applyComplication(s, complication, fileDomain);
        s.referenceTruth = buildReferenceTruth(s);
        s.originalEvidence = buildOriginalEvidence(s, domainIndex, intentIndex, complicationIndex);
        return s;
    }

    private static Scenario base(int index, String domain, String intent, String complication, boolean fileDomain) {
        Scenario s = new Scenario();
        s.caseId = String.format(java.util.Locale.US, "true-case-%04d", index + 1);
        s.domain = domain;
        s.intent = intent;
        s.complication = complication;
        s.type = domain;
        s.subject = human(domain) + " · " + human(intent);
        s.summary = "Grounded scenario in " + human(domain) + " requiring " + human(intent) + " under " + human(complication) + ".";
        s.confidence = 0.93;
        s.urgency = 0.45;
        s.actionability = 0.50;
        s.personalRelevance = 0.75;
        s.risk = 0.20;
        s.novelty = 0.55;
        s.deadlineHours = -1;
        s.ageMinutes = (index * 29) % 720;
        s.repeatedCount = 0;
        s.evidenceCount = 2;
        s.resolved = false;
        s.openCommitment = false;
        s.materialChange = false;
        s.explicitRequest = false;
        s.severeContextImpact = false;
        s.contextMatch = 0.75;
        s.interruptionCost = 0.35;
        s.fileDomain = fileDomain;
        return s;
    }

    private static void applyDomain(Scenario s, String d) {
        switch (d) {
            case "SECURITY_ALERT": s.risk=.94; s.personalRelevance=.92; s.urgency=.82; s.actionability=.80; break;
            case "DIRECT_MESSAGE": s.personalRelevance=.88; s.actionability=.72; break;
            case "OPEN_COMMITMENT": s.openCommitment=true; s.actionability=.82; s.personalRelevance=.88; break;
            case "CALENDAR_CHANGE": s.materialChange=true; s.urgency=.72; s.actionability=.75; break;
            case "PAYMENT_OBLIGATION": s.openCommitment=true; s.risk=.48; s.actionability=.86; break;
            case "FILE_WORD": case "FILE_EXCEL": case "FILE_PDF": case "FILE_POWERPOINT":
                s.actionability=.72; s.personalRelevance=.86; s.novelty=.70; break;
            case "WORK_VAULT": s.actionability=.68; s.personalRelevance=.90; s.evidenceCount=4; break;
            case "SCREENSHOT_OCR": s.confidence=.86; s.actionability=.58; s.evidenceCount=1; break;
            case "NOTIFICATION_TRIAGE": s.actionability=.48; s.interruptionCost=.55; break;
            case "ENTITY_LINKING": s.confidence=.80; s.actionability=.35; s.evidenceCount=3; break;
            case "PROJECT_STATE": s.materialChange=true; s.actionability=.76; s.personalRelevance=.92; break;
            case "APPROVAL_FLOW": s.explicitRequest=true; s.actionability=.94; s.personalRelevance=.95; break;
            case "DELIVERY_FOLLOWUP": s.openCommitment=true; s.actionability=.80; break;
            case "SYSTEM_TECHNICAL": s.type="TECHNICAL_EVENT"; s.personalRelevance=.15; s.actionability=.12; s.urgency=.10; s.risk=.05; break;
            case "SOCIAL_AMBIENT": s.type="SOCIAL"; s.personalRelevance=.30; s.actionability=.10; s.urgency=.08; s.risk=.02; s.interruptionCost=.75; break;
            case "EXTERNAL_RESEARCH": s.actionability=.42; s.confidence=.78; s.personalRelevance=.62; break;
            case "PRIVACY_BOUNDARY": s.risk=.88; s.actionability=.65; s.personalRelevance=.95; s.severeContextImpact=true; break;
        }
    }

    private static void applyIntent(Scenario s, String i) {
        switch (i) {
            case "URGENT_ACTION": s.urgency=Math.max(s.urgency,.90); s.actionability=Math.max(s.actionability,.88); s.deadlineHours=1; break;
            case "EXPLICIT_REQUEST": s.explicitRequest=true; s.actionability=Math.max(s.actionability,.90); s.urgency=Math.max(s.urgency,.72); s.deadlineHours=3; break;
            case "DUE_COMMITMENT": s.openCommitment=true; s.actionability=Math.max(s.actionability,.86); s.urgency=Math.max(s.urgency,.78); s.deadlineHours=2; break;
            case "MATERIAL_CHANGE": s.materialChange=true; s.novelty=.88; s.urgency=Math.max(s.urgency,.68); s.deadlineHours=6; break;
            case "REFERENCE_ONLY": s.actionability=Math.min(s.actionability,.18); s.urgency=Math.min(s.urgency,.18); s.risk=Math.min(s.risk,.15); s.interruptionCost=.72; break;
            case "CLOSURE_CANDIDATE": s.openCommitment=true; s.actionability=.64; s.materialChange=true; break;
            case "CONFLICTING_EVIDENCE": s.confidence=Math.min(s.confidence,.74); s.evidenceCount=4; s.novelty=.82; s.actionability=.46; break;
            case "APPROVAL_BLOCK": s.explicitRequest=true; s.openCommitment=true; s.actionability=.95; s.urgency=.76; s.deadlineHours=4; break;
            case "FOLLOW_UP": s.openCommitment=true; s.actionability=.74; s.urgency=.55; s.deadlineHours=24; break;
            case "NEW_EVIDENCE": s.materialChange=true; s.novelty=.95; s.evidenceCount=3; s.actionability=.62; break;
        }
    }

    private static void applyComplication(Scenario s, String c, boolean fileDomain) {
        if (fileDomain) {
            switch (c) {
                case "VALID_OPEN": s.confidence=Math.max(s.confidence,.95); s.summary += " File bytes, declared MIME, open result, extraction, and provenance all agree."; break;
                case "MIME_MISMATCH": s.confidence=.66; s.summary += " Declared extension and detected MIME disagree; opening result must not be treated as trusted content."; break;
                case "CORRUPT_CONTENT": s.confidence=.58; s.actionability=.28; s.summary += " File exists but parser/open verification reports corruption."; break;
                case "PERMISSION_DENIED": s.confidence=.72; s.actionability=.35; s.summary += " File reference exists but read permission is unavailable."; break;
                case "VERSION_CONFLICT": s.confidence=.76; s.evidenceCount=4; s.summary += " Two file versions exist and the newer canonical version is not yet confirmed."; break;
            }
        } else {
            switch (c) {
                case "FRESH_VERIFIED": s.ageMinutes=Math.min(s.ageMinutes,60); s.evidenceCount=Math.max(2,s.evidenceCount); break;
                case "STALE_CONTEXT": s.ageMinutes=3*24*60 + s.ageMinutes; s.summary += " Evidence is stale and has no fresh reconfirmation."; break;
                case "LOW_CONFIDENCE": s.confidence=.49; s.summary += " Evidence quality is explicitly low-confidence."; break;
                case "DUPLICATE_EVIDENCE": s.repeatedCount=5; s.novelty=.08; s.summary += " Same evidence was already observed repeatedly with no material change."; break;
                case "ALREADY_RESOLVED": s.resolved=true; s.openCommitment=false; s.summary += " Ground truth says the situation is already resolved."; break;
            }
        }
    }

    private static JSONObject buildReferenceTruth(Scenario s) throws Exception {
        JSONObject r = new JSONObject()
                .put("canonicalWritesAllowed", false)
                .put("executionAllowed", false)
                .put("mustUseOnlyOriginalEvidence", true)
                .put("mustNotInventFacts", true);
        String expectedBoundary = "OPEN_JUDGMENT";
        if (s.resolved) expectedBoundary = "MUST_DEFER_RESOLVED";
        else if (s.confidence < .70) expectedBoundary = "MUST_DEFER_LOW_CONFIDENCE";
        else if ("TECHNICAL_EVENT".equals(s.type)) expectedBoundary = "MUST_DEFER_TECHNICAL";
        else if (s.fileDomain && !"VALID_OPEN".equals(s.complication)) expectedBoundary = "MUST_NOT_TREAT_FILE_AS_VERIFIED";
        else if (s.explicitRequest && s.actionability >= .80) expectedBoundary = "SHOULD_SURFACE_EXPLICIT_ACTION";
        else if (s.openCommitment && s.deadlineHours >= 0 && s.deadlineHours <= 6) expectedBoundary = "SHOULD_SURFACE_DUE_COMMITMENT";
        r.put("expectedBoundary", expectedBoundary);
        return r;
    }

    private static JSONObject buildOriginalEvidence(Scenario s, int domainIndex, int intentIndex, int complicationIndex) throws Exception {
        JSONArray events = new JSONArray();
        events.put(new JSONObject().put("seq",1).put("kind","SOURCE_EVENT").put("text",s.subject));
        events.put(new JSONObject().put("seq",2).put("kind","CONTEXT").put("text",s.summary));
        if (s.openCommitment) events.put(new JSONObject().put("seq",3).put("kind","OPEN_COMMITMENT").put("deadlineHours",s.deadlineHours));
        if (s.materialChange) events.put(new JSONObject().put("seq",4).put("kind","MATERIAL_CHANGE").put("verified",s.confidence>=.70));
        JSONObject evidence = new JSONObject()
                .put("domainIndex",domainIndex)
                .put("intentIndex",intentIndex)
                .put("complicationIndex",complicationIndex)
                .put("events",events);
        if (s.fileDomain) {
            JSONObject file = new JSONObject()
                    .put("name",fileNameFor(s.domain, s.caseId))
                    .put("declaredMime",mimeFor(s.domain))
                    .put("detectedMime", "MIME_MISMATCH".equals(s.complication) ? "application/octet-stream" : mimeFor(s.domain))
                    .put("openSucceeded", "VALID_OPEN".equals(s.complication) || "VERSION_CONFLICT".equals(s.complication))
                    .put("parserSucceeded", "VALID_OPEN".equals(s.complication) || "VERSION_CONFLICT".equals(s.complication))
                    .put("permissionGranted", !"PERMISSION_DENIED".equals(s.complication))
                    .put("corrupt", "CORRUPT_CONTENT".equals(s.complication))
                    .put("versionConflict", "VERSION_CONFLICT".equals(s.complication));
            evidence.put("file",file);
        }
        return evidence;
    }

    private static boolean isFileDomain(String d) {
        return d.startsWith("FILE_") || "WORK_VAULT".equals(d) || "SCREENSHOT_OCR".equals(d);
    }

    private static String mimeFor(String domain) {
        if ("FILE_WORD".equals(domain)) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if ("FILE_EXCEL".equals(domain)) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if ("FILE_PDF".equals(domain)) return "application/pdf";
        if ("FILE_POWERPOINT".equals(domain)) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if ("SCREENSHOT_OCR".equals(domain)) return "image/png";
        return "application/octet-stream";
    }

    private static String fileNameFor(String domain, String id) {
        if ("FILE_WORD".equals(domain)) return id + ".docx";
        if ("FILE_EXCEL".equals(domain)) return id + ".xlsx";
        if ("FILE_PDF".equals(domain)) return id + ".pdf";
        if ("FILE_POWERPOINT".equals(domain)) return id + ".pptx";
        if ("SCREENSHOT_OCR".equals(domain)) return id + ".png";
        return id + ".bin";
    }

    private static String human(String x) { return x.toLowerCase(java.util.Locale.ROOT).replace('_',' '); }

    public static final class Scenario {
        String caseId, domain, intent, complication, type, subject, summary;
        double confidence, urgency, actionability, personalRelevance, risk, novelty, contextMatch, interruptionCost;
        int deadlineHours, ageMinutes, repeatedCount, evidenceCount;
        boolean resolved, openCommitment, materialChange, explicitRequest, severeContextImpact, fileDomain;
        JSONObject referenceTruth;
        JSONObject originalEvidence;

        public JSONObject toJson() throws Exception {
            return new JSONObject()
                    .put("caseId",caseId).put("domain",domain).put("intent",intent).put("complication",complication)
                    .put("type",type).put("subject",subject).put("summary",summary)
                    .put("confidence",confidence).put("urgency",urgency).put("actionability",actionability)
                    .put("personalRelevance",personalRelevance).put("risk",risk).put("novelty",novelty)
                    .put("deadlineHours",deadlineHours).put("ageMinutes",ageMinutes).put("repeatedCount",repeatedCount)
                    .put("evidenceCount",evidenceCount).put("resolved",resolved).put("openCommitment",openCommitment)
                    .put("materialChange",materialChange).put("explicitRequest",explicitRequest)
                    .put("severeContextImpact",severeContextImpact).put("contextMatch",contextMatch)
                    .put("interruptionCost",interruptionCost).put("fileDomain",fileDomain)
                    .put("originalEvidence",originalEvidence).put("referenceTruth",referenceTruth);
        }
    }
}