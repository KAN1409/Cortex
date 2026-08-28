package com.kareem.cortex;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Quality gate for analysis-derived World proposals only. */
public final class CognitiveWorldProposalQualityV4 {
    private CognitiveWorldProposalQualityV4() {}

    private static final Set<String> GENERIC = new HashSet<>(Arrays.asList(
            "self-test", "self test", "test", "backup", "backup in progress",
            "notification", "notifications", "message", "messages", "new messages",
            "phone", "unknown", "system", "android", "gmail", "whatsapp"));

    private static final Set<String> ARABIC_SINGLE_NOISE = new HashSet<>(Arrays.asList(
            "ال", "في", "من", "على", "عن", "الى", "إلى", "فور", "كان", "كنت", "انه", "إنه"));

    public static final class Result {
        public final boolean accepted;
        public final String canonicalName;
        public final String reason;
        Result(boolean accepted, String canonicalName, String reason) {
            this.accepted = accepted;
            this.canonicalName = canonicalName == null ? "" : canonicalName;
            this.reason = reason == null ? "" : reason;
        }
    }

    public static Result inspect(CognitiveDomainV4.WorldTypeHint type, String raw) {
        String value = clean(raw);
        if (type == null || value.length() < 2) return reject("too short");
        if (value.length() > 120) return reject("too long");

        String normalized = CognitiveIdentityV4.normalizeText(value);
        if (normalized.isEmpty()) return reject("empty after normalization");
        if (GENERIC.contains(normalized)) return reject("generic system/test label");

        if (type == CognitiveDomainV4.WorldTypeHint.PERSON) {
            value = cleanPerson(value);
            normalized = CognitiveIdentityV4.normalizeText(value);
            if (value.length() < 2 || normalized.isEmpty()) return reject("empty person after cleanup");
            if (GENERIC.contains(normalized) || ARABIC_SINGLE_NOISE.contains(normalized)) {
                return reject("generic person token");
            }
            if (looksSentenceLike(normalized)) return reject("sentence fragment, not person identity");
        }

        return new Result(true, value, "accepted as review proposal");
    }

    private static String cleanPerson(String raw) {
        String x = clean(raw);
        // Contact/table extraction often appends a column label to the visible name.
        x = x.replaceAll("(?i)\\s+(?:web\\s+)?phone\\s*$", "").trim();
        return x;
    }

    private static boolean looksSentenceLike(String normalized) {
        String x = " " + normalized.toLowerCase(Locale.ROOT) + " ";
        int words = normalized.split("\\s+").length;
        if (words >= 7) return true;
        if (x.startsWith(" من ") || x.startsWith(" في ") || x.startsWith(" على ")) return true;
        String[] sentenceMarkers = {
                " كنت ", " سيتم ", " محتاج ", " عاوز ", " عايز ", " عندي ", " لازم ",
                " should ", " need to ", " was ", " will ", " has been "
        };
        int markers = 0;
        for (String marker : sentenceMarkers) if (x.contains(marker)) markers++;
        return markers >= 1 && words >= 3;
    }

    private static Result reject(String reason) { return new Result(false, "", reason); }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ").replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "").trim();
    }
}
