package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small asynchronous bridge from an already-authoritatively-promoted raw signal into canonical
 * V4 Memory/Situation state.
 *
 * <p>This does not promote filtered/context-only notifications. It only runs after the legacy
 * relevance governor has already produced a durable knowledge item. Evidence remains immutable;
 * richer connector text is consumed from additive Evidence analysis when projecting Memory.</p>
 */
public final class CognitiveRealtimeProjectionV4 {
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"cortex-v4-realtime");t.setPriority(Thread.NORM_PRIORITY-1);return t;
    });
    private CognitiveRealtimeProjectionV4(){}

    public static void schedule(Context context,long signalId){
        if(context==null||signalId<=0)return;Context app=context.getApplicationContext();
        EXECUTOR.execute(()->{VaultDb db=null;try{db=new VaultDb(app);Result result=project(db,signalId);if(result.situationRefreshRan)CognitiveReasoningOrchestratorV4.schedule(app,"realtime_signal");}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}});
    }

    static Result project(VaultDb db,long signalId){
        if(db==null||signalId<=0)return new Result(0,"",false,false);
        CognitiveStoreV4.ensure(db);
        Cursor r=db.getReadableDatabase().rawQuery(
                "SELECT promoted_item_id,thread_id,COALESCE(source,''),occurred_at,importance FROM raw_signals WHERE id=? LIMIT 1",
                new String[]{String.valueOf(signalId)});
        long itemId=0,threadId=0,occurredAt=0;String signalSource="";double importance=.5;
        try{if(!r.moveToFirst())return new Result(0,"",false,false);itemId=r.getLong(0);threadId=r.getLong(1);signalSource=n(r.getString(2));occurredAt=r.getLong(3);importance=normalizeImportance(r.getDouble(4));}finally{r.close();}
        // Filtering/relevance remains the authority. No durable legacy item means no Memory.
        if(itemId<=0)return new Result(0,"",false,false);

        String evidenceId=mapped(db,"raw_signals",String.valueOf(signalId),"EVIDENCE");
        if(evidenceId.isEmpty())return new Result(itemId,"",false,false);

        Cursor k=db.getReadableDatabase().rawQuery(
                "SELECT type,COALESCE(source,''),COALESCE(title,''),COALESCE(raw_text,''),COALESCE(extracted_text,''),COALESCE(summary,''),created_at FROM knowledge_items WHERE id=? LIMIT 1",
                new String[]{String.valueOf(itemId)});
        String type="",source="",title="",raw="",extracted="",summary="";long createdAt=occurredAt;
        try{if(!k.moveToFirst())return new Result(itemId,"",false,false);type=n(k.getString(0));source=n(k.getString(1));title=n(k.getString(2));raw=n(k.getString(3));extracted=n(k.getString(4));summary=n(k.getString(5));createdAt=k.getLong(6);}finally{k.close();}
        if(source.isEmpty())source=signalSource;if(createdAt<=0)createdAt=occurredAt>0?occurredAt:System.currentTimeMillis();

        String base=first(raw,extracted,summary,title);
        String connector=latestConnectorText(db,evidenceId);
        String body=preferConnectorText(base,connector);
        if(body.isEmpty())return new Result(itemId,"",false,false);

        String episodeId=threadId>0?mapped(db,"signal_threads",String.valueOf(threadId),"EPISODE"):"";
        String memoryId=CognitiveIdentityV4.objectId("mem","legacy-knowledge-item|"+itemId);
        CognitiveDomainV4.Memory memory=new CognitiveDomainV4.Memory(
                memoryId,
                CognitiveMemoryBackfillV4.memoryKind(type),
                title,
                body,
                createdAt,
                null,
                Collections.singletonList(evidenceId),
                episodeId.isEmpty()?null:episodeId,
                source,
                Collections.<String>emptyList(),
                importance,
                false,
                CognitiveDomainV4.RetentionClass.EPISODIC_90_DAY);
        String actual=CognitiveStoreV4.putMemory(db,memory,"legacy-knowledge-item:"+itemId,createdAt+CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS);
        CognitiveStoreV4.mapLegacy(db,"knowledge_items",String.valueOf(itemId),CognitiveDomainV4.CanonicalObjectType.MEMORY,actual,"FORWARD_REALTIME");
        if(!episodeId.isEmpty())CognitiveStoreV4.addProvenance(db,CognitiveDomainV4.CanonicalObjectType.MEMORY,actual,CognitiveDomainV4.CanonicalObjectType.EPISODE,episodeId,"part_of",1.0);

        boolean situation=false,reconciled=false;
        try{CognitiveSituationEngineV4.Result s=CognitiveSituationEngineV4.refresh(db);situation=shouldScheduleReasoning(s);}catch(Throwable ignored){}
        try{CognitiveDeepBrainReconcilerV4.reconcile(db);reconciled=true;}catch(Throwable ignored){}
        return new Result(itemId,actual,situation,reconciled);
    }

    /**
     * Only a newly-created canonical Situation should wake the autonomous cloud brain. The detector
     * also returns IDs for already-existing candidates; treating those as fresh would schedule a
     * WorkManager pass for unrelated notifications whenever any old Situation existed in lookback.
     */
    static boolean shouldScheduleReasoning(CognitiveSituationEngineV4.Result result){
        return result!=null&&result.situationsDetected>0;
    }

    /** Prefer trusted connector enrichment when it contains at least as much usable context. */
    static String preferConnectorText(String base,String connector){
        String b=n(base),c=n(connector);if(c.isEmpty())return b;if(b.isEmpty())return c;
        String bn=MasterRelevanceFilter.ruleNorm(b),cn=MasterRelevanceFilter.ruleNorm(c);
        if(cn.contains(bn)||c.length()>=b.length())return c;
        // Explicit obligation/deadline language is semantically richer even if the payload is shorter.
        MasterRelevanceFilter.Decision bd=MasterRelevanceFilter.evaluateThread(b,b);
        MasterRelevanceFilter.Decision cd=MasterRelevanceFilter.evaluateThread(c,c);
        if(cd.durable()&&!bd.durable())return c;
        return b;
    }

    private static String latestConnectorText(VaultDb db,String evidenceId){
        Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT COALESCE(output_text,'') FROM v4_evidence_analysis WHERE evidence_id=? AND analysis_kind='CONNECTOR_ENRICHMENT' ORDER BY created_at DESC,id DESC LIMIT 1",
                new String[]{evidenceId});
        try{return c.moveToFirst()?n(c.getString(0)):"";}finally{c.close();}
    }

    private static String mapped(VaultDb db,String table,String legacyId,String objectType){
        Cursor c=db.getReadableDatabase().query("v4_legacy_map",new String[]{"object_id"},"legacy_table=? AND legacy_id=? AND object_type=?",new String[]{table,legacyId,objectType},null,null,null,"1");
        try{return c.moveToFirst()?n(c.getString(0)):"";}finally{c.close();}
    }
    private static double normalizeImportance(double x){if(Double.isNaN(x)||Double.isInfinite(x))return .5;if(x>1)x/=100.0;return Math.max(0,Math.min(1,x));}
    private static String first(String...xs){if(xs!=null)for(String x:xs)if(!n(x).isEmpty())return n(x);return"";}
    private static String n(String s){return s==null?"":s.replace('\u0000',' ').replaceAll("\\s+"," ").trim();}

    static final class Result{
        final long knowledgeItemId;final String memoryId;final boolean situationRefreshRan,reconciled;
        Result(long item,String memory,boolean situations,boolean reconciled){this.knowledgeItemId=item;this.memoryId=memory;this.situationRefreshRan=situations;this.reconciled=reconciled;}
    }
}
