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

/**
 * Proposal-only baseline. It gives Cortex a grounded comparator before learned models are admitted.
 */
public final class LocalIntelligenceEngine {
    private static final Pattern TIME = Pattern.compile("(?iu)(?:\\b(?:mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\\b|(?:الاثنين|الإثنين|الثلاثاء|الأربعاء|الخميس|الجمعة|السبت|الأحد)|(?:الساعة\\s*)?(?:[01]?\\d|2[0-3])[:٫.]?[0-5]\\d)");
    private static final String[] TASK_CUES = {
            "فكرني", "ذكرني", "لازم", "محتاج", "اتصل", "اكلم", "أكلم", "اجتماع", "موعد",
            "remind me", "remember to", "need to", "have to", "todo", "to-do", "call ", "meeting", "appointment"
    };

    private LocalIntelligenceEngine() {}

    public static IntelligenceSnapshot analyze(List<EvidenceRecord> evidence) {
        List<EvidenceRecord> ordered = new ArrayList<>(evidence == null ? List.of() : evidence);
        ordered.sort(Comparator.comparingLong((EvidenceRecord item) -> item.capturedAtEpochMs).reversed());

        Map<String, MutableThread> threads = new LinkedHashMap<>();
        Set<String> people = new LinkedHashSet<>();
        List<IntelligenceSnapshot.SignalProposal> tasks = new ArrayList<>();
        List<IntelligenceSnapshot.SignalProposal> times = new ArrayList<>();

        for (EvidenceRecord record : ordered) {
            String raw = safe(record.rawText).trim();
            if (record.source == EvidenceSource.NOTIFICATION) {
                String title = lineValue(raw, "title:");
                String body = lineValue(raw, "body:");
                String label = usefulTitle(title) ? clean(title) : fallbackLabel(record.sourceRef);
                if (!label.isEmpty()) {
                    people.add(label);
                    String key = normalizeIdentity(label);
                    MutableThread thread = threads.computeIfAbsent(key, ignored -> new MutableThread(key, label));
                    thread.add(record.id, record.capturedAtEpochMs, !body.isEmpty() ? body : raw);
                }
            }

            String lower = raw.toLowerCase(Locale.ROOT);
            for (String cue : TASK_CUES) {
                if (lower.contains(cue.toLowerCase(Locale.ROOT))) {
                    tasks.add(new IntelligenceSnapshot.SignalProposal(
                            "TASK_CANDIDATE",
                            compact(raw),
                            record.id,
                            0.70
                    ));
                    break;
                }
            }

            Matcher matcher = TIME.matcher(raw);
            if (matcher.find()) {
                times.add(new IntelligenceSnapshot.SignalProposal(
                        "TEMPORAL_HINT",
                        matcher.group(),
                        record.id,
                        0.82
                ));
            }
        }

        List<IntelligenceSnapshot.ThreadProposal> threadProposals = new ArrayList<>();
        for (MutableThread thread : threads.values()) threadProposals.add(thread.freeze());
        threadProposals.sort(Comparator.comparingLong((IntelligenceSnapshot.ThreadProposal t) -> t.latestEpochMs).reversed());

        return new IntelligenceSnapshot(threadProposals, new ArrayList<>(people), dedupeSignals(tasks), dedupeSignals(times));
    }

    private static List<IntelligenceSnapshot.SignalProposal> dedupeSignals(List<IntelligenceSnapshot.SignalProposal> input) {
        Map<String, IntelligenceSnapshot.SignalProposal> unique = new LinkedHashMap<>();
        for (IntelligenceSnapshot.SignalProposal proposal : input) {
            String key = proposal.kind + "|" + proposal.label.toLowerCase(Locale.ROOT);
            unique.putIfAbsent(key, proposal);
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean usefulTitle(String title) {
        if (title == null) return false;
        String value = title.trim();
        if (value.isEmpty()) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        return !lower.equals("android system") && !lower.equals("system ui") && !lower.equals("cortex prime");
    }

    private static String lineValue(String raw, String prefix) {
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
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
        String clean = clean(value).replace("title:", "").replace("body:", " · ");
        return clean.length() <= 120 ? clean : clean.substring(0, 120) + "…";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class MutableThread {
        final String key;
        final String label;
        final List<String> ids = new ArrayList<>();
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
            return new IntelligenceSnapshot.ThreadProposal(key, label, ids, latest, snippet);
        }
    }
}
