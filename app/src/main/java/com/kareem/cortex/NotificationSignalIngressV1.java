package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;

/** Shared notification ingress so multiple sensors cannot create duplicate Raw Signals for one post. */
public final class NotificationSignalIngressV1 {
    private NotificationSignalIngressV1() {}

    public static long capture(VaultDb db, MasterRelevanceFilter.Signal signal) {
        if (db == null || signal == null) return 0;
        long existing = findSameNotification(db, signal);
        return existing > 0 ? existing : RawSignalStore.capture(db, signal);
    }

    private static long findSameNotification(VaultDb db, MasterRelevanceFilter.Signal signal) {
        if (!"notification".equalsIgnoreCase(signal.kind) || signal.occurredAt <= 0) return 0;
        String key = notificationKey(signal.metadataJson);
        if (key.isEmpty()) return 0;
        Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id,metadata_json FROM raw_signals WHERE kind='notification' AND source=? AND occurred_at=? ORDER BY id DESC LIMIT 16",
                new String[]{signal.source, String.valueOf(signal.occurredAt)});
        try {
            while (c.moveToNext()) {
                if (key.equals(notificationKey(c.getString(1)))) return c.getLong(0);
            }
            return 0;
        } finally { c.close(); }
    }

    static String notificationKey(String metadataJson) {
        try { return metadataJson == null ? "" : new JSONObject(metadataJson).optString("notification_key", "").trim(); }
        catch (Throwable ignored) { return ""; }
    }
}
