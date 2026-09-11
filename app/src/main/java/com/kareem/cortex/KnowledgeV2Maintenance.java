package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Locale;

/**
 * One-time preparation and recurring hygiene for the completion Knowledge V2 pipeline.
 * Raw evidence is never deleted. All tables touched here are derived/rebuildable projections.
 */
public final class KnowledgeV2Maintenance {
    private static final String META_PREPARED="completion_pipeline_prepared";
    private KnowledgeV2Maintenance(){}

    public static void prepare(SQLiteDatabase db){
        if(db==null)return;
        KnowledgeV2Schema.ensure(db);
        String prepared=meta(db,META_PREPARED);
        if(String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION).equals(prepared)){
            ensureProcessingCoverage(db);
            canonicalizeCategories(db);
            return;
        }

        long now=System.currentTimeMillis();
        db.beginTransaction();
        try{
            // Preserve evidence. Rebuild only derived Knowledge V2 state so quality fixes can
            // deterministically repair historical screenshots without duplicating old mistakes.
            db.delete("kv2_fact_evidence",null,null);
            db.delete("kv2_event_evidence",null,null);
            db.delete("kv2_entity_mentions",null,null);
            db.delete("kv2_edges",null,null);
            db.delete("kv2_category_memberships",null,null);
            db.delete("kv2_facts",null,null);
            db.delete("kv2_events",null,null);
            db.delete("kv2_understanding",null,null);
            db.delete("kv2_categories",null,null);

            // Knowledge V2 aliases are derived. Removing them avoids a bad old alias forcing a
            // newly-cleaned mention back onto the wrong identity. Identity nodes themselves remain
            // reversible history and are filtered separately by EntityQualityMaintenance.
            try{db.delete("entity_aliases","source=?",new String[]{"knowledge_v2"});}catch(Throwable ignored){}

            ensureProcessingCoverage(db);
            putMeta(db,META_PREPARED,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION),now);
            db.setTransactionSuccessful();
        }finally{
            db.endTransaction();
        }
    }

    /** Every screenshot evidence gets exactly one visible terminal/working state for this version. */
    public static void ensureProcessingCoverage(SQLiteDatabase db){
        long now=System.currentTimeMillis();
        db.execSQL(
                "INSERT OR IGNORE INTO kv2_processing(evidence_id,stage,pipeline_version,state,attempt_count,last_error,updated_at) "+
                "SELECT id,?,?,CASE "+
                "WHEN knowledge_eligible=0 OR self_reference_score>=0.72 THEN 'BLOCKED' "+
                "WHEN TRIM(COALESCE(raw_text,''))='' THEN 'SKIPPED' "+
                "ELSE 'PENDING' END,0,CASE "+
                "WHEN knowledge_eligible=0 OR self_reference_score>=0.72 THEN 'Blocked by provenance/self-reference policy' "+
                "WHEN TRIM(COALESCE(raw_text,''))='' THEN 'No OCR text to extract' ELSE NULL END,? "+
                "FROM kv2_evidence",
                new Object[]{KnowledgeV2Store.STAGE_EXTRACTION,KnowledgeV2Schema.PIPELINE_VERSION,now}
        );
    }

    public static String canonicalCategory(String raw){
        String x=n(raw).replaceAll("\\s+"," ").trim();
        if(x.isEmpty())return "";
        String k=x.toLowerCase(Locale.ROOT).replace('_',' ').replaceAll("\\s+"," ").trim();
        if(k.equals("url")||k.equals("urls")||k.equals("link")||k.equals("links")||
                k.equals("links & research")||k.equals("links and research")||
                k.equals("links & references")||k.equals("links and references")||
                k.equals("research link")||k.equals("research links"))return "Links & references";
        if(k.equals("money")||k.equals("money & purchases")||k.equals("money and purchases")||
                k.equals("purchase")||k.equals("purchases")||k.equals("shopping")||k.equals("price")||k.equals("prices"))return "Money & purchases";
        if(k.equals("action")||k.equals("actions")||k.equals("commitment")||k.equals("commitments")||
                k.equals("actions & commitments")||k.equals("actions and commitments")||k.equals("todo")||k.equals("to do"))return "Actions & commitments";
        if(k.equals("date")||k.equals("dates")||k.equals("deadline")||k.equals("deadlines")||
                k.equals("dates & deadlines")||k.equals("dates and deadlines"))return "Dates & deadlines";
        if(k.equals("note")||k.equals("notes")||k.equals("memo")||k.equals("memos")||k.equals("data"))return "Notes";
        if(k.equals("contact")||k.equals("contacts")||k.equals("phone")||k.equals("email")||k.equals("e-mail"))return "Contacts";
        if(k.equals("code")||k.equals("commands")||k.equals("code & commands")||k.equals("code and commands")||k.equals("technical"))return "Code & commands";
        if(k.equals("project")||k.equals("projects"))return "Projects";
        if(k.equals("person")||k.equals("people"))return "People";
        if(k.equals("product")||k.equals("products"))return "Products";
        return titleCaseCompact(x);
    }

    public static void canonicalizeCategories(SQLiteDatabase db){
        Cursor c=db.rawQuery("SELECT id,canonical_name FROM kv2_categories",null);
        while(c.moveToNext()){
            long id=c.getLong(0);String old=n(c.getString(1));String canonical=canonicalCategory(old);
            if(canonical.isEmpty()||canonical.equals(old))continue;
            long target=findCategory(db,canonical);
            if(target>0&&target!=id){
                db.execSQL("INSERT OR REPLACE INTO kv2_category_memberships(knowledge_type,knowledge_id,category_id,score,reason,model_version,created_at) "+
                                "SELECT knowledge_type,knowledge_id,?,score,reason,model_version,created_at FROM kv2_category_memberships WHERE category_id=?",
                        new Object[]{target,id});
                db.delete("kv2_category_memberships","category_id=?",new String[]{String.valueOf(id)});
                ContentValues hidden=new ContentValues();hidden.put("state","merged");db.update("kv2_categories",hidden,"id=?",new String[]{String.valueOf(id)});
            }else{
                ContentValues v=new ContentValues();v.put("canonical_name",canonical);db.update("kv2_categories",v,"id=?",new String[]{String.valueOf(id)});
            }
        }
        c.close();
    }

    private static long findCategory(SQLiteDatabase db,String name){
        Cursor c=db.rawQuery("SELECT id FROM kv2_categories WHERE lower(canonical_name)=lower(?) AND state<>'merged' ORDER BY id LIMIT 1",new String[]{name});
        long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;
    }

    private static String titleCaseCompact(String s){
        if(s.isEmpty())return s;
        // Preserve Arabic and mixed-case brands; only normalize all-lowercase Latin labels.
        boolean latin=s.matches("[A-Za-z0-9 &/+-]+");
        if(!latin||!s.equals(s.toLowerCase(Locale.ROOT)))return s;
        StringBuilder out=new StringBuilder();
        for(String p:s.split(" ")){
            if(p.isEmpty())continue;
            if(out.length()>0)out.append(' ');
            if("&".equals(p))out.append('&');
            else out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return out.toString();
    }

    private static String meta(SQLiteDatabase db,String key){
        Cursor c=db.rawQuery("SELECT value FROM kv2_meta WHERE key=? LIMIT 1",new String[]{key});
        String v=c.moveToFirst()?n(c.getString(0)):"";c.close();return v;
    }
    private static void putMeta(SQLiteDatabase db,String key,String value,long now){
        ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);v.put("updated_at",now);db.insertWithOnConflict("kv2_meta",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    private static String n(String s){return s==null?"":s.trim();}
}
