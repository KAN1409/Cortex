package com.kareem.cortex;

import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * Bounded one-way rescue for recent Local Bus events accepted by an older Cortex build before
 * trusted connector enrichment was allowed to cross the durable relevance boundary.
 *
 * <p>No event is replayed through the bus and no Evidence is rewritten. We only revisit an existing
 * unpromoted Raw Signal when its already-stored CONNECTOR_ENRICHMENT contains richer grounded text.
 * If the current relevance policy now judges it durable, the same Raw Signal is promoted and
 * projected into canonical Memory/Situations.</p>
 */
public final class CognitiveConnectorEnrichmentRescueV4 {
    static final long LOOKBACK_MS=48L*60L*60L*1000L;
    private static final int MAX_EVENTS=48;
    private CognitiveConnectorEnrichmentRescueV4(){}

    public static Result run(VaultDb db){
        if(db==null)return new Result(0,0,0,0);
        CognitiveStoreV4.ensure(db);CortexLocalBusStoreV1.ensure(db);RawSignalStore.ensure(db);
        long cutoff=System.currentTimeMillis()-LOOKBACK_MS;
        Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT r.id,COALESCE(r.source,''),COALESCE(r.title,''),r.occurred_at,r.thread_id,r.promoted_item_id,COALESCE(r.metadata_json,''),"+
                "COALESCE((SELECT ea.output_text FROM v4_legacy_map m JOIN v4_evidence_analysis ea ON ea.evidence_id=m.object_id " +
                "WHERE m.legacy_table='raw_signals' AND m.legacy_id=CAST(r.id AS TEXT) AND m.object_type='EVIDENCE' " +
                "AND ea.analysis_kind='CONNECTOR_ENRICHMENT' AND ea.engine='local_bus:second_brain' " +
                "ORDER BY ea.created_at DESC,ea.id DESC LIMIT 1),'') AS connector_text " +
                "FROM connector_ingest_events e JOIN raw_signals r ON r.id=e.signal_id " +
                "WHERE e.connector_id='second_brain' AND e.state='ACCEPTED' AND e.received_at>=? AND r.promoted_item_id=0 " +
                "ORDER BY e.received_at DESC,e.event_id DESC LIMIT ?",
                new String[]{String.valueOf(cutoff),String.valueOf(MAX_EVENTS)});
        ArrayList<Candidate> pending=new ArrayList<>();
        try{while(c.moveToNext())pending.add(new Candidate(c.getLong(0),n(c.getString(1)),n(c.getString(2)),c.getLong(3),c.getLong(4),c.getLong(5),n(c.getString(6)),n(c.getString(7))));}finally{c.close();}

        int considered=0,promoted=0,projected=0,skipped=0;
        for(Candidate x:pending){
            considered++;
            if(x.promotedItemId>0||x.connectorText.isEmpty()){skipped++;continue;}
            boolean ongoing=false;try{ongoing=new JSONObject(x.metadataJson.isEmpty()?"{}":x.metadataJson).optBoolean("ongoing",false);}catch(Throwable ignored){}
            MasterRelevanceFilter.Signal enriched=new MasterRelevanceFilter.Signal(
                    "notification",x.source,x.title,x.connectorText,x.metadataJson,x.occurredAt,ongoing);
            long itemId=0;try{itemId=RawSignalStore.promoteTrustedEnrichment(db,x.signalId,x.threadId,enriched);}catch(Throwable ignored){}
            if(itemId<=0){skipped++;continue;}
            promoted++;
            try{CognitiveRealtimeProjectionV4.Result p=CognitiveRealtimeProjectionV4.project(db,x.signalId);if(p!=null&&!p.memoryId.isEmpty())projected++;}catch(Throwable ignored){}
        }
        return new Result(considered,promoted,projected,skipped);
    }

    static List<Long> recentUnpromotedSignalIds(VaultDb db){
        ArrayList<Long> out=new ArrayList<>();if(db==null)return out;long cutoff=System.currentTimeMillis()-LOOKBACK_MS;
        Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT DISTINCT e.signal_id FROM connector_ingest_events e JOIN raw_signals r ON r.id=e.signal_id " +
                "WHERE e.connector_id='second_brain' AND e.state='ACCEPTED' AND e.received_at>=? AND r.promoted_item_id=0 ORDER BY e.received_at DESC LIMIT ?",
                new String[]{String.valueOf(cutoff),String.valueOf(MAX_EVENTS)});
        try{while(c.moveToNext())out.add(c.getLong(0));}finally{c.close();}return out;
    }

    private static String n(String s){return s==null?"":s.replace('\u0000',' ').replaceAll("\\s+"," ").trim();}
    private static final class Candidate{
        final long signalId,occurredAt,threadId,promotedItemId;final String source,title,metadataJson,connectorText;
        Candidate(long signalId,String source,String title,long occurredAt,long threadId,long promotedItemId,String metadataJson,String connectorText){this.signalId=signalId;this.source=source;this.title=title;this.occurredAt=occurredAt;this.threadId=threadId;this.promotedItemId=promotedItemId;this.metadataJson=metadataJson;this.connectorText=connectorText;}
    }
    public static final class Result{
        public final int considered,promoted,projected,skipped;
        Result(int considered,int promoted,int projected,int skipped){this.considered=considered;this.promoted=promoted;this.projected=projected;this.skipped=skipped;}
    }
}
