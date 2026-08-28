package com.kareem.cortex;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/**
 * User-triggered refresh coordinator shared by NOW/Pulse and Inbox.
 *
 * A refresh is deliberately stronger than a view reload: it repairs abandoned analysis work,
 * retries bounded retryable audio when an ASR provider is currently usable, projects already
 * analyzed intentional captures into V4, refreshes Situations/Pulse and wakes autonomous reasoning.
 */
public final class CognitiveManualRefreshV4 {
    private static final long STALE_ANALYZING_MS=5L*60L*1000L;
    private static final int MAX_RETRYABLE_AUDIO=5;
    private CognitiveManualRefreshV4(){}

    public static Result run(Context context,VaultDb db,Runnable changed){
        if(context==null||db==null)return new Result(0,0,0);
        Context app=context.getApplicationContext();
        int recovered=0,requeued=0,material=0;
        try{
            DiagnosticsLog.ensure(db);
            recovered=recoverAbandonedAnalysis(db);
            requeued=requeueRetryableAudio(app,db);
            IntentionalCognitiveBridge.backfill(db,500);
            CognitiveSituationEngineV4.Result r=CognitiveSituationEngineV4.refresh(db);
            if(r!=null)material=r.materialChanges();
            CognitiveDeepBrainReconcilerV4.reconcile(db);
            JSONObject meta=new JSONObject();meta.put("recovered_stale_analyzing",recovered);meta.put("requeued_retryable_audio",requeued);meta.put("situation_material_changes",material);meta.put("analysis_pending",db.pendingCount());
            DiagnosticsLog.info(db,"manual_refresh","cognitive_refresh","complete",0,0,0,0,0,0,meta);
        }catch(Throwable e){
            DiagnosticsLog.error(db,"manual_refresh","cognitive_refresh",e,"MANUAL_REFRESH",0,0,0,0,0,null);
        }
        try{AnalysisQueue.kick(app,null,changed);}catch(Throwable ignored){}
        try{CognitiveReasoningOrchestratorV4.schedule(app,"manual_pull_refresh");}catch(Throwable ignored){}
        return new Result(recovered,requeued,material);
    }

    /** A process death can leave a row permanently 'analyzing'. Only rescue clearly stale work. */
    static int recoverAbandonedAnalysis(VaultDb db){
        long cutoff=System.currentTimeMillis()-STALE_ANALYZING_MS;
        ContentValues v=new ContentValues();v.put("status","queued");v.put("analysis_error","Recovered stale analysis after manual refresh");v.put("updated_at",System.currentTimeMillis());
        return db.getWritableDatabase().update("knowledge_items",v,"status='analyzing' AND updated_at<?",new String[]{String.valueOf(cutoff)});
    }

    /**
     * Retry only audio failures that were explicitly marked retryable and only when cloud ASR can
     * actually run now. This avoids turning every pull gesture into an infinite failure loop.
     */
    static int requeueRetryableAudio(Context context,VaultDb db){
        if(!PrivacyPolicy.canUseCloud(context,"audio"))return 0;
        if(!GeminiKeyStore.has(context)&&!GroqKeyStore.has(context))return 0;
        SQLiteDatabase sql=db.getWritableDatabase();
        Cursor c=sql.rawQuery("SELECT id FROM knowledge_items WHERE type='AUDIO' AND status='failed_retryable' ORDER BY updated_at DESC LIMIT "+MAX_RETRYABLE_AUDIO,null);
        int n=0;try{while(c.moveToNext()){db.retry(c.getLong(0));n++;}}finally{c.close();}
        return n;
    }

    public static final class Result{
        public final int recoveredStaleAnalyzing,requeuedRetryableAudio,situationMaterialChanges;
        Result(int recovered,int requeued,int material){recoveredStaleAnalyzing=recovered;requeuedRetryableAudio=requeued;situationMaterialChanges=material;}
    }
}
