package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Learns what the user wants Cortex to retain from different screenshot classes. */
public final class ScreenshotLearning {
    private ScreenshotLearning(){}

    public static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS screenshot_intents(item_id INTEGER NOT NULL,intent TEXT NOT NULL,selected INTEGER DEFAULT 1,created_at INTEGER NOT NULL,PRIMARY KEY(item_id,intent))");
        s.execSQL("CREATE TABLE IF NOT EXISTS screenshot_preferences(content_type TEXT NOT NULL,intent TEXT NOT NULL,selected_count INTEGER DEFAULT 0,updated_at INTEGER NOT NULL,PRIMARY KEY(content_type,intent))");
    }

    public static String contentType(VaultDb db,long itemId){
        Cursor c=db.getReadableDatabase().query("vision_fields",new String[]{"field_value"},"item_id=? AND field_key='Content type'",new String[]{String.valueOf(itemId)},null,null,"id DESC","1");
        String x=c.moveToFirst()?nz(c.getString(0)):"General screenshot";c.close();return x;
    }

    public static String[] options(String type){
        String t=nz(type).toLowerCase(Locale.US);
        if(t.contains("product"))return new String[]{"Price","Specs","Reviews","Where to buy","Compare later"};
        if(t.contains("receipt")||t.contains("invoice"))return new String[]{"Total","Items","Merchant","Date","Payment details"};
        if(t.contains("ai")||t.contains("prompt"))return new String[]{"Prompt","Result","Technique","Model/tool","Save as example"};
        if(t.contains("tutorial"))return new String[]{"Steps","Commands","Tools needed","Warnings","Save for later"};
        if(t.contains("recipe"))return new String[]{"Ingredients","Quantities","Steps","Nutrition","Source"};
        if(t.contains("web")||t.contains("document"))return new String[]{"Key facts","Steps","People/entities","Dates/tasks","Source/link"};
        return new String[]{"Text/OCR","People/entities","Dates/tasks","Topic/context","Save as reference"};
    }

    public static boolean[] suggested(VaultDb db,String type,String[] options){
        ensure(db);boolean[] out=new boolean[options.length];SQLiteDatabase s=db.getReadableDatabase();
        for(int i=0;i<options.length;i++){
            Cursor c=s.query("screenshot_preferences",new String[]{"selected_count"},"content_type=? AND intent=?",new String[]{nz(type),options[i]},null,null,null,"1");
            if(c.moveToFirst()&&c.getInt(0)>=2)out[i]=true;c.close();
        }
        return out;
    }

    public static void record(VaultDb db,long itemId,String type,String[] options,boolean[] checked){
        ensure(db);CoreBrainEngine.ensure(db);SQLiteDatabase s=db.getWritableDatabase();long now=System.currentTimeMillis();String ct=nz(type);
        HashSet<String> before=new HashSet<>(selected(db,itemId));HashSet<String> after=new HashSet<>();for(int i=0;i<options.length;i++)if(checked[i])after.add(options[i]);
        s.beginTransaction();try{
            s.delete("screenshot_intents","item_id=?",new String[]{String.valueOf(itemId)});
            s.delete("memory_facets","item_id=? AND facet_type='USER_PRIORITY'",new String[]{String.valueOf(itemId)});
            for(String intent:after){ContentValues v=new ContentValues();v.put("item_id",itemId);v.put("intent",intent);v.put("selected",1);v.put("created_at",now);s.insertWithOnConflict("screenshot_intents",null,v,SQLiteDatabase.CONFLICT_REPLACE);ContentValues f=new ContentValues();f.put("item_id",itemId);f.put("facet_type","USER_PRIORITY");f.put("facet_value",intent);f.put("normalized",LocalSemanticEmbedder.norm(intent));f.put("confidence",1.0);f.put("created_at",now);s.insert("memory_facets",null,f);}
            LinkedHashSet<String> touched=new LinkedHashSet<>();touched.addAll(before);touched.addAll(after);for(String intent:touched){int delta=(after.contains(intent)?1:0)-(before.contains(intent)?1:0);if(delta==0)continue;Cursor c=s.query("screenshot_preferences",new String[]{"selected_count"},"content_type=? AND intent=?",new String[]{ct,intent},null,null,null,"1");int count=c.moveToFirst()?c.getInt(0):0;c.close();count=Math.max(0,count+delta);ContentValues p=new ContentValues();p.put("content_type",ct);p.put("intent",intent);p.put("selected_count",count);p.put("updated_at",now);s.insertWithOnConflict("screenshot_preferences",null,p,SQLiteDatabase.CONFLICT_REPLACE);}
            s.setTransactionSuccessful();
        }finally{s.endTransaction();}
        // Rebuild only controlled, taught screenshot facets; raw OCR is never promoted automatically.
        try{CoreBrainEngine.afterAnalysis(db,itemId);}catch(Exception ignored){}
    }

    public static ArrayList<String> selected(VaultDb db,long itemId){
        ensure(db);ArrayList<String> out=new ArrayList<>();Cursor c=db.getReadableDatabase().query("screenshot_intents",new String[]{"intent"},"item_id=? AND selected=1",new String[]{String.valueOf(itemId)},null,null,"created_at ASC");while(c.moveToNext())out.add(c.getString(0));c.close();return out;
    }

    public static int taughtCount(VaultDb db){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT item_id) FROM screenshot_intents WHERE selected=1",null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    public static int learnedPreferenceCount(VaultDb db){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM screenshot_preferences WHERE selected_count>=2",null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    private static String nz(String s){return s==null?"":s;}
}
