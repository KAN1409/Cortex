package com.kareem.cortex;

import java.util.Locale;

/**
 * Conservative feature extraction for already-grounded conversation semantics.
 * This class never creates facts or projections; it only exposes bounded attention features
 * from text that is already present in canonical semantic state.
 */
public final class ConversationAttentionFeatures {
    public static final String VERSION = "conversation_attention_features_001";

    public static final class Result {
        public final boolean eligibleConversation;
        public final boolean explicitRequest;
        public final boolean responseExpected;
        public final double actionabilityFloor;
        public final double relevanceFloor;
        public final double urgencyFloor;
        public final String reason;

        Result(boolean conversation, boolean request, boolean expected,
               double actionability, double relevance, double urgency, String why) {
            eligibleConversation = conversation;
            explicitRequest = request;
            responseExpected = expected;
            actionabilityFloor = clamp(actionability);
            relevanceFloor = clamp(relevance);
            urgencyFloor = clamp(urgency);
            reason = why == null ? "" : why;
        }
    }

    private ConversationAttentionFeatures() {}

    public static Result evaluate(String semanticType, String intent, String subject, String summary) {
        String type = norm(semanticType);
        String in = norm(intent);
        String text = norm(n(subject) + " " + n(summary));
        boolean conversation = containsAny(type, "conversation", "message", "action_request", "required_response")
                || containsAny(in, "message", "conversation", "request", "reply", "respond", "confirm");
        if (!conversation || text.isEmpty()) {
            return none(conversation, "not a grounded conversation candidate");
        }
        if (ActionSpecificityGate.isExplicitNoAction(text) || ActionSpecificityGate.isUiChromeLike(text)) {
            return none(true, "conversation text is explicit no-action or UI chrome");
        }

        boolean directEnglish = containsAny(text,
                "kindly confirm", "please confirm", "please reply", "please respond",
                "please send", "please provide", "please share", "please check and confirm",
                "can you ", "could you ", "would you ", "need you to ", "reply with ",
                "respond with ");
        boolean directArabic = containsAny(text,
                "يرجى التأكيد", "يرجى التاكيد", "يرجى التأكد", "يرجى التاكد",
                "برجاء التأكيد", "برجاء التاكيد", "برجاء التأكد", "برجاء التاكد",
                "برجاء الرد", "يرجى الرد", "من فضلك أكد", "من فضلك اكد",
                "من فضلك رد", "من فضلك ابعت", "من فضلك ارسل", "لو سمحت أكد",
                "لو سمحت اكد", "لو سمحت رد", "لو سمحت ابعت", "لو سمحت ارسل");

        boolean confirmationQuestion =
                (containsAny(text, "is this your shipment", "is this yours", "is that yours",
                        "هل هذه شحنتك", "هل دي شحنتك", "هل دى شحنتك", "هل الشحنة دي بتاعتك",
                        "هل الشحنه دي بتاعتك", "هل الشحنة دى بتاعتك")
                        && containsAny(text, "confirm", "yes", "no", "تأكد", "تاكد", "نعم", "لا", "شحنتك"));

        boolean typedRequest = containsAny(type, "action_request", "required_response", "request")
                || containsAny(in, "request", "reply", "respond", "confirm", "submit");
        boolean request = typedRequest || directEnglish || directArabic || confirmationQuestion;
        if (!request) return none(true, "no explicit grounded request language");

        boolean expected = confirmationQuestion || containsAny(text,
                "please reply", "please respond", "reply with", "respond with",
                "برجاء الرد", "يرجى الرد", "هل هذه", "هل دي", "هل دى");
        double actionability = confirmationQuestion ? .90 : .86;
        double relevance = confirmationQuestion ? .76 : .70;
        double urgency = .58;
        return new Result(true, true, expected, actionability, relevance, urgency,
                confirmationQuestion ? "grounded confirmation response requested"
                        : "grounded conversation contains explicit request language");
    }

    private static Result none(boolean conversation, String why) {
        return new Result(conversation, false, false, 0, 0, 0, why);
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(norm(value))) return true;
        return false;
    }

    private static String norm(String s) {
        return n(s).toLowerCase(Locale.ROOT)
                .replace('‑', '-').replace('–', '-').replace('—', '-')
                .replaceAll("\\s+", " ").trim();
    }

    private static String n(String s) { return s == null ? "" : s.trim(); }
    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
}
