package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Small write API over the unified cognitive schema. */
public final class CognitiveStore {
    private CognitiveStore(){}

    public static void ensure(VaultDb db){CognitiveSchema.ensure(db.getWritableDatabase());}

    public static void link(VaultDb db,String fromType,long fromId,String toType,long toId,String relation,double confidence,String metadataJson){linkChecked(db,fromType,fromId,toType,toId,relation,confidence,metadataJson);}
    public static boolean linkChecked(VaultDb db,String fromType,long fromId,String toType,long toId,String relation,double confidence,String metadataJson){
        if(fromId<=0||toId<=0||empty(fromType)||empty(toType)||empty(relation))return false;
        ensure(db);ContentValues v=new ContentValues();v.put("from_type",fromType);v.put("from_id",fromId);v.put("to_type",toType);v.put("to_id",toId);v.put("relation",relation);v.put("confidence",confidence);v.put("metadata_json",n(metadataJson));v.put("created_at",System.currentTimeMillis());
        long id=db.getWritableDatabase().insertWithOnConflict("source_links",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id>0)return true;
        Cursor c=db.getReadableDatabase().query("source_links",new String[]{"id"},"from_type=? AND from_id=? AND to_type=? AND to_id=? AND relation=?",new String[]{fromType,String.valueOf(fromId),toType,String.valueOf(toId),relation},null,null,null,"1");boolean exists=c.moveToFirst();c.close();return exists;
    }

    public static long addDerived(VaultDb db,String kind,String title,String body,String state,double confidence,int importance,String fingerprint,String metadataJson){
        ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("kind",n(kind).toUpperCase());v.put("title",empty(title)?friendly(kind):title.trim());v.put("body",n(body));v.put("state",empty(state)?"open":state);v.put("confidence",confidence);v.put("importance",importance);v.put("fingerprint",n(fingerprint));v.put("metadata_json",n(metadataJson));v.put("created_at",now);v.put("updated_at",now);
        long id=db.getWritableDatabase().insertWithOnConflict("derived_items",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id>0)return id;
        if(!empty(fingerprint)){Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"id"},"fingerprint=?",new String[]{fingerprint},null,null,null,"1");long existing=c.moveToFirst()?c.getLong(0):0;c.close();return existing;}
        return id;
    }

    /** Hot routing fields stay typed/indexed; metadata_json remains flexible provenance. */
    public static void setDerivedRouting(VaultDb db,long derivedId,String sourceKey,long threadId,long anchorSignalId,String candidateKind){setDerivedRoutingChecked(db,derivedId,sourceKey,threadId,anchorSignalId,candidateKind,"");}
    public static boolean setDerivedRoutingChecked(VaultDb db,long derivedId,String sourceKey,long threadId,long anchorSignalId,String candidateKind,String semanticKey){
        if(db==null||derivedId<=0)return false;ensure(db);ContentValues v=new ContentValues();v.put("source_key",n(sourceKey));v.put("thread_id",Math.max(0,threadId));v.put("anchor_signal_id",Math.max(0,anchorSignalId));v.put("candidate_kind",n(candidateKind).toUpperCase());if(!empty(semanticKey))v.put("semantic_key",semanticKey);v.put("updated_at",System.currentTimeMillis());return db.getWritableDatabase().update("derived_items",v,"id=?",new String[]{String.valueOf(derivedId)})>0;
    }

    public static void feedback(VaultDb db,String targetType,long targetId,String eventType,String valueJson,String policyVersion){
        if(targetId<=0||empty(targetType)||empty(eventType))return;ensure(db);String source="",candidate="";
        if("derived".equalsIgnoreCase(targetType)){
            Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"source_key","candidate_kind","kind"},"id=?",new String[]{String.valueOf(targetId)},null,null,null,"1");
            if(c.moveToFirst()){source=n(c.getString(0));candidate=n(c.getString(1));if(candidate.isEmpty()&&"REVIEW".equalsIgnoreCase(n(c.getString(2))))candidate="UNKNOWN";}c.close();
        }
        ContentValues v=new ContentValues();v.put("target_type",targetType);v.put("target_id",targetId);v.put("event_type",eventType);v.put("value_json",n(valueJson));v.put("policy_version",n(policyVersion));v.put("source_key",source);v.put("candidate_kind",candidate.toUpperCase());v.put("scope_key",source+"|"+candidate.toUpperCase());v.put("created_at",System.currentTimeMillis());db.getWritableDatabase().insert("feedback_events",null,v);
    }

    public static String schemaRevision(VaultDb db){ensure(db);Cursor c=db.getReadableDatabase().query("schema_meta",new String[]{"value"},"key='cognitive_schema'",null,null,null,null,"1");String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}

    private static String friendly(String kind){String x=n(kind).toLowerCase().replace('_',' ');return x.isEmpty()?"Derived intelligence":Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String n(String s){return s==null?"":s;}
}
