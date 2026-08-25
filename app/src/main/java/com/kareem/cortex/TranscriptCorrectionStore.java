package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Durable manual transcript override for captured audio.
 * The ASR/original text is preserved as correction history while the corrected text becomes the
 * effective evidence used by Cortex. A small trigger keeps the manual override authoritative if a
 * later analysis pass writes extracted_text again.
 */
public final class TranscriptCorrectionStore {
    private TranscriptCorrectionStore(){}

    private static void ensure(VaultDb db){
        if(db==null)return;
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS item_text_corrections("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "item_id INTEGER NOT NULL,"+
                "field TEXT NOT NULL DEFAULT 'transcript',"+
                "original_text TEXT NOT NULL DEFAULT '',"+
                "corrected_text TEXT NOT NULL DEFAULT '',"+
                "source TEXT NOT NULL DEFAULT 'manual_result_edit',"+
                "created_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_text_corrections_item ON item_text_corrections(item_id,id DESC)");
        s.execSQL("CREATE TRIGGER IF NOT EXISTS keep_manual_transcript_correction " +
                "AFTER UPDATE OF extracted_text ON knowledge_items " +
                "WHEN EXISTS(SELECT 1 FROM item_text_corrections c WHERE c.item_id=NEW.id AND c.field='transcript') " +
                "AND COALESCE(NEW.extracted_text,'') <> COALESCE((SELECT corrected_text FROM item_text_corrections c WHERE c.item_id=NEW.id AND c.field='transcript' ORDER BY c.id DESC LIMIT 1),'') " +
                "BEGIN UPDATE knowledge_items SET extracted_text=(SELECT corrected_text FROM item_text_corrections c WHERE c.item_id=NEW.id AND c.field='transcript' ORDER BY c.id DESC LIMIT 1) WHERE id=NEW.id; END");
    }

    public static String effectiveText(VaultDb db,KnowledgeItem item){
        if(item==null)return "";
        try{
            ensure(db);
            Cursor c=db.getReadableDatabase().query("item_text_corrections",new String[]{"corrected_text"},"item_id=? AND field='transcript'",new String[]{String.valueOf(item.id)},null,null,"id DESC","1");
            String corrected=c.moveToFirst()?safe(c.getString(0)):"";c.close();
            if(!corrected.trim().isEmpty())return corrected;
        }catch(Throwable ignored){}
        if(item.extractedText!=null&&!item.extractedText.trim().isEmpty())return item.extractedText;
        return safe(item.rawText);
    }

    public static boolean hasCorrection(VaultDb db,long itemId){
        if(db==null||itemId<=0)return false;
        try{ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM item_text_corrections WHERE item_id=? AND field='transcript' LIMIT 1",new String[]{String.valueOf(itemId)});boolean yes=c.moveToFirst();c.close();return yes;}catch(Throwable ignored){return false;}
    }

    public static boolean save(VaultDb db,KnowledgeItem item,String corrected){
        if(db==null||item==null||item.id<=0)return false;
        String clean=safe(corrected).trim();if(clean.isEmpty())return false;
        ensure(db);String before=effectiveText(db,item).trim();if(clean.equals(before))return true;
        SQLiteDatabase s=db.getWritableDatabase();long now=System.currentTimeMillis();s.beginTransaction();
        try{
            ContentValues h=new ContentValues();h.put("item_id",item.id);h.put("field","transcript");h.put("original_text",before);h.put("corrected_text",clean);h.put("source","manual_result_edit");h.put("created_at",now);s.insertOrThrow("item_text_corrections",null,h);
            ContentValues v=new ContentValues();v.put("extracted_text",clean);v.put("updated_at",now);s.update("knowledge_items",v,"id=?",new String[]{String.valueOf(item.id)});
            s.setTransactionSuccessful();
        }finally{s.endTransaction();}
        try{ResultProposalEngine.invalidateSource(db,item.id);}catch(Throwable ignored){}
        try{SemanticIndex.indexItem(db,item.id);}catch(Throwable ignored){}
        return true;
    }

    private static String safe(String s){return s==null?"":s;}
}
