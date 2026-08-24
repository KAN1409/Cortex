package com.kareem.cortex;

import android.content.Context;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnalysisQueue {
    private static final AtomicBoolean running=new AtomicBoolean(false);
    private AnalysisQueue(){}
    public static void kick(Context context,VaultDb db,Runnable changed){if(!running.compareAndSet(false,true))return;next(context.getApplicationContext(),db,changed);}
    private static void next(Context ctx,VaultDb db,Runnable changed){
        KnowledgeItem item=db.nextPending();if(item==null){running.set(false);if(changed!=null)changed.run();return;}db.markAnalyzing(item.id);if(changed!=null)changed.run();
        if("SCREENSHOT".equals(item.type)||"IMAGE".equals(item.type)){
            OcrAnalyzer.analyze(ctx,item,new OcrAnalyzer.Callback(){public void ok(AnalysisResult r){finish(db,item.id,r,changed);next(ctx,db,changed);}public void fail(Exception e){AnalysisQueue.fail(db,item.id,e,changed);next(ctx,db,changed);}});
        }else if("AUDIO".equals(item.type)){
            AudioAnalyzer.analyze(ctx,item,new AudioAnalyzer.Callback(){public void ok(AnalysisResult r){db.applyAnalysis(item.id,r);AudioStore.save(db,item.id,r);post(db,item.id);if(changed!=null)changed.run();next(ctx,db,changed);}public void fail(Exception e){AnalysisQueue.fail(db,item.id,e,changed);next(ctx,db,changed);}});
        }else if("FILE".equals(item.type)){
            try{finish(db,item.id,AttachmentAnalyzer.analyze(item),changed);}catch(Exception e){AnalysisQueue.fail(db,item.id,e,changed);}next(ctx,db,changed);
        }else{
            try{
                AnalysisResult r=LocalAnalyzer.analyze(item.rawText,"text/plain");
                guardPassiveSources(db,item,r);
                db.applyAnalysis(item.id,r);post(db,item.id);
            }catch(Exception e){db.markFailed(item.id,e.getMessage());}
            if(changed!=null)changed.run();next(ctx,db,changed);
        }
    }
    private static void guardPassiveSources(VaultDb db,KnowledgeItem item,AnalysisResult r){
        if(item==null||r==null)return;
        if("CONTACT".equals(item.type)&&"contacts_sync".equals(item.source)&&!r.actions.isEmpty()){
            int suppressed=r.actions.size();r.actions.clear();
            try{JSONObject m=new JSONObject();m.put("suppressed_actions",suppressed);m.put("policy","contacts_are_passive_evidence");DiagnosticsLog.info(db,"analysis","contact_action_suppressed","safe",item.id,0,0,0,0,0,m);}catch(Throwable ignored){}
        }
    }
    private static void finish(VaultDb db,long id,AnalysisResult r,Runnable changed){db.applyAnalysis(id,r);post(db,id);if(changed!=null)changed.run();}
    private static void post(VaultDb db,long id){try{TemporalResolver.afterAnalysis(db,id);}catch(Exception ignored){}try{CoreBrainEngine.afterAnalysis(db,id);}catch(Exception ignored){}}
    private static void fail(VaultDb db,long id,Exception e,Runnable changed){String message=e==null?"Unknown error":e.getMessage();if(message!=null&&message.startsWith("RETRYABLE:")){String clean=message.substring("RETRYABLE:".length()).trim();db.markFailedRetryable(id,clean.isEmpty()?"Retryable analysis failure":clean);}else db.markFailed(id,message);if(changed!=null)changed.run();}
}
