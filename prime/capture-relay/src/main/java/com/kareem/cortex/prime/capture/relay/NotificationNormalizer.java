package com.kareem.cortex.prime.capture.relay;

import android.app.Notification;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean extraction of the proven Relay notification semantics.
 * No Hilt, repository, Local Bus or cross-app transport lives here.
 */
public final class NotificationNormalizer {
    private NotificationNormalizer() {}

    public static NotificationObservation from(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras == null ? Bundle.EMPTY : notification.extras;
        List<NotificationMessage> messages = new ArrayList<>();

        Parcelable[] rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (rawMessages != null) {
            List<Notification.MessagingStyle.Message> parsed =
                    Notification.MessagingStyle.Message.getMessagesFromBundleArray(rawMessages);
            for (Notification.MessagingStyle.Message message : parsed) {
                String text = string(message.getText());
                if (text.isEmpty()) continue;
                String sender = "";
                if (Build.VERSION.SDK_INT >= 28 && message.getSenderPerson() != null) {
                    sender = string(message.getSenderPerson().getName());
                } else {
                    sender = string(message.getSender());
                }
                messages.add(new NotificationMessage(sender, text, Math.max(0L, message.getTimestamp())));
            }
        }

        return new NotificationObservation(
                sbn.getPostTime(),
                sbn.getPackageName(),
                sbn.getKey(),
                sbn.getId(),
                sbn.getTag(),
                sbn.getGroupKey(),
                sbn.isGroup(),
                sbn.isOngoing(),
                string(notification.category),
                string(notification.getChannelId()),
                string(extras.getCharSequence(Notification.EXTRA_TITLE)),
                string(extras.getCharSequence(Notification.EXTRA_TEXT)),
                string(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
                string(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)),
                messages
        );
    }

    private static String string(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static String string(String value) {
        return value == null ? "" : value;
    }
}
