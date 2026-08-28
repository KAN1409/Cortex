package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads entity proposals already preserved in v4_evidence_analysis.
 *
 * <p>Analysis/model entities are retrieval/enrichment hints, never identity authority. They are
 * grounded to the Evidence that produced them and intentionally use weak MODEL_ALIAS claims so
 * they cannot create or merge canonical Worlds without corroboration or user confirmation.</p>
 */
public final class CognitiveWorldAnalysisCandidateExtractorV4 {
    private CognitiveWorldAnalysisCandidateExtractorV4() {}

    static List<CognitiveWorldResolverV4.Candidate> fromEvidence(
            SQLiteDatabase sql, String memoryId, String evidenceId, long observedAt) {
        if (sql == null || empty(memoryId) || empty(evidenceId)) return Collections.emptyList();
        ArrayList<CognitiveWorldResolverV4.Candidate> out = new ArrayList<>();
        Cursor c = sql.query(
                "v4_evidence_analysis",
                new String[]{"output_json"},
                "evidence_id=? AND COALESCE(output_json,'')<>''",
                new String[]{evidenceId}, null, null, "id ASC");
        try {
            while (c.moveToNext()) extractJson(out, memoryId, evidenceId, observedAt, c.getString(0));
        } finally { c.close(); }
        return Collections.unmodifiableList(out);
    }

    private static void extractJson(List<CognitiveWorldResolverV4.Candidate> out,
                                    String memoryId, String evidenceId, long at, String raw) {
        JSONObject root = json(raw);
        JSONArray entities = root.optJSONArray("entities");
        if (entities == null) {
            JSONObject analysis = root.optJSONObject("analysis");
            if (analysis != null) entities = analysis.optJSONArray("entities");
        }
        if (entities == null) return;

        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) continue;
            String kind = clean(entity.optString("kind", "")).toUpperCase(Locale.ROOT);
            String rawValue = clean(entity.optString("value", ""));
            double confidence = entity.optDouble("confidence", 0.0);
            if (rawValue.length() < 2 || rawValue.length() > 160 || confidence < 0.60) continue;
            if (CognitiveWorldCandidateClassifierV4.looksGenericSystemLabel(rawValue)) continue;

            CognitiveDomainV4.WorldTypeHint type = typeFor(kind);
            if (type == null) continue;

            CognitiveWorldProposalQualityV4.Result quality =
                    CognitiveWorldProposalQualityV4.inspect(type, rawValue);
            if (!quality.accepted) continue;
            String value = quality.canonicalName;

            // Analysis-derived type is always provisional. A repeated/model-extracted label may be
            // useful for review and retrieval, but it does not establish canonical semantic type.
            boolean typeApproved = false;
            CognitiveIdentityV4.IdentityClaim claim = new CognitiveIdentityV4.IdentityClaim(
                    CognitiveIdentityV4.ClaimType.MODEL_ALIAS,
                    value,
                    CognitiveIdentityV4.ClaimStrength.WEAK,
                    false,
                    evidenceId);
            CognitiveWorldResolverV4.Candidate candidate = new CognitiveWorldResolverV4.Candidate(
                    value,
                    type,
                    rawValue.equals(value) ? Collections.<String>emptyList() : Collections.singletonList(rawValue),
                    Collections.singletonList(claim),
                    Collections.singletonList(evidenceId),
                    Collections.singletonList(memoryId),
                    at,
                    false,
                    typeApproved);
            addUnique(out, candidate);
        }
    }

    private static CognitiveDomainV4.WorldTypeHint typeFor(String kind) {
        if ("PERSON".equals(kind) || "PEOPLE".equals(kind)) return CognitiveDomainV4.WorldTypeHint.PERSON;
        if ("PROJECT".equals(kind)) return CognitiveDomainV4.WorldTypeHint.PROJECT;
        if ("ORGANIZATION".equals(kind) || "ORG".equals(kind) || "COMPANY".equals(kind)) return CognitiveDomainV4.WorldTypeHint.ORGANIZATION;
        if ("PLACE".equals(kind) || "LOCATION".equals(kind)) return CognitiveDomainV4.WorldTypeHint.PLACE;
        if ("TOPIC".equals(kind)) return CognitiveDomainV4.WorldTypeHint.TOPIC;
        if ("PRODUCT".equals(kind)) return CognitiveDomainV4.WorldTypeHint.PRODUCT;
        return null;
    }

    private static void addUnique(List<CognitiveWorldResolverV4.Candidate> out,
                                  CognitiveWorldResolverV4.Candidate candidate) {
        String key = candidate.typeHint.name() + "|" + CognitiveIdentityV4.normalizeText(candidate.canonicalName);
        for (CognitiveWorldResolverV4.Candidate existing : out) {
            String other = existing.typeHint.name() + "|" + CognitiveIdentityV4.normalizeText(existing.canonicalName);
            if (key.equals(other)) return;
        }
        out.add(candidate);
    }

    private static JSONObject json(String raw) {
        try { return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw); }
        catch (Throwable ignored) { return new JSONObject(); }
    }

    private static boolean empty(String value) { return value == null || value.trim().isEmpty(); }
    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }
}
