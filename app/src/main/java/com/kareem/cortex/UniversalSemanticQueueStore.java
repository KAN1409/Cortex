package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Persistent lease/retry ledger for local semantic refinement.
 * A process death may abandon a claim, but it cannot strand the semantic event forever.
 */
public final class UniversalSemanticQueueStore {
    public static final String VERSION="semantic_queue_001";
    public static final int MAX_ATTEMPTS=4;
    public static final long LEASE_MS=5L*60L*1000L;
    private static final long BASE_RETRY_MS=30_000L;
    private static final long MAX_RETRY_MS=15L*60L*1000L;
    private UniversalSemanticQueueStore(){}

    public static void ensure(SQLiteDatabase db){
        UniversalEventStore.ensure(db);
        addColumn(db,"ue_semantic_events","semantic_attempts","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"ue_semantic_events","semantic_claimed_at","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"ue_semantic_events","semantic_next_attempt_at","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"ue_semantic_events","semantic_last_error","TEXT NOT NULL DEFAULT ''");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_semantic_queue_due ON ue_semantic_events(semantic_state,semantic_next_attempt_at,occurred_at ASC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_semantic_attempts(id INTEGER PRIMARY KEY AUTOINCREMENT,semantic_event_id INTEGER NOT NULL,raw_observation_id INTEGER NOT NULL,attempt INTEGER NOT NULL,state TEXT NOT NULL,started_at INTEGER NOT NULL,completed_at INTEGER NOT NULL DEFAULT 0,latency_ms INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',UNIQUE(semantic_event_id,attempt))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_semantic_attempt_event ON ue_semantic_attempts(semantic_event_id,attempt DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_semantic_attempt_state ON ue_semantic_attempts(state,started_at DESC)");
    }

    public static int recoverExpiredClaims(SQLiteDatabase db){
        ensure(db);long now=System.currentTimeMillis(),cutoff=now-LEASE_MS;Cursor c=db.rawQuery("SELECT id,raw_observation_id,semantic_attempts FROM ue_semantic_events WHERE semantic_state='running' AND semantic_claimed_at>0 AND semantic_claimed_at<? AND superseded_by=0",new String[]{String.valueOf(cutoff)});int n=0;
        try{while(c.moveToNext()){
            long eventId=c.getLong(0),rawId=c.getLong(1);int attempt=c.getInt(2);boolean exhausted=attempt>=MAX_ATTEMPTS;String error="semantic lease expired after process interruption";
            ContentValues e=new ContentValues();e.put("semantic_state",exhausted?"blocked":"waiting");e.put("semantic_claimed_at",0);e.put("semantic_next_attempt_at",exhausted?0:now);e.put("semantic_last_error",error);e.put("reason",exhausted?"Semantic retry budget exhausted after expired lease":"Expired semantic lease recovered; retry queued");db.update("ue_semantic_events",e,"id=?",new String[]{String.valueOf(eventId)});
            finishAttempt(db,eventId,attempt,"expired",error,now);n++;
        }}finally{c.close();}return n;
    }

    public static Row claimNext(SQLiteDatabase db){
        ensure(db);long now=System.currentTimeMillis();boolean began=false;
        try{
            db.beginTransaction();began=true;
            String sql="SELECT e.id,e.raw_observation_id,e.stream_id,e.semantic_type,e.subject,e.summary,r.source_key,r.title,r.body,r.payload_json,r.occurred_at,e.semantic_attempts " +
                    "FROM ue_semantic_events e JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                    "WHERE e.semantic_state='waiting' AND e.superseded_by=0 AND e.semantic_attempts<? AND COALESCE(e.semantic_next_attempt_at,0)<=? ORDER BY e.occurred_at ASC,e.id ASC LIMIT 1";
            Cursor c=db.rawQuery(sql,new String[]{String.valueOf(MAX_ATTEMPTS),String.valueOf(now)});Row row=null;
            if(c.moveToFirst()){
                long eventId=c.getLong(0),rawId=c.getLong(1);int attempt=c.getInt(11)+1;
                ContentValues e=new ContentValues();e.put("semantic_state","running");e.put("semantic_attempts",attempt);e.put("semantic_claimed_at",now);e.put("semantic_next_attempt_at",0);e.put("semantic_last_error","");
                int changed=db.update("ue_semantic_events",e,"id=? AND semantic_state='waiting' AND superseded_by=0",new String[]{String.valueOf(eventId)});
                if(changed==1){
                    ContentValues a=new ContentValues();a.put("semantic_event_id",eventId);a.put("raw_observation_id",rawId);a.put("attempt",attempt);a.put("state","running");a.put("started_at",now);a.put("completed_at",0);a.put("latency_ms",0);a.put("error","");db.insertWithOnConflict("ue_semantic_attempts",null,a,SQLiteDatabase.CONFLICT_REPLACE);
                    row=new Row(eventId,rawId,c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),c.getLong(10),attempt,now);
                }
            }
            c.close();db.setTransactionSuccessful();return row;
        }finally{if(began)try{db.endTransaction();}catch(Throwable ignored){}}
    }

    public static void complete(SQLiteDatabase db,Row row){
        if(row==null)return;long now=System.currentTimeMillis();ContentValues e=new ContentValues();e.put("semantic_state","complete");e.put("semantic_claimed_at",0);e.put("semantic_next_attempt_at",0);e.put("semantic_last_error","");db.update("ue_semantic_events",e,"id=?",new String[]{String.valueOf(row.eventId)});finishAttempt(db,row.eventId,row.attempt,"complete","",now);
    }

    public static void fail(SQLiteDatabase db,Row row,Throwable error){
        if(row==null)return;long now=System.currentTimeMillis();String detail=error==null?"unknown semantic failure":error.getClass().getSimpleName()+": "+safe(error.getMessage());boolean exhausted=row.attempt>=MAX_ATTEMPTS;long next=exhausted?0:now+backoff(row.attempt);
        ContentValues e=new ContentValues();e.put("semantic_state",exhausted?"blocked":"waiting");e.put("semantic_claimed_at",0);e.put("semantic_next_attempt_at",next);e.put("semantic_last_error",detail);e.put("reason",exhausted?"Semantic retry budget exhausted: "+detail:"Semantic refinement retry scheduled: "+detail);db.update("ue_semantic_events",e,"id=?",new String[]{String.valueOf(row.eventId)});finishAttempt(db,row.eventId,row.attempt,"failed",detail,now);
    }

    public static void markRuntimeBlocked(SQLiteDatabase db,String detail){
        ensure(db);ContentValues e=new ContentValues();e.put("semantic_state","blocked");e.put("model_route","local_background_model");e.put("semantic_claimed_at",0);e.put("semantic_next_attempt_at",0);e.put("semantic_last_error",safe(detail));e.put("reason",safe(detail));db.update("ue_semantic_events",e,"semantic_state='waiting' AND superseded_by=0",null);
    }

    /** Resume only model-runtime blocks; evidence-integrity blocks remain blocked. */
    public static int resumeRuntimeBlocked(SQLiteDatabase db){
        ensure(db);ContentValues e=new ContentValues();e.put("semantic_state","waiting");e.put("semantic_claimed_at",0);e.put("semantic_next_attempt_at",0);e.put("semantic_last_error","");e.put("reason","Local runtime recovered; semantic refinement resumed");return db.update("ue_semantic_events",e,"semantic_state='blocked' AND superseded_by=0 AND model_route='local_background_model' AND (semantic_last_error<>'' OR reason LIKE 'Local runtime%')",null);
    }

    public static boolean hasPending(SQLiteDatabase db){ensure(db);Cursor c=db.rawQuery("SELECT 1 FROM ue_semantic_events WHERE semantic_state IN ('waiting','running') AND superseded_by=0 LIMIT 1",null);boolean yes=c.moveToFirst();c.close();return yes;}
    public static long waitingCount(SQLiteDatabase db){return scalar(db,"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='waiting' AND superseded_by=0");}
    public static long runningCount(SQLiteDatabase db){return scalar(db,"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='running' AND superseded_by=0");}
    public static long expiredLeaseCount(SQLiteDatabase db){ensure(db);long cutoff=System.currentTimeMillis()-LEASE_MS;Cursor c=db.rawQuery("SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='running' AND semantic_claimed_at>0 AND semantic_claimed_at<? AND superseded_by=0",new String[]{String.valueOf(cutoff)});long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    public static long oldestWaitingAgeMs(SQLiteDatabase db){ensure(db);Cursor c=db.rawQuery("SELECT MIN(occurred_at) FROM ue_semantic_events WHERE semantic_state='waiting' AND superseded_by=0",null);long at=c.moveToFirst()&&!c.isNull(0)?c.getLong(0):0;c.close();return at<=0?0:Math.max(0,System.currentTimeMillis()-at);}
    public static long oldestRunningAgeMs(SQLiteDatabase db){ensure(db);Cursor c=db.rawQuery("SELECT MIN(semantic_claimed_at) FROM ue_semantic_events WHERE semantic_state='running' AND semantic_claimed_at>0 AND superseded_by=0",null);long at=c.moveToFirst()&&!c.isNull(0)?c.getLong(0):0;c.close();return at<=0?0:Math.max(0,System.currentTimeMillis()-at);}
    public static long maxAttemptCount(SQLiteDatabase db){return scalar(db,"SELECT COALESCE(MAX(semantic_attempts),0) FROM ue_semantic_events");}

    private static void finishAttempt(SQLiteDatabase db,long eventId,int attempt,String state,String error,long completed){
        if(eventId<=0||attempt<=0)return;Cursor c=db.rawQuery("SELECT started_at FROM ue_semantic_attempts WHERE semantic_event_id=? AND attempt=? LIMIT 1",new String[]{String.valueOf(eventId),String.valueOf(attempt)});long started=c.moveToFirst()?c.getLong(0):completed;c.close();ContentValues a=new ContentValues();a.put("state",state);a.put("completed_at",completed);a.put("latency_ms",Math.max(0,completed-started));a.put("error",safe(error));db.update("ue_semantic_attempts",a,"semantic_event_id=? AND attempt=?",new String[]{String.valueOf(eventId),String.valueOf(attempt)});
    }
    private static long backoff(int attempt){long factor=1L<<Math.max(0,Math.min(8,attempt-1));return Math.min(MAX_RETRY_MS,BASE_RETRY_MS*factor);}
    private static long scalar(SQLiteDatabase db,String sql){ensure(db);Cursor c=db.rawQuery(sql,null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    private static void addColumn(SQLiteDatabase db,String table,String column,String definition){Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null);boolean found=false;while(c.moveToNext())if(column.equals(c.getString(1))){found=true;break;}c.close();if(!found)db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
    private static String safe(String s){String x=s==null?"":s.trim();return x.length()<=1200?x:x.substring(0,1200);}

    public static final class Row{
        public final long eventId,rawId,streamId,occurredAt,claimedAt;
        public final int attempt;
        public final String type,subject,summary,source,title,body,payload;
        Row(long e,long r,long s,String t,String sub,String sum,String src,String ti,String b,String p,long at,int a,long claimed){eventId=e;rawId=r;streamId=s;type=safe(t);subject=safe(sub);summary=safe(sum);source=safe(src);title=safe(ti);body=safe(b);payload=p==null?"{}":p;occurredAt=at;attempt=a;claimedAt=claimed;}
    }
}
