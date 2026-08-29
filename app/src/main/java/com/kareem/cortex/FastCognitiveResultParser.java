package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FastCognitiveResultParser {
    private static final Pattern THINK = Pattern.compile("(?is)<think>(.*?)</think>");

    private FastCognitiveResultParser() {}

    public static CognitiveResult parse(String raw) throws CognitiveContractException {
        return parse(raw, "");
    }

    /**
     * Parses the compact fast wire. Confidence and summary are intentionally optional on this
     * latency-sensitive path: when the model emits only a classification token, Cortex supplies
     * conservative policy confidence and uses the grounded input text as the item summary.
     */
    public static CognitiveResult parse(String raw, String fallbackSummary) throws CognitiveContractException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new CognitiveContractException("Empty fast cognitive response");
        }

        try {
            String json = extractJson(stripThinking(raw));
            JSONObject root = new JSONObject(json);

            // Transition safety: accept the old verbose contract if a model unexpectedly emits it.
            if (!root.has("t") && !root.has("d") && root.has("disposition")) {
                return CognitiveResultParser.parse(raw);
            }

            String label = root.has("t")
                    ? requiredString(root, "t")
                    : requiredString(root, "d");
            Classification classification = classification(label);
            double confidence = root.has("c") && !root.isNull("c")
                    ? confidence(root.get("c"))
                    : defaultConfidence(classification.disposition);
            List<CognitiveItem> items = new ArrayList<>();

            if (classification.disposition == CognitiveDisposition.DERIVE) {
                JSONArray array = root.optJSONArray("it");
                if (array != null) {
                    if (array.length() < 1 || array.length() > 2) {
                        throw new CognitiveContractException("Fast DERIVE supports one or two items");
                    }
                    for (int i = 0; i < array.length(); i++) {
                        items.add(item(array.getJSONObject(i), classification.kindCode, fallbackSummary));
                    }
                } else {
                    items.add(item(root, classification.kindCode, fallbackSummary));
                }
            }

            return new CognitiveResult(
                    classification.disposition,
                    confidence,
                    root.optString("r", ""),
                    items
            );
        } catch (CognitiveContractException error) {
            throw error;
        } catch (Throwable error) {
            throw new CognitiveContractException("Invalid fast cognitive JSON", error);
        }
    }

    public static boolean hasNonEmptyThinking(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        Matcher matcher = THINK.matcher(raw);
        boolean foundClosed = false;
        while (matcher.find()) {
            foundClosed = true;
            if (!matcher.group(1).trim().isEmpty()) return true;
        }
        if (foundClosed) return false;

        String lower = raw.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("<think>");
        if (start < 0) return false;
        int contentStart = start + "<think>".length();
        int jsonStart = raw.indexOf('{', contentStart);
        String remainder = jsonStart >= 0
                ? raw.substring(contentStart, jsonStart)
                : raw.substring(contentStart);
        return !remainder.trim().isEmpty();
    }

    private static CognitiveItem item(
            JSONObject object,
            String fallbackKind,
            String fallbackSummary
    ) throws CognitiveContractException {
        String kindValue = object.has("k") && !object.isNull("k")
                ? requiredString(object, "k")
                : fallbackKind;
        if (kindValue == null || kindValue.trim().isEmpty()) {
            throw new CognitiveContractException("Fast derived item has no kind");
        }

        CognitiveKind kind = kind(kindValue);
        String summary = object.has("s") && !object.isNull("s")
                ? clean(String.valueOf(object.opt("s")))
                : compactSummary(fallbackSummary);
        if (summary.isEmpty()) {
            throw new CognitiveContractException("Fast derived item has no grounded summary");
        }

        return new CognitiveItem(
                kind,
                summary,
                intValue(object, "i", 0),
                intValue(object, "u", 0),
                nullableString(object, "p"),
                dueAt(object.opt("due")),
                boolValue(object, "ua", kind == CognitiveKind.ACTION),
                boolValue(object, "fu", kind == CognitiveKind.WAITING),
                boolValue(object, "ce", kind == CognitiveKind.CONTENT)
        );
    }

    private static Classification classification(String code) throws CognitiveContractException {
        String normalized = clean(code).toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "I":
            case "IGNORE":
                return new Classification(CognitiveDisposition.IGNORE, null);
            case "C":
            case "CONTEXT":
                return new Classification(CognitiveDisposition.CONTEXT, null);
            case "R":
            case "REVIEW":
                return new Classification(CognitiveDisposition.REVIEW, null);
            case "D":
            case "DERIVE":
                return new Classification(CognitiveDisposition.DERIVE, null);
            case "AC":
            case "ACTION":
                return new Classification(CognitiveDisposition.DERIVE, "AC");
            case "WA":
            case "WAITING":
                return new Classification(CognitiveDisposition.DERIVE, "WA");
            case "DE":
            case "DECISION":
                return new Classification(CognitiveDisposition.DERIVE, "DE");
            case "EV":
            case "EVENT":
                return new Classification(CognitiveDisposition.DERIVE, "EV");
            case "CO":
            case "CONTENT":
                return new Classification(CognitiveDisposition.DERIVE, "CO");
            case "MS":
            case "MESSAGE":
                return new Classification(CognitiveDisposition.DERIVE, "MS");
            case "RE":
            case "REMINDER":
                return new Classification(CognitiveDisposition.DERIVE, "RE");
            case "IN":
            case "INSIGHT":
                return new Classification(CognitiveDisposition.DERIVE, "IN");
            case "ME":
            case "MEMORY":
                return new Classification(CognitiveDisposition.DERIVE, "ME");
            default:
                throw new CognitiveContractException("Unknown fast classification: " + code);
        }
    }

    private static CognitiveKind kind(String code) throws CognitiveContractException {
        switch (clean(code).toUpperCase(Locale.ROOT)) {
            case "AC":
            case "ACTION": return CognitiveKind.ACTION;
            case "WA":
            case "WAITING": return CognitiveKind.WAITING;
            case "DE":
            case "DECISION": return CognitiveKind.DECISION;
            case "EV":
            case "EVENT": return CognitiveKind.EVENT;
            case "CO":
            case "CONTENT": return CognitiveKind.CONTENT;
            case "MS":
            case "MESSAGE": return CognitiveKind.MESSAGE;
            case "RE":
            case "REMINDER": return CognitiveKind.REMINDER;
            case "IN":
            case "INSIGHT": return CognitiveKind.INSIGHT;
            case "ME":
            case "MEMORY": return CognitiveKind.MEMORY;
            default: throw new CognitiveContractException("Unknown fast kind: " + code);
        }
    }

    private static double defaultConfidence(CognitiveDisposition disposition) {
        if (disposition == CognitiveDisposition.DERIVE) return 0.90;
        if (disposition == CognitiveDisposition.CONTEXT) return 0.90;
        if (disposition == CognitiveDisposition.IGNORE) return 0.70;
        return 0.60;
    }

    private static double confidence(Object value) throws CognitiveContractException {
        if (!(value instanceof Number)) {
            try {
                value = Double.parseDouble(String.valueOf(value));
            } catch (Throwable error) {
                throw new CognitiveContractException("Invalid fast confidence");
            }
        }
        double c = ((Number) value).doubleValue();
        if (Double.isNaN(c) || Double.isInfinite(c) || c < 0.0 || c > 100.0) {
            throw new CognitiveContractException("Fast confidence out of range");
        }
        return c <= 1.0 ? c : c / 100.0;
    }

    private static String requiredString(JSONObject object, String key) throws CognitiveContractException {
        if (object == null || key == null || !object.has(key) || object.isNull(key)) {
            throw new CognitiveContractException("Missing fast field: " + key);
        }
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) {
            throw new CognitiveContractException("Missing fast field: " + key);
        }
        return String.valueOf(value);
    }

    private static int intValue(JSONObject object, String key, int fallback) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) return fallback;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean boolValue(JSONObject object, String key, boolean fallback) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) return fallback;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        String text = String.valueOf(value).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text)) return true;
        if ("0".equals(text) || "false".equalsIgnoreCase(text)) return false;
        return fallback;
    }

    private static String nullableString(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return "";
        return clean(object.optString(key, ""));
    }

    private static Long dueAt(Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof Number) return ((Number) value).longValue();

        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;

        try {
            return Long.parseLong(text);
        } catch (Throwable ignored) {}

        try {
            return Instant.parse(text).toEpochMilli();
        } catch (Throwable ignored) {}

        return null;
    }

    private static String compactSummary(String value) {
        String text = clean(value).replaceAll("\\s+", " ");
        if (text.length() > 140) text = text.substring(0, 140).trim();
        return text;
    }

    private static String stripThinking(String raw) {
        return THINK.matcher(raw).replaceAll("").trim();
    }

    private static String extractJson(String raw) throws CognitiveContractException {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new CognitiveContractException("No JSON object in fast cognitive response");
        }
        return raw.substring(start, end + 1);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Classification {
        final CognitiveDisposition disposition;
        final String kindCode;

        Classification(CognitiveDisposition disposition, String kindCode) {
            this.disposition = disposition;
            this.kindCode = kindCode;
        }
    }
}
