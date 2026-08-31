package com.kareem.cortex.prime.evidence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal append-only evidence journal for Cortex Prime V0.
 * Evidence IDs are primary keys, so exact duplicate captures are idempotent.
 */
public final class EvidenceSqliteStore extends SQLiteOpenHelper implements EvidenceStore {
    private static final String DB_NAME = "cortex_prime_evidence.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "evidence_raw";

    public EvidenceSqliteStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id TEXT PRIMARY KEY NOT NULL,"
                + "source TEXT NOT NULL,"
                + "captured_at INTEGER NOT NULL,"
                + "raw_text TEXT NOT NULL,"
                + "source_ref TEXT NOT NULL,"
                + "raw_payload_json TEXT NOT NULL,"
                + "inserted_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX idx_evidence_source_ref ON " + TABLE + "(source_ref, captured_at)");
        db.execSQL("CREATE TRIGGER evidence_raw_no_update BEFORE UPDATE ON " + TABLE
                + " BEGIN SELECT RAISE(ABORT, 'raw evidence is immutable'); END");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // V0 starts at schema 1. Future migrations must be additive.
    }

    @Override
    public boolean append(EvidenceRecord record) {
        if (record == null) return false;
        ContentValues values = new ContentValues();
        values.put("id", record.id);
        values.put("source", record.source.name());
        values.put("captured_at", record.capturedAtEpochMs);
        values.put("raw_text", record.rawText);
        values.put("source_ref", record.sourceRef);
        values.put("raw_payload_json", record.rawPayloadJson);
        values.put("inserted_at", System.currentTimeMillis());
        long rowId = getWritableDatabase().insertWithOnConflict(
                TABLE,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
        return rowId != -1L;
    }

    @Override
    public List<EvidenceRecord> recent(int limit) {
        if (limit <= 0) return Collections.emptyList();
        int boundedLimit = Math.min(limit, 1000);
        List<EvidenceRecord> records = new ArrayList<>(boundedLimit);
        try (Cursor cursor = getReadableDatabase().query(
                TABLE,
                new String[]{"id", "source", "captured_at", "raw_text", "source_ref", "raw_payload_json"},
                null,
                null,
                null,
                null,
                "captured_at DESC, inserted_at DESC",
                String.valueOf(boundedLimit)
        )) {
            while (cursor.moveToNext()) {
                records.add(new EvidenceRecord(
                        cursor.getString(0),
                        EvidenceSource.valueOf(cursor.getString(1)),
                        cursor.getLong(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5)
                ));
            }
        }
        return records;
    }
}
