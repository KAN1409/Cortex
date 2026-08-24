package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Fast local extraction. Its job is searchable evidence; visual meaning is handled by VisualIntelligenceWorker. */
public class ScreenshotAnalysisWorker extends Worker {
    private static final int BATCH=8;
    public ScreenshotAnalysisWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){Context ctx=getApplicationContext();VaultDb db=new VaultDb(ctx);requeueStale(db);int done=0;try{
        while(done<BATCH&&!isStopped()){
            KnowledgeItem item=next(db);if(item==null)break;db.markAnalyzing(item.id);
            CountDownLatch latch=new CountDownLatch(1);AtomicReference<AnalysisResult> ok=new AtomicReference<>();AtomicReference<Exception> bad=new AtomicReference<>();
            try{OcrAnalyzer.analyze(ctx,item,new OcrAnalyzer.Callback(){public void ok(AnalysisResult r){ok.set(r);latch.countDown();}public void fail(Exception e){bad.set(e);latch.countDown();}});}catch(Throwable e){db.markFailedRetryable(item.id,"Quick extraction failed safely: "+e.getClass().getSimpleName());done++;continue;}
            boolean returned=latch.await(150,TimeUnit.SECONDS);if(!returned){db.markFailedRetryable(item.id,"Quick extraction timeout");done++;continue;}
            try{
                AnalysisResult r=ok.get();if(r!=null){db.applyAnalysis(item.id,r);try{TemporalResolver.afterAnalysis(db,item.id);}catch(Throwable ignored){}try{CoreBrainEngine.afterAnalysis(db,item.id);}catch(Throwable ignored){}}
                else{Exception e=bad.get();String msg=e==null?"Unknown quick extraction failure":e.getMessage();if(msg!=null&&msg.startsWith("RETRYABLE:"))db.markFailedRetryable(item.id,msg.substring("RETRYABLE:".length()).trim());else db.markFailed(item.id,msg);}
            }catch(Throwable e){db.markFailedRetryable(item.id,"Quick extraction persistence failed safely: "+e.getClass().getSimpleName());}
            done++;
        }
        VisualIntelligenceScheduler.kick(ctx);if(hasQueued(db))ScreenshotWorkScheduler.continueChain(ctx);return Result.success();
    }catch(InterruptedException e){Thread.currentThread().interrupt();return Result.retry();}catch(Throwable e){return Result.retry();}finally{try{db.close();}catch(Throwable ignored){}}}

    private static KnowledgeItem next(VaultDb db){Cursor c=db.getReadableDatabase().rawQuery("SELECT * FROM knowledge_items WHERE status='queued' AND type IN ('SCREENSHOT','IMAGE') ORDER BY created_at ASC LIMIT 1",null);KnowledgeItem k=null;if(c.moveToFirst())k=from(c);c.close();return k;}
    private static boolean hasQueued(VaultDb db){Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM knowledge_items WHERE status='queued' AND type IN ('SCREENSHOT','IMAGE') LIMIT 1",null);boolean x=c.moveToFirst();c.close();return x;}
    private static void requeueStale(VaultDb db){long cutoff=System.currentTimeMillis()-15*60*1000L;android.content.ContentValues v=new android.content.ContentValues();v.put("status","queued");v.put("analysis_error","Recovered stale screenshot analysis after process interruption");v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("knowledge_items",v,"status='analyzing' AND type IN ('SCREENSHOT','IMAGE') AND updated_at<?",new String[]{String.valueOf(cutoff)});}
    private static KnowledgeItem from(Cursor c){return new KnowledgeItem(g(c,"id"),s(c,"type"),s(c,"source"),s(c,"title"),s(c,"raw_text"),s(c,"extracted_text"),s(c,"summary"),s(c,"category"),s(c,"tags"),s(c,"attachment_path"),s(c,"status"),s(c,"fingerprint"),s(c,"analysis_error"),s(c,"metadata_json"),g(c,"created_at"),g(c,"updated_at"));}
    private static String s(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?"":c.getString(i);}private static long g(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?0:c.getLong(i);}
}
