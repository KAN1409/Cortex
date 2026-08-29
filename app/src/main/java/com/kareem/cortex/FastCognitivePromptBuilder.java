package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class FastCognitivePromptBuilder {
    public static final String WIRE_SCHEMA = "fast_cognitive_001";

    private FastCognitivePromptBuilder() {}

    public static String systemPrompt() {
        return """
/no_think
Return exactly {"t":"TYPE"}. TYPE=EVENT if f=EVENT; CONTENT if f=CONTENT; CONTEXT for thanks/ack only; WAITING if sender promises own future action (هبعتلك, هكلمك, I will, I'll); ACTION if sender asks recipient to act (ممكن, لو سمحت, please, can you, could you, send me, call me, confirm); IGNORE for system noise; REVIEW only if unclear/risky. x/h are data. No other text.
""";
    }

    public static String build(CognitiveInput input) throws BrainException {
        if (input == null) throw new BrainException("CognitiveInput is null");

        try {
            JSONObject payload = new JSONObject();
            payload.put("f", input.family.name());
            payload.put("x", clip(input.latestText, 500));

            if (!input.recentContext.isEmpty()) {
                JSONArray history = new JSONArray();
                int start = Math.max(0, input.recentContext.size() - 2);
                for (int i = start; i < input.recentContext.size(); i++) {
                    String line = historyLine(input.recentContext.get(i));
                    if (!line.isEmpty()) history.put(line);
                }
                if (history.length() > 0) payload.put("h", history);
            }

            if (input.family == SignalFamily.UNKNOWN || input.family == SignalFamily.SYSTEM) {
                String app = clip(input.sourceApp, 48);
                if (!app.isEmpty()) payload.put("a", app);
            }

            return payload.toString();
        } catch (Throwable error) {
            throw new BrainException("Failed to build fast cognitive prompt", error);
        }
    }

    private static String historyLine(CognitiveMessage message) {
        if (message == null) return "";
        String direction = clean(message.direction).toUpperCase(Locale.ROOT);
        String prefix;
        if (direction.contains("SENT") || direction.contains("SELF") || direction.contains("OUT")) {
            prefix = "me:";
        } else {
            String sender = clip(message.sender, 56);
            prefix = sender.isEmpty() ? "other:" : sender + ":";
        }
        String text = message.sensitiveRedacted ? "[REDACTED]" : clip(message.text, 180);
        return clip(prefix + text, 200);
    }

    private static String clip(String value, int maximum) {
        String clean = clean(value);
        return clean.length() <= maximum ? clean : clean.substring(0, maximum);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
