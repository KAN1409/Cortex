package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Per-signal execution ledger so no cognition attempt becomes an invisible black box. */
public final class CognitiveRunStoreV2 {
    private CognitiveRunStoreV2(){}

    public static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS cognitive_runs(id INTEGER PRIMARY KEY AUTOINCREMENT,raw_signal_id INTEGER NOT NULL,provider TEXT NOT NULL,model TEXT NOT NULL,started_at INTEGER NOT NULL,completed_at INTEGER DEFAULT 0,latency_ms INTEGER DEFAULT 0,disposition TEXT,confidence REAL DEFAULT 0,result_json TEXT,status TEXT NOT NULL,error TEXT)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_cognitive_runs_signal ON cognitive_runs(raw_signal_id,id DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_cognitive_runs_status ON cognitive_runs(status,started_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_cognitive_runs_provider ON cognitive_runs(provider,model,started_at DESC)");
    }

    public static long queued(VaultDb db,long signalId,String provider,String model){
        ensure(db);ContentValues v=new ContentValues();v.put("raw_signal_id",signalId);v.put("provider",n(provider));v.put("model",n(model));v.put("started_at",System.currentTimeMillis());v.put("status","QUEUED");v.put("result_json","");v.put("error","");return db.getWritableDatabase().insert("cognitive_runs",null,v);
    }

    public static void running(VaultDb db,long runId){state(db,runId,"RUNNING","",0,"",0,0,false);}
    public static void escalated(VaultDb db,long runId,String detail){state(db,runId,"ESCALATED",detail,0,"",0,0,false);}
    public static void succeeded(VaultDb db,long runId,String disposition,double confidence,String resultJson,long latencyMs){state(db,runId,"SUCCEEDED","",latencyMs,disposition,confidence,resultJson,true);}
    public static void rejected(VaultDb db,long runId,String error,String resultJson,long latencyMs){state(db,runId,"REJECTED",error,latencyMs,"",0,resultJson,true);}
    public static void failed(VaultDb db,long runId,String error,long latencyMs){state(db,runId,"FAILED",error,latencyMs,"",0,"",true);}

    private static void state(VaultDb db,long runId,String status,String error,long latency,String disposition,double confidence,Object resultJson,boolean done){
        if(db==null||runId<=0)return;ensure(db);ContentValues v=new ContentValues();v.put("status",status);v.put("error",n(error));v.put("latency_ms",Math.max(0,latency));v.put("disposition",n(disposition));v.put("confidence",Math.max(0,Math.min(1,confidence)));v.put("result_json",resultJson==null?"":String.valueOf(resultJson));if(done)v.put("completed_at",System.currentTimeMillis());db.getWritableDatabase().update("cognitive_runs",v,"id=?",new String[]{String.valueOf(runId)});
    }

    public static Snapshot latestForSignal(VaultDb db,long signalId){
        if(db==null||signalId<=0)return null;ensure(db);Cursor c=db.getReadableDatabase().query("cognitive_runs",new String[]{"id","provider","model","started_at","completed_at","latency_ms","disposition","confidence","status","error"},"raw_signal_id=?",new String[]{String.valueOf(signalId)},null,null,"id DESC","1");
        try{return c.moveToFirst()?new Snapshot(c.getLong(0),n(c.getString(1)),n(c.getString(2)),c.getLong(3),c.getLong(4),c.getLong(5),n(c.getString(6)),c.getDouble(7),n(c.getString(8)),n(c.getString(9))):null;}finally{c.close();}
    }

    public static final class Snapshot{
        public final long id,startedAt,completedAt,latencyMs;public final String provider,model,disposition,status,error;public final double confidence;
        Snapshot(long id,String provider,String model,long started,long completed,long latency,String disposition,double confidence,String status,String error){this.id=id;this.provider=provider;this.model=model;this.startedAt=started;this.completedAt=completed;this.latencyMs=latency;this.disposition=disposition;this.confidence=confidence;this.status=status;this.error=error;}
    }
    private static String n(String s){return s==null?"":s.trim();}
}
