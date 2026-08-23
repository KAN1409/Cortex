package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/** Strong vision only after cheap local triage. Background scope starts at v47 install to avoid cloud-processing the old library blindly. */
public class VisualIntelligenceWorker extends Worker {
    private static final int BATCH=2;
    public VisualIntelligenceWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){Context ctx=getApplicationContext();VaultDb db=new VaultDb(ctx);VisualInsightStore.ensure(db);long start=VisualInsightStore.backgroundStart(ctx);int done=0;try{
        while(done<BATCH&&!isStopped()){
            KnowledgeItem k=VisualInsightStore.nextBackground(db,start);if(k==null)break;
            VisualInsightStore.setWorker(ctx,"running",k.id,"Local value + privacy check","Deciding whether this screenshot deserves strong vision");
            VisualTriage.Result t=VisualTriage.evaluate(k);String triage=t.reason+(t.signals.isEmpty()?"":" • signal="+t.signals)+(t.privacyScore>0?" • privacy_score="+t.privacyScore+"/100":"");
            if(t.sensitive){VisualInsightStore.saveState(db,k.id,"local_only","local_only",triage);VisualInsightStore.setWorker(ctx,"running",k.id,"Protected locally",triage);done++;continue;}
            if(t.ephemeral&&t.valueScore<25){VisualInsightStore.saveState(db,k.id,"skipped","safe",triage);VisualInsightStore.setWorker(ctx,"running",k.id,"Skipped low-value screen",triage);done++;continue;}
            if(!GeminiKeyStore.has(ctx)){VisualInsightStore.saveState(db,k.id,"waiting_provider","safe","Gemini vision is not configured");VisualInsightStore.setWorker(ctx,"waiting",k.id,"Waiting for vision provider","Gemini key not configured");break;}
            try{VisualInsightStore.setWorker(ctx,"running",k.id,"Understanding the image","Strong vision is reading objects, text, context and useful actions");JSONObject r=GeminiVisionAnalyzer.analyze(ctx,k);VisualInsightStore.saveModel(db,k.id,r.optString("_provider","gemini-vision"),r);VisualInsightStore.setWorker(ctx,"running",k.id,"Saved useful understanding",r.optString("description",""));}
            catch(Exception e){String msg=e.getClass().getSimpleName()+": "+(e.getMessage()==null?"":e.getMessage());VisualInsightStore.saveState(db,k.id,"failed","safe",msg);VisualInsightStore.setWorker(ctx,"running",k.id,"Vision failed",msg);}
            done++;
        }
        if(VisualInsightStore.countPendingSince(db,start)>0)VisualIntelligenceScheduler.continueChain(ctx);else VisualInsightStore.setWorker(ctx,"idle",0,"Caught up","New screenshots will be understood when useful, charging and online");return Result.success();
    }catch(Exception e){VisualInsightStore.setWorker(ctx,"waiting",0,"Will retry",e.getMessage());return Result.retry();}finally{db.close();}}
}
