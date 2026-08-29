package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * Materialized current truth for Cortex.
 *
 * Evidence and derived decisions remain append-only for provenance. This table is different: it is
 * the replaceable projection of what Cortex currently believes is happening. One communication
 * thread therefore becomes one changing situation instead of a growing pile of notification cards.
 */
public final class BrainSituationStore {
    public static final String VERSION = "brain_situation_002";
    private static final String TABLE = "brain_situations";
    private BrainSituationStore() {}

    public static final class Item {
        public final long id, currentDerivedId, threadId, signalId, modelRunId, dueAt;
        public final long firstSeenAt, lastChangedAt, updatedAt;
        public final String key, kind, title, body, state, sourceKey, personKey, whyChanged;
        public final double confidence;
        public final int importance, urgency, evidenceCount;

        Item(long id, String key, String kind, String title, String body, String state,
             String sourceKey, String personKey, long currentDerivedId, long threadId,
             long signalId, long modelRunId, long dueAt, double confidence, int importance,
             int urgency, int evidenceCount, long firstSeenAt, long lastChangedAt, long updatedAt,
             String whyChanged) {
            this.id=id;this.key=n(key);this.kind=n(kind);this.title=n(title);this.body=n(body);
            this.state=n(state);this.sourceKey=n(sourceKey);this.personKey=n(personKey);
            this.currentDerivedId=currentDerivedId;this.threadId=threadId;this.signalId=signalId;
            this.modelRunId=modelRunId;this.dueAt=dueAt;this.confidence=confidence;
            this.importance=importance;this.urgency=urgency;this.evidenceCount=evidenceCount;
            this.firstSeenAt=firstSeenAt;this.lastChangedAt=lastChangedAt;this.updatedAt=updatedAt;
            this.whyChanged=n(whyChanged);
        }
    }

    public static void ensure(VaultDb vault) {
        if (vault == null) return;
        SQLiteDatabase db = vault.getWritableDatabase();
        db.execSQL("CREATE TABLE IF NOT EXISTS "+TABLE+"("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "situation_key TEXT NOT NULL UNIQUE,"+
                "kind TEXT NOT NULL,title TEXT NOT NULL,body TEXT,state TEXT NOT NULL DEFAULT 'active',"+
                "source_key TEXT DEFAULT '',person_key TEXT DEFAULT '',"+
                "current_derived_id INTEGER DEFAULT 0,thread_id INTEGER DEFAULT 0,anchor_signal_id INTEGER DEFAULT 0,"+
                "model_run_id INTEGER DEFAULT 0,due_at INTEGER DEFAULT 0,confidence REAL DEFAULT 0,"+
                "importance INTEGER DEFAULT 0,urgency INTEGER DEFAULT 0,evidence_count INTEGER DEFAULT 1,"+
                "first_seen_at INTEGER NOT NULL,last_changed_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,"+
                "resolved_at INTEGER DEFAULT 0,why_changed TEXT DEFAULT '',metadata_json TEXT DEFAULT '')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_brain_situation_state ON "+TABLE+"(state,importance DESC,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_brain_situation_thread ON "+TABLE+"(thread_id,state,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_brain_situation_signal ON "+TABLE+"(anchor_signal_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_brain_situation_changed ON "+TABLE+"(last_changed_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_brain_situation_due ON "+TABLE+"(state,due_at ASC)");
    }

    /** Rebuilds the replaceable projection from canonical open derived truth without deleting history. */
    public static int reconcile(VaultDb vault) {
        if (vault == null) return 0;
        CognitiveStore.ensure(vault);
        ensure(vault);
        try { SituationLifecycle.reconcile(vault); } catch (Throwable ignored) {}
        SQLiteDatabase db = vault.getWritableDatabase();
        ArrayList<Candidate> candidates = loadCandidates(db);
        HashSet<String> touched = new HashSet<>();
        long now = System.currentTimeMillis();
        int changed = 0;

        for (Candidate candidate : candidates) {
            String key = situationKey(candidate);
            if (key.isEmpty() || !touched.add(key)) continue;
            long id = upsert(db, key, candidate, now);
            if (id > 0) {
                changed++;
                linkProvenance(vault, id, candidate);
            }
        }

        Cursor active = db.query(TABLE, new String[]{"id","situation_key"}, "state='active'", null,
                null,null,null);
        ArrayList<Long> retire = new ArrayList<>();
        try {
            while (active.moveToNext()) if (!touched.contains(n(active.getString(1)))) retire.add(active.getLong(0));
        } finally { active.close(); }
        for (Long id : retire) {
            ContentValues v = new ContentValues();
            v.put("state", "resolved");v.put("resolved_at", now);v.put("updated_at", now);
            v.put("why_changed", "No longer supported by an open canonical state");
            if (db.update(TABLE, v, "id=? AND state='active'", new String[]{String.valueOf(id)}) > 0) changed++;
        }
        return changed;
    }

    public static ArrayList<Item> current(VaultDb vault, int limit) {
        ensure(vault);
        ArrayList<Item> out = new ArrayList<>();
        Cursor c = vault.getReadableDatabase().query(TABLE,
                new String[]{"id","situation_key","kind","title","body","state","source_key","person_key",
                        "current_derived_id","thread_id","anchor_signal_id","model_run_id","due_at","confidence",
                        "importance","urgency","evidence_count","first_seen_at","last_changed_at","updated_at","why_changed"},
                "state='active'",null,null,null,
                "importance DESC,urgency DESC,last_changed_at DESC,updated_at DESC",
                String.valueOf(Math.max(1, Math.min(200, limit))));
        try { while (c.moveToNext()) out.add(from(c)); } finally { c.close(); }
        return out;
    }

    public static Item byDerivedId(VaultDb vault, long derivedId) {
        if (vault == null || derivedId <= 0) return null;
        ensure(vault);
        Cursor c = vault.getReadableDatabase().query(TABLE,
                new String[]{"id","situation_key","kind","title","body","state","source_key","person_key",
                        "current_derived_id","thread_id","anchor_signal_id","model_run_id","due_at","confidence",
                        "importance","urgency","evidence_count","first_seen_at","last_changed_at","updated_at","why_changed"},
                "current_derived_id=?",new String[]{String.valueOf(derivedId)},null,null,null,"1");
        try { return c.moveToFirst() ? from(c) : null; } finally { c.close(); }
    }

    public static int activeCount(VaultDb vault) {
        ensure(vault);Cursor c=vault.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+TABLE+" WHERE state='active'",null);
        try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}
    }

    public static long lastChangedAt(VaultDb vault) {
        ensure(vault);Cursor c=vault.getReadableDatabase().rawQuery("SELECT MAX(last_changed_at) FROM "+TABLE+" WHERE state='active'",null);
        try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}
    }

    private static ArrayList<Candidate> loadCandidates(SQLiteDatabase db) {
        ArrayList<Candidate> out = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT id,kind,title,body,source_key,confidence,importance,urgency,person_key,due_at,"+
                "thread_id,anchor_signal_id,model_run_id,semantic_key,created_at,updated_at " +
                "FROM derived_items WHERE state='open' AND kind IN ("+
                "'ACTION','WAITING','DECISION','REMINDER','ALERT','CHANGE','EVENT','CONTENT','INSIGHT','IDEA','OPPORTUNITY','PROJECT_CANDIDATE','GOAL_SIGNAL') " +
                "ORDER BY updated_at DESC,id DESC LIMIT 2500", null);
        try {
            while (c.moveToNext()) {
                Candidate x = new Candidate();
                x.derivedId=c.getLong(0);x.kind=n(c.getString(1)).toUpperCase(Locale.ROOT);
                x.title=n(c.getString(2));x.body=n(c.getString(3));x.source=n(c.getString(4));
                x.confidence=c.getDouble(5);x.importance=c.getInt(6);x.urgency=c.getInt(7);
                x.person=n(c.getString(8));x.dueAt=c.getLong(9);x.threadId=c.getLong(10);
                x.signalId=c.getLong(11);x.modelRunId=c.getLong(12);x.semanticKey=n(c.getString(13));
                x.createdAt=c.getLong(14);x.updatedAt=c.getLong(15);
                if (!PrimeBriefStore.hardSurfaceNoise(new PrimeBriefStore.Item(x.derivedId,x.kind,x.title,x.body,x.source,"open",x.confidence,x.importance,x.threadId,x.signalId,x.updatedAt))) out.add(x);
            }
        } finally { c.close(); }
        return out;
    }

    private static String situationKey(Candidate x) {
        if (x.threadId > 0) return "thread:" + x.threadId;
        if (!x.semanticKey.isEmpty()) return "semantic:" + x.semanticKey;
        String identity = n(x.person) + "|" + n(x.title) + "|" + n(x.body);
        if (identity.trim().isEmpty()) identity = x.kind + "|derived:" + x.derivedId;
        return "meaning:" + Fingerprint.text(identity);
    }

    private static long upsert(SQLiteDatabase db, String key, Candidate x, long now) {
        Existing old = existing(db, key);
        long anchor = x.createdAt>0 ? x.createdAt : (x.updatedAt>0 ? x.updatedAt : now);
        long resolvedDue = x.dueAt>0 ? x.dueAt : TemporalResolver.resolveForAttention(title(x)+" "+x.body, anchor);
        String why = changeReason(old, x, resolvedDue);
        long firstSeen = old == null || old.firstSeenAt <= 0 ? Math.max(1, x.createdAt) : old.firstSeenAt;
        long lastChanged = old == null || !why.isEmpty() ? now : old.lastChangedAt;
        int evidence = evidenceCount(db, x);
        JSONObject meta = new JSONObject();
        try {
            meta.put("projection", VERSION);meta.put("semantic_key",x.semanticKey);
            meta.put("current_derived_id",x.derivedId);meta.put("anchor_signal_id",x.signalId);
            meta.put("temporal_anchor_at",anchor);meta.put("resolved_due_at",Math.max(0,resolvedDue));
        } catch (Throwable ignored) {}
        ContentValues v = new ContentValues();
        v.put("situation_key", key);v.put("kind",x.kind);v.put("title",title(x));v.put("body",n(x.body));
        v.put("state","active");v.put("source_key",x.source);v.put("person_key",x.person);
        v.put("current_derived_id",x.derivedId);v.put("thread_id",Math.max(0,x.threadId));v.put("anchor_signal_id",Math.max(0,x.signalId));
        v.put("model_run_id",Math.max(0,x.modelRunId));v.put("due_at",Math.max(0,resolvedDue));v.put("confidence",x.confidence);
        v.put("importance",clamp100(x.importance));v.put("urgency",clamp100(x.urgency));v.put("evidence_count",Math.max(1,evidence));
        v.put("first_seen_at",firstSeen);v.put("last_changed_at",lastChanged);v.put("updated_at",now);v.put("resolved_at",0);
        v.put("why_changed",why);v.put("metadata_json",meta.toString());
        if(old==null){
            long inserted=db.insert(TABLE,null,v);return inserted>0?inserted:0;
        }
        if(db.update(TABLE,v,"id=?",new String[]{String.valueOf(old.id)})<=0)return 0;
        return old.id;
    }

    private static Existing existing(SQLiteDatabase db, String key) {
        Cursor c=db.query(TABLE,new String[]{"id","kind","title","body","current_derived_id","due_at","first_seen_at","last_changed_at"},
                "situation_key=?",new String[]{key},null,null,null,"1");
        try {
            if(!c.moveToFirst())return null;
            Existing e=new Existing();e.id=c.getLong(0);e.kind=n(c.getString(1));e.title=n(c.getString(2));e.body=n(c.getString(3));
            e.derivedId=c.getLong(4);e.dueAt=c.getLong(5);e.firstSeenAt=c.getLong(6);e.lastChangedAt=c.getLong(7);return e;
        } finally { c.close(); }
    }

    private static int evidenceCount(SQLiteDatabase db, Candidate x) {
        if (x.threadId > 0) {
            Cursor c=db.rawQuery("SELECT COUNT(*) FROM raw_signals WHERE thread_id=?",new String[]{String.valueOf(x.threadId)});
            try{return c.moveToFirst()?Math.max(1,c.getInt(0)):1;}finally{c.close();}
        }
        return x.signalId>0?1:0;
    }

    private static String changeReason(Existing old, Candidate x, long dueAt) {
        if (old == null) return "New grounded situation";
        if (!old.kind.equalsIgnoreCase(x.kind)) return old.kind + " → " + x.kind;
        if (!canon(old.title+" "+old.body).equals(canon(title(x)+" "+x.body))) return "Meaning changed with newer evidence";
        if (old.dueAt != Math.max(0,dueAt)) return "Timing changed with newer evidence";
        if (old.derivedId != x.derivedId) return "Refreshed by newer evidence";
        return "";
    }

    private static void linkProvenance(VaultDb vault, long situationId, Candidate x) {
        String meta="{\"projection\":\""+VERSION+"\"}";
        if(x.derivedId>0)CognitiveStore.link(vault,"derived",x.derivedId,"situation",situationId,"current_state_of",1.0,meta);
        if(x.signalId>0)CognitiveStore.link(vault,"raw_signal",x.signalId,"situation",situationId,"supports_current_state",1.0,meta);
        if(x.modelRunId>0)CognitiveStore.link(vault,"model_run",x.modelRunId,"situation",situationId,"generated_current_state",x.confidence,meta);
        if(x.threadId>0)CognitiveStore.link(vault,"thread",x.threadId,"situation",situationId,"represented_by",1.0,meta);
    }

    private static Item from(Cursor c) {
        return new Item(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),
                c.getString(6),c.getString(7),c.getLong(8),c.getLong(9),c.getLong(10),c.getLong(11),c.getLong(12),
                c.getDouble(13),c.getInt(14),c.getInt(15),c.getInt(16),c.getLong(17),c.getLong(18),c.getLong(19),c.getString(20));
    }

    private static String title(Candidate x){return x.title.isEmpty()?friendly(x.kind):x.title;}
    private static String friendly(String kind){String x=n(kind).toLowerCase(Locale.ROOT).replace('_',' ');return x.isEmpty()?"Current situation":Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private static String canon(String value){return LocalSemanticEmbedder.norm(n(value)).replaceAll("\\s+"," ").trim();}
    private static int clamp100(int x){return Math.max(0,Math.min(100,x));}
    private static String n(String s){return s==null?"":s.trim();}

    private static final class Candidate {long derivedId,threadId,signalId,modelRunId,dueAt,createdAt,updatedAt;String kind,title,body,source,person,semanticKey;double confidence;int importance,urgency;}
    private static final class Existing {long id,derivedId,dueAt,firstSeenAt,lastChangedAt;String kind,title,body;}
}
