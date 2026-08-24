package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Background enhancement pass. Reprocesses already-indexed screenshots with 3 visual variants while charging. */
public class ScreenshotDeepOcrWorker extends Worker {
    private static final int BATCH=3;
    public ScreenshotDeepOcrWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){Context ctx=getApplicationContext();VaultDb db=new VaultDb(ctx);OcrPassStore.ensure(db);int done=0;try{
        while(done<BATCH&&!isStopped()){
            KnowledgeItem k=next(db);if(k==null)break;
            try{MultiPassOcrAnalyzer.Result r=MultiPassOcrAnalyzer.analyze(ctx,k);OcrPassStore.replace(db,k.id,r.passes);db.applyAnalysis(k.id,r.analysis);try{CoreBrainEngine.afterAnalysis(db,k.id);}catch(Throwable ignored){}done++;}
            catch(Throwable e){String msg=e.getClass().getSimpleName()+": "+(e.getMessage()==null?"":e.getMessage());try{OcrPassStore.replace(db,k.id,java.util.Collections.singletonList(new OcrPassStore.Pass("deep_error","multipass_v6","",0,false,msg)));}catch(Throwable ignored){}done++;}
        }
        if(hasMore(db))ScreenshotDeepOcrScheduler.continueChain(ctx);return Result.success();
    }catch(Throwable e){return Result.retry();}finally{try{db.close();}catch(Throwable ignored){}}}

    private static KnowledgeItem next(VaultDb db){Cursor c=db.getReadableDatabase().rawQuery("SELECT k.* FROM knowledge_items k WHERE k.source='screenshot-folder' AND k.type IN ('SCREENSHOT','IMAGE') AND k.status='analyzed' AND NOT EXISTS (SELECT 1 FROM screenshot_ocr_passes p WHERE p.item_id=k.id AND p.pipeline_version=?) ORDER BY k.created_at DESC LIMIT 1",new String[]{String.valueOf(OcrPassStore.PIPELINE_VERSION)});KnowledgeItem k=null;if(c.moveToFirst())k=from(c);c.close();return k;}
    private static boolean hasMore(VaultDb db){Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM knowledge_items k WHERE k.source='screenshot-folder' AND k.status='analyzed' AND NOT EXISTS (SELECT 1 FROM screenshot_ocr_passes p WHERE p.item_id=k.id AND p.pipeline_version=?) LIMIT 1",new String[]{String.valueOf(OcrPassStore.PIPELINE_VERSION)});boolean b=c.moveToFirst();c.close();return b;}
    private static KnowledgeItem from(Cursor c){return new KnowledgeItem(g(c,"id"),s(c,"type"),s(c,"source"),s(c,"title"),s(c,"raw_text"),s(c,"extracted_text"),s(c,"summary"),s(c,"category"),s(c,"tags"),s(c,"attachment_path"),s(c,"status"),s(c,"fingerprint"),s(c,"analysis_error"),s(c,"metadata_json"),g(c,"created_at"),g(c,"updated_at"));}
    private static String s(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?"":c.getString(i);}private static long g(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?0:c.getLong(i);}
}
