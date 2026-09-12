package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.Locale;

/** Durable-memory promotion from supported canonical semantics only. */
public final class CanonicalMemoryPromoter {
    public static final String VERSION="canonical_memory_promotion_002";
    private CanonicalMemoryPromoter(){}

    public static long reconcile(VaultDb vault,SQLiteDatabase db,long semanticEventId,long situationId){
        if(vault==null||db==null||semanticEventId<=0)return 0;UniversalEventStore.ensure(db);CanonicalStateStore.ensure(db);
        Cursor old=db.rawQuery("SELECT knowledge_item_id FROM ue_memory_promotions WHERE semantic_event_id=? AND state='promoted' LIMIT 1",new String[]{String.valueOf(semanticEventId)});long existing=old.moveToFirst()?old.getLong(0):0;old.close();if(existing>0)return existing;
        String canonical=CanonicalStateStore.state(db,semanticEventId);if(!"supported".equals(canonical)&&!"verified".equals(canonical))return 0;
        Cursor c=db.rawQuery("SELECT semantic_type,intent,subject,summary,confidence,raw_observation_id FROM ue_semantic_events WHERE id=? AND semantic_state='complete' AND superseded_by=0 LIMIT 1",new String[]{String.valueOf(semanticEventId)});if(!c.moveToFirst()){c.close();return 0;}
        String type=n(c.getString(0)).toLowerCase(Locale.ROOT),intent=n(c.getString(1)).toLowerCase(Locale.ROOT),subject=n(c.getString(2)),summary=n(c.getString(3));double confidence=c.getDouble(4);long rawId=c.getLong(5);c.close();
        boolean durable=type.contains("decision")||type.contains("commitment")||intent.contains("decision")||intent.contains("commitment");
        if(!durable||confidence<.82)return 0;
        int evidence=countMembers(db,situationId);if(type.contains("commitment")&&confidence<.88&&evidence<2)return 0;
        String title=CanonicalPresentation.cleanTitle("memory",type,subject,subject);JSONObject meta=new JSONObject();try{meta.put("semantic_event_id",semanticEventId);meta.put("raw_observation_id",rawId);meta.put("situation_id",situationId);meta.put("canonical_state",canonical);meta.put("promotion_policy",VERSION);meta.put("evidence_count",evidence);}catch(Exception ignored){}
        long inserted=vault.insert("MEMORY","canonical_semantic",title,CanonicalPresentation.cleanBody(summary),"Memory","semantic,"+type,"",Fingerprint.text("canonical-memory|"+semanticEventId),meta.toString());long item=inserted<0?-inserted:inserted;if(item<=0)return 0;
        ContentValues p=new ContentValues();p.put("semantic_event_id",semanticEventId);p.put("knowledge_item_id",item);p.put("state","promoted");p.put("policy_version",VERSION);p.put("score",Math.min(1.0,confidence+(evidence>=2?.06:0)));p.put("reason","supported durable "+type+" promoted from canonical semantics");p.put("created_at",System.currentTimeMillis());p.put("updated_at",System.currentTimeMillis());db.insertWithOnConflict("ue_memory_promotions",null,p,SQLiteDatabase.CONFLICT_REPLACE);return item;
    }

    public static long countPromotable(SQLiteDatabase db){
        if(db==null)return 0;CanonicalStateStore.ensure(db);Cursor c=db.rawQuery("SELECT COUNT(*) FROM ue_semantic_events e JOIN ue_canonical_states cs ON cs.semantic_event_id=e.id WHERE e.semantic_state='complete' AND e.superseded_by=0 AND cs.canonical_state IN ('supported','verified') AND e.confidence>=0.82 AND (lower(e.semantic_type) LIKE '%decision%' OR lower(e.semantic_type) LIKE '%commitment%' OR lower(e.intent) LIKE '%decision%' OR lower(e.intent) LIKE '%commitment%')",null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;
    }

    private static int countMembers(SQLiteDatabase db,long situationId){if(situationId<=0)return 1;Cursor c=db.rawQuery("SELECT COUNT(*) FROM ue_situation_members_v2 WHERE situation_id=?",new String[]{String.valueOf(situationId)});int n=c.moveToFirst()?Math.max(1,c.getInt(0)):1;c.close();return n;}
    private static String n(String s){return s==null?"":s.trim();}
}
