package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Tiny durable counters for idempotent notification ingestion. */
public final class NotificationIngestDiagnostics {
    private NotificationIngestDiagnostics(){}
    public static void ensure(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS notification_ingest_metrics(id INTEGER PRIMARY KEY CHECK(id=1),exact_duplicate_deliveries INTEGER NOT NULL DEFAULT 0,raw_writes_avoided INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0)");db.execSQL("INSERT OR IGNORE INTO notification_ingest_metrics(id,exact_duplicate_deliveries,raw_writes_avoided,updated_at) VALUES(1,0,0,0)");}
    public static void recordDuplicate(SQLiteDatabase db){if(db==null)return;ensure(db);db.execSQL("UPDATE notification_ingest_metrics SET exact_duplicate_deliveries=exact_duplicate_deliveries+1,raw_writes_avoided=raw_writes_avoided+1,updated_at=? WHERE id=1",new Object[]{System.currentTimeMillis()});}
    public static Snapshot snapshot(SQLiteDatabase db){if(db==null)return new Snapshot(0,0,0);ensure(db);Cursor c=db.rawQuery("SELECT exact_duplicate_deliveries,raw_writes_avoided,updated_at FROM notification_ingest_metrics WHERE id=1",null);try{return c.moveToFirst()?new Snapshot(c.getLong(0),c.getLong(1),c.getLong(2)):new Snapshot(0,0,0);}finally{c.close();}}
    public static final class Snapshot{public final long exactDuplicateDeliveries,rawWritesAvoided,updatedAt;Snapshot(long d,long r,long u){exactDuplicateDeliveries=d;rawWritesAvoided=r;updatedAt=u;}}
}
