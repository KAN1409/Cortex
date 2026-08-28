package com.kareem.cortex;

import android.app.Notification;
import android.app.Person;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import org.json.JSONObject;

/**
 * Adds identity-shaped notification metadata without deciding canonical identity.
 * Values here are Evidence hints; the V4 resolver still enforces durable-identity rules.
 */
public final class NotificationIdentityHintsV4 {
    private NotificationIdentityHintsV4() {}

    public static void enrich(JSONObject meta, Notification notification, Bundle extras) {
        if (meta == null || notification == null || extras == null) return;
        try {
            CharSequence conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
            if (conversation != null && !conversation.toString().trim().isEmpty()) {
                meta.put("conversation_title", conversation.toString().trim());
            }

            boolean group = false;
            if (Build.VERSION.SDK_INT >= 28 && extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)) {
                group = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false);
                meta.put("group_conversation", group);
            }

            Bundle latest = latestMessage(extras);
            if (latest != null) {
                String senderName = text(latest.getCharSequence("sender"));
                if (!senderName.isEmpty()) meta.put("participant_name", senderName);

                if (Build.VERSION.SDK_INT >= 28) {
                    Parcelable parcelable = latest.getParcelable("sender_person");
                    if (parcelable instanceof Person) {
                        Person person = (Person) parcelable;
                        String name = text(person.getName());
                        String key = clean(person.getKey());
                        String uri = clean(person.getUri());
                        if (!name.isEmpty()) meta.put("participant_name", name);
                        if (!key.isEmpty()) meta.put("participant_key", key);
                        if (!uri.isEmpty()) meta.put("participant_uri", uri);
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= 26) {
                String shortcut = clean(notification.getShortcutId());
                if (!shortcut.isEmpty()) meta.put("conversation_key", shortcut);
            }
        } catch (Throwable ignored) {
            // Identity enrichment must never break notification capture.
        }
    }

    private static Bundle latestMessage(Bundle extras) {
        try {
            Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages == null) return null;
            for (int i = messages.length - 1; i >= 0; i--) {
                if (messages[i] instanceof Bundle) return (Bundle) messages[i];
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String text(CharSequence value) {
        return value == null ? "" : clean(value.toString());
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }
}
