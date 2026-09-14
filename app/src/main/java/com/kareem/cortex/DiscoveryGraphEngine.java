package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Builds evidence-backed situation-to-situation links without crossing sovereign spaces. */
public final class DiscoveryGraphEngine {
    private DiscoveryGraphEngine(){}

    public static void rebuildForSituation(SQLiteDatabase db,long sid){
        String space=space(db,sid);if("UNKNOWN".equals(space))return;
        LinkedHashMap<String,EntityRef> entities=new LinkedHashMap<>();
        Cursor c=db.rawQuery("SELECT e.kind,e.value,e.confidence,e.item_id FROM entities e JOIN discovery_situation_evidence se ON se.item_id=e.item_id WHERE se.situation_id=? ORDER BY e.confidence DESC LIMIT 64",
                new String[]{String.valueOf(sid)});
        while(c.moveToNext()){
            String kind=s(c,0),value=s(c,1);double conf=c.getDouble(2);long item=c.getLong(3);
            String norm=DiscoveryPolicy.norm(value);if(norm.length()<3||conf<.55)continue;
            String key=kind.toLowerCase(Locale.ROOT)+"|"+norm;
            if(!entities.containsKey(key))entities.put(key,new EntityRef(kind,norm,conf,item));
        }c.close();

        long now=System.currentTimeMillis();
        for(EntityRef e:entities.values()){
            String canonical=Fingerprint.text(space+"|"+e.kind+"|"+e.norm);
            ContentValues a=new ContentValues();a.put("space",space);a.put("canonical_key",canonical);a.put("alias_norm",e.norm);
            a.put("entity_kind",e.kind);a.put("confidence",e.confidence);a.put("source_item_id",e.itemId);a.put("created_at",now);
            db.insertWithOnConflict("discovery_entity_aliases",null,a,SQLiteDatabase.CONFLICT_IGNORE);

            Cursor other=db.rawQuery("SELECT DISTINCT se.situation_id FROM discovery_entity_aliases da JOIN discovery_situation_evidence se ON se.item_id=da.source_item_id WHERE da.space=? AND da.canonical_key=? AND se.situation_id<>? LIMIT 12",
                    new String[]{space,canonical,String.valueOf(sid)});
            while(other.moveToNext()){
                long target=other.getLong(0);
                long from=Math.min(sid,target),to=Math.max(sid,target);
                ContentValues l=new ContentValues();l.put("from_situation_id",from);l.put("to_situation_id",to);l.put("relation","shared_entity");
                l.put("reason",e.kind+": "+e.norm);l.put("confidence",Math.min(.95,e.confidence));l.put("updated_at",now);l.put("created_at",now);
                db.insertWithOnConflict("discovery_situation_links",null,l,SQLiteDatabase.CONFLICT_IGNORE);
            }other.close();
        }
    }

    public static ArrayList<Long> related(SQLiteDatabase db,long sid,int limit){
        ArrayList<Long> out=new ArrayList<>();
        Cursor c=db.rawQuery("SELECT CASE WHEN from_situation_id=? THEN to_situation_id ELSE from_situation_id END AS other FROM discovery_situation_links WHERE (from_situation_id=? OR to_situation_id=?) AND confidence>=.6 ORDER BY confidence DESC,updated_at DESC LIMIT ?",
                new String[]{String.valueOf(sid),String.valueOf(sid),String.valueOf(sid),String.valueOf(Math.max(1,limit))});
        while(c.moveToNext())out.add(c.getLong(0));c.close();return out;
    }

    private static String space(SQLiteDatabase db,long sid){Cursor c=db.rawQuery("SELECT d.space FROM discovery_annotations d JOIN discovery_situation_evidence se ON se.item_id=d.item_id WHERE se.situation_id=? LIMIT 1",new String[]{String.valueOf(sid)});String x=c.moveToFirst()?s(c,0):"UNKNOWN";c.close();return x;}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static final class EntityRef{final String kind,norm;final double confidence;final long itemId;EntityRef(String k,String n,double c,long i){kind=k;norm=n;confidence=c;itemId=i;}}
}
