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
        CognitiveStoreV4.ensure(db);
        return fromMemory(db.getReadableDatabase(), memoryId);
    }

    static List<CognitiveWorldResolverV4.Candidate> fromMemory(SQLiteDatabase sql, String memoryId) {
        if (sql == null) throw new IllegalArgumentException("db required");
        if (memoryId == null || memoryId.trim().isEmpty()) throw new IllegalArgumentException("memoryId required");
        ArrayList<CognitiveWorldResolverV4.Candidate> out = new ArrayList<>();
        Cursor c = sql.rawQuery(
                "SELECT e.id,e.metadata_json,e.occurred_at,e.source_package FROM v4_memory_evidence me " +
                        "JOIN v4_evidence e ON e.id=me.evidence_id WHERE me.memory_id=? ORDER BY me.ordinal ASC,e.id ASC",
                new String[]{memoryId});
        try {
            while (c.moveToNext()) {
                extractStructured(out, memoryId, c.getString(0), c.getString(1), c.getString(3), c.getLong(2));
            }
        } finally { c.close(); }
        return Collections.unmodifiableList(out);
    }

    private static void extractStructured(List<CognitiveWorldResolverV4.Candidate> out, String memoryId,
                                          String evidenceId, String metadata, String sourcePackage, long at) {
        JSONObject root = json(metadata);
        JSONObject legacy = object(root, "legacy_metadata");
        JSONObject source = object(legacy, "source_metadata");
        if (source.length() == 0) source = object(root, "source_metadata");

        CognitiveWorldCandidateClassifierV4.Decision semantic =
                CognitiveWorldCandidateClassifierV4.inspect(sourcePackage, metadata);
        if (semantic.semanticClass == CognitiveWorldCandidateClassifierV4.SemanticClass.PERSON
                && !semantic.candidateName.isEmpty()) {
            String contactId = first(root, legacy, source, "contact_id");
            String phone = first(root, legacy, source, "phone_e164", "phone");
            String accountId = first(root, legacy, source, "account_id", "sender_id", "participant_id");
            String participantKey = first(root, legacy, source, "participant_key", "sender_key");
            String participantUri = first(root, legacy, source, "participant_uri", "sender_uri");

            ArrayList<CognitiveIdentityV4.IdentityClaim> claims = new ArrayList<>();
            claims.add(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, semantic.candidateName,
                    CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId));
            if (!contactId.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.CONTACT_ID,
                    contactId, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            if (!phone.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.PHONE_E164,
                    phone, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            if (!accountId.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.ACCOUNT_ID,
                    scoped(sourcePackage, accountId), CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            if (!participantKey.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.ACCOUNT_ID,
                    scoped(sourcePackage, "key:" + participantKey), CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            if (!participantUri.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.ACCOUNT_ID,
                    scoped(sourcePackage, "uri:" + participantUri), CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));

            out.add(candidate(semantic.candidateName, CognitiveDomainV4.WorldTypeHint.PERSON,
                    claims, memoryId, evidenceId, at, semantic.typeMaterializationApproved));
        }

        String projectName = first(root, legacy, source, "project_name");
        String projectId = first(root, legacy, source, "project_id");
        if (!projectName.isEmpty()) {
            ArrayList<CognitiveIdentityV4.IdentityClaim> claims = new ArrayList<>();
            claims.add(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, projectName,
                    CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId));
            if (!projectId.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.EXTERNAL_ID,
                    projectId, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            out.add(candidate(projectName, CognitiveDomainV4.WorldTypeHint.PROJECT,
                    claims, memoryId, evidenceId, at, true));
        }

        String organization = first(root, legacy, source, "organization_name", "company_name");
        String domain = first(root, legacy, source, "domain", "organization_domain");
        String organizationPackage = first(root, legacy, source, "organization_package");
        if (!organization.isEmpty()) {
            ArrayList<CognitiveIdentityV4.IdentityClaim> claims = new ArrayList<>();
            claims.add(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, organization,
                    CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId));
            if (!domain.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.DOMAIN,
                    domain, CognitiveIdentityV4.ClaimStrength.MEDIUM, evidenceId));
            if (!organizationPackage.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.PACKAGE_NAME,
                    organizationPackage, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            out.add(candidate(organization, CognitiveDomainV4.WorldTypeHint.ORGANIZATION,
                    claims, memoryId, evidenceId, at, true));
        }

        String placeName = first(root, legacy, source, "place_name", "location_name");
        String placeId = first(root, legacy, source, "place_id", "location_id");
        if (!placeName.isEmpty()) {
            ArrayList<CognitiveIdentityV4.IdentityClaim> claims = new ArrayList<>();
            claims.add(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, placeName,
                    CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId));
            if (!placeId.isEmpty()) claims.add(claim(CognitiveIdentityV4.ClaimType.EXTERNAL_ID,
                    placeId, CognitiveIdentityV4.ClaimStrength.STRONG, evidenceId));
            out.add(candidate(placeName, CognitiveDomainV4.WorldTypeHint.PLACE,
                    claims, memoryId, evidenceId, at, true));
        }

        String topic = first(root, legacy, source, "topic", "topic_name");
        if (!topic.isEmpty()) {
            out.add(candidate(topic, CognitiveDomainV4.WorldTypeHint.TOPIC,
                    Collections.singletonList(claim(CognitiveIdentityV4.ClaimType.EXACT_NAME, topic,
                            CognitiveIdentityV4.ClaimStrength.WEAK, evidenceId)),
                    memoryId, evidenceId, at, true));
        }
    }

    private static CognitiveWorldResolverV4.Candidate candidate(
            String name, CognitiveDomainV4.WorldTypeHint type,
            List<CognitiveIdentityV4.IdentityClaim> claims, String memoryId, String evidenceId,
            long at, boolean typeMaterializationApproved) {
        return new CognitiveWorldResolverV4.Candidate(name, type, Collections.<String>emptyList(), claims,
                Collections.singletonList(evidenceId), Collections.singletonList(memoryId),
                at, false, typeMaterializationApproved);
    }

    private static CognitiveIdentityV4.IdentityClaim claim(
            CognitiveIdentityV4.ClaimType type, String value, CognitiveIdentityV4.ClaimStrength strength, String evidenceId) {
        return new CognitiveIdentityV4.IdentityClaim(type, value, strength, false, evidenceId);
    }

    private static String scoped(String sourcePackage, String value) {
        String pkg = sourcePackage == null ? "" : sourcePackage.trim().toLowerCase(java.util.Locale.ROOT);
        String v = value == null ? "" : value.trim();
        return pkg.isEmpty() ? v : pkg + "|" + v;
    }

    private static JSONObject json(String raw) {
        try { return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw); }
        catch (Throwable ignored) { return new JSONObject(); }
    }

    private static JSONObject object(JSONObject parent, String key) {
        if (parent == null) return new JSONObject();
        JSONObject x = parent.optJSONObject(key);
        return x == null ? new JSONObject() : x;
    }

    private static String first(JSONObject a, JSONObject b, JSONObject c, String... keys) {
        JSONObject[] sources = new JSONObject[]{a, b, c};
        for (String key : keys) {
            for (JSONObject source : sources) {
                if (source == null) continue;
                String x = source.optString(key, "").trim();
                if (!x.isEmpty()) return x;
            }
        }
        return "";
    }
}
