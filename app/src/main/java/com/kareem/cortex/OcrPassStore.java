package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Stores every OCR pass so raw evidence survives even when the chosen final text changes. */
public final class OcrPassStore {
    public static final int PIPELINE_VERSION=5;
    private OcrPassStore(){}

    public static void ensure(VaultDb db){
        db.getWritableDatabase().execSQL("CREATE TABLE IF NOT EXISTS screenshot_ocr_passes (id INTEGER PRIMARY KEY AUTOINCREMENT,item_id INTEGER NOT NULL,pipeline_version INTEGER NOT NULL,pass_name TEXT NOT NULL,engine TEXT NOT NULL,text TEXT NOT NULL,quality REAL NOT NULL,selected INTEGER NOT NULL DEFAULT 0,reason TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)");
        db.getWritableDatabase().execSQL("CREATE INDEX IF NOT EXISTS idx_ocr_pass_item ON screenshot_ocr_passes(item_id,pipeline_version)");
    }

    public static void replace(VaultDb db,long itemId,List<Pass> passes){
        ensure(db);SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();try{
            s.delete("screenshot_ocr_passes","item_id=? AND pipeline_version=?",new String[]{String.valueOf(itemId),String.valueOf(PIPELINE_VERSION)});
            long now=System.currentTimeMillis();for(Pass p:passes){ContentValues v=new ContentValues();v.put("item_id",itemId);v.put("pipeline_version",PIPELINE_VERSION);v.put("pass_name",p.name);v.put("engine",p.engine);v.put("text",p.text==null?"":p.text);v.put("quality",p.quality);v.put("selected",p.selected?1:0);v.put("reason",p.reason==null?"":p.reason);v.put("created_at",now);s.insert("screenshot_ocr_passes",null,v);}s.setTransactionSuccessful();
        }finally{s.endTransaction();}
    }

    public static boolean processed(VaultDb db,long itemId){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM screenshot_ocr_passes WHERE item_id=? AND pipeline_version=? LIMIT 1",new String[]{String.valueOf(itemId),String.valueOf(PIPELINE_VERSION)});boolean ok=c.moveToFirst();c.close();return ok;}
    public static int processedCount(VaultDb db){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT item_id) FROM screenshot_ocr_passes WHERE pipeline_version=?",new String[]{String.valueOf(PIPELINE_VERSION)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}

    public static final class Pass {public final String name,engine,text,reason;public final double quality;public final boolean selected;public Pass(String n,String e,String t,double q,boolean s,String r){name=n;engine=e;text=t;quality=q;selected=s;reason=r;}}
}
