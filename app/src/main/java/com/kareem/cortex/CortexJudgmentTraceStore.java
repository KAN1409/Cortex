package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Persistent bounded observability for FINAL JUDGMENT. No chain-of-thought is stored. */
public final class CortexJudgmentTraceStore {
    public static final String VERSION="cortex_judgment_trace_002";
    private CortexJudgmentTraceStore(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS cortex_judgment_trace(id INTEGER PRIMARY KEY AUTOINCREMENT,situation_id INTEGER NOT NULL,policy_version TEXT NOT NULL,score REAL NOT NULL,threshold REAL NOT NULL,surface_now INTEGER NOT NULL,interruption_cost REAL NOT NULL,reason TEXT NOT NULL,candidate_type TEXT,candidate_subject TEXT,final_decision TEXT NOT NULL DEFAULT 'SILENT',created_at INTEGER NOT NULL)");
        addColumn(db,"cortex_judgment_trace","final_decision","TEXT NOT NULL DEFAULT 'SILENT'");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cortex_judgment_trace_situation ON cortex_judgment_trace(situation_id,created_at DESC)");db.execSQL("CREATE INDEX IF NOT EXISTS idx_cortex_judgment_trace_policy ON cortex_judgment_trace(policy_version,created_at DESC)");db.execSQL("CREATE INDEX IF NOT EXISTS idx_cortex_judgment_trace_decision ON cortex_judgment_trace(final_decision,created_at DESC)");
    }

    public static String finalDecision(CortexAttentionJudge.Judgment j,boolean selectedNow){
        if(j==null)return"SUPPRESS";if(selectedNow&&j.surfaceNow)return"NOW";if(j.surfaceNow)return"LATER";String r=clean(j.reason).toLowerCase();if(r.contains("resolved")||r.contains("technical")||r.contains("insufficient grounded confidence")||r.contains("low-confidence"))return"SUPPRESS";if(j.interruptionCost>=.70&&j.score>0)return"LATER";return"SILENT";
    }

    public static void record(SQLiteDatabase db,CortexAttentionJudge.Judgment j){record(db,j,j!=null&&j.surfaceNow);}
    public static void record(SQLiteDatabase db,CortexAttentionJudge.Judgment j,boolean selectedNow){
        if(db==null||j==null||j.candidate==null)return;ensure(db);ContentValues v=new ContentValues();v.put("situation_id",j.candidate.situationId);v.put("policy_version",clean(j.policyVersion));v.put("score",j.score);v.put("threshold",j.threshold);v.put("surface_now",selectedNow&&j.surfaceNow?1:0);v.put("interruption_cost",j.interruptionCost);v.put("reason",clean(j.reason));v.put("candidate_type",clean(j.candidate.type));v.put("candidate_subject",clip(j.candidate.subject,240));v.put("final_decision",finalDecision(j,selectedNow));v.put("created_at",System.currentTimeMillis());db.insert("cortex_judgment_trace",null,v);
    }

    public static Snapshot latest(SQLiteDatabase db,long situationId){if(db==null||situationId<=0)return null;ensure(db);Cursor c=db.rawQuery("SELECT policy_version,score,threshold,surface_now,interruption_cost,reason,final_decision,created_at FROM cortex_judgment_trace WHERE situation_id=? ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(situationId)});try{if(!c.moveToFirst())return null;return new Snapshot(c.getString(0),c.getDouble(1),c.getDouble(2),c.getInt(3)!=0,c.getDouble(4),c.getString(5),c.getString(6),c.getLong(7));}finally{c.close();}}

    public static Stats latestPolicyStats(SQLiteDatabase db,String policyVersion){Stats s=new Stats();if(db==null)return s;ensure(db);Cursor c=db.rawQuery("SELECT COUNT(*),SUM(CASE WHEN final_decision='NOW' THEN 1 ELSE 0 END),SUM(CASE WHEN final_decision='LATER' THEN 1 ELSE 0 END),SUM(CASE WHEN final_decision='SILENT' THEN 1 ELSE 0 END),SUM(CASE WHEN final_decision='SUPPRESS' THEN 1 ELSE 0 END),AVG(score) FROM cortex_judgment_trace WHERE policy_version=? AND created_at>=?",new String[]{clean(policyVersion),String.valueOf(System.currentTimeMillis()-6L*60L*60L*1000L)});try{if(c.moveToFirst()){s.evaluated=c.getLong(0);s.surfaced=c.isNull(1)?0:c.getLong(1);s.later=c.isNull(2)?0:c.getLong(2);s.silent=c.isNull(3)?0:c.getLong(3);s.suppressed=c.isNull(4)?0:c.getLong(4);s.deferred=s.later+s.silent+s.suppressed;s.averageScore=c.isNull(5)?0:c.getDouble(5);}}finally{c.close();}return s;}

    public static void prune(SQLiteDatabase db){if(db==null)return;ensure(db);db.execSQL("DELETE FROM cortex_judgment_trace WHERE id NOT IN (SELECT id FROM cortex_judgment_trace ORDER BY id DESC LIMIT 1200)");}
    public static final class Snapshot{public final String policyVersion,reason,finalDecision;public final double score,threshold,interruptionCost;public final boolean surfaceNow;public final long createdAt;Snapshot(String p,double s,double t,boolean surfaced,double i,String r,String d,long c){policyVersion=clean(p);score=s;threshold=t;surfaceNow=surfaced;interruptionCost=i;reason=clean(r);finalDecision=clean(d);createdAt=c;}}
    public static final class Stats{public long evaluated,surfaced,deferred,later,silent,suppressed;public double averageScore;}
    private static void addColumn(SQLiteDatabase db,String table,String column,String definition){Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null);boolean found=false;while(c.moveToNext())if(column.equals(c.getString(1))){found=true;break;}c.close();if(!found)db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
    private static String clip(String s,int n){String x=clean(s);return x.length()<=n?x:x.substring(0,n);}private static String clean(String s){return s==null?"":s.trim();}
}
