package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Durable audit trail for autonomous reasoning provider runs. */
public final class CognitiveReasoningRunStoreV4 {
    private CognitiveReasoningRunStoreV4(){}

    public static void ensure(VaultDb db){if(db==null)throw new IllegalArgumentException("db required");ensure(db.getWritableDatabase());}
    static void ensure(SQLiteDatabase sql){
        sql.execSQL("CREATE TABLE IF NOT EXISTS v4_reasoning_runs(id TEXT PRIMARY KEY,request_id TEXT NOT NULL,provider TEXT NOT NULL,model TEXT NOT NULL,trigger_kind TEXT NOT NULL,context_fingerprint TEXT NOT NULL,state TEXT NOT NULL,started_at INTEGER NOT NULL,completed_at INTEGER DEFAULT 0,duration_ms INTEGER DEFAULT 0,error TEXT,updated_at INTEGER NOT NULL)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_reasoning_runs_state ON v4_reasoning_runs(state,started_at DESC)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_reasoning_runs_request ON v4_reasoning_runs(request_id,started_at DESC)");
    }

    static String begin(VaultDb db,String requestId,String provider,String model,String trigger,String fingerprint,long now){
        ensure(db);String id=CognitiveIdentityV4.objectId("rrn","reasoning-run|"+requestId+"|"+provider+"|"+now);ContentValues v=new ContentValues();v.put("id",id);v.put("request_id",n(requestId));v.put("provider",n(provider));v.put("model",n(model));v.put("trigger_kind",n(trigger));v.put("context_fingerprint",n(fingerprint));v.put("state","RUNNING");v.put("started_at",now);v.put("completed_at",0);v.put("duration_ms",0);v.put("error","");v.put("updated_at",now);db.getWritableDatabase().insertWithOnConflict("v4_reasoning_runs",null,v,SQLiteDatabase.CONFLICT_REPLACE);return id;
    }
    static void complete(VaultDb db,String runId,long durationMs,long now){finish(db,runId,"APPLIED","",durationMs,now);}
    static void stale(VaultDb db,String runId,long durationMs,long now){finish(db,runId,"STALE_CONTEXT","canonical context changed before apply",durationMs,now);}
    static void fail(VaultDb db,String runId,String error,long durationMs,long now){finish(db,runId,"FAILED",clip(error,800),durationMs,now);}
    private static void finish(VaultDb db,String runId,String state,String error,long durationMs,long now){if(db==null||n(runId).isEmpty())return;ensure(db);ContentValues v=new ContentValues();v.put("state",state);v.put("completed_at",now);v.put("duration_ms",Math.max(0,durationMs));v.put("error",error);v.put("updated_at",now);db.getWritableDatabase().update("v4_reasoning_runs",v,"id=?",new String[]{runId});}

    public static Latest latest(VaultDb db){
        if(db==null)return null;ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT provider,model,trigger_kind,state,started_at,completed_at,duration_ms,COALESCE(error,'') FROM v4_reasoning_runs ORDER BY started_at DESC LIMIT 1",null);try{if(!c.moveToFirst())return null;return new Latest(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getLong(6),c.getString(7));}finally{c.close();}
    }
    private static String n(String s){return s==null?"":s.trim();}
    private static String clip(String s,int n){String x=n(s);return x.length()<=n?x:x.substring(0,n);}
    public static final class Latest{public final String provider,model,trigger,state,error;public final long startedAt,completedAt,durationMs;Latest(String p,String m,String t,String s,long a,long c,long d,String e){provider=n(p);model=n(m);trigger=n(t);state=n(s);startedAt=a;completedAt=c;durationMs=d;error=n(e);}}
}
