package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/**
 * Canonical user-resolution bridge for FINAL_JUDGE attention mirrors.
 *
 * Prime/Now rows are presented through the compatibility mirror in derived_items. A user tapping
 * "Mark resolved" must retire the canonical attention row and the owning situation as one action,
 * otherwise the next materialization pass can legitimately surface the still-open situation again.
 */
public final class CanonicalResolutionTriggers {
    public static final String VERSION = "canonical_resolution_triggers_001";

    private CanonicalResolutionTriggers() {}

    public static void ensure(SQLiteDatabase db) {
        if (db == null) return;
        UniversalEventStore.ensure(db);
        db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS trg_cortex_user_resolve_attention " +
                "AFTER UPDATE OF state ON derived_items " +
                "WHEN NEW.candidate_kind='UE_ATTENTION' AND NEW.state='resolved' " +
                "BEGIN " +
                "UPDATE ue_attention_items SET state='resolved',updated_at=NEW.updated_at," +
                "resolved_at=CASE WHEN NEW.updated_at>0 THEN NEW.updated_at ELSE CAST(strftime('%s','now') AS INTEGER)*1000 END " +
                "WHERE id=NEW.id-" + UniversalEventStore.ATTENTION_COMPAT_OFFSET + "; " +
                "UPDATE ue_situations SET state='resolved',last_changed_at=NEW.updated_at,updated_at=NEW.updated_at," +
                "resolved_at=CASE WHEN NEW.updated_at>0 THEN NEW.updated_at ELSE CAST(strftime('%s','now') AS INTEGER)*1000 END " +
                "WHERE id=NEW.thread_id AND NEW.thread_id>0; " +
                "END");
    }
}
