package com.kareem.cortex;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/** One-time v43 cleanup: screenshots remain evidence/searchable, but raw OCR no longer pollutes the Brain graph. */
public final class V43Maintenance {
    private static final String PREF="cortex_v43_maintenance",KEY="done";
    private V43Maintenance(){}

    public static void run(Context c,VaultDb db){
        if(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(KEY,false)){ScreenshotWorkScheduler.kick(c);return;}
        CoreBrainEngine.ensure(db);ScreenshotLearning.ensure(db);SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();try{
            String shots="SELECT id FROM knowledge_items WHERE source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE')";
            s.execSQL("DELETE FROM context_pack_items WHERE item_id IN ("+shots+")");
            s.execSQL("DELETE FROM memory_facets WHERE item_id IN ("+shots+") AND facet_type<>'USER_PRIORITY'");
            s.execSQL("DELETE FROM relations WHERE relation IN ('related','same_person','same_project','continuation') AND (from_item_id IN ("+shots+") OR to_item_id IN ("+shots+"))");
            s.execSQL("DELETE FROM context_packs WHERE id NOT IN (SELECT DISTINCT pack_id FROM context_pack_items)");
            // If the process died while OCR was marked analyzing, make that work recoverable immediately on upgrade.
            android.content.ContentValues v=new android.content.ContentValues();v.put("status","queued");v.put("analysis_error","Recovered for durable v43 screenshot worker");v.put("updated_at",System.currentTimeMillis());s.update("knowledge_items",v,"status='analyzing' AND source='screenshot-folder' AND type IN ('SCREENSHOT','IMAGE')",null);
            s.setTransactionSuccessful();
        }finally{s.endTransaction();}
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean(KEY,true).apply();ScreenshotWorkScheduler.kick(c);
    }
}
