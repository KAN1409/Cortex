package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/** Process-death and bounded retry recovery for both legacy and Cognitive Adjudicator V2 work. */
public final class AdjudicationRecovery {
    private static final long STALE_MS=5L*60L*1000L;
    private static final long FAILED_RETRY_AGE_MS=15L*60L*1000L;
    private static final int MAX_V2_RECOVERY_ATTEMPTS=3;
    private static final String UNIQUE_WORK="cortex-adjudication-recovery";
    private AdjudicationRecovery(){}

    public static int run(Context context,VaultDb db){
        if(context==null||db==null)return 0;CognitiveStore.ensure(db);RelevanceDecisionStatusStore.ensure(db);CognitiveRunStoreV2.ensure(db);
        long now=System.currentTimeMillis(),cutoff=now-STALE_MS;ArrayList<Target> legacyRetry=new ArrayList<>(),v2Retry=new ArrayList<>();HashSet<Long> seenV2=new HashSet<>();SQLiteDatabase sql=db.getWritableDatabase();

        // Preserve recovery for pre-V2 thread jobs. Once V2 is authoritative, interrupted legacy
        // work is re-routed through V2 instead of reviving semanticCue() gating.
        Cursor c=sql.query("ai_jobs",new String[]{"id","input_json","updated_at"},"kind='relevance_adjudication' AND state IN ('queued','running') AND updated_at<?",new String[]{String.valueOf(cutoff)},null,null,"updated_at ASC","100");
        while(c.moveToNext()){
            long jobId=c.getLong(0),lastUpdated=c.getLong(2),staleAge=Math.max(0,now-lastUpdated);String input=c.getString(1)==null?"":c.getString(1);long threadId=0,signalId=0;try{JSONObject o=new JSONObject(input);threadId=o.optLong("thread_id",0);signalId=o.optLong("latest_signal_id",0);}catch(Exception ignored){}
            interruptJob(sql,jobId,now);
            if(signalId>0)RelevanceDecisionStatusStore.modelStatus(db,signalId,"PROCESS_INTERRUPTED");
            if(threadId>0&&signalId>0&&latestSignalId(sql,threadId)==signalId)legacyRetry.add(new Target(threadId,signalId));
            DiagnosticsLog.info(db,"AdjudicationRecovery","legacy_process_interrupted","recovered",0,threadId,signalId,jobId,0,staleAge,null);
        }c.close();

        // Any V2 job abandoned by process death is made explicit before the signal is re-queued.
        Cursor v2Jobs=sql.query("ai_jobs",new String[]{"id","input_json","updated_at"},"kind='cognitive_adjudication_v2' AND state IN ('queued','running') AND updated_at<?",new String[]{String.valueOf(cutoff)},null,null,"updated_at ASC","100");
        while(v2Jobs.moveToNext()){
            long jobId=v2Jobs.getLong(0),lastUpdated=v2Jobs.getLong(2),staleAge=Math.max(0,now-lastUpdated);String input=v2Jobs.getString(1)==null?"":v2Jobs.getString(1);long threadId=0,signalId=0;try{JSONObject o=new JSONObject(input);threadId=o.optLong("thread_id",0);signalId=o.optLong("latest_signal_id",0);}catch(Exception ignored){}
            interruptJob(sql,jobId,now);
            if(signalId>0){ContentValues u=new ContentValues();u.put("cognitive_state",CognitiveSignalV2.CognitiveState.MODEL_FAILED.name());u.put("final_reason","Android process ended during cognitive adjudication; bounded retry queued");u.put("updated_at",now);sql.update("raw_signals",u,"id=? AND cognitive_state IN ('LOCAL_QUEUED','LOCAL_RUNNING','DEEP_QUEUED','PENDING_ADJUDICATION')",new String[]{String.valueOf(signalId)});}
            if(eligibleCurrent(sql,threadId,signalId)&&seenV2.add(signalId))v2Retry.add(new Target(threadId,signalId));
            DiagnosticsLog.info(db,"AdjudicationRecovery","v2_process_interrupted","recovered",0,threadId,signalId,jobId,0,staleAge,null);
        }v2Jobs.close();

        // MODEL_FAILED is never a drop state. Retry later, but cap automatic recovery so one bad
        // signal cannot heat the phone forever. A manual refresh/diagnostic can still requeue it.
        Cursor failed=sql.rawQuery("SELECT id,thread_id,updated_at FROM raw_signals WHERE kind='notification' AND cognitive_state='MODEL_FAILED' AND updated_at<? ORDER BY updated_at ASC LIMIT 100",new String[]{String.valueOf(now-FAILED_RETRY_AGE_MS)});
        while(failed.moveToNext()){
            long signalId=failed.getLong(0),threadId=failed.getLong(1);if(seenV2.contains(signalId)||!eligibleCurrent(sql,threadId,signalId))continue;
            if(cognitiveAttempts(sql,signalId)>=MAX_V2_RECOVERY_ATTEMPTS)continue;
            seenV2.add(signalId);v2Retry.add(new Target(threadId,signalId));
        }failed.close();

        if(LocalModelManager.installed(context)){
            for(Target t:legacyRetry){
                if(CognitiveFeatureFlags.authoritative(context)){ContentValues u=new ContentValues();u.put("cognitive_state",CognitiveSignalV2.CognitiveState.LOCAL_QUEUED.name());u.put("final_reason","legacy recovery routed to authoritative Cognitive V2");u.put("updated_at",now);sql.update("raw_signals",u,"id=?",new String[]{String.valueOf(t.signalId)});CognitiveAdjudicatorV2.enqueue(context,t.threadId,t.signalId);}
                else ThreadModelAdjudicator.enqueue(context,t.threadId,t.signalId);
            }
            if(LocalBrainRuntimePolicy.thermalAllowsInference(context))for(Target t:v2Retry){ContentValues u=new ContentValues();u.put("cognitive_state",CognitiveSignalV2.CognitiveState.LOCAL_QUEUED.name());u.put("final_reason","bounded recovery retry queued");u.put("updated_at",now);sql.update("raw_signals",u,"id=?",new String[]{String.valueOf(t.signalId)});CognitiveAdjudicatorV2.enqueue(context,t.threadId,t.signalId);}
        }
        return legacyRetry.size()+v2Retry.size();
    }

    public static void schedule(Context context){
        if(context==null)return;try{PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(AdjudicationRecoveryWorker.class,15,TimeUnit.MINUTES).build();WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(UNIQUE_WORK,ExistingPeriodicWorkPolicy.KEEP,request);}catch(Throwable ignored){}
    }

    private static void interruptJob(SQLiteDatabase sql,long jobId,long now){ContentValues j=new ContentValues();j.put("state","failed");j.put("error","PROCESS_INTERRUPTED");j.put("progress_json",progress(now));j.put("updated_at",now);j.put("completed_at",now);sql.update("ai_jobs",j,"id=?",new String[]{String.valueOf(jobId)});ContentValues r=new ContentValues();r.put("state","interrupted");r.put("error","PROCESS_INTERRUPTED");sql.update("model_runs",r,"job_id=? AND state NOT IN ('complete','invalid','superseded')",new String[]{String.valueOf(jobId)});}
    private static boolean eligibleCurrent(SQLiteDatabase sql,long threadId,long signalId){if(signalId<=0)return false;return threadId<=0||latestSignalId(sql,threadId)==signalId;}
    private static int cognitiveAttempts(SQLiteDatabase sql,long signalId){Cursor c=sql.rawQuery("SELECT COUNT(*) FROM cognitive_runs WHERE raw_signal_id=?",new String[]{String.valueOf(signalId)});try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    private static long latestSignalId(SQLiteDatabase sql,long threadId){Cursor c=sql.rawQuery("SELECT id FROM raw_signals WHERE thread_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1",new String[]{String.valueOf(threadId)});long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    private static String progress(long now){try{return new JSONObject().put("stage","Interrupted").put("code","process_interrupted").put("percent",100).put("detail","Android process ended before local adjudication completed; safe retry may be scheduled").put("at",now).toString();}catch(Exception e){return"{}";}}
    private static final class Target{final long threadId,signalId;Target(long t,long s){threadId=t;signalId=s;}}
}
