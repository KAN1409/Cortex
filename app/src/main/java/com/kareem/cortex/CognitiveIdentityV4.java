package com.kareem.cortex;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic identity policy for the V4 Evidence / Worlds / Facts / Situations model.
 *
 * <p>Identity is deliberately stricter than semantic similarity. Similar-looking text may be
 * retrieved together without becoming the same canonical object. Automatic destructive merges
 * are allowed only from durable identity anchors.</p>
 */
public final class CognitiveIdentityV4 {
    private CognitiveIdentityV4() {}

    public enum ClaimType {
        USER_KEY,
        CONTACT_ID,
        PHONE_E164,
        EMAIL,
        ACCOUNT_ID,
        PACKAGE_NAME,
        DOMAIN,
        CANONICAL_URL,
        EXTERNAL_ID,
        EXACT_NAME,
        MODEL_ALIAS
    }

    public enum ClaimStrength {
        STRONG,
        MEDIUM,
        WEAK
    }

    public enum MatchDecision {
        SAME,
        POSSIBLE,
        DISTINCT
    }

    public static final class IdentityClaim {
        public final ClaimType type;
        public final String value;
        public final String normalizedValue;
        public final ClaimStrength strength;
        public final boolean userConfirmed;
        public final String evidenceId;

        public IdentityClaim(
                ClaimType type,
                String value,
                ClaimStrength strength,
                boolean userConfirmed,
                String evidenceId) {
            if (type == null) throw new IllegalArgumentException("type required");
            if (strength == null) throw new IllegalArgumentException("strength required");
            this.type = type;
            this.value = value == null ? "" : value.trim();
            this.normalizedValue = normalizeClaim(type, this.value);
            this.strength = strength;
            this.userConfirmed = userConfirmed;
            this.evidenceId = evidenceId == null ? "" : evidenceId.trim();
            if (this.normalizedValue.isEmpty()) throw new IllegalArgumentException("claim value required");
        }
    }

    public static final class Match {
        public final MatchDecision decision;
        public final double confidence;
        public final String reason;

        Match(MatchDecision decision, double confidence, String reason) {
            this.decision = decision;
            this.confidence = clamp01(confidence);
            this.reason = reason == null ? "" : reason;
        }

        public boolean canAutoMerge() {
            return decision == MatchDecision.SAME && confidence >= 0.98;
        }
    }

    /**
     * Stable evidence revision identity.
     *
     * <p>When the platform provides an external identity (notification key, capture UUID, file
     * identity), the same external object + same content hash is the same evidence revision no
     * matter how many callbacks are delivered. Without an external identity, bounded time buckets
     * prevent high-volume screen/app callbacks from duplicating while avoiding permanent collapse
     * of repeated real events.</p>
     */
    public static String evidenceKey(
            CognitiveDomainV4.EvidenceSourceType sourceType,
            String sourcePackage,
            String externalId,
            String contentHash,
            String normalizedText,
            long occurredAt) {
        if (sourceType == null) throw new IllegalArgumentException("sourceType required");
        String pkg = normalizePackage(sourcePackage);
        String ext = normalizeText(externalId);
        String hash = normalizeHash(contentHash);
        if (hash.isEmpty()) hash = Fingerprint.text(normalizeText(normalizedText));

        if (!ext.isEmpty()) {
            return "evidence|" + sourceType.name() + "|" + pkg + "|ext:" + ext + "|hash:" + hash;
        }

        // Assets are intrinsically content-addressable. Re-importing the exact bytes should reuse
        // the same evidence asset identity unless the caller supplies an explicit external ID.
        if (isContentAddressable(sourceType) && !hash.isEmpty()) {
            return "evidence|" + sourceType.name() + "|" + pkg + "|asset:" + hash;
        }

        long bucket = timeBucket(sourceType, occurredAt);
        return "evidence|" + sourceType.name() + "|" + pkg + "|hash:" + hash + "|t:" + bucket;
    }

    public static String evidenceId(
            CognitiveDomainV4.EvidenceSourceType sourceType,
            String sourcePackage,
            String externalId,
            String contentHash,
            String normalizedText,
            long occurredAt) {
        return objectId("ev", evidenceKey(sourceType, sourcePackage, externalId, contentHash, normalizedText, occurredAt));
    }

    /** Episodes are mutable groupings; allocate their opaque ID once and persist the identity key. */
    public static String episodeIdentityKey(
            CognitiveDomainV4.EpisodeKind kind,
            String primarySourcePackage,
            String durableContextKey,
            long startedAt) {
        if (kind == null) throw new IllegalArgumentException("kind required");
        String context = normalizeText(durableContextKey);
        long bucketMs = episodeBucketMs(kind);
        long bucket = bucketMs <= 0 ? startedAt : Math.max(0L, startedAt) / bucketMs;
        return "episode|" + kind.name() + "|" + normalizePackage(primarySourcePackage)
                + "|ctx:" + context + "|t:" + bucket;
    }

    /** Memory identity describes a retrievable unit, not the current title generated for it. */
    public static String memoryIdentityKey(
            CognitiveDomainV4.MemoryKind kind,
            String episodeId,
            List<String> evidenceIds,
            String stableSemanticAnchor) {
        if (kind == null) throw new IllegalArgumentException("kind required");
        String episode = normalizeText(episodeId);
        List<String> evidence = sortedIds(evidenceIds);
        String anchor = normalizeText(stableSemanticAnchor);
        StringBuilder b = new StringBuilder("memory|").append(kind.name()).append("|ep:").append(episode);
        for (String id : evidence) b.append("|ev:").append(id);
        if (!anchor.isEmpty()) b.append("|a:").append(anchor);
        return b.toString();
    }

    public static String factSlotKey(String subjectWorldId, String predicate) {
        return "fact-slot|" + normalizeText(subjectWorldId) + "|" + normalizePredicate(predicate);
    }

    public static String factVersionKey(
            String subjectWorldId,
            String predicate,
            String value,
            Long validFrom) {
        String slot = factSlotKey(subjectWorldId, predicate);
        return slot + "|value:" + normalizeText(value) + "|from:" + (validFrom == null ? 0 : validFrom.longValue());
    }

    public static String relationIdentityKey(
            CognitiveDomainV4.CanonicalObjectType sourceType,
            String sourceId,
            CognitiveDomainV4.RelationType relationType,
            CognitiveDomainV4.CanonicalObjectType targetType,
            String targetId) {
        if (sourceType == null || targetType == null || relationType == null) {
            throw new IllegalArgumentException("relation types required");
        }
        return "relation|" + sourceType.name() + ":" + normalizeText(sourceId)
                + "|" + relationType.name() + "|" + targetType.name() + ":" + normalizeText(targetId);
    }

    /**
     * Stable Situation identity for one unresolved reality.
     *
     * <p>The semantic anchor should represent the obligation/change itself, not notification copy.
     * Long-lived kinds deliberately ignore clock time. Event-shaped kinds may add occurrenceKey
     * (transaction ID, appointment ID, due-date bucket, etc.) so two real events are not merged.</p>
     */
    public static String situationIdentityKey(
            CognitiveDomainV4.SituationKind kind,
            String primaryWorldId,
            String semanticAnchor,
            String occurrenceKey) {
        if (kind == null) throw new IllegalArgumentException("kind required");
        String world = normalizeText(primaryWorldId);
        String anchor = normalizeText(semanticAnchor);
        if (anchor.isEmpty()) throw new IllegalArgumentException("semanticAnchor required");
        String occurrence = normalizeText(occurrenceKey);
        StringBuilder b = new StringBuilder("situation|").append(kind.name())
                .append("|world:").append(world).append("|anchor:").append(anchor);
        if (requiresOccurrenceDiscriminator(kind) && !occurrence.isEmpty()) b.append("|event:").append(occurrence);
        return b.toString();
    }

    public static String situationId(
            CognitiveDomainV4.SituationKind kind,
            String primaryWorldId,
            String semanticAnchor,
            String occurrenceKey) {
        return objectId("si", situationIdentityKey(kind, primaryWorldId, semanticAnchor, occurrenceKey));
    }

    /** Conservative World matching. Exact names alone never auto-merge. */
    public static Match matchWorlds(
            CognitiveDomainV4.WorldTypeHint leftType,
            List<IdentityClaim> leftClaims,
            CognitiveDomainV4.WorldTypeHint rightType,
            List<IdentityClaim> rightClaims) {
        List<IdentityClaim> a = leftClaims == null ? Collections.emptyList() : leftClaims;
        List<IdentityClaim> b = rightClaims == null ? Collections.emptyList() : rightClaims;

        Map<ClaimType, Set<String>> av = byType(a);
        Map<ClaimType, Set<String>> bv = byType(b);

        // Explicit user identity is the strongest authority.
        if (sharedUserConfirmed(a, b)) return new Match(MatchDecision.SAME, 1.0, "shared user-confirmed identity anchor");

        for (ClaimType t : strongAnchorOrder(leftType, rightType)) {
            String shared = firstShared(av.get(t), bv.get(t));
            if (shared != null) return new Match(MatchDecision.SAME, 0.995, "shared durable " + t.name().toLowerCase(Locale.ROOT));
        }

        // A direct user key conflict is the rare case where we can say distinct deterministically.
        if (disjointNonEmpty(av.get(ClaimType.USER_KEY), bv.get(ClaimType.USER_KEY))) {
            return new Match(MatchDecision.DISTINCT, 0.999, "different explicit user identity keys");
        }

        double evidence = 0.0;
        String reason = "no durable shared identity";
        if (firstShared(av.get(ClaimType.EMAIL), bv.get(ClaimType.EMAIL)) != null) {
            evidence = Math.max(evidence, 0.96);
            reason = "shared email";
        }
        if (firstShared(av.get(ClaimType.DOMAIN), bv.get(ClaimType.DOMAIN)) != null
                && organizationLike(leftType, rightType)) {
            evidence = Math.max(evidence, 0.94);
            reason = "shared organization domain";
        }
        if (firstShared(av.get(ClaimType.EXACT_NAME), bv.get(ClaimType.EXACT_NAME)) != null) {
            evidence = Math.max(evidence, 0.72);
            reason = "same normalized name only";
        }
        if (firstShared(av.get(ClaimType.MODEL_ALIAS), bv.get(ClaimType.MODEL_ALIAS)) != null) {
            evidence = Math.max(evidence, 0.60);
            reason = "model alias similarity only";
        }

        return new Match(MatchDecision.POSSIBLE, evidence, reason);
    }

    public static String worldSeedKey(
            CognitiveDomainV4.WorldTypeHint type,
            String canonicalName,
            List<IdentityClaim> claims) {
        if (type == null) throw new IllegalArgumentException("type required");
        List<IdentityClaim> xs = claims == null ? Collections.emptyList() : new ArrayList<>(claims);
        xs.sort(Comparator.comparing((IdentityClaim c) -> c.type.name()).thenComparing(c -> c.normalizedValue));
        for (IdentityClaim c : xs) {
            if (c.userConfirmed || c.strength == ClaimStrength.STRONG) {
                return "world|" + type.name() + "|" + c.type.name() + ":" + c.normalizedValue;
            }
        }
        return "world-candidate|" + type.name() + "|name:" + normalizeText(canonicalName);
    }

    public static String objectId(String prefix, String identityKey) {
        if (prefix == null || prefix.trim().isEmpty()) throw new IllegalArgumentException("prefix required");
        String hash = Fingerprint.text(identityKey == null ? "" : identityKey);
        String shortHash = hash.length() <= 24 ? hash : hash.substring(0, 24);
        return prefix.toLowerCase(Locale.ROOT) + "_" + shortHash;
    }

    public static String normalizeText(String raw) {
        if (raw == null) return "";
        String x = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
        x = x.replace('\u200e', ' ').replace('\u200f', ' ').replace('\u061c', ' ');
        x = x.replaceAll("[\\p{Z}\\s]+", " ");
        return x.trim();
    }

    private static String normalizePredicate(String raw) {
        return normalizeText(raw).replace(' ', '_');
    }

    private static String normalizePackage(String raw) {
        return normalizeText(raw).replace(" ", "");
    }

    private static String normalizeHash(String raw) {
        String x = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return x.matches("[0-9a-f]{16,128}") ? x : "";
    }

    private static String normalizeClaim(ClaimType type, String raw) {
        String x = normalizeText(raw);
        if (type == ClaimType.PHONE_E164) return x.replaceAll("[^0-9+]", "");
        if (type == ClaimType.EMAIL || type == ClaimType.DOMAIN || type == ClaimType.PACKAGE_NAME) {
            return x.replace(" ", "");
        }
        if (type == ClaimType.CANONICAL_URL) {
            x = x.replaceAll("#.*$", "");
            while (x.endsWith("/")) x = x.substring(0, x.length() - 1);
        }
        return x;
    }

    private static boolean isContentAddressable(CognitiveDomainV4.EvidenceSourceType t) {
        return t == CognitiveDomainV4.EvidenceSourceType.IMAGE
                || t == CognitiveDomainV4.EvidenceSourceType.FILE
                || t == CognitiveDomainV4.EvidenceSourceType.VOICE;
    }

    private static long timeBucket(CognitiveDomainV4.EvidenceSourceType t, long occurredAt) {
        long ms;
        switch (t) {
            case SCREEN: ms = 10_000L; break;
            case APP_ACTIVITY: ms = 30_000L; break;
            case NOTIFICATION: ms = 60_000L; break;
            case SYSTEM: ms = 60_000L; break;
            default: ms = 1_000L; break;
        }
        return Math.max(0L, occurredAt) / ms;
    }

    private static long episodeBucketMs(CognitiveDomainV4.EpisodeKind kind) {
        switch (kind) {
            case CONVERSATION: return 30L * 60L * 1000L;
            case APP_SESSION: return 5L * 60L * 1000L;
            case RESEARCH: return 30L * 60L * 1000L;
            case DOCUMENT_WORK: return 30L * 60L * 1000L;
            case MEETING: return 2L * 60L * 60L * 1000L;
            case TRAVEL: return 2L * 60L * 60L * 1000L;
            case HEALTH_EVENT: return 6L * 60L * 60L * 1000L;
            default: return 10L * 60L * 1000L;
        }
    }

    private static boolean requiresOccurrenceDiscriminator(CognitiveDomainV4.SituationKind kind) {
        switch (kind) {
            case COMMITMENT:
            case WAITING:
            case UNRESOLVED_QUESTION:
            case FOLLOW_UP:
                return false;
            default:
                return true;
        }
    }

    private static List<ClaimType> strongAnchorOrder(
            CognitiveDomainV4.WorldTypeHint left,
            CognitiveDomainV4.WorldTypeHint right) {
        ArrayList<ClaimType> out = new ArrayList<>();
        out.add(ClaimType.USER_KEY);
        if (left == CognitiveDomainV4.WorldTypeHint.PERSON || right == CognitiveDomainV4.WorldTypeHint.PERSON) {
            out.add(ClaimType.CONTACT_ID);
            out.add(ClaimType.PHONE_E164);
            out.add(ClaimType.ACCOUNT_ID);
        }
        if (organizationLike(left, right)) {
            out.add(ClaimType.ACCOUNT_ID);
            out.add(ClaimType.PACKAGE_NAME);
        }
        out.add(ClaimType.CANONICAL_URL);
        out.add(ClaimType.EXTERNAL_ID);
        return out;
    }

    private static boolean organizationLike(
            CognitiveDomainV4.WorldTypeHint left,
            CognitiveDomainV4.WorldTypeHint right) {
        return left == CognitiveDomainV4.WorldTypeHint.ORGANIZATION
                || right == CognitiveDomainV4.WorldTypeHint.ORGANIZATION;
    }

    private static Map<ClaimType, Set<String>> byType(List<IdentityClaim> xs) {
        HashMap<ClaimType, Set<String>> out = new HashMap<>();
        for (IdentityClaim c : xs) {
            if (c == null || c.normalizedValue.isEmpty()) continue;
            out.computeIfAbsent(c.type, k -> new HashSet<>()).add(c.normalizedValue);
        }
        return out;
    }

    private static boolean sharedUserConfirmed(List<IdentityClaim> a, List<IdentityClaim> b) {
        for (IdentityClaim x : a) {
            if (x == null || !x.userConfirmed) continue;
            for (IdentityClaim y : b) {
                if (y == null || !y.userConfirmed) continue;
                if (x.type == y.type && x.normalizedValue.equals(y.normalizedValue)) return true;
            }
        }
        return false;
    }

    private static String firstShared(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return null;
        for (String x : a) if (b.contains(x)) return x;
        return null;
    }

    private static boolean disjointNonEmpty(Set<String> a, Set<String> b) {
        return a != null && b != null && !a.isEmpty() && !b.isEmpty() && firstShared(a, b) == null;
    }

    private static List<String> sortedIds(List<String> ids) {
        ArrayList<String> out = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                String x = normalizeText(id);
                if (!x.isEmpty() && !out.contains(x)) out.add(x);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static double clamp01(double x) {
        if (Double.isNaN(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }
}
