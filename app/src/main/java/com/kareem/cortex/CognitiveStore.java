package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Small write API over the unified cognitive schema. */
public final class CognitiveStore {
    private CognitiveStore(){}

    public static void ensure(VaultDb db){CognitiveSchema.ensure(db.getWritableDatabase());}

    public static void link(VaultDb db,String fromType,long fromId,String toType,long toId,String relation,double confidence,String metadataJson){
        if(fromId<=0||toId<=0||empty(fromType)||empty(toType)||empty(relation))return;
        ensure(db);ContentValues v=new ContentValues();v.put("from_type",fromType);v.put("from_id",fromId);v.put("to_type",toType);v.put("to_id",toId);v.put("relation",relation);v.put("confidence",confidence);v.put("metadata_json",n(metadataJson));v.put("created_at",System.currentTimeMillis());
        db.getWritableDatabase().insertWithOnConflict("source_links",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    public static long addDerived(VaultDb db,String kind,String title,String body,String state,double confidence,int importance,String fingerprint,String metadataJson){
        ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("kind",n(kind).toUpperCase());v.put("title",empty(title)?friendly(kind):title.trim());v.put("body",n(body));v.put("state",empty(state)?"open":state);v.put("confidence",confidence);v.put("importance",importance);v.put("fingerprint",n(fingerprint));v.put("metadata_json",n(metadataJson));v.put("created_at",now);v.put("updated_at",now);
        long id=db.getWritableDatabase().insertWithOnConflict("derived_items",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id>0)return id;
        if(!empty(fingerprint)){Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"id"},"fingerprint=?",new String[]{fingerprint},null,null,null,"1");long existing=c.moveToFirst()?c.getLong(0):0;c.close();return existing;}
        return id;
    }

    public static void feedback(VaultDb db,String targetType,long targetId,String eventType,String valueJson,String policyVersion){
        if(targetId<=0||empty(targetType)||empty(eventType))return;ensure(db);ContentValues v=new ContentValues();v.put("target_type",targetType);v.put("target_id",targetId);v.put("event_type",eventType);v.put("value_json",n(valueJson));v.put("policy_version",n(policyVersion));v.put("created_at",System.currentTimeMillis());db.getWritableDatabase().insert("feedback_events",null,v);
    }

    public static String schemaRevision(VaultDb db){
        ensure(db);Cursor c=db.getReadableDatabase().query("schema_meta",new String[]{"value"},"key='cognitive_schema'",null,null,null,null,"1");String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;
    }

    private static String friendly(String kind){String x=n(kind).toLowerCase().replace('_',' ');return x.isEmpty()?"Derived intelligence":Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String n(String s){return s==null?"":s;}
}
