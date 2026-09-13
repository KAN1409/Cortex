package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-time recovery for files imported before document intelligence existed. */
public final class DocumentIntelligenceMigration {
    private static final String PREF="document_intelligence_migration";
    private static final String KEY="v101_reindexed";
    private static final AtomicBoolean started=new AtomicBoolean(false);
    private static final ExecutorService work=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-document-migration");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private DocumentIntelligenceMigration(){}

    public static void runAfterLauncher(Context context){
        if(context==null||!started.compareAndSet(false,true))return;
        Context app=context.getApplicationContext();SharedPreferences p=app.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(p.getBoolean(KEY,false))return;
        work.execute(()->{
            VaultDb vault=null;
            try{
                vault=new VaultDb(app);SQLiteDatabase db=vault.getWritableDatabase();
                String supported="type='FILE' AND COALESCE(extracted_text,'')='' AND (lower(attachment_path) LIKE '%.pdf' OR lower(attachment_path) LIKE '%.xlsx' OR lower(attachment_path) LIKE '%.docx' OR lower(attachment_path) LIKE '%.pptx' OR lower(raw_text) LIKE '%.pdf' OR lower(raw_text) LIKE '%.xlsx' OR lower(raw_text) LIKE '%.docx' OR lower(raw_text) LIKE '%.pptx')";
                db.beginTransaction();
                try{
                    try{db.execSQL("DELETE FROM result_proposals WHERE source_item_id IN (SELECT id FROM knowledge_items WHERE "+supported+")");}catch(Throwable ignored){}
                    db.execSQL("UPDATE knowledge_items SET status='queued',analysis_error='',updated_at=? WHERE "+supported+" AND status IN ('analyzed','analysis_failed','failed_retryable')",new Object[]{System.currentTimeMillis()});
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                p.edit().putBoolean(KEY,true).apply();
            }catch(Throwable ignored){started.set(false);return;}
            finally{if(vault!=null)try{vault.close();}catch(Throwable ignored){}}
            AnalysisQueue.kick(app,null,null);
        });
    }
}
