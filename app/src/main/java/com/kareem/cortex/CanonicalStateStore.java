package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Explicit lifecycle for canonical semantic interpretations; raw evidence remains immutable. */
public final class CanonicalStateStore {
    public static final String VERSION="canonical_state_store_001";
    private CanonicalStateStore(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_canonical_states("+
                "semantic_event_id INTEGER PRIMARY KEY,"+
                "canonical_state TEXT NOT NULL,"+
                "quality_score REAL NOT NULL DEFAULT 0,"+
                "reason TEXT,"+
                "policy_version TEXT NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_canonical_state ON ue_canonical_states(canonical_state,updated_at DESC)");
    }

    public static void proposeIfAbsent(SQLiteDatabase db,long eventId,double score,String reason){
        if(db==null||eventId<=0)return;ensure(db);
        Cursor c=db.rawQuery("SELECT 1 FROM ue_canonical_states WHERE semantic_event_id=? LIMIT 1",new String[]{String.valueOf(eventId)});
        boolean exists=c.moveToFirst();c.close();if(exists)return;
        set(db,eventId,"proposed",score,reason);
    }

    public static void set(SQLiteDatabase db,long eventId,String state,double score,String reason){
        if(db==null||eventId<=0)return;ensure(db);ContentValues v=new ContentValues();
        v.put("semantic_event_id",eventId);v.put("canonical_state",n(state));v.put("quality_score",clamp(score));v.put("reason",n(reason));v.put("policy_version",CanonicalSemanticQualityGate.VERSION);v.put("updated_at",System.currentTimeMillis());
        db.insertWithOnConflict("ue_canonical_states",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String state(SQLiteDatabase db,long eventId){
        if(db==null||eventId<=0)return"";ensure(db);Cursor c=db.rawQuery("SELECT canonical_state FROM ue_canonical_states WHERE semantic_event_id=? LIMIT 1",new String[]{String.valueOf(eventId)});String s=c.moveToFirst()?n(c.getString(0)):"";c.close();return s;
    }

    public static void verifyIfRepeated(SQLiteDatabase db,long eventId,long situationId){
        if(db==null||eventId<=0||situationId<=0)return;ensure(db);Cursor c=db.rawQuery("SELECT COUNT(*) FROM ue_situation_members_v2 WHERE situation_id=?",new String[]{String.valueOf(situationId)});int count=c.moveToFirst()?c.getInt(0):0;c.close();if(count>=2){Cursor q=db.rawQuery("SELECT quality_score,reason FROM ue_canonical_states WHERE semantic_event_id=? LIMIT 1",new String[]{String.valueOf(eventId)});if(q.moveToFirst())set(db,eventId,"verified",q.getDouble(0),"supported by repeated/correlated evidence: "+n(q.getString(1)));q.close();}
    }

    private static String n(String s){return s==null?"":s.trim();}
    private static double clamp(double x){if(Double.isNaN(x))return 0;return Math.max(0,Math.min(1,x));}
}
