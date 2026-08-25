package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Lightweight local learning from explicit Context corrections.
 * Learned fingerprints can only add a small resolver boost; they never override evidence/boundaries.
 */
public final class ContextFingerprintLearner {
    private static final double MAX_BOOST=.08;
    private ContextFingerprintLearner(){}

    public static void reinforceSelection(VaultDb db,ContextStateStore.ContextState chosen,ContextStateStore.ContextState previous){
        if(db==null||chosen==null)return;ContextSchema.ensure(db);learn(db,chosen.id,"stable_key",chosen.stableKey,+.14,true);String prefix=prefix(chosen.stableKey);if(!prefix.isEmpty())learn(db,chosen.id,"context_kind",prefix,+.06,true);
        if(previous!=null&&previous.id!=chosen.id){learn(db,previous.id,"stable_key",previous.stableKey,-.04,false);String p=prefix(previous.stableKey);if(!p.isEmpty())learn(db,previous.id,"context_kind",p,-.02,false);}
    }

    public static void reinforceResume(VaultDb db,ContextStateStore.ContextState chosen){if(db==null||chosen==null)return;ContextSchema.ensure(db);learn(db,chosen.id,"stable_key",chosen.stableKey,+.08,true);}

    /** Small exact-fingerprint boost used after ordinary evidence scoring. */
    public static double boost(VaultDb db,String stableKey){
        if(db==null||stableKey==null||stableKey.trim().isEmpty())return 0;ContextSchema.ensure(db);double total=score(db,"stable_key",stableKey.trim());String p=prefix(stableKey);if(!p.isEmpty())total+=.35*score(db,"context_kind",p);return Math.max(-MAX_BOOST,Math.min(MAX_BOOST,total));
    }

    private static void learn(VaultDb db,long contextId,String type,String key,double delta,boolean positive){if(contextId<=0||key==null||key.trim().isEmpty())return;SQLiteDatabase s=db.getWritableDatabase();long now=System.currentTimeMillis();Cursor c=s.rawQuery("SELECT id,weight,positive_count,negative_count FROM context_fingerprint_features WHERE context_id=? AND feature_type=? AND feature_key=? LIMIT 1",new String[]{String.valueOf(contextId),type,key});long id=0;double weight=0;int pos=0,neg=0;if(c.moveToFirst()){id=c.getLong(0);weight=c.getDouble(1);pos=c.getInt(2);neg=c.getInt(3);}c.close();weight=Math.max(-.35,Math.min(.35,weight+delta));ContentValues v=new ContentValues();v.put("context_id",contextId);v.put("feature_type",type);v.put("feature_key",key);v.put("weight",weight);v.put("positive_count",pos+(positive?1:0));v.put("negative_count",neg+(positive?0:1));v.put("updated_at",now);if(id>0)s.update("context_fingerprint_features",v,"id=?",new String[]{String.valueOf(id)});else s.insertWithOnConflict("context_fingerprint_features",null,v,SQLiteDatabase.CONFLICT_REPLACE);}

    private static double score(VaultDb db,String type,String key){Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT weight,positive_count,negative_count FROM context_fingerprint_features WHERE feature_type=? AND feature_key=? ORDER BY ABS(weight) DESC,updated_at DESC LIMIT 6",new String[]{type,key});double weighted=0,norm=0;while(c.moveToNext()){double w=c.getDouble(0);int p=c.getInt(1),n=c.getInt(2);double trust=Math.min(1.0,.35+.12*(p+n));weighted+=w*trust;norm+=trust;}return norm<=0?0:weighted/norm;}catch(Throwable ignored){return 0;}finally{if(c!=null)c.close();}}
    private static String prefix(String key){int at=key==null?-1:key.indexOf(':');return at>0?key.substring(0,at):"";}
}
