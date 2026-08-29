package com.kareem.cortex;

import java.util.Locale;

/**
 * Last deterministic quality gate before anything reaches a brain-first product surface.
 * This does not replace the model: it only removes UI chrome/mechanical residue and prevents
 * low-information EVENT guesses from becoming a visible "change".
 */
public final class CortexSurfacePolicy {
    private CortexSurfacePolicy() {}

    public static boolean isSurfaceNoise(String kind, String title, String body, String source) {
        String t = norm((title == null ? "" : title) + " " + (body == null ? "" : body));
        String k = clean(kind).toUpperCase(Locale.ROOT);
        String src = clean(source).toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return true;
        if (mechanicalProgress(t) || notificationChrome(t)) return true;
        if (src.contains("systemui") && genericSystemChrome(t)) return true;
        if (("EVENT".equals(k) || "CHANGE".equals(k) || "ALERT".equals(k)) && lowInformationEvent(t)) return true;
        return false;
    }

    public static boolean meaningfulChange(String kind, String title, String body, String source,
                                           int importance, double confidence) {
        if (isSurfaceNoise(kind, title, body, source)) return false;
        String k = clean(kind).toUpperCase(Locale.ROOT);
        if (!("EVENT".equals(k) || "CHANGE".equals(k) || "ALERT".equals(k))) return false;
        if (importance < 55 || confidence < 0.58) return false;
        String t = norm((title == null ? "" : title) + " " + (body == null ? "" : body));
        if (eventCue(t)) return true;
        return tokenCount(t) >= 4 && t.length() >= 24;
    }

    public static boolean reviewDeservesNow(String candidateKind, int importance, double confidence,
                                            long createdAt, long now) {
        String k = clean(candidateKind).toUpperCase(Locale.ROOT);
        if (!("ACTION".equals(k) || "WAITING".equals(k) || "DECISION".equals(k))) return false;
        if (importance < 65 || confidence < 0.58) return false;
        long age = createdAt <= 0 ? 0 : Math.max(0, now - createdAt);
        return age <= 72L * 60L * 60L * 1000L;
    }

    public static boolean notificationChrome(String text) {
        String t = norm(text);
        if (t.matches(".*\\b\\d+\\s+more\\s+notifications?\\b.*")) return true;
        return has(t,
                "more notifications",
                "tap to copy the url for this app",
                "tap to copy url",
                "tap to copy the url",
                "tap to copy the link",
                "copied to clipboard",
                "clipboard copied",
                "notification settings",
                "manage notifications",
                "tap to manage notifications",
                "tap for more information",
                "tap to view more",
                "app is running in the background",
                "is running in background",
                "display over other apps",
                "choose input method",
                "open supported links",
                "اضغط لنسخ الرابط",
                "تم النسخ الي الحافظه",
                "تم النسخ إلى الحافظة",
                "اشعارات اخري",
                "إشعارات أخرى");
    }

    private static boolean mechanicalProgress(String t) {
        boolean verb = has(t, "deleting item", "deleting ", "uploading ", "downloading ",
                "syncing ", "processing ", "installing ", "optimizing ");
        boolean counter = t.matches(".*\\b\\d+\\s+of\\s+\\d+\\b.*") || t.contains("%") || t.contains("progress");
        return verb && counter;
    }

    private static boolean genericSystemChrome(String t) {
        return notificationChrome(t) || has(t,
                "android system", "system ui", "usb debugging", "vpn is active", "media output",
                "charging", "battery", "connected to wifi", "wi fi connected", "bluetooth connected");
    }

    private static boolean lowInformationEvent(String t) {
        if (eventCue(t)) return false;
        int tokens = tokenCount(t);
        return tokens <= 2 || t.length() < 18;
    }

    private static boolean eventCue(String t) {
        return has(t,
                "confirmed", "cancelled", "canceled", "delayed", "rescheduled", "delivered", "shipped",
                "arrived", "approved", "rejected", "paid", "charged", "transfer", "received", "sent",
                "missed call", "booked", "appointment", "meeting", "started", "ended", "completed",
                "failed", "changed", "updated", "ready for pickup", "out for delivery", "due",
                "تم تاكيد", "تم تأكيد", "اتلغي", "اتلغى", "الغي", "ألغى", "اتاجل", "اتأجل",
                "وصل", "اتبعت", "تم الارسال", "تم الإرسال", "تحويل", "خصم", "تمت الموافقه",
                "تمت الموافقة", "تم الرفض", "موعد", "حجز", "مكالمه فائته", "مكالمة فائتة");
    }

    private static int tokenCount(String t) {
        int n = 0;
        for (String x : clean(t).split("[^\\p{L}\\p{Nd}]+")) if (x.length() >= 2) n++;
        return n;
    }

    private static boolean has(String text, String... needles) {
        for (String x : needles) if (text.contains(norm(x))) return true;
        return false;
    }

    private static String norm(String s) { return MasterRelevanceFilter.ruleNorm(clean(s)); }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
