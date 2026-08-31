package com.kareem.cortex.prime.capture.relay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure-Java semantic cleanup for noisy notification surfaces.
 * Raw notification fields remain preserved in canonicalPayloadJson(); this class only shapes the
 * derived text view that downstream intelligence consumes.
 */
public final class NotificationTextCleaner {
    private static final Pattern COUNT_SUMMARY = Pattern.compile(
            "(?iu)^\\s*(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(?:new\\s+)?messages?\\s*$"
    );
    private static final Pattern ARABIC_COUNT_SUMMARY = Pattern.compile(
            "(?iu)^\\s*(?:\\d+\\s+)?(?:رسائل|رسالة|رسالتان|رسالتين)(?:\\s+جديدة|\\s+جديدتان|\\s+جديدتين)?\\s*$"
    );

    private NotificationTextCleaner() {}

    public static String conversationLabel(String conversationTitle, String title) {
        String conversation = clean(conversationTitle);
        if (!conversation.isEmpty() && !isSummary(conversation)) return simplifyDecoratedTitle(conversation);
        String fallback = clean(title);
        if (isSummary(fallback)) return "";
        return simplifyDecoratedTitle(fallback);
    }

    public static List<String> semanticMessages(
            String conversationTitle,
            String title,
            String body,
            String expandedText,
            List<NotificationMessage> messagingStyleMessages
    ) {
        String label = conversationLabel(conversationTitle, title);
        List<String> candidates = new ArrayList<>();

        if (messagingStyleMessages != null && !messagingStyleMessages.isEmpty()) {
            for (NotificationMessage message : messagingStyleMessages) {
                if (message == null) continue;
                String text = clean(message.text);
                if (text.isEmpty() || isSummary(text)) continue;
                String sender = clean(message.sender);
                if (!sender.isEmpty() && !sameIdentity(sender, label)) {
                    candidates.add(sender + ": " + text);
                } else {
                    candidates.add(text);
                }
            }
        }

        if (candidates.isEmpty()) {
            String compactBody = clean(body);
            String compactExpanded = clean(expandedText);
            String fallback = chooseFallback(compactBody, compactExpanded);
            candidates.addAll(splitRepeatedSender(fallback, label));
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            String value = stripLeadingSender(clean(candidate), label);
            if (value.isEmpty() || isSummary(value)) continue;
            String key = value.toLowerCase(Locale.ROOT);
            if (seen.add(key)) out.add(value);
        }
        return out;
    }

    public static boolean isSummary(String value) {
        String text = clean(value);
        if (text.isEmpty()) return false;
        return COUNT_SUMMARY.matcher(text).matches() || ARABIC_COUNT_SUMMARY.matcher(text).matches();
    }

    private static String chooseFallback(String body, String expanded) {
        if (!expanded.isEmpty() && !isSummary(expanded) && (body.isEmpty() || isSummary(body) || expanded.length() > body.length())) {
            return expanded;
        }
        return isSummary(body) ? "" : body;
    }

    private static List<String> splitRepeatedSender(String text, String label) {
        List<String> out = new ArrayList<>();
        String cleanText = clean(text);
        if (cleanText.isEmpty()) return out;
        if (label.isEmpty()) {
            out.add(cleanText);
            return out;
        }

        Pattern marker = Pattern.compile(Pattern.quote(label) + "\\s*:\\s*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        String[] chunks = marker.split(cleanText, -1);
        if (chunks.length <= 1) {
            out.add(cleanText);
            return out;
        }
        for (String chunk : chunks) {
            String value = clean(chunk);
            if (!value.isEmpty()) out.add(value);
        }
        return out;
    }

    private static String simplifyDecoratedTitle(String value) {
        String title = clean(value);
        int colon = title.indexOf(':');
        if (colon > 0 && colon + 1 < title.length()) {
            String suffix = title.substring(colon + 1);
            if (suffix.contains("|") || suffix.contains("•")) {
                return clean(title.substring(0, colon));
            }
        }
        return title;
    }

    private static String stripLeadingSender(String value, String label) {
        if (label == null || label.isEmpty()) return value;
        String prefix = label + ":";
        if (value.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return clean(value.substring(prefix.length()));
        }
        return value;
    }

    private static boolean sameIdentity(String left, String right) {
        return normalizeIdentity(left).equals(normalizeIdentity(right));
    }

    private static String normalizeIdentity(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}+]+", "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
