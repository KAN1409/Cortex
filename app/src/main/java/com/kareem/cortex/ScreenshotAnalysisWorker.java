package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Fast local extraction. Its job is searchable evidence; visual meaning is handled by VisualIntelligenceWorker. */
public class ScreenshotAnalysisWorker extends Worker {
    private static final int BATCH=8;
    public ScreenshotAnalysisWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){Context ctx=getApplicationContext();VaultDb db=new VaultDb(ctx);requeueStale(db);int done=0;try{
        while(done<BATCH&&!isStopped()){
            KnowledgeItem item=nextPrioritized(db);if(item==null)break;db.markAnalyzing(item.id);
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

    /**
     * Real queue policy, not just a diagnostic policy:
     * 1) direct/manual IMAGE evidence outranks background screenshot-folder imports;
     * 2) screenshot-folder catch-up is newest-original-image first using source_modified;
     * 3) created_at is only the fallback when source metadata is absent.
     *
     * Scanning the queued rows is deliberate: import time can make hundreds of old screenshots look
     * equally new, so SQL created_at ordering cannot recover the user's actual chronology.
     */
    static KnowledgeItem nextPrioritized(VaultDb db){
        Cursor c=db.getReadableDatabase().rawQuery("SELECT * FROM knowledge_items WHERE status='queued' AND type IN ('SCREENSHOT','IMAGE') ORDER BY created_at DESC LIMIT 5000",null);
        KnowledgeItem best=null;int bestLane=-1;long bestAt=Long.MIN_VALUE;
        try{
            while(c.moveToNext()){
                KnowledgeItem k=from(c);int lane="screenshot-folder".equals(k.source)?1:2;long at=sourceModified(k);
                if(best==null||lane>bestLane||(lane==bestLane&&at>bestAt)){best=k;bestLane=lane;bestAt=at;}
            }
        }finally{c.close();}
        return best;
    }

    static long sourceModified(KnowledgeItem k){
        if(k==null)return 0;long fallback=k.createdAt;
        try{JSONObject m=new JSONObject(k.metadataJson==null?"{}":k.metadataJson);long x=m.optLong("source_modified",0);return x>0?x:fallback;}catch(Throwable ignored){return fallback;}
    }

    static BacklogStats backlog(VaultDb db){
        Cursor c=db.getReadableDatabase().rawQuery("SELECT * FROM knowledge_items WHERE status='queued' AND type IN ('SCREENSHOT','IMAGE') ORDER BY created_at DESC LIMIT 5000",null);int total=0,foreground=0,screenshots=0;long newest=0,oldest=Long.MAX_VALUE;
        try{while(c.moveToNext()){KnowledgeItem k=from(c);total++;long at=sourceModified(k);newest=Math.max(newest,at);oldest=Math.min(oldest,at);if("screenshot-folder".equals(k.source))screenshots++;else foreground++;}}finally{c.close();}
        if(total==0)oldest=0;return new BacklogStats(total,foreground,screenshots,newest,oldest);
    }

    static final class BacklogStats{final int total,foreground,screenshots;final long newestAt,oldestAt;BacklogStats(int total,int foreground,int screenshots,long newestAt,long oldestAt){this.total=total;this.foreground=foreground;this.screenshots=screenshots;this.newestAt=newestAt;this.oldestAt=oldestAt;}}

    private static boolean hasQueued(VaultDb db){Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM knowledge_items WHERE status='queued' AND type IN ('SCREENSHOT','IMAGE') LIMIT 1",null);boolean x=c.moveToFirst();c.close();return x;}
    private static void requeueStale(VaultDb db){long cutoff=System.currentTimeMillis()-15*60*1000L;android.content.ContentValues v=new android.content.ContentValues();v.put("status","queued");v.put("analysis_error","Recovered stale screenshot analysis after process interruption");v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("knowledge_items",v,"status='analyzing' AND type IN ('SCREENSHOT','IMAGE') AND updated_at<?",new String[]{String.valueOf(cutoff)});}
    private static KnowledgeItem from(Cursor c){return new KnowledgeItem(g(c,"id"),s(c,"type"),s(c,"source"),s(c,"title"),s(c,"raw_text"),s(c,"extracted_text"),s(c,"summary"),s(c,"category"),s(c,"tags"),s(c,"attachment_path"),s(c,"status"),s(c,"fingerprint"),s(c,"analysis_error"),s(c,"metadata_json"),g(c,"created_at"),g(c,"updated_at"));}
    private static String s(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?"":c.getString(i);}private static long g(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?0:c.getLong(i);}
}
