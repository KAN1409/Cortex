package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Compatibility facade for per-signal V2 execution lifecycle.
 *
 * V2 no longer creates a separate cognitive_runs authority. Lifecycle rows live in the existing
 * model_runs telemetry table. If an upgraded database still has the earlier cognitive_runs table,
 * it is renamed to cognitive_runs_legacy (data preserved) and the old name becomes a read-only
 * compatibility view over current model_runs lifecycle rows.
 */
public final class CognitiveRunStoreV2 {
    private static final String ROLE="cognitive_adjudicator_lifecycle";
    private static final String ROUTE="signal_cognition_v2_lifecycle";
    private static volatile boolean compatibilityReady;
    private CognitiveRunStoreV2(){}

    public static void ensure(VaultDb db){
        if(db==null)return;SQLiteDatabase s=db.getWritableDatabase();CognitiveSchema.ensure(s);if(compatibilityReady)return;
        synchronized(CognitiveRunStoreV2.class){if(compatibilityReady)return;ensureColumn(s,"model_runs","disposition","TEXT DEFAULT ''");ensureColumn(s,"model_runs","completed_at","INTEGER DEFAULT 0");s.execSQL("CREATE INDEX IF NOT EXISTS idx_model_runs_cognitive_signal ON model_runs(role,input_hash,id DESC)");installCompatibilityView(s);compatibilityReady=true;}
    }

    private static void installCompatibilityView(SQLiteDatabase s){
        String type=objectType(s,"cognitive_runs");
        if("table".equals(type)&&objectType(s,"cognitive_runs_legacy").isEmpty()){
            s.execSQL("ALTER TABLE cognitive_runs RENAME TO cognitive_runs_legacy");type="";
        }
        if(type.isEmpty()||"view".equals(type)){
            if("view".equals(objectType(s,"cognitive_runs")))s.execSQL("DROP VIEW cognitive_runs");
            s.execSQL("CREATE VIEW IF NOT EXISTS cognitive_runs AS SELECT id,CAST(substr(input_hash,12) AS INTEGER) AS raw_signal_id,provider,model,created_at AS started_at,completed_at,latency_ms,disposition,confidence,output_json AS result_json,state AS status,error FROM model_runs WHERE role='"+ROLE+"' AND input_hash LIKE 'raw_signal:%'");
        }
    }

    public static long queued(VaultDb db,long signalId,String provider,String model){
        if(db==null||signalId<=0)return 0;ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();
        v.put("job_id",0);v.put("pass_index",0);v.put("role",ROLE);v.put("provider",n(provider));v.put("model",n(model));v.put("route",ROUTE);v.put("state","QUEUED");v.put("input_hash",signalKey(signalId));v.put("latency_ms",0);v.put("tokens_in",0);v.put("tokens_out",0);v.put("confidence",0);v.put("disposition","");v.put("output_json","");v.put("error","");v.put("created_at",now);v.put("completed_at",0);
        long id=db.getWritableDatabase().insert("model_runs",null,v);if(id>0)setRawState(db,signalId,deep(provider)?CognitiveSignalV2.CognitiveState.DEEP_QUEUED:CognitiveSignalV2.CognitiveState.LOCAL_QUEUED,id,deep(provider)?"optional Deep Qwen queued":"local Qwen queued");return id;
    }

    public static void running(VaultDb db,long runId){
        Snapshot before=byId(db,runId);state(db,runId,"RUNNING","",0,"",0,0,false);if(before!=null)setRawState(db,before.signalId,deep(before.provider)?CognitiveSignalV2.CognitiveState.DEEP_QUEUED:CognitiveSignalV2.CognitiveState.LOCAL_RUNNING,runId,deep(before.provider)?"optional Deep Qwen analyzing":"local Qwen analyzing");
    }
    public static void escalated(VaultDb db,long runId,String detail){state(db,runId,"ESCALATED",detail,0,"",0,0,true);}
    public static void succeeded(VaultDb db,long runId,String disposition,double confidence,String resultJson,long latencyMs){state(db,runId,"SUCCEEDED","",latencyMs,disposition,confidence,resultJson,true);}
    public static void rejected(VaultDb db,long runId,String error,String resultJson,long latencyMs){state(db,runId,"REJECTED",error,latencyMs,"",0,resultJson,true);}
    public static void failed(VaultDb db,long runId,String error,long latencyMs){state(db,runId,"FAILED",error,latencyMs,"",0,"",true);}

    private static void state(VaultDb db,long runId,String status,String error,long latency,String disposition,double confidence,Object resultJson,boolean done){
        if(db==null||runId<=0)return;ensure(db);ContentValues v=new ContentValues();v.put("state",status);v.put("error",n(error));v.put("latency_ms",Math.max(0,latency));v.put("disposition",n(disposition));v.put("confidence",Math.max(0,Math.min(1,confidence)));v.put("output_json",resultJson==null?"":String.valueOf(resultJson));if(done)v.put("completed_at",System.currentTimeMillis());db.getWritableDatabase().update("model_runs",v,"id=? AND role=?",new String[]{String.valueOf(runId),ROLE});
    }

    public static Snapshot latestForSignal(VaultDb db,long signalId){
        if(db==null||signalId<=0)return null;ensure(db);Cursor c=db.getReadableDatabase().query("model_runs",new String[]{"id","provider","model","created_at","completed_at","latency_ms","disposition","confidence","state","error","input_hash"},"role=? AND input_hash=?",new String[]{ROLE,signalKey(signalId)},null,null,"id DESC","1");
        try{return c.moveToFirst()?snapshot(c,signalId):null;}finally{c.close();}
    }

    private static Snapshot byId(VaultDb db,long runId){if(db==null||runId<=0)return null;ensure(db);Cursor c=db.getReadableDatabase().query("model_runs",new String[]{"id","provider","model","created_at","completed_at","latency_ms","disposition","confidence","state","error","input_hash"},"id=? AND role=?",new String[]{String.valueOf(runId),ROLE},null,null,null,"1");try{if(!c.moveToFirst())return null;return snapshot(c,signalId(c.getString(10)));}finally{c.close();}}
    private static Snapshot snapshot(Cursor c,long signalId){return new Snapshot(c.getLong(0),signalId,n(c.getString(1)),n(c.getString(2)),c.getLong(3),c.getLong(4),c.getLong(5),n(c.getString(6)),c.getDouble(7),n(c.getString(8)),n(c.getString(9)));}

    private static void setRawState(VaultDb db,long signalId,CognitiveSignalV2.CognitiveState state,long runId,String reason){ContentValues v=new ContentValues();v.put("cognitive_state",state.name());v.put("cognitive_run_id",runId);v.put("final_reason",reason);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=? AND cognitive_state NOT IN ('IGNORED_NOISE','CONTEXT_ONLY','DERIVED','REVIEW_REQUIRED','SENSITIVE_BLOCKED','SUPERSEDED')",new String[]{String.valueOf(signalId)});}
    private static void ensureColumn(SQLiteDatabase db,String table,String column,String definition){Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null);boolean found=false;try{while(c.moveToNext()){int i=c.getColumnIndex("name");if(i>=0&&column.equals(c.getString(i))){found=true;break;}}}finally{c.close();}if(!found)db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
    private static String objectType(SQLiteDatabase db,String name){Cursor c=db.rawQuery("SELECT type FROM sqlite_master WHERE name=? LIMIT 1",new String[]{name});try{return c.moveToFirst()?n(c.getString(0)):"";}finally{c.close();}}
    private static boolean deep(String provider){return"DEEP".equalsIgnoreCase(n(provider));}
    static String role(){return ROLE;}
    static String signalKey(long signalId){return"raw_signal:"+Math.max(0,signalId);}
    static long signalId(String key){String x=n(key);if(!x.startsWith("raw_signal:"))return 0;try{return Long.parseLong(x.substring("raw_signal:".length()));}catch(Throwable ignored){return 0;}}

    public static final class Snapshot{
        public final long id,signalId,startedAt,completedAt,latencyMs;public final String provider,model,disposition,status,error;public final double confidence;
        Snapshot(long id,long signalId,String provider,String model,long started,long completed,long latency,String disposition,double confidence,String status,String error){this.id=id;this.signalId=signalId;this.provider=provider;this.model=model;this.startedAt=started;this.completedAt=completed;this.latencyMs=latency;this.disposition=disposition;this.confidence=confidence;this.status=status;this.error=error;}
    }
    private static String n(String s){return s==null?"":s.trim();}
}
