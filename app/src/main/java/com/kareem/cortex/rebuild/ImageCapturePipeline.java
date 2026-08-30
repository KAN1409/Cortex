package com.kareem.cortex.rebuild;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Photo -> grounded vision -> Cortex Brain. Vision survives even when cognition providers fail. */
public final class ImageCapturePipeline {
    public interface Callback{void complete(long evidenceId,Outcome outcome);}
    public static final class Outcome{
        public final ImageVisionEngine.Result vision;public final BrainStore.ApplyResult brainResult;public final Exception visionError,brainError;
        Outcome(ImageVisionEngine.Result v,BrainStore.ApplyResult b,Exception ve,Exception be){vision=v;brainResult=b;visionError=ve;brainError=be;}
        public boolean visionSucceeded(){return vision!=null&&visionError==null;}public boolean brainSucceeded(){return brainResult!=null&&brainError==null;}
    }
    private static final ExecutorService QUEUE=Executors.newSingleThreadExecutor(r->new Thread(r,"cortex-photo-analysis"));
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private ImageCapturePipeline(){}

    public static void analyze(Context context,long evidenceId,Callback callback){Context app=context.getApplicationContext();QUEUE.execute(()->run(app,evidenceId,true,callback));}
    public static void reanalyze(Context context,long evidenceId,Callback callback){Context app=context.getApplicationContext();QUEUE.execute(()->{CortexDb db=null;try{db=new CortexDb(app);CaptureRecordStore.retireDerived(db,evidenceId);}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}run(app,evidenceId,true,callback);});}
    public static void rebrainStored(Context context,long evidenceId,Callback callback){Context app=context.getApplicationContext();QUEUE.execute(()->run(app,evidenceId,false,callback));}

    private static void run(Context app,long evidenceId,boolean doVision,Callback callback){
        CortexDb db=null;ImageVisionEngine.Result vision=null;BrainStore.ApplyResult brain=null;Exception visionError=null,brainError=null;
        try{
            db=new CortexDb(app);CaptureRecordStore.Record record=CaptureRecordStore.get(db,evidenceId);if(record==null||!record.isImage())throw new IllegalArgumentException("Photo evidence not found");
            if(doVision){CaptureRecordStore.markVisionRunning(db,evidenceId);vision=ImageVisionEngine.analyze(app,new File(record.path),record.mimeType);CaptureRecordStore.saveVision(db,evidenceId,vision);record=CaptureRecordStore.get(db,evidenceId);}else{
                if(!CaptureRecordStore.hasVision(record))throw new IllegalStateException("No stored photo analysis to re-run");vision=stored(record);
            }
        }catch(Exception e){visionError=e;if(db!=null&&doVision)try{CaptureRecordStore.markVisionFailed(db,evidenceId,e);}catch(Throwable ignored){}}

        if(visionError==null&&db!=null){
            try{
                CaptureRecordStore.Record record=CaptureRecordStore.get(db,evidenceId);String grounded=record==null?"":record.body;
                CortexDb.AttachmentEvidence evidence=db.attachmentEvidence(evidenceId);if(evidence==null||grounded.trim().isEmpty())throw new IllegalStateException("Grounded photo description missing");
                BrainStore.ensure(db);BrainStore.markRunning(db,evidenceId);String snapshot=BrainStore.contextSnapshot(db,16);
                BrainIntakeEngine.Decision decision=CortexBrainRouter.understand(app,evidence,grounded,snapshot);brain=BrainStore.apply(db,evidenceId,decision);
                if(decision.situationCreate&&"PERSONAL".equals(decision.captureClass)){
                    try{List<SituationDecomposer.Spec> specs=SituationDecomposer.decompose(app,evidence,grounded,BrainStore.contextSnapshot(db,20));if(specs!=null&&!specs.isEmpty())SituationActions.replaceEvidenceSituations(db,evidenceId,specs);}catch(Exception ignored){}
                }
            }catch(Exception e){brainError=e;try{BrainStore.markFailed(db,evidenceId,e);}catch(Throwable ignored){}}
        }
        if(db!=null)try{db.close();}catch(Throwable ignored){}
        Outcome out=new Outcome(vision,brain,visionError,brainError);if(callback!=null)MAIN.post(()->callback.complete(evidenceId,out));
    }

    private static ImageVisionEngine.Result stored(CaptureRecordStore.Record r){ImageVisionEngine.Result x=new ImageVisionEngine.Result();try{org.json.JSONObject v=new org.json.JSONObject(r.payloadJson).optJSONObject("vision");if(v!=null){x.provider=v.optString("provider","stored");x.model=v.optString("model","");x.summary=v.optString("summary","");x.extractedText=v.optString("extracted_text","");x.visibleEntities=v.optJSONArray("visible_entities")==null?new org.json.JSONArray():v.optJSONArray("visible_entities");x.urls=v.optJSONArray("urls")==null?new org.json.JSONArray():v.optJSONArray("urls");x.barcodes=v.optJSONArray("barcodes")==null?new org.json.JSONArray():v.optJSONArray("barcodes");x.uncertainties=v.optJSONArray("uncertainties")==null?new org.json.JSONArray():v.optJSONArray("uncertainties");}}catch(Exception ignored){}return x;}
}
