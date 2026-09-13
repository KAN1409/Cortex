package com.kareem.cortex;

import android.database.Cursor;

/** Deterministic queue ordering: explicit user captures must not wait behind passive context. */
final class AnalysisQueuePriority {
    private AnalysisQueuePriority(){}

    static KnowledgeItem next(VaultDb db){
        Cursor c=null;
        try{
            c=db.getReadableDatabase().rawQuery(
                    "SELECT id FROM knowledge_items WHERE status='queued' ORDER BY " +
                    "CASE " +
                    "WHEN type='AUDIO' AND source IN ('manual_recording','audio_import') THEN 0 " +
                    "WHEN source IN ('manual','quick_capture','android_share','screen_understand') THEN 1 " +
                    "ELSE 2 END, created_at ASC LIMIT 1",null);
            if(!c.moveToFirst())return null;
            return db.getById(c.getLong(0));
        }finally{if(c!=null)c.close();}
    }
}
