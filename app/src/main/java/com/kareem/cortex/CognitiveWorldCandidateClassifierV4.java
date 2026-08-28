package com.kareem.cortex;

import org.json.JSONObject;
import java.util.Locale;

/**
 * Deterministic semantic gate before a hint is allowed to become a typed V4 World.
 *
 * <p>Identity and semantic type are deliberately separate. A stable sender key can prove that two
 * observations came from the same participant without proving that the participant is a human
 * PERSON rather than an organization, bot, group, or app/system surface.</p>
 */
public final class CognitiveWorldCandidateClassifierV4 {
    private CognitiveWorldCandidateClassifierV4() {}

    public enum SemanticClass {
        PERSON,
        ORGANIZATION,
        GROUP_CONVERSATION,
        APP_SYSTEM,
        UNKNOWN
    }

    public static final class Decision {
        public final SemanticClass semanticClass;
        public final String candidateName;
        public final double confidence;
        public final boolean typeMaterializationApproved;
        public final String reason;

        Decision(SemanticClass semanticClass, String candidateName, double confidence,
                 boolean typeMaterializationApproved, String reason) {
            this.semanticClass = semanticClass == null ? SemanticClass.UNKNOWN : semanticClass;
            this.candidateName = clean(candidateName);
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            this.typeMaterializationApproved = typeMaterializationApproved;
            this.reason = reason == null ? "" : reason;
        }
    }

    public static Decision inspect(String sourcePackage, String metadataJson) {
        JSONObject root = json(metadataJson);
        JSONObject legacy = object(root, "legacy_metadata");
        JSONObject source = object(legacy, "source_metadata");
        if (source.length() == 0) source = object(root, "source_metadata");

        String organization = first(root, legacy, source, "organization_name", "company_name");
        if (!organization.isEmpty()) {
            return new Decision(SemanticClass.ORGANIZATION, organization, 0.995, true,
                    "explicit structured organization field");
        }

        String explicitPerson = first(root, legacy, source, "person_name", "contact_name");
        String participant = first(root, legacy, source, "participant_name", "sender_name");
        String personHint = first(root, legacy, source, "person_hint");
        String name = !explicitPerson.isEmpty() ? explicitPerson : (!participant.isEmpty() ? participant : personHint);

        boolean group = firstBoolean(root, legacy, source, "group_conversation", "is_group_conversation");
        String notificationKind = first(root, legacy, source, "notification_kind").toLowerCase(Locale.ROOT);
        boolean communication = firstBoolean(root, legacy, source, "communication");

        if (name.isEmpty()) {
            if (group) {
                String conversation = first(root, legacy, source, "conversation_title");
                return new Decision(SemanticClass.GROUP_CONVERSATION, conversation, 0.99, false,
                        "group conversation metadata without a participant");
            }
            return new Decision(SemanticClass.UNKNOWN, "", 0.0, false, "no semantic candidate name");
        }

        if (looksGenericSystemLabel(name)) {
            return new Decision(SemanticClass.APP_SYSTEM, name, 0.99, false,
                    "generic app/system notification label");
        }

        if (group && explicitPerson.isEmpty() && participant.isEmpty()) {
            return new Decision(SemanticClass.GROUP_CONVERSATION, name, 0.99, false,
                    "conversation title/hint is a group, not a person");
        }

        boolean durableContact = !first(root, legacy, source, "contact_id").isEmpty();
        boolean durablePhone = !first(root, legacy, source, "phone_e164", "phone").isEmpty();
        boolean durableParticipant = !first(root, legacy, source,
                "participant_key", "sender_key", "participant_uri", "sender_uri",
                "participant_id", "sender_id", "account_id").isEmpty();

        if (!explicitPerson.isEmpty()) {
            return new Decision(SemanticClass.PERSON, explicitPerson,
                    durableContact || durablePhone ? 0.995 : 0.96, true,
                    "explicit structured person/contact field");
        }

        if (!participant.isEmpty()) {
            // Android messaging participants are identity-shaped, but can still be businesses/bots.
            // Keep their durable key for identity matching while requiring another semantic signal
            // before a brand-new PERSON World may be materialized.
            double confidence = durableParticipant ? 0.90 : 0.82;
            return new Decision(SemanticClass.PERSON, participant, confidence, false,
                    durableParticipant
                            ? "stable message participant identity; human type still unconfirmed"
                            : "message participant name; human type still unconfirmed");
        }

        if ("email".equals(notificationKind)) {
            return new Decision(SemanticClass.UNKNOWN, name, 0.60, false,
                    "email sender may be a person, organization, automation, or list");
        }

        if (("message".equals(notificationKind) || "call".equals(notificationKind)) && communication) {
            return new Decision(SemanticClass.PERSON, name, 0.70, false,
                    "communication person hint only; type and identity require corroboration");
        }

        String pkg = clean(sourcePackage).toLowerCase(Locale.ROOT);
        if (isSystemPackage(pkg) || !communication || "notification".equals(notificationKind)) {
            return new Decision(SemanticClass.APP_SYSTEM, name, 0.84, false,
                    "non-communication notification hint is not a safe person assertion");
        }

        return new Decision(SemanticClass.UNKNOWN, name, 0.45, false,
                "insufficient semantic evidence");
    }

    static boolean looksGenericSystemLabel(String raw) {
        String x = clean(raw).toLowerCase(Locale.ROOT);
        if (x.isEmpty()) return true;
        if (x.equals("backup") || x.equals("backup in progress") || x.equals("syncing")
                || x.equals("missed call") || x.equals("incoming call") || x.equals("edge lighting")) return true;
        if (x.matches("\\d+\\s+new\\s+messages?")) return true;
        if (x.contains("displaying over other apps")) return true;
        if (x.startsWith("battery ") || x.matches("battery.*\\d+%.*")) return true;
        if (x.endsWith(" voice") && (x.startsWith("chatgpt") || x.startsWith("google") || x.startsWith("assistant"))) return true;
        return false;
    }

    private static boolean isSystemPackage(String pkg) {
        return pkg.equals("android") || pkg.equals("com.android.systemui") || pkg.startsWith("com.android.");
    }

    private static JSONObject json(String raw) {
        try { return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw); }
        catch (Throwable ignored) { return new JSONObject(); }
    }

    private static JSONObject object(JSONObject parent, String key) {
        if (parent == null) return new JSONObject();
        JSONObject value = parent.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }

    private static String first(JSONObject a, JSONObject b, JSONObject c, String... keys) {
        JSONObject[] sources = new JSONObject[]{a, b, c};
        for (String key : keys) {
            for (JSONObject source : sources) {
                if (source == null) continue;
                String value = source.optString(key, "").trim();
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }

    private static boolean firstBoolean(JSONObject a, JSONObject b, JSONObject c, String... keys) {
        JSONObject[] sources = new JSONObject[]{a, b, c};
        for (String key : keys) {
            for (JSONObject source : sources) {
                if (source == null || !source.has(key)) continue;
                Object value = source.opt(key);
                if (value instanceof Boolean) return (Boolean) value;
                String text = value == null ? "" : String.valueOf(value).trim();
                if ("true".equalsIgnoreCase(text) || "1".equals(text)) return true;
                if ("false".equalsIgnoreCase(text) || "0".equals(text)) return false;
            }
        }
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }
}
