package com.kareem.cortex;

import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnalysisQueue {
    private static final AtomicBoolean running=new AtomicBoolean(false);
    private AnalysisQueue(){}
    public static void kick(Context context,VaultDb db,Runnable changed){if(!running.compareAndSet(false,true))return;next(context.getApplicationContext(),db,changed);}
    private static void next(Context ctx,VaultDb db,Runnable changed){
        KnowledgeItem item=db.nextPending();if(item==null){running.set(false);if(changed!=null)changed.run();return;}db.markAnalyzing(item.id);if(changed!=null)changed.run();
        if("SCREENSHOT".equals(item.type)||"IMAGE".equals(item.type)){
            OcrAnalyzer.analyze(ctx,item,new OcrAnalyzer.Callback(){public void ok(AnalysisResult r){finish(db,item.id,r,changed);next(ctx,db,changed);}public void fail(Exception e){fail(db,item.id,e,changed);next(ctx,db,changed);}});
        }else if("AUDIO".equals(item.type)){
            AudioAnalyzer.analyze(ctx,item,new AudioAnalyzer.Callback(){public void ok(AnalysisResult r){db.applyAnalysis(item.id,r);AudioStore.save(db,item.id,r);if(changed!=null)changed.run();next(ctx,db,changed);}public void fail(Exception e){fail(db,item.id,e,changed);next(ctx,db,changed);}});
        }else{
            try{AnalysisResult r=LocalAnalyzer.analyze(item.rawText,"text/plain");db.applyAnalysis(item.id,r);}catch(Exception e){db.markFailed(item.id,e.getMessage());}if(changed!=null)changed.run();next(ctx,db,changed);
        }
    }
    private static void finish(VaultDb db,long id,AnalysisResult r,Runnable changed){db.applyAnalysis(id,r);if(changed!=null)changed.run();}
    private static void fail(VaultDb db,long id,Exception e,Runnable changed){db.markFailed(id,e==null?"Unknown error":e.getMessage());if(changed!=null)changed.run();}
}
