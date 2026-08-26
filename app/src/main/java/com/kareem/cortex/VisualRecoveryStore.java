package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;

/** Persistent retry ledger kept separate from visual_insights so recovery state survives workers/restarts. */
public final class VisualRecoveryStore {
    private static final int LEGACY_PIPELINE=VisualInsightStore.PIPELINE_VERSION-1;
    private VisualRecoveryStore(){}

    public static final class State {
        public final long itemId,nextRetryAt,updatedAt;public final int attempts;public final String failureKind,nextAction,lastError;public final boolean recoverable;
        State(long id,int a,String kind,boolean r,long next,String action,String error,long at){itemId=id;attempts=a;failureKind=n(kind);recoverable=r;nextRetryAt=next;nextAction=n(action);lastError=n(error);updatedAt=at;}
    }

    public static void ensure(VaultDb db){
        db.getWritableDatabase().execSQL("CREATE TABLE IF NOT EXISTS visual_recovery(item_id INTEGER PRIMARY KEY,attempt_count INTEGER NOT NULL DEFAULT 0,failure_kind TEXT,recoverable INTEGER NOT NULL DEFAULT 1,next_retry_at INTEGER NOT NULL DEFAULT 0,next_action TEXT,last_error TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.getWritableDatabase().execSQL("CREATE INDEX IF NOT EXISTS idx_visual_recovery_due ON visual_recovery(recoverable,next_retry_at)");
    }

    public static State get(VaultDb db,long itemId){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT item_id,attempt_count,failure_kind,recoverable,next_retry_at,next_action,last_error,updated_at FROM visual_recovery WHERE item_id=?",new String[]{String.valueOf(itemId)});State s=c.moveToFirst()?new State(c.getLong(0),c.getInt(1),c.getString(2),c.getInt(3)!=0,c.getLong(4),c.getString(5),c.getString(6),c.getLong(7)):null;c.close();return s;}
    public static int attempts(VaultDb db,long itemId){State s=get(db,itemId);return s==null?0:s.attempts;}

    public static State record(VaultDb db,long itemId,VisualFailurePolicy.Decision d,Throwable error){
        if(db==null||itemId<=0||d==null)return null;ensure(db);State old=get(db,itemId);int attempts=(old==null?0:old.attempts)+(d.countsAttempt?1:0);boolean recoverable=d.recoverable&&(attempts<VisualFailurePolicy.MAX_TRANSIENT_ATTEMPTS||!d.countsAttempt);long now=System.currentTimeMillis(),next=recoverable?now+Math.max(1_000L,d.retryAfterMs):0;
        ContentValues v=new ContentValues();v.put("item_id",itemId);v.put("attempt_count",attempts);v.put("failure_kind",d.kind);v.put("recoverable",recoverable?1:0);v.put("next_retry_at",next);v.put("next_action",d.nextAction);v.put("last_error",message(error));v.put("created_at",old==null?now:Math.min(now,old.updatedAt));v.put("updated_at",now);db.getWritableDatabase().insertWithOnConflict("visual_recovery",null,v,SQLiteDatabase.CONFLICT_REPLACE);return get(db,itemId);
    }

    public static void clear(VaultDb db,long itemId){if(db==null||itemId<=0)return;ensure(db);db.getWritableDatabase().delete("visual_recovery","item_id=?",new String[]{String.valueOf(itemId)});}

    /**
     * Convert due retry ledger entries into the existing pipeline's explicit old-failure retry form.
     * Successful/protected visual results are never touched.
     */
    public static int activateDue(VaultDb db){
        ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("pipeline_version",LEGACY_PIPELINE);v.put("status","failed");v.put("updated_at",now);
        return db.getWritableDatabase().update("visual_insights",v,"item_id IN (SELECT r.item_id FROM visual_recovery r LEFT JOIN visual_insights vi ON vi.item_id=r.item_id WHERE r.recoverable=1 AND r.next_retry_at>0 AND r.next_retry_at<=? AND (r.attempt_count<? OR r.failure_kind='provider_rate_limit') AND COALESCE(vi.status,'') NOT IN ('done','local_only','skipped')) AND status NOT IN ('done','local_only','skipped')",new String[]{String.valueOf(now),String.valueOf(VisualFailurePolicy.MAX_TRANSIENT_ATTEMPTS)});
    }

    /**
     * Explicit diagnostics retry for recoverable items. Attempts are deliberately preserved so a
     * manual "retry now" cannot turn bounded recovery into an infinite retry loop. Provider cooldown
     * remains owned by VisualIntelligenceScheduler/VisionRateLimitGate.
     */
    public static int retryRecoverableNow(VaultDb db){return retryRecoverableNow(db,0);}
    static int retryRecoverableNow(VaultDb db,long exactItemId){
        if(db==null)return 0;ensure(db);long now=System.currentTimeMillis();SQLiteDatabase sql=db.getWritableDatabase();ArrayList<Long> ids=new ArrayList<>();Cursor c;if(exactItemId>0)c=sql.rawQuery("SELECT r.item_id FROM visual_recovery r LEFT JOIN visual_insights v ON v.item_id=r.item_id WHERE r.recoverable=1 AND r.item_id=? AND COALESCE(v.status,'') NOT IN ('done','local_only','skipped') LIMIT 1",new String[]{String.valueOf(exactItemId)});else c=sql.rawQuery("SELECT r.item_id FROM visual_recovery r LEFT JOIN visual_insights v ON v.item_id=r.item_id WHERE r.recoverable=1 AND COALESCE(v.status,'') NOT IN ('done','local_only','skipped') ORDER BY r.updated_at DESC LIMIT 100",null);while(c.moveToNext())ids.add(c.getLong(0));c.close();if(ids.isEmpty())return 0;
        boolean own=!sql.inTransaction();if(own)sql.beginTransaction();int changed=0;try{for(long id:ids){ContentValues r=new ContentValues();r.put("next_retry_at",now);r.put("updated_at",now);sql.update("visual_recovery",r,"item_id=? AND recoverable=1",new String[]{String.valueOf(id)});ContentValues v=new ContentValues();v.put("pipeline_version",LEGACY_PIPELINE);v.put("status","failed");v.put("error","Explicit retry-now requested from Advanced diagnostics; bounded attempt history preserved");v.put("updated_at",now);changed+=sql.update("visual_insights",v,"item_id=? AND status NOT IN ('done','local_only','skipped')",new String[]{String.valueOf(id)});}if(own)sql.setTransactionSuccessful();}finally{if(own)sql.endTransaction();}return changed;
    }

    /**
     * Explicit fresh budget for terminal items after the user fixes the underlying cause. This is the
     * only bulk recovery operation that deletes terminal attempt history; successful/protected items
     * remain untouched. Transaction-aware so deterministic verification can roll it back safely.
     */
    public static int resetTerminalBudget(VaultDb db,int limit){return resetTerminalBudget(db,limit,0);}
    static int resetTerminalBudget(VaultDb db,long exactItemId){return resetTerminalBudget(db,1,exactItemId);}
    private static int resetTerminalBudget(VaultDb db,int limit,long exactItemId){
        if(db==null)return 0;ensure(db);SQLiteDatabase sql=db.getWritableDatabase();ArrayList<Long> ids=new ArrayList<>();Cursor c;if(exactItemId>0)c=sql.rawQuery("SELECT r.item_id FROM visual_recovery r LEFT JOIN visual_insights v ON v.item_id=r.item_id WHERE r.recoverable=0 AND r.item_id=? AND COALESCE(v.status,'') NOT IN ('done','local_only','skipped') LIMIT 1",new String[]{String.valueOf(exactItemId)});else c=sql.rawQuery("SELECT r.item_id FROM visual_recovery r LEFT JOIN visual_insights v ON v.item_id=r.item_id WHERE r.recoverable=0 AND COALESCE(v.status,'') NOT IN ('done','local_only','skipped') ORDER BY r.updated_at DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,Math.min(100,limit)))});while(c.moveToNext())ids.add(c.getLong(0));c.close();if(ids.isEmpty())return 0;long now=System.currentTimeMillis();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();int changed=0;try{for(long id:ids){sql.delete("visual_recovery","item_id=? AND recoverable=0",new String[]{String.valueOf(id)});ContentValues v=new ContentValues();v.put("pipeline_version",LEGACY_PIPELINE);v.put("status","failed");v.put("error","Explicit terminal retry budget reset after user review");v.put("updated_at",now);changed+=sql.update("visual_insights",v,"item_id=? AND status NOT IN ('done','local_only','skipped')",new String[]{String.valueOf(id)});}if(own)sql.setTransactionSuccessful();}finally{if(own)sql.endTransaction();}return changed;
    }

    /** Latest unresolved visual issue, including provider-waiting items that do not have retry-ledger rows. */
    public static long latestIssueId(VaultDb db){if(db==null)return 0;ensure(db);Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT v.item_id FROM visual_insights v LEFT JOIN visual_recovery r ON r.item_id=v.item_id WHERE v.status IN ('failed','retry_wait','rate_limited','waiting_provider') ORDER BY v.updated_at DESC,COALESCE(r.updated_at,0) DESC LIMIT 1",null);return c.moveToFirst()?c.getLong(0):0;}catch(Throwable ignored){return 0;}finally{if(c!=null)c.close();}}

    public static long nextDelayMs(VaultDb db){ensure(db);long now=System.currentTimeMillis();Cursor c=db.getReadableDatabase().rawQuery("SELECT MIN(r.next_retry_at) FROM visual_recovery r LEFT JOIN visual_insights v ON v.item_id=r.item_id WHERE r.recoverable=1 AND r.next_retry_at>? AND COALESCE(v.status,'') NOT IN ('done','local_only','skipped')",new String[]{String.valueOf(now)});long at=c.moveToFirst()&&!c.isNull(0)?c.getLong(0):0;c.close();return at<=0?-1:Math.max(1_000L,at-now);}
    public static int countRecoverable(VaultDb db){ensure(db);return count(db,"SELECT COUNT(*) FROM visual_recovery r LEFT JOIN visual_insights v ON v.item_id=r.item_id WHERE r.recoverable=1 AND COALESCE(v.status,'') NOT IN ('done','local_only','skipped')");}
    public static int countTerminal(VaultDb db){ensure(db);return count(db,"SELECT COUNT(*) FROM visual_recovery r LEFT JOIN visual_insights v ON v.item_id=r.item_id WHERE r.recoverable=0 AND COALESCE(v.status,'') NOT IN ('done','local_only','skipped')");}

    /** One-time adoption of pre-ledger failures. Obviously permanent failures stay terminal. */
    public static void adoptLegacyFailures(VaultDb db){
        ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT item_id,error FROM visual_insights WHERE status='failed' AND NOT EXISTS(SELECT 1 FROM visual_recovery r WHERE r.item_id=visual_insights.item_id) LIMIT 100",null);while(c.moveToNext()){
            long id=c.getLong(0);String err=n(c.getString(1));VisualFailurePolicy.Decision d=legacy(err);record(db,id,d,new IOExceptionCompat(err));
        }c.close();
    }

    private static VisualFailurePolicy.Decision legacy(String error){String e=n(error).toLowerCase();if(e.contains("filenotfound")||e.contains("missing")||e.contains("decode")||e.contains("401")||e.contains("403"))return new VisualFailurePolicy.Decision("legacy_terminal",false,false,0,"Review the archived image/provider and use explicit Retry if the cause has been fixed.");return new VisualFailurePolicy.Decision("legacy_transient",true,true,30_000L,"Legacy failure adopted into bounded recovery; Cortex will retry once automatically.");}
    private static int count(VaultDb db,String q){Cursor c=db.getReadableDatabase().rawQuery(q,null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    private static String message(Throwable e){return e==null||e.getMessage()==null?"":e.getClass().getSimpleName()+": "+e.getMessage();}
    private static String n(String s){return s==null?"":s.trim();}
    private static final class IOExceptionCompat extends RuntimeException {IOExceptionCompat(String message){super(message==null?"":message);}}
}
