package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/**
 * Conservative World candidate extraction from explicit structured evidence metadata.
 * Free text is intentionally not entity-guessed here; a model/user extractor can feed the same resolver later.
 */
public final class CognitiveWorldCandidateExtractorV4 {
    private CognitiveWorldCandidateExtractorV4() {}

    public static List<CognitiveWorldResolverV4.Candidate> fromMemory(VaultDb db, String memoryId) {
        if (db == null) throw new IllegalArgumentException("db required");
        if (memoryId == null || memoryId.trim().isEmpty()) throw new IllegalArgumentException("memoryId required");
        CognitiveStoreV4.ensure(db);
        SQLiteDatabase sql = db.getReadableDatabase();
        ArrayList<CognitiveWorldResolverV4.Candidate> out = new ArrayList<>();
        Cursor c = sql.rawQuery(
                "SELECT e.id,e.metadata_json,e.occurred_at,e.source_package FROM v4_memory_evidence me " +
                        "JOIN v4_evidence e ON e.id=me.evidence_id WHERE me.memory_id=? ORDER BY me.ordinal ASC,e.id ASC",
                new String[]{memoryId});
        try {
            while (c.moveToNext()) {
                String evidenceId = c.getString(0);
                String metadata = c.getString(1);
                long at = c.getLong(2);
                String sourcePackage = c.getString(3);
                extractStructured(out, memoryId, evidenceId, metadata, sourcePackage, at);
            }
        } finally { c.close(); }
        return Collections.unmodifiableList(out);
    }

    private static void extractStructured(List<CognitiveWorldResolverV4.Candidate> out, String memoryId,
                                          String evidenceId, String metadata, String sourcePackage, long at) {
        JSONObject root = json(metadata);
        JSONObject source = root.optJSONObject("source_metadata");
        if (source == null) source = new JSONObject();

        String personName = first(root, source, "person_name", "participant_name", "sender_name", "contact_name");
        String contactId = first(root, source, "contact_id");
        String phone = first(root, source, "phone_e164", "phone");
        String accountId = first(root, source, "account_id", "sender_id", "participant_id");
        if (!personName.isEmpty()) {
            ArrayList<CognitiveIdentityV4.IdentityClaim> claims = new ArrayList<>();
            claims.add(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, personName, CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId));
            if (!contactId.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.CONTACT_ID, contactId, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            if (!phone.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.PHONE_E164, phone, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            if (!accountId.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.ACCOUNT_ID, accountId, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            out.add(candidate(personName, CognitiveDomainV4.WorldTypeHint.PERSON, claims, memoryId, evidenceId, at));
        }

        String projectName = first(root, source, "project_name");
        String projectId = first(root, source, "project_id");
        if (!projectName.isEmpty()) {
            ArrayList<CognitiveIdentityV4.IdentityClaim> claims = new ArrayList<>();
            claims.add(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, projectName, CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId));
            if (!projectId.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.EXTERNAL_ID, projectId, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            out.add(candidate(projectName, CognitiveDomainV4.WorldTypeHint.PROJECT, claims, memoryId, evidenceId, at));
        }

        String organization = first(root, source, "organization_name", "company_name");
        String domain = first(root, source, "domain", "organization_domain");
        if (!organization.isEmpty()) {
            ArrayList<CognitiveIdentityV4.IdentityClaim> claims = new ArrayList<>();
            claims.add(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, organization, CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId));
            if (!domain.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.DOMAIN, domain, CognitiveIdentityV4.ClaimStrength.MEDIUM, evidenceId));
            if (sourcePackage != null && !sourcePackage.trim().isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.PACKAGE_NAME, sourcePackage, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            out.add(candidate(organization, CognitiveDomainV4.WorldTypeHint.ORGANIZATION, claims, memoryId, evidenceId, at));
        }

        String placeName = first(root, source, "place_name", "location_name");
        String placeId = first(root, source, "place_id", "location_id");
        if (!placeName.isEmpty()) {
            ArrayList<CognitiveIdentityV4.IdentityClaim> claims = new ArrayList<>();
            claims.add(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, placeName, CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId));
            if (!placeId.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.EXTERNAL_ID, placeId, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            out.add(candidate(placeName, CognitiveDomainV4.WorldTypeHint.PLACE, claims, memoryId, evidenceId, at));
        }

        String topic = first(root, source, "topic", "topic_name");
        if (!topic.isEmpty()) {
            out.add(candidate(topic, CognitiveDomainV4.WorldTypeHint.TOPIC,
                    Collections.singletonList(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, topic,
                            CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId)), memoryId, evidenceId, at));
        }
    }

    private static CognitiveWorldResolverV4.Candidate candidate(
            String name, CognitiveDomainV4.WorldTypeHint type,
            List<CognitiveIdentityV4.IdentityClaim> claims, String memoryId, String evidenceId, long at) {
        return new CognitiveWorldResolverV4.Candidate(name, type, Collections.<String>emptyList(), claims,
                Collections.singletonList(evidenceId), Collections.singletonList(memoryId), at, false);
    }

    private static CognitiveIdentityV4.IdentityClaim claim(
            CognitiveIdentityV4.ClaimType type, String value, CognitiveIdentityV4.ClaimStrength strength, String evidenceId) {
        return new CognitiveIdentityV4.IdentityClaim(type, value, strength, false, evidenceId);
    }

    private static JSONObject json(String raw) {
        try { return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw); }
        catch (Throwable ignored) { return new JSONObject(); }
    }

    private static String first(JSONObject a, JSONObject b, String... keys) {
        for (String key : keys) {
            String x = a.optString(key, "").trim();
            if (!x.isEmpty()) return x;
            x = b.optString(key, "").trim();
            if (!x.isEmpty()) return x;
        }
        return "";
    }
}
