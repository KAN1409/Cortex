package com.kareem.cortex;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

public final class FeatureStore {
    public static class InboxEntry { public final KnowledgeItem item;public final String bucket;public final boolean pinned;InboxEntry(KnowledgeItem i,String b,boolean p){item=i;bucket=b;pinned=p;} }
    private FeatureStore(){}

    public static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS smart_inbox(item_id INTEGER PRIMARY KEY,bucket TEXT NOT NULL,reviewed INTEGER DEFAULT 0,pinned INTEGER DEFAULT 0,updated_at INTEGER NOT NULL)");
        s.execSQL("CREATE TABLE IF NOT EXISTS correction_rules(id INTEGER PRIMARY KEY AUTOINCREMENT,item_id INTEGER,field TEXT,original_text TEXT,corrected_text TEXT,apply_future INTEGER DEFAULT 1,created_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_correction_field ON correction_rules(field,created_at DESC)");
        s.execSQL("CREATE TABLE IF NOT EXISTS integration_log(id INTEGER PRIMARY KEY AUTOINCREMENT,source TEXT,status TEXT,detail TEXT,created_at INTEGER NOT NULL)");
    }

    public static ArrayList<InboxEntry> inbox(VaultDb db,int limit){
        ensure(db);ArrayList<InboxEntry> out=new ArrayList<>();
        ArrayList<KnowledgeItem> items=db.lexicalSearch("",Math.max(limit*3,120));
        SQLiteDatabase s=db.getWritableDatabase();long now=System.currentTimeMillis();
        for(KnowledgeItem k:items){
            Cursor c=s.query("smart_inbox",new String[]{"bucket","reviewed","pinned"},"item_id=?",new String[]{String.valueOf(k.id)},null,null,null,"1");
            String bucket;boolean reviewed=false,pinned=false;
            if(c.moveToFirst()){bucket=c.getString(0);reviewed=c.getInt(1)!=0;pinned=c.getInt(2)!=0;}else{
                bucket=autoBucket(db,k);ContentValues v=new ContentValues();v.put("item_id",k.id);v.put("bucket",bucket);v.put("reviewed",0);v.put("pinned",0);v.put("updated_at",now);s.insert("smart_inbox",null,v);
            }c.close();
            if(!reviewed||pinned){out.add(new InboxEntry(k,bucket,pinned));if(out.size()>=limit)break;}
        }
        return out;
    }

    public static String autoBucket(VaultDb db,KnowledgeItem k){
        if("failed_retryable".equals(k.status)||"analysis_failed".equals(k.status))return "Needs attention";
        if(!db.actions(k.id).isEmpty())return "Action";
        String cat=k.category==null?"":k.category.toLowerCase(Locale.US),tags=k.tags==null?"":k.tags.toLowerCase(Locale.US),type=k.type==null?"":k.type;
        if(cat.contains("project")||tags.contains("project"))return "Project";
        if(cat.contains("people")||tags.contains("person")||tags.contains("contact"))return "Person";
        if(tags.contains("decision")||text(k).contains("decided")||text(k).contains("قرر"))return "Decision";
        if(tags.contains("waiting")||text(k).contains("waiting")||text(k).contains("مستني"))return "Waiting";
        if("AI_PROMPT".equals(type)||"AI_RESULT".equals(type))return "Reference";
        return "Reference";
    }

    private static String text(KnowledgeItem k){return ((k.title==null?"":k.title)+" "+(k.summary==null?"":k.summary)+" "+(k.extractedText==null?"":k.extractedText)).toLowerCase(Locale.US);}

    public static void review(VaultDb db,long itemId,boolean reviewed){ensure(db);ContentValues v=new ContentValues();v.put("reviewed",reviewed?1:0);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("smart_inbox",v,"item_id=?",new String[]{String.valueOf(itemId)});}
    public static void pin(VaultDb db,long itemId,boolean pinned){ensure(db);ContentValues v=new ContentValues();v.put("pinned",pinned?1:0);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("smart_inbox",v,"item_id=?",new String[]{String.valueOf(itemId)});}

    public static void saveCorrection(VaultDb db,long itemId,String field,String original,String corrected,boolean future){
        ensure(db);ContentValues v=new ContentValues();v.put("item_id",itemId);v.put("field",field);v.put("original_text",original==null?"":original);v.put("corrected_text",corrected==null?"":corrected);v.put("apply_future",future?1:0);v.put("created_at",System.currentTimeMillis());db.getWritableDatabase().insert("correction_rules",null,v);
    }

    public static ArrayList<String[]> futureCorrections(VaultDb db,String field){
        ensure(db);ArrayList<String[]> out=new ArrayList<>();Cursor c=db.getReadableDatabase().query("correction_rules",new String[]{"original_text","corrected_text"},"field=? AND apply_future=1",new String[]{field},null,null,"created_at DESC","100");
        while(c.moveToNext()){String a=c.getString(0),b=c.getString(1);if(a!=null&&b!=null&&!a.trim().isEmpty()&&!a.equals(b))out.add(new String[]{a,b});}c.close();return out;
    }

    public static void logIntegration(VaultDb db,String source,String status,String detail){ensure(db);ContentValues v=new ContentValues();v.put("source",source);v.put("status",status);v.put("detail",detail);v.put("created_at",System.currentTimeMillis());db.getWritableDatabase().insert("integration_log",null,v);}
}
