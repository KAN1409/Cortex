package com.kareem.cortex;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.*;

/** One-time v41 repair pass for screenshot ingestion/action pollution found by the debug export. */
public final class V41Maintenance {
    private static final String PREF="cortex_migrations", KEY="v41_complete";
    private V41Maintenance(){}

    public static void run(Context context,VaultDb db){
        Context app=context.getApplicationContext();
        if(app.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(KEY,false))return;
        new Thread(()->{
            try{
                FeatureStore.ensure(db);CoreBrainEngine.ensure(db);TemporalResolver.ensure(db);ScreenshotLearning.ensure(db);
                SQLiteDatabase s=db.getWritableDatabase();
                s.beginTransaction();
                try{
                    // OCR from a folder screenshot is evidence, not user intent. Remove legacy auto-actions
                    // and their temporal rows while preserving actions from voice/text memories.
                    s.execSQL("DELETE FROM action_temporal WHERE item_id IN (SELECT id FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE'))");
                    s.execSQL("DELETE FROM actions WHERE item_id IN (SELECT id FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE'))");

                    // Remove automatically-derived graph noise from screenshots, but preserve explicit USER_PRIORITY
                    // facets created by Teach Cortex.
                    s.execSQL("DELETE FROM context_pack_items WHERE item_id IN (SELECT id FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE'))");
                    s.execSQL("DELETE FROM memory_facets WHERE item_id IN (SELECT id FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE')) AND facet_type<>'USER_PRIORITY'");
                    s.execSQL("DELETE FROM relations WHERE relation IN ('related','same_person','same_project','continuation') AND (from_item_id IN (SELECT id FROM knowledge_items WHERE source='screenshot-folder') OR to_item_id IN (SELECT id FROM knowledge_items WHERE source='screenshot-folder'))");
                    s.execSQL("DELETE FROM context_packs WHERE id NOT IN (SELECT DISTINCT pack_id FROM context_pack_items)");

                    // Auto-imported screenshots stay quiet/reference by default. Preserve anything the user manually
                    // bucketed or pinned.
                    long now=System.currentTimeMillis();
                    s.execSQL("UPDATE smart_inbox SET bucket='Reference',reviewed=1,attention_dismissed=1,updated_at="+now+" WHERE item_id IN (SELECT id FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE')) AND COALESCE(manual_bucket,0)=0 AND COALESCE(pinned,0)=0");
                    s.setTransactionSuccessful();
                }finally{s.endTransaction();}

                // Repair files that v38/v39 accidentally deleted during duplicate scans, then re-run OCR using
                // the now bundled offline Arabic model. This only touches rows whose source URI still exists.
                int repaired=ScreenshotIngestor.repairMissingAttachments(app,db,2000);
                int queued=0;
                for(KnowledgeItem k:db.lexicalSearch("",3000)){
                    if(!"screenshot-folder".equals(k.source)||!("SCREENSHOT".equals(k.type)||"IMAGE".equals(k.type)))continue;
                    File f=k.attachmentPath==null?null:new File(k.attachmentPath);
                    if(f!=null&&f.exists()&&f.length()>0){
                        db.retry(k.id);queued++;
                    }
                }
                if(queued>0)AnalysisQueue.kick(app,db,null);
                app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean(KEY,true).putInt("v41_repaired",repaired).putInt("v41_requeued",queued).apply();
            }catch(Exception ignored){
                // Leave migration flag false so the next app start retries safely.
            }
        },"cortex-v41-maintenance").start();
    }
}
