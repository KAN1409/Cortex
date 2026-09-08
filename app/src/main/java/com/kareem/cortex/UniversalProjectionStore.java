package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** CQRS-lite read models. UI destinations never query the raw ledger as if it were memory. */
public final class UniversalProjectionStore {
    private UniversalProjectionStore(){}

    public static final class Attention {
        public final long id,eventId,situationId,updatedAt; public final String kind,title,body,source,reason; public final int priority; public final double confidence;
        Attention(long i,long e,long s,String k,String t,String b,String src,String r,int p,double c,long u){id=i;eventId=e;situationId=s;kind=n(k);title=n(t);body=n(b);source=n(src);reason=n(r);priority=p;confidence=c;updatedAt=u;}
    }
    public static final class Change {
        public final long id,occurredAt; public final String type,subject,summary,route; public final double confidence;
        Change(long i,String t,String s,String m,String r,double c,long at){id=i;type=n(t);subject=n(s);summary=n(m);route=n(r);confidence=c;occurredAt=at;}
    }
    public static final class CaptureEvent {
        public final long rawId,streamId,eventId,occurredAt; public final String sourceType,sourceKey,eventType,platformHint,technicalType,title,body,semanticType,semanticState,stage,state;
        CaptureEvent(long raw,long stream,long event,long at,String sourceType,String sourceKey,String eventType,String hint,String tech,String title,String body,String semanticType,String semanticState,String stage,String state){this.rawId=raw;this.streamId=stream;eventId=event;occurredAt=at;this.sourceType=n(sourceType);this.sourceKey=n(sourceKey);this.eventType=n(eventType);platformHint=n(hint);technicalType=n(tech);this.title=n(title);this.body=n(body);this.semanticType=n(semanticType);this.semanticState=n(semanticState);this.stage=n(stage);this.state=n(state);}
    }

    public static ArrayList<Attention> now(VaultDb vault,int limit){
        SQLiteDatabase db=vault.getReadableDatabase();UniversalEventStore.ensure(vault.getWritableDatabase());ArrayList<Attention> out=new ArrayList<>();
        Cursor c=db.rawQuery("SELECT id,semantic_event_id,situation_id,kind,title,body,source_key,reason,priority,confidence,updated_at FROM ue_attention_items WHERE state='open' ORDER BY priority DESC,updated_at DESC LIMIT ?",new String[]{String.valueOf(limit)});
        while(c.moveToNext())out.add(new Attention(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getInt(8),c.getDouble(9),c.getLong(10)));c.close();return out;
    }

    public static ArrayList<Change> brief(VaultDb vault,long since,int limit){
        SQLiteDatabase db=vault.getReadableDatabase();UniversalEventStore.ensure(vault.getWritableDatabase());ArrayList<Change> out=new ArrayList<>();
        Cursor c=db.rawQuery("SELECT id,semantic_type,subject,summary,model_route,confidence,occurred_at FROM ue_semantic_events WHERE meaningful=1 AND semantic_state IN ('complete','waiting') AND superseded_by=0 AND occurred_at>=? AND semantic_type NOT IN ('technical_state') ORDER BY occurred_at DESC LIMIT ?",new String[]{String.valueOf(since),String.valueOf(limit)});
        while(c.moveToNext())out.add(new Change(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getDouble(5),c.getLong(6)));c.close();return out;
    }

    public static ArrayList<CaptureEvent> capture(VaultDb vault,long since,int limit){
        SQLiteDatabase db=vault.getReadableDatabase();UniversalEventStore.ensure(vault.getWritableDatabase());ArrayList<CaptureEvent> out=new ArrayList<>();
        String sql="SELECT r.id,COALESCE(e.stream_id,0),COALESCE(e.id,0),r.occurred_at,r.source_type,r.source_key,r.event_type,r.platform_hint,r.technical_type,r.title,r.body,COALESCE(e.semantic_type,''),COALESCE(e.semantic_state,''),COALESCE((SELECT stage FROM ue_pipeline_stages p WHERE p.raw_observation_id=r.id ORDER BY p.started_at DESC LIMIT 1),'CAPTURED'),COALESCE((SELECT state FROM ue_pipeline_stages p WHERE p.raw_observation_id=r.id ORDER BY p.started_at DESC LIMIT 1),'complete') FROM ue_raw_observations r LEFT JOIN ue_semantic_events e ON e.raw_observation_id=r.id AND e.superseded_by=0 WHERE r.occurred_at>=? ORDER BY r.occurred_at DESC LIMIT ?";
        Cursor c=db.rawQuery(sql,new String[]{String.valueOf(since),String.valueOf(limit)});while(c.moveToNext())out.add(new CaptureEvent(c.getLong(0),c.getLong(1),c.getLong(2),c.getLong(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),c.getString(10),c.getString(11),c.getString(12),c.getString(13),c.getString(14)));c.close();return out;
    }

    public static ArrayList<KnowledgeItem> durableMemories(VaultDb vault,String query,int limit){
        UniversalEventStore.ensure(vault.getWritableDatabase());ArrayList<KnowledgeItem> out=new ArrayList<>();for(KnowledgeItem k:vault.lexicalSearch(query,Math.max(limit*4,120))){if(k==null)continue;if(UniversalEventStore.isLegacyCapture(vault.getReadableDatabase(),k.id)&&!UniversalEventStore.isPromotedMemory(vault.getReadableDatabase(),k.id))continue;out.add(k);if(out.size()>=limit)break;}return out;
    }

    public static long durableMemoryCount(VaultDb vault){UniversalEventStore.ensure(vault.getWritableDatabase());Cursor c=vault.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM knowledge_items k WHERE NOT EXISTS(SELECT 1 FROM ue_legacy_classification l WHERE l.knowledge_item_id=k.id AND l.classification='captured_artifact') OR EXISTS(SELECT 1 FROM ue_memory_promotions p WHERE p.knowledge_item_id=k.id AND p.state='promoted')",null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    public static long rawCountSince(VaultDb v,long since){UniversalEventStore.ensure(v.getWritableDatabase());return UniversalEventStore.scalar(v.getReadableDatabase(),"SELECT COUNT(*) FROM ue_raw_observations WHERE occurred_at>=?",new String[]{String.valueOf(since)});}
    public static long streamCountSince(VaultDb v,long since){UniversalEventStore.ensure(v.getWritableDatabase());return UniversalEventStore.scalar(v.getReadableDatabase(),"SELECT COUNT(*) FROM ue_streams WHERE last_seen_at>=?",new String[]{String.valueOf(since)});}
    public static long semanticCountSince(VaultDb v,long since){UniversalEventStore.ensure(v.getWritableDatabase());return UniversalEventStore.scalar(v.getReadableDatabase(),"SELECT COUNT(*) FROM ue_semantic_events WHERE meaningful=1 AND superseded_by=0 AND occurred_at>=?",new String[]{String.valueOf(since)});}
    public static long waitingCount(VaultDb v){UniversalEventStore.ensure(v.getWritableDatabase());return UniversalEventStore.scalar(v.getReadableDatabase(),"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='waiting' AND superseded_by=0",null);}
    public static long failedStageCount(VaultDb v){UniversalEventStore.ensure(v.getWritableDatabase());return UniversalEventStore.scalar(v.getReadableDatabase(),"SELECT COUNT(*) FROM ue_pipeline_stages WHERE state='failed'",null);}
    public static long appCountSince(VaultDb v,long since){UniversalEventStore.ensure(v.getWritableDatabase());return UniversalEventStore.scalar(v.getReadableDatabase(),"SELECT COUNT(DISTINCT source_key) FROM ue_raw_observations WHERE source_type='notification' AND occurred_at>=?",new String[]{String.valueOf(since)});}
    private static String n(String s){return s==null?"":s.trim();}
}
