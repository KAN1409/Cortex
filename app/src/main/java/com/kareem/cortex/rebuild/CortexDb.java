package com.kareem.cortex.rebuild;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Fresh Cortex storage. No schema, table or migration is inherited from the previous app. */
public final class CortexDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "cortex_rebuild.db";
    private static final int DB_VERSION = 1;

    public CortexDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE evidence(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "kind TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "source TEXT NOT NULL," +
                "occurred_at INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE memories(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "evidence_id INTEGER NOT NULL," +
                "title TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE situations(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "summary TEXT NOT NULL," +
                "state TEXT NOT NULL DEFAULT 'active'," +
                "attention TEXT NOT NULL DEFAULT 'quiet'," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE world_entities(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "entity_type TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "summary TEXT NOT NULL DEFAULT ''," +
                "state TEXT NOT NULL DEFAULT 'active'," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_memories_created ON memories(created_at DESC)");
        db.execSQL("CREATE INDEX idx_situations_state ON situations(state,updated_at DESC)");
        db.execSQL("CREATE INDEX idx_world_state ON world_entities(state,updated_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Rebuild V1 starts at schema 1. Future upgrades will be additive.
    }

    public long capture(String text) {
        String clean = clean(text);
        if (clean.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues evidence = new ContentValues();
            evidence.put("kind", "MANUAL_CAPTURE");
            evidence.put("body", clean);
            evidence.put("source", "user");
            evidence.put("occurred_at", now);
            evidence.put("created_at", now);
            long evidenceId = db.insertOrThrow("evidence", null, evidence);

            ContentValues memory = new ContentValues();
            memory.put("evidence_id", evidenceId);
            memory.put("title", titleFor(clean));
            memory.put("body", clean);
            memory.put("created_at", now);
            long memoryId = db.insertOrThrow("memories", null, memory);
            db.setTransactionSuccessful();
            return memoryId;
        } finally {
            db.endTransaction();
        }
    }

    public List<Row> recentMemories(int limit) {
        ArrayList<Row> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("memories",
                new String[]{"id","title","body","created_at"},
                null,null,null,null,"created_at DESC",String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), "MEMORY"));
        } finally { c.close(); }
        return out;
    }

    public List<Row> activeSituations(int limit) {
        ArrayList<Row> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("situations",
                new String[]{"id","title","summary","updated_at","attention"},
                "state='active'",null,null,null,"updated_at DESC",String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4)));
        } finally { c.close(); }
        return out;
    }

    public List<Row> worldEntities(int limit) {
        ArrayList<Row> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("world_entities",
                new String[]{"id","name","summary","updated_at","entity_type"},
                "state='active'",null,null,null,"updated_at DESC",String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4)));
        } finally { c.close(); }
        return out;
    }

    public List<Row> searchMemories(String query, int limit) {
        String q = clean(query);
        if (q.isEmpty()) return new ArrayList<>();
        ArrayList<Row> out = new ArrayList<>();
        String like = "%" + q.replace("%", "\\%").replace("_", "\\_") + "%";
        Cursor c = getReadableDatabase().query("memories",
                new String[]{"id","title","body","created_at"},
                "title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\'",
                new String[]{like,like},null,null,"created_at DESC",String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), "MEMORY"));
        } finally { c.close(); }
        return out;
    }

    private static String titleFor(String text) {
        String one = text.replace('\n',' ').replace('\r',' ').replaceAll("\\s+", " ").trim();
        if (one.length() <= 52) return one;
        return one.substring(0, 52).trim() + "…";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Row {
        public final long id, updatedAt;
        public final String title, body, type;
        Row(long id, String title, String body, long updatedAt, String type) {
            this.id=id; this.title=title==null?"":title; this.body=body==null?"":body;
            this.updatedAt=updatedAt; this.type=type==null?"":type;
        }
    }
}
