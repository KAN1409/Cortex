package com.kareem.cortex.prime.intelligence;

import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Proposal-only grounded baseline. Learned models must beat this comparator before replacing it. */
public final class LocalIntelligenceEngine {
    private static final Pattern TIME = Pattern.compile(
            "(?iu)(?:\\b(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\\b|(?:الاثنين|الإثنين|الثلاثاء|الأربعاء|الخميس|الجمعة|السبت|الأحد)|(?:الساعة\\s*)?(?:[01]?\\d|2[0-3])(?:[:٫.]?[0-5]\\d)?\\s*(?:am|pm)?)"
    );
    private static final Pattern COUNT_SUMMARY = Pattern.compile("(?iu)^\\s*\\d+\\s+(?:new\\s+)?messages?\\s*$");
    private static final String[] TASK_CUES = {
            "فكرني", "ذكرني", "لازم", "محتاج", "محتاج أ", "اتصل", "اكلم", "أكلم", "اجتماع", "موعد",
            "remind me", "remember to", "need to", "have to", "todo", "to-do", "call ", "meeting", "appointment"
    };
    private static final String[] ATTENTION_CUES = {
            "ممكن", "لو سمحت", "محتاج منك", "عايز منك", "رد علي", "كلمني", "فينك", "؟",
            "can you", "could you", "please", "need you", "call me", "reply", "where are you", "?"
    };
    private static final String[] RELATIONAL_CUES = {
            "وحشتني", "مشتاق", "haven't seen you", "havent seen you", "miss you", "miss u"
    };

    private LocalIntelligenceEngine() {}

    public static IntelligenceSnapshot analyze(List<EvidenceRecord> evidence) {
        List<EvidenceRecord> ordered = new ArrayList<>(evidence == null ? List.of() : evidence);
        ordered.sort(Comparator.comparingLong((EvidenceRecord item) -> item.capturedAtEpochMs).reversed());

        Map<String, MutableThread> threads = new LinkedHashMap<>();
        Set<String> people = new LinkedHashSet<>();
        List<IntelligenceSnapshot.SignalProposal> attention = new ArrayList<>();
        List<IntelligenceSnapshot.SignalProposal> tasks = new ArrayList<>();
        List<IntelligenceSnapshot.SignalProposal> times = new ArrayList<>();

        for (EvidenceRecord record : ordered) {
            String raw = safe(record.rawText).trim();
            List<String> semanticTexts = new ArrayList<>();

            if (record.source == EvidenceSource.NOTIFICATION) {
                String label = notificationLabel(raw, record.sourceRef);
                List<String> messages = notificationMessages(raw);
                if (!label.isEmpty()) {
                    String key = normalizeIdentity(label);
                    MutableThread thread = threads.computeIfAbsent(key, ignored -> new MutableThread(key, label));
                    String snippet = messages.isEmpty() ? legacyBody(raw) : messages.get(messages.size() - 1);
                    thread.add(record.id, record.capturedAtEpochMs, snippet);
                    if (looksLikePerson(label)) people.add(label);
                }
                semanticTexts.addAll(messages);
                if (semanticTexts.isEmpty()) {
                    String legacy = legacyBody(raw);
                    if (!legacy.isEmpty() && !isCountSummary(legacy)) semanticTexts.add(legacy);
                }
            } else if (!raw.isEmpty() && admissibleSemanticEvidence(record)) {
                semanticTexts.add(raw);
            }

            for (String semantic : semanticTexts) {
                analyzeText(record, semantic, attention, tasks, times);
            }
        }

        List<IntelligenceSnapshot.ThreadProposal> threadProposals = new ArrayList<>();
        for (MutableThread thread : threads.values()) threadProposals.add(thread.freeze());
        threadProposals.sort(Comparator.comparingLong((IntelligenceSnapshot.ThreadProposal t) -> t.latestEpochMs).reversed());

        return new IntelligenceSnapshot(
                threadProposals,
                new ArrayList<>(people),
                dedupeSignals(attention),
                dedupeSignals(tasks),
                dedupeSignals(times)
        );
    }

    /**
     * Validator boundary for perception-derived semantic evidence. Raw evidence remains stored regardless.
     * Only COMPLETE transcript/OCR records with an explicit immutable parent contract may feed intelligence.
     */
    static boolean admissibleSemanticEvidence(EvidenceRecord record) {
        if (record == null) return false;
        String ref = safe(record.sourceRef).trim();
        if (!ref.startsWith("derived-from:")) return true;

        String parentId = ref.substring("derived-from:".length()).trim();
        if (parentId.isEmpty()) return false;
        if (record.source != EvidenceSource.TEXT && record.source != EvidenceSource.OCR) return false;

        String payload = safe(record.rawPayloadJson).replaceAll("\\s+", "");
        if (!payload.contains("\"derived\":true") || !payload.contains("\"immutable_parent\":true")) return false;
        if (!payload.contains("\"status\":\"COMPLETE\"")) return false;
        if (!payload.contains("\"parent_evidence_id\":\"" + jsonLiteral(parentId) + "\"")) return false;

        if (record.source == EvidenceSource.TEXT) {
            return payload.contains("\"schema\":\"CORTEX_PRIME_DERIVED_TRANSCRIPT_V1\"");
        }
        return payload.contains("\"schema\":\"CORTEX_PRIME_DERIVED_OCR_V1\"");
    }

    private static String jsonLiteral(String value) {
        return safe(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void analyzeText(
            EvidenceRecord record,
            String text,
            List<IntelligenceSnapshot.SignalProposal> attention,
            List<IntelligenceSnapshot.SignalProposal> tasks,
            List<IntelligenceSnapshot.SignalProposal> times
    ) {
        String clean = clean(text);
        if (clean.isEmpty() || isCountSummary(clean)) return;
        String lower = clean.toLowerCase(Locale.ROOT);

        for (String cue : TASK_CUES) {
            if (lower.contains(cue.toLowerCase(Locale.ROOT))) {
                tasks.add(new IntelligenceSnapshot.SignalProposal("TASK_CANDIDATE", compact(clean), record.id, 0.76));
                break;
            }
        }

        boolean explicitAttention = containsAny(lower, ATTENTION_CUES);
        boolean relationalAttention = containsAny(lower, RELATIONAL_CUES);
        if (explicitAttention || relationalAttention) {
            attention.add(new IntelligenceSnapshot.SignalProposal(
                    explicitAttention ? "REPLY_CANDIDATE" : "SOCIAL_FOLLOWUP_CANDIDATE",
                    compact(clean),
                    record.id,
                    explicitAttention ? 0.78 : 0.62
            ));
        }

        Matcher matcher = TIME.matcher(clean);
        if (matcher.find()) {
            times.add(new IntelligenceSnapshot.SignalProposal("TEMPORAL_HINT", matcher.group(), record.id, 0.84));
        }
    }

    private static String notificationLabel(String raw, String sourceRef) {
        String normalized = lineValue(raw, "conversation:");
        if (!normalized.isEmpty()) return clean(normalized);
        String legacy = lineValue(raw, "title:");
        if (!legacy.isEmpty() && !isCountSummary(legacy)) return clean(decoratedTitlePrefix(legacy));
        return fallbackLabel(sourceRef);
    }

    private static List<String> notificationMessages(String raw) {
        List<String> out = lineValues(raw, "message:");
        if (!out.isEmpty()) return out;
        String body = lineValue(raw, "body:");
        String expanded = lineValue(raw, "expanded:");
        if (!expanded.isEmpty() && (body.isEmpty() || isCountSummary(body) || expanded.length() > body.length())) {
            out.add(clean(expanded));
        } else if (!body.isEmpty() && !isCountSummary(body)) {
            out.add(clean(body));
        }
        return out;
    }

    private static String legacyBody(String raw) {
        List<String> messages = notificationMessages(raw);
        if (!messages.isEmpty()) return messages.get(messages.size() - 1);
        return "";
    }

    private static List<IntelligenceSnapshot.SignalProposal> dedupeSignals(List<IntelligenceSnapshot.SignalProposal> input) {
        Map<String, IntelligenceSnapshot.SignalProposal> unique = new LinkedHashMap<>();
        for (IntelligenceSnapshot.SignalProposal proposal : input) {
            String key = proposal.kind + "|" + proposal.label.toLowerCase(Locale.ROOT);
            unique.putIfAbsent(key, proposal);
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean looksLikePerson(String label) {
        String value = clean(label);
        if (value.isEmpty() || value.length() > 64) return false;
        if (value.contains("|") || value.contains("•")) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        return !lower.equals("android system") && !lower.equals("system ui") && !lower.equals("cortex prime");
    }

    private static String decoratedTitlePrefix(String title) {
        int colon = title.indexOf(':');
        if (colon > 0 && colon + 1 < title.length()) {
            String suffix = title.substring(colon + 1);
            if (suffix.contains("|") || suffix.contains("•")) return title.substring(0, colon);
        }
        return title;
    }

    private static String lineValue(String raw, String prefix) {
        List<String> values = lineValues(raw, prefix);
        return values.isEmpty() ? "" : values.get(0);
    }

    private static List<String> lineValues(String raw, String prefix) {
        List<String> values = new ArrayList<>();
        for (String line : safe(raw).split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String value = trimmed.substring(prefix.length()).trim();
                if (!value.isEmpty()) values.add(value);
            }
        }
        return values;
    }

    private static boolean containsAny(String lower, String[] cues) {
        for (String cue : cues) if (lower.contains(cue.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean isCountSummary(String value) {
        return COUNT_SUMMARY.matcher(clean(value)).matches();
    }

    private static String fallbackLabel(String sourceRef) {
        String value = safe(sourceRef).trim();
        if (value.isEmpty()) return "";
        int cut = Math.max(value.lastIndexOf('/'), value.lastIndexOf(':'));
        return clean(cut >= 0 && cut + 1 < value.length() ? value.substring(cut + 1) : value);
    }

    private static String normalizeIdentity(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}+]+", "").trim();
    }

    private static String clean(String value) {
        return safe(value).replaceAll("\\s+", " ").trim();
    }

    private static String compact(String value) {
        String normalized = clean(value);
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "…";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class MutableThread {
        final String key;
        final String label;
        final Set<String> ids = new LinkedHashSet<>();
        long latest = Long.MIN_VALUE;
        String snippet = "";

        MutableThread(String key, String label) {
            this.key = key;
            this.label = label;
        }

        void add(String id, long epochMs, String body) {
            ids.add(id);
            if (epochMs >= latest) {
                latest = epochMs;
                snippet = compact(body);
            }
        }

        IntelligenceSnapshot.ThreadProposal freeze() {
            return new IntelligenceSnapshot.ThreadProposal(key, label, new ArrayList<>(ids), latest, snippet);
        }
    }
}
