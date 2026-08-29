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
        if (raw == null || raw.trim().isEmpty()) {
            throw new CognitiveContractException("Empty fast cognitive response");
        }

        try {
            String json = extractJson(stripThinking(raw));
            JSONObject root = new JSONObject(json);

            // Transition safety: accept the old verbose contract if a model unexpectedly emits it.
            if (!root.has("d") && root.has("disposition")) {
                return CognitiveResultParser.parse(raw);
            }

            CognitiveDisposition disposition = disposition(root.getString("d"));
            double confidence = confidence(root.get("c"));
            List<CognitiveItem> items = new ArrayList<>();

            if (disposition == CognitiveDisposition.DERIVE) {
                JSONArray array = root.optJSONArray("it");
                if (array != null) {
                    if (array.length() < 1 || array.length() > 2) {
                        throw new CognitiveContractException("Fast DERIVE supports one or two items");
                    }
                    for (int i = 0; i < array.length(); i++) {
                        items.add(item(array.getJSONObject(i)));
                    }
                } else {
                    items.add(item(root));
                }
            }

            return new CognitiveResult(
                    disposition,
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

    private static CognitiveItem item(JSONObject object) throws CognitiveContractException {
        CognitiveKind kind = kind(object.getString("k"));
        String summary = object.getString("s").trim();
        if (summary.isEmpty()) {
            throw new CognitiveContractException("Fast derived item has no summary");
        }

        return new CognitiveItem(
                kind,
                summary,
                intValue(object, "i", 0),
                intValue(object, "u", 0),
                nullableString(object, "p"),
                dueAt(object.opt("due")),
                boolValue(object, "ua", false),
                boolValue(object, "fu", false),
                boolValue(object, "ce", false)
        );
    }

    private static CognitiveDisposition disposition(String code) throws CognitiveContractException {
        switch (clean(code).toUpperCase(Locale.ROOT)) {
            case "I": return CognitiveDisposition.IGNORE;
            case "C": return CognitiveDisposition.CONTEXT;
            case "D": return CognitiveDisposition.DERIVE;
            case "R": return CognitiveDisposition.REVIEW;
            default: throw new CognitiveContractException("Unknown fast disposition: " + code);
        }
    }

    private static CognitiveKind kind(String code) throws CognitiveContractException {
        switch (clean(code).toUpperCase(Locale.ROOT)) {
            case "AC": return CognitiveKind.ACTION;
            case "WA": return CognitiveKind.WAITING;
            case "DE": return CognitiveKind.DECISION;
            case "EV": return CognitiveKind.EVENT;
            case "CO": return CognitiveKind.CONTENT;
            case "MS": return CognitiveKind.MESSAGE;
            case "RE": return CognitiveKind.REMINDER;
            case "IN": return CognitiveKind.INSIGHT;
            case "ME": return CognitiveKind.MEMORY;
            default: throw new CognitiveContractException("Unknown fast kind: " + code);
        }
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
        return c / 100.0;
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
}
