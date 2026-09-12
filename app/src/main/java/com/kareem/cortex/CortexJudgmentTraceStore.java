package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Persistent bounded observability for FINAL JUDGMENT. No chain-of-thought is stored. */
public final class CortexJudgmentTraceStore {
    public static final String VERSION="cortex_judgment_trace_003";
    public enum Decision { NOW,LATER,SILENT,SUPPRESS }
    private CortexJudgmentTraceStore(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS cortex_judgment_trace(id INTEGER PRIMARY KEY AUTOINCREMENT,situation_id INTEGER NOT NULL,policy_version TEXT NOT NULL,score REAL NOT NULL,threshold REAL NOT NULL,surface_now INTEGER NOT NULL,interruption_cost REAL NOT NULL,reason TEXT NOT NULL,candidate_type TEXT,candidate_subject TEXT,final_decision TEXT NOT NULL DEFAULT 'SILENT',created_at INTEGER NOT NULL)");
        addColumn(db,"cortex_judgment_trace","final_decision","TEXT NOT NULL DEFAULT 'SILENT'");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cortex_judgment_trace_situation ON cortex_judgment_trace(situation_id,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cortex_judgment_trace_policy ON cortex_judgment_trace(policy_version,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cortex_judgment_trace_decision ON cortex_judgment_trace(final_decision,created_at DESC)");
    }

    /** Structured outcome classification. Explanation text never controls the decision. */
    static Decision classify(CortexAttentionJudge.Judgment j,boolean selectedNow){
        if(j==null||j.candidate==null)return Decision.SUPPRESS;
        if(selectedNow&&j.surfaceNow)return Decision.NOW;
        if(j.surfaceNow)return Decision.LATER; // eligible but excluded by bounded top-k
        AttentionDecisionEngine.Candidate c=j.candidate;
        if(!c.unresolved||c.confidence<.70)return Decision.SUPPRESS;
        AttentionDecisionEngine.Decision structural=AttentionDecisionEngine.evaluate(c);
        if(!structural.surfaceNow&&structural.score<=0.000001)return Decision.SUPPRESS;
        if(j.interruptionCost>=.70&&j.score>0)return Decision.LATER;
        return Decision.SILENT;
    }

    public static String finalDecision(CortexAttentionJudge.Judgment j,boolean selectedNow){return classify(j,selectedNow).name();}
    public static void record(SQLiteDatabase db,CortexAttentionJudge.Judgment j){record(db,j,j!=null&&j.surfaceNow);}
    public static void record(SQLiteDatabase db,CortexAttentionJudge.Judgment j,boolean selectedNow){
        if(db==null||j==null||j.candidate==null)return;ensure(db);String decision=finalDecision(j,selectedNow);
        if(sameAsLatest(db,j,selectedNow,decision))return;
        ContentValues v=new ContentValues();v.put("situation_id",j.candidate.situationId);v.put("policy_version",clean(j.policyVersion));v.put("score",j.score);v.put("threshold",j.threshold);v.put("surface_now",selectedNow&&j.surfaceNow?1:0);v.put("interruption_cost",j.interruptionCost);v.put("reason",clean(j.reason));v.put("candidate_type",clean(j.candidate.type));v.put("candidate_subject",clip(j.candidate.subject,240));v.put("final_decision",decision);v.put("created_at",System.currentTimeMillis());db.insert("cortex_judgment_trace",null,v);
    }

    private static boolean sameAsLatest(SQLiteDatabase db,CortexAttentionJudge.Judgment j,boolean selectedNow,String decision){
        Cursor c=db.rawQuery("SELECT policy_version,score,threshold,surface_now,interruption_cost,final_decision,candidate_type,candidate_subject,created_at FROM cortex_judgment_trace WHERE situation_id=? ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(j.candidate.situationId)});
        try{
            if(!c.moveToFirst())return false;
            long age=System.currentTimeMillis()-c.getLong(8);if(age<0||age>60_000L)return false;
            return clean(j.policyVersion).equals(clean(c.getString(0)))&&near(j.score,c.getDouble(1))&&near(j.threshold,c.getDouble(2))&&
                    (selectedNow&&j.surfaceNow)==(c.getInt(3)!=0)&&near(j.interruptionCost,c.getDouble(4))&&decision.equals(clean(c.getString(5)))&&
                    clean(j.candidate.type).equals(clean(c.getString(6)))&&clip(j.candidate.subject,240).equals(clean(c.getString(7)));
        }finally{c.close();}
    }

    public static Snapshot latest(SQLiteDatabase db,long situationId){if(db==null||situationId<=0)return null;ensure(db);Cursor c=db.rawQuery("SELECT policy_version,score,threshold,surface_now,interruption_cost,reason,final_decision,created_at FROM cortex_judgment_trace WHERE situation_id=? ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(situationId)});try{if(!c.moveToFirst())return null;return new Snapshot(c.getString(0),c.getDouble(1),c.getDouble(2),c.getInt(3)!=0,c.getDouble(4),c.getString(5),c.getString(6),c.getLong(7));}finally{c.close();}}

    public static Stats latestPolicyStats(SQLiteDatabase db,String policyVersion){Stats s=new Stats();if(db==null)return s;ensure(db);Cursor c=db.rawQuery("SELECT COUNT(*),SUM(CASE WHEN final_decision='NOW' THEN 1 ELSE 0 END),SUM(CASE WHEN final_decision='LATER' THEN 1 ELSE 0 END),SUM(CASE WHEN final_decision='SILENT' THEN 1 ELSE 0 END),SUM(CASE WHEN final_decision='SUPPRESS' THEN 1 ELSE 0 END),AVG(score) FROM cortex_judgment_trace WHERE policy_version=? AND created_at>=?",new String[]{clean(policyVersion),String.valueOf(System.currentTimeMillis()-6L*60L*60L*1000L)});try{if(c.moveToFirst()){s.evaluated=c.getLong(0);s.surfaced=c.isNull(1)?0:c.getLong(1);s.later=c.isNull(2)?0:c.getLong(2);s.silent=c.isNull(3)?0:c.getLong(3);s.suppressed=c.isNull(4)?0:c.getLong(4);s.deferred=s.later+s.silent+s.suppressed;s.averageScore=c.isNull(5)?0:c.getDouble(5);}}finally{c.close();}return s;}

    public static void prune(SQLiteDatabase db){if(db==null)return;ensure(db);db.execSQL("DELETE FROM cortex_judgment_trace WHERE id NOT IN (SELECT id FROM cortex_judgment_trace ORDER BY id DESC LIMIT 1200)");}
    public static final class Snapshot{public final String policyVersion,reason,finalDecision;public final double score,threshold,interruptionCost;public final boolean surfaceNow;public final long createdAt;Snapshot(String p,double s,double t,boolean surfaced,double i,String r,String d,long c){policyVersion=clean(p);score=s;threshold=t;surfaceNow=surfaced;interruptionCost=i;reason=clean(r);finalDecision=clean(d);createdAt=c;}}
    public static final class Stats{public long evaluated,surfaced,deferred,later,silent,suppressed;public double averageScore;}
    private static boolean near(double a,double b){return Math.abs(a-b)<.000001;}
    private static void addColumn(SQLiteDatabase db,String table,String column,String definition){Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null);boolean found=false;while(c.moveToNext())if(column.equals(c.getString(1))){found=true;break;}c.close();if(!found)db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
    private static String clip(String s,int n){String x=clean(s);return x.length()<=n?x:x.substring(0,n);}private static String clean(String s){return s==null?"":s.trim();}
}
