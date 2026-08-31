package com.kareem.cortex.prime.capture.relay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NotificationObservation {
    public final long occurredAtEpochMs;
    public final String packageName;
    public final String notificationKey;
    public final int notificationId;
    public final String tag;
    public final String groupKey;
    public final boolean group;
    public final boolean ongoing;
    public final String category;
    public final String channelId;
    public final String title;
    public final String body;
    public final String expandedText;
    public final String conversationTitle;
    public final List<NotificationMessage> messages;

    public NotificationObservation(
            long occurredAtEpochMs,
            String packageName,
            String notificationKey,
            int notificationId,
            String tag,
            String groupKey,
            boolean group,
            boolean ongoing,
            String category,
            String channelId,
            String title,
            String body,
            String expandedText,
            String conversationTitle,
            List<NotificationMessage> messages
    ) {
        this.occurredAtEpochMs = occurredAtEpochMs;
        this.packageName = clean(packageName);
        this.notificationKey = clean(notificationKey);
        this.notificationId = notificationId;
        this.tag = clean(tag);
        this.groupKey = clean(groupKey);
        this.group = group;
        this.ongoing = ongoing;
        this.category = clean(category);
        this.channelId = clean(channelId);
        this.title = clean(title);
        this.body = clean(body);
        this.expandedText = clean(expandedText);
        this.conversationTitle = clean(conversationTitle);
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages == null ? Collections.emptyList() : messages));
    }

    public String sourceRef() {
        return "notification://" + packageName + "/" + notificationKey;
    }

    public String textForEvidence() {
        StringBuilder out = new StringBuilder();
        appendLine(out, "conversation", conversationTitle);
        appendLine(out, "title", title);
        appendLine(out, "body", body);
        if (!expandedText.isEmpty() && !expandedText.equals(body)) appendLine(out, "expanded", expandedText);
        for (NotificationMessage message : messages) {
            if (message.text.isEmpty()) continue;
            if (!message.sender.isEmpty()) out.append(message.sender).append(": ");
            out.append(message.text).append('\n');
        }
        return out.toString().trim();
    }

    public String canonicalPayloadJson() {
        StringBuilder out = new StringBuilder(512);
        out.append('{');
        field(out, "packageName", packageName).append(',');
        field(out, "notificationKey", notificationKey).append(',');
        out.append("\"notificationId\":").append(notificationId).append(',');
        field(out, "tag", tag).append(',');
        field(out, "groupKey", groupKey).append(',');
        out.append("\"isGroup\":").append(group).append(',');
        out.append("\"isOngoing\":").append(ongoing).append(',');
        field(out, "category", category).append(',');
        field(out, "channelId", channelId).append(',');
        field(out, "title", title).append(',');
        field(out, "body", body).append(',');
        field(out, "expandedText", expandedText).append(',');
        field(out, "conversationTitle", conversationTitle).append(',');
        out.append("\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) out.append(',');
            NotificationMessage message = messages.get(i);
            out.append('{');
            field(out, "sender", message.sender).append(',');
            field(out, "text", message.text).append(',');
            out.append("\"timestampEpochMs\":").append(message.timestampEpochMs);
            out.append('}');
        }
        out.append("],\"occurredAtEpochMs\":").append(occurredAtEpochMs).append('}');
        return out.toString();
    }

    private static void appendLine(StringBuilder out, String label, String value) {
        if (value == null || value.isEmpty()) return;
        if (out.length() > 0) out.append('\n');
        out.append(label).append(": ").append(value);
    }

    private static StringBuilder field(StringBuilder out, String name, String value) {
        return out.append('"').append(name).append("\":\"").append(escape(clean(value))).append('"');
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
