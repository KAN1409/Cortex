package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.work.*;

/** Background-only refinement. Failure never changes Today; deterministic attention remains valid. */
public final class AttentionAiWorker extends Worker {
    private static final int BATCH=4;
    public AttentionAiWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){Context ctx=getApplicationContext();if(!ExternalBrainProvider.configured(ctx))return Result.success();VaultDb db=new VaultDb(ctx);try{
        CognitiveStore.ensure(db);AttentionAdjudicationStore.ensure(db);int done=0;
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,source_key,state,confidence,importance,thread_id,anchor_signal_id,updated_at FROM derived_items WHERE state='open' AND kind IN ('ACTION','WAITING','DECISION') ORDER BY updated_at DESC LIMIT 30",null);
        try{while(c.moveToNext()&&done<BATCH&&!isStopped()){
            PrimeBriefStore.Item item=new PrimeBriefStore.Item(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getDouble(6),c.getInt(7),c.getLong(8),c.getLong(9),c.getLong(10));
            if(AttentionAdjudicationStore.fresh(db,item.id,item.updatedAt))continue;
            AttentionEngine.Decision baseline=AttentionEngine.evaluate(item,System.currentTimeMillis());AttentionContextBuilder.Pack pack=AttentionContextBuilder.build(ctx,db,item);if(!pack.usable())continue;
            try{AttentionAiAdjudicator.Result r=AttentionAiAdjudicator.adjudicate(ctx,item,baseline,pack);if(r!=null)AttentionAdjudicationStore.save(db,item,baseline,r.modelScore,r.merged,r.confidence,r.provider,r.evidence);}catch(Throwable ignored){}
            done++;
        }}finally{c.close();}
        return Result.success();
    }catch(Throwable e){return Result.success();}finally{try{db.close();}catch(Throwable ignored){}}
    }
}
