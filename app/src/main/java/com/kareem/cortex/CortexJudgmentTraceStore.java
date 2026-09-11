package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Persistent, bounded observability for FINAL JUDGMENT decisions. No chain-of-thought is stored. */
public final class CortexJudgmentTraceStore {
    public static final String VERSION="cortex_judgment_trace_001";
    private CortexJudgmentTraceStore(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS cortex_judgment_trace("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "situation_id INTEGER NOT NULL,"+
                "policy_version TEXT NOT NULL,"+
                "score REAL NOT NULL,"+
                "threshold REAL NOT NULL,"+
                "surface_now INTEGER NOT NULL,"+
                "interruption_cost REAL NOT NULL,"+
                "reason TEXT NOT NULL,"+
                "candidate_type TEXT,"+
                "candidate_subject TEXT,"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cortex_judgment_trace_situation ON cortex_judgment_trace(situation_id,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cortex_judgment_trace_policy ON cortex_judgment_trace(policy_version,created_at DESC)");
    }

    public static void record(SQLiteDatabase db,CortexAttentionJudge.Judgment j){
        if(db==null||j==null||j.candidate==null)return;
        ensure(db);
        ContentValues v=new ContentValues();
        v.put("situation_id",j.candidate.situationId);
        v.put("policy_version",n(j.policyVersion));
        v.put("score",j.score);
        v.put("threshold",j.threshold);
        v.put("surface_now",j.surfaceNow?1:0);
        v.put("interruption_cost",j.interruptionCost);
        v.put("reason",n(j.reason));
        v.put("candidate_type",n(j.candidate.type));
        v.put("candidate_subject",clip(j.candidate.subject,240));
        v.put("created_at",System.currentTimeMillis());
        db.insert("cortex_judgment_trace",null,v);
    }

    public static Snapshot latest(SQLiteDatabase db,long situationId){
        if(db==null||situationId<=0)return null;
        ensure(db);
        Cursor c=db.rawQuery(
                "SELECT policy_version,score,threshold,surface_now,interruption_cost,reason,created_at "+
                "FROM cortex_judgment_trace WHERE situation_id=? ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(situationId)});
        try{
            if(!c.moveToFirst())return null;
            return new Snapshot(c.getString(0),c.getDouble(1),c.getDouble(2),c.getInt(3)!=0,
                    c.getDouble(4),c.getString(5),c.getLong(6));
        }finally{c.close();}
    }

    public static Stats latestPolicyStats(SQLiteDatabase db,String policyVersion){
        Stats s=new Stats();
        if(db==null)return s;
        ensure(db);
        String p=n(policyVersion);
        Cursor c=db.rawQuery(
                "SELECT COUNT(*),"+
                "SUM(CASE WHEN surface_now=1 THEN 1 ELSE 0 END),"+
                "SUM(CASE WHEN surface_now=0 THEN 1 ELSE 0 END),"+
                "AVG(score) FROM cortex_judgment_trace WHERE policy_version=? AND created_at>=?",
                new String[]{p,String.valueOf(System.currentTimeMillis()-6L*60L*60L*1000L)});
        try{
            if(c.moveToFirst()){
                s.evaluated=c.getLong(0);
                s.surfaced=c.isNull(1)?0:c.getLong(1);
                s.deferred=c.isNull(2)?0:c.getLong(2);
                s.averageScore=c.isNull(3)?0:c.getDouble(3);
            }
        }finally{c.close();}
        return s;
    }

    public static void prune(SQLiteDatabase db){
        if(db==null)return;
        ensure(db);
        db.execSQL("DELETE FROM cortex_judgment_trace WHERE id NOT IN ("+
                "SELECT id FROM cortex_judgment_trace ORDER BY id DESC LIMIT 1200)");
    }

    public static final class Snapshot{
        public final String policyVersion,reason;
        public final double score,threshold,interruptionCost;
        public final boolean surfaceNow;
        public final long createdAt;
        Snapshot(String policyVersion,double score,double threshold,boolean surfaceNow,
                 double interruptionCost,String reason,long createdAt){
            this.policyVersion=n(policyVersion);this.score=score;this.threshold=threshold;
            this.surfaceNow=surfaceNow;this.interruptionCost=interruptionCost;
            this.reason=n(reason);this.createdAt=createdAt;
        }
    }

    public static final class Stats{
        public long evaluated,surfaced,deferred;
        public double averageScore;
    }

    private static String clip(String s,int n){String x=n(s);return x.length()<=n?x:x.substring(0,n);}
    private static String n(String s){return s==null?"":s.trim();}
}
