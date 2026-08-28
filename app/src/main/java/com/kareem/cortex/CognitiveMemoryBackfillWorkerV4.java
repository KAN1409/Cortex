package com.kareem.cortex;

import android.content.Context;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Runs additive V4 migration outside capture callbacks and outside the UI thread. */
public final class CognitiveMemoryBackfillWorkerV4 extends Worker {
    public CognitiveMemoryBackfillWorkerV4(Context appContext,WorkerParameters params){super(appContext,params);}

    @Override public Result doWork(){
        VaultDb db=null;
        try{
            db=new VaultDb(getApplicationContext());int evidence=0,episodes=0,memories=0,deferred=0,failed=0;
            for(int pass=0;pass<4;pass++){
                CognitiveMemoryBackfillV4.Stats s=CognitiveMemoryBackfillV4.runBatch(db,100);
                evidence+=s.evidenceMapped;episodes+=s.episodesMapped;memories+=s.memoriesMapped;deferred+=s.deferred;failed+=s.failed;
                if(s.totalMapped()==0)break;
            }
            int pinnedEvidence=CognitiveRetentionV4.reconcilePinnedEvidence(db);
            Data out=new Data.Builder().putInt("evidence_mapped",evidence).putInt("episodes_mapped",episodes).putInt("memories_mapped",memories).putInt("deferred",deferred).putInt("failed",failed).putInt("pinned_evidence_protected",pinnedEvidence).build();
            return Result.success(out);
        }catch(Throwable e){return Result.retry();}
        finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }
}
