package com.kareem.cortex;

import java.util.Collections;
import java.util.Locale;

/**
 * Conservative deterministic front lane for obvious cognitive signals.
 *
 * This is intentionally small. It only claims cases whose meaning is explicit enough that running
 * a local LLM would add latency without adding useful uncertainty resolution. Returning null means
 * "ask Qwen"; it is not a negative classification.
 */
public final class HybridFastClassifier {
    public static final String POLICY = "hybrid_fast_001";

    private HybridFastClassifier() {}

    /** Returns a grounded result for an obvious signal, or null when Qwen should decide. */
    public static CognitiveResult classify(CognitiveInput input) {
        if (input == null || input.latestText == null || input.latestText.trim().isEmpty()) return null;

        if (input.family == SignalFamily.EVENT) {
            return derive(CognitiveKind.EVENT, input.latestText, 0.99, 62, 62, "family_event");
        }
        if (input.family == SignalFamily.CONTENT) {
            return derive(CognitiveKind.CONTENT, input.latestText, 0.99, 52, 35, "family_content");
        }
        if (input.family != SignalFamily.COMMUNICATION) return null;

        String text = normalize(input.latestText);
        if (text.isEmpty()) return null;

        boolean waiting = containsAny(text,
                "هبعتلك", "هابعتلك", "هكلمك", "هراجع", "هأكدلك", "هاكدلك", "هقولك",
                "i will ", "i ll ", "we will ", "we ll ", "will send", "will call", "will confirm");
        boolean action = containsAny(text,
                "ممكن ", "لو سمحت", "can you ", "could you ", "send me", "call me",
                "please call", "please send", "please confirm", "please review", "please share", "please reply");

        // Mixed responsibility is exactly the sort of case that should go to Qwen.
        if (waiting && action) return null;
        if (waiting) {
            return derive(CognitiveKind.WAITING, input.latestText, 0.98, 58, 45, "explicit_sender_commitment");
        }
        if (action) {
            return derive(CognitiveKind.ACTION, input.latestText, 0.98, 72, 68, "explicit_user_request");
        }

        if (ackOnly(text)) {
            return new CognitiveResult(
                    CognitiveDisposition.CONTEXT,
                    0.99,
                    POLICY + ":ack_only",
                    Collections.emptyList()
            );
        }

        return null;
    }

    private static CognitiveResult derive(
            CognitiveKind kind,
            String summary,
            double confidence,
            int importance,
            int urgency,
            String reason
    ) {
        CognitiveItem item = new CognitiveItem(
                kind,
                clip(summary, 240),
                importance,
                urgency,
                "",
                null,
                kind == CognitiveKind.ACTION,
                kind == CognitiveKind.WAITING,
                kind == CognitiveKind.CONTENT
        );
        return new CognitiveResult(
                CognitiveDisposition.DERIVE,
                confidence,
                POLICY + ":" + reason,
                Collections.singletonList(item)
        );
    }

    private static boolean ackOnly(String text) {
        if (wordCount(text) > 8) return false;
        return text.equals("تمام")
                || text.equals("تمام وصلت")
                || text.equals("وصلت")
                || text.equals("got it")
                || text.equals("received")
                || text.startsWith("شكرا ")
                || text.equals("شكرا")
                || text.startsWith("thanks ")
                || text.equals("thanks")
                || text.startsWith("thank you");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private static int wordCount(String text) {
        String t = text.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    private static String normalize(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        text = text.replace('’', '\'');
        text = text.replaceAll("[\\u064B-\\u065F\\u0670]", "");
        text = text.replaceAll("[\\p{Punct}،؛؟]+", " ");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String clip(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
