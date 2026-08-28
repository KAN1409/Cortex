package com.kareem.cortex;

import android.content.Context;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/** Runs additive V4 migration outside capture callbacks and outside the UI thread. */
public final class CognitiveMemoryBackfillWorkerV4 extends Worker {
    private static final int PASSES_PER_RUN=4;
    private static final int BATCH_PER_LAYER=200;

    public CognitiveMemoryBackfillWorkerV4(Context appContext,WorkerParameters params){super(appContext,params);}

    @Override public Result doWork(){
        VaultDb db=null;
        try{
            db=new VaultDb(getApplicationContext());
            int rescued=0,evidence=0,episodes=0,memories=0,deferred=0,failed=0;
            for(int pass=0;pass<PASSES_PER_RUN;pass++){
                int rescuedThisPass=CognitiveMemoryHistoricalRescueV4.runBatch(db,BATCH_PER_LAYER);
                rescued+=rescuedThisPass;
                CognitiveMemoryBackfillV4.Stats s=CognitiveMemoryBackfillV4.runBatch(db,BATCH_PER_LAYER);
                evidence+=s.evidenceMapped;
                episodes+=s.episodesMapped;
                memories+=s.memoriesMapped;
                deferred+=s.deferred;
                failed+=s.failed;
                if(s.totalMapped()==0&&rescuedThisPass==0)break;
            }

            int pinnedEvidence=CognitiveRetentionV4.reconcilePinnedEvidence(db);
            CognitiveMemoryEquivalenceV4.Report report=CognitiveMemoryEquivalenceV4.evaluate(db);
            int progress=rescued+evidence+episodes+memories;
            boolean pending=report.pendingRawEvidence()>0||report.pendingEpisodes()>0||report.pendingMemories()>0;

            if(!report.integrityClean())logReport(db,"projection_equivalence","mismatch","V4_MEMORY_EQUIVALENCE",report,progress,failed);

            Data out=new Data.Builder()
                    .putInt("historical_evidence_rescued",rescued)
                    .putInt("evidence_mapped",evidence)
                    .putInt("episodes_mapped",episodes)
                    .putInt("memories_mapped",memories)
                    .putInt("deferred",deferred)
                    .putInt("failed",failed)
                    .putInt("progress",progress)
                    .putInt("run_attempt",getRunAttemptCount())
                    .putInt("pinned_evidence_protected",pinnedEvidence)
                    .putInt("pending_raw_evidence",report.pendingRawEvidence())
                    .putInt("pending_episodes",report.pendingEpisodes())
                    .putInt("pending_memories",report.pendingMemories())
                    .putInt("memory_without_evidence",report.memoryWithoutEvidence)
                    .putInt("broken_memory_evidence",report.brokenMemoryEvidenceLinks)
                    .putInt("broken_legacy_mappings",report.brokenLegacyMappings)
                    .putInt("content_mismatches",report.contentMismatches)
                    .putBoolean("integrity_clean",report.integrityClean())
                    .putBoolean("migration_complete",report.migrationComplete())
                    .putBoolean("cutover_ready",report.cutoverReady())
                    .build();

            if(report.cutoverReady())return Result.success(out);
            if(pending&&progress>0)return Result.retry();
            if(pending)logReport(db,"migration_stalled","blocked","V4_MEMORY_BACKFILL_STALLED",report,progress,failed);
            return Result.success(out);
        }catch(Throwable e){
            return Result.retry();
        }finally{
            if(db!=null)try{db.close();}catch(Throwable ignored){}
        }
    }

    private static void logReport(VaultDb db,String event,String status,String code,CognitiveMemoryEquivalenceV4.Report report,int progress,int failed){
        try{
            JSONObject metadata;
            try{metadata=new JSONObject(report.toJson());}catch(Throwable ignored){metadata=new JSONObject();}
            metadata.put("progress",progress);
            metadata.put("failed",failed);
            DiagnosticsLog.warn(db,"CognitiveMemoryBackfillWorkerV4",event,status,code,0,0,0,0,0,metadata);
        }catch(Throwable ignored){}
    }
}
