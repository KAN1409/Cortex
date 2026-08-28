package com.kareem.cortex;

import java.util.Locale;
import org.json.JSONObject;

/**
 * Shared notification cognition vocabulary. Relay supplies evidence; Cortex owns classification,
 * final outcome and priority. Nothing in this class is allowed to infer from Relay-side labels.
 */
public final class CognitiveSignalV2 {
    public enum SignalFamily { COMMUNICATION, EVENT, CONTENT, TRANSACTION, SECURITY, DELIVERY, SOCIAL, SYSTEM, UNKNOWN }
    public enum CognitiveState { PENDING_ADJUDICATION, IGNORED_NOISE, CONTEXT_ONLY, DERIVED, REVIEW_REQUIRED, SENSITIVE_BLOCKED, MODEL_FAILED, SUPERSEDED }
    public enum Kind { ACTION, WAITING, DECISION, EVENT, CONTENT, MESSAGE, REMINDER, INSIGHT, MEMORY }

    private CognitiveSignalV2() {}

    public static SignalFamily classify(MasterRelevanceFilter.Signal signal) {
        if (signal == null) return SignalFamily.UNKNOWN;
        String source = low(signal.source);
        String text = low(signal.text());
        String category = "", notificationKind = "";
        try {
            JSONObject meta = new JSONObject(n(signal.metadataJson));
            category = low(meta.optString("category", ""));
            notificationKind = low(meta.optString("notification_kind", ""));
        } catch (Throwable ignored) {}

        if (isSystemSource(source) || category.contains("sys")) return SignalFamily.SYSTEM;
        // Provider family is also a privacy/routing hint inside Cortex: likely authenticator/security
        // and banking/wallet apps stay local even when the individual notification text is vague.
        if (has(source, "authenticator", "authy", "1password", "bitwarden", "password")) return SignalFamily.SECURITY;
        if (has(source, "bank", "wallet", "paymob", "instapay", "paypal", "venmo", "cashapp")) return SignalFamily.TRANSACTION;
        if (has(text, "security alert", "new sign-in", "new login", "unusual activity", "password changed", "تسجيل دخول جديد", "تنبيه امان")) return SignalFamily.SECURITY;
        if (has(text, "payment", "transaction", "purchase", "card charged", "transfer", "تم خصم", "عمليه شراء", "تحويل بنكي")) return SignalFamily.TRANSACTION;
        if (has(text, "delivery", "delivered", "out for delivery", "ready for pickup", "arriving", "تم التوصيل", "خرج للتوصيل", "جاهز للاستلام")) return SignalFamily.DELIVERY;
        if (category.contains("event") || source.contains("calendar") || source.contains("agenda") || has(text, "calendar", "appointment", "meeting", "موعد", "حجز")) return SignalFamily.EVENT;
        if (isCommunicationSource(source) || "message".equals(notificationKind) || "email".equals(notificationKind)) return SignalFamily.COMMUNICATION;
        if (source.contains("instagram") || source.contains("facebook") || source.contains("tiktok") || source.contains("reddit") || source.contains("x.com") || source.contains("twitter")) {
            if (has(text, "reel", "video", "photo", "post", "shared", "sent you")) return SignalFamily.CONTENT;
            return SignalFamily.SOCIAL;
        }
        if (has(text, "voice message", "voice note", "audio message", "reel", "shared a", "sent a video", "sent a photo")) return SignalFamily.CONTENT;
        return SignalFamily.UNKNOWN;
    }

    public static int priorityScore(int importance, int urgency, Kind kind, boolean requiresUserAction,
                                    boolean requiresFollowUp, boolean requiresContentExtraction,
                                    long dueAt, long occurredAt, int relationshipWeight, boolean securityCritical,
                                    long now) {
        int actionWeight = actionWeight(kind, requiresUserAction, requiresFollowUp, requiresContentExtraction);
        int recency = recencyScore(occurredAt, now);
        double score = .35 * clamp100(importance)
                + .30 * clamp100(urgency)
                + .20 * actionWeight
                + .10 * recency
                + .05 * clamp100(relationshipWeight);
        if (dueAt > 0) {
            long delta = dueAt - now;
            if (delta < 0) score += 25;
            else if (delta <= 2L * 60L * 60L * 1000L) score += 20;
            else if (delta <= 24L * 60L * 60L * 1000L) score += 10;
        }
        if (securityCritical) score += 20;
        return clamp100((int)Math.round(score));
    }

    public static boolean pulseEligible(Kind kind, int score, boolean requiresUserAction,
                                        boolean requiresFollowUp, boolean requiresContentExtraction) {
        if (kind == null) return false;
        if (requiresUserAction || requiresFollowUp || requiresContentExtraction) return true;
        if (kind == Kind.EVENT || kind == Kind.REMINDER || kind == Kind.WAITING || kind == Kind.ACTION) return true;
        return score >= 65 && kind != Kind.MESSAGE;
    }

    private static int actionWeight(Kind kind, boolean userAction, boolean followUp, boolean contentExtraction) {
        if (userAction) return 100;
        if (followUp) return 75;
        if (kind == Kind.EVENT || kind == Kind.REMINDER) return 70;
        if (kind == Kind.WAITING) return 60;
        if (contentExtraction || kind == Kind.CONTENT) return 40;
        if (kind == Kind.INSIGHT || kind == Kind.MEMORY) return 20;
        return 10;
    }

    private static int recencyScore(long occurredAt, long now) {
        if (occurredAt <= 0 || now <= occurredAt) return 100;
        long age = now - occurredAt;
        if (age <= 60L * 60L * 1000L) return 100;
        if (age <= 6L * 60L * 60L * 1000L) return 80;
        if (age <= 24L * 60L * 60L * 1000L) return 60;
        if (age <= 3L * 24L * 60L * 60L * 1000L) return 35;
        return 15;
    }

    private static boolean isCommunicationSource(String source) {
        return has(source, "whatsapp", "telegram", "messenger", "messages", "sms", "signal", "gmail", "outlook", "mail", "slack", "teams", "discord");
    }

    private static boolean isSystemSource(String source) {
        return has(source, "systemui", "android.system", "settings", "packageinstaller");
    }

    private static int clamp100(int value) { return Math.max(0, Math.min(100, value)); }
    private static String low(String s) { return n(s).toLowerCase(Locale.ROOT); }
    private static String n(String s) { return s == null ? "" : s.trim(); }
    private static boolean has(String s, String... xs) { for (String x : xs) if (s.contains(low(x))) return true; return false; }
}
