package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Newest signal anchors the deterministic baseline; persistence is one short atomic transition. */
public final class ThreadRelevanceEngine {
    private static final String POLICY="thread_master_005";
    private static final int CONTEXT_SIGNALS=8;
    private ThreadRelevanceEngine(){}

    public static MasterRelevanceFilter.Decision onSignal(VaultDb db,long threadId,long signalId){
        if(threadId<=0||signalId<=0)return null;ThreadSnapshot t=load(db,threadId,signalId);if(t==null||!("communication".equals(t.kind)||"email".equals(t.kind)))return null;
        String context=SignalThreadStore.recentContext(db,threadId,CONTEXT_SIGNALS),evidence=context.isEmpty()?t.latestBody:context;
        MasterRelevanceFilter.Decision base=MasterRelevanceFilter.evaluateThread(t.latestBody,context);MasterRelevanceFilter.Decision d=AdaptiveRelevanceLearning.adapt(db,t.source,base);
        RelevanceEvaluationStore.deterministic(db,threadId,signalId,t.source,base,d);RelevanceDecisionStatusStore.ensure(db);

        // Review/durable baselines are not final semantics until their backing persistence succeeds.
        if(d.reviewable()||d.durable())RelevanceDecisionStatusStore.pendingApply(db,signalId);

        SQLiteDatabase sql=db.getWritableDatabase();boolean applied=false,superseded=false;String failure="";sql.beginTransaction();
        try{
            if(latestSignalId(sql,threadId)!=signalId){superseded=true;return d;}
            if(d.disposition==MasterRelevanceFilter.Disposition.CONTEXT||d.disposition==MasterRelevanceFilter.Disposition.IGNORE){
                if(!markSignal(sql,signalId,"context",d))throw new IllegalStateException("signal context transition failed");
                if(!RelevanceDecisionStatusStore.writeFinal(sql,signalId,"deterministic+learning",d,0,"APPLIED"))throw new IllegalStateException("context evaluation transition failed");
            }else if(d.reviewable()){
                long reviewId=ReviewQueueStore.enqueue(db,d.candidateKind,t.title,evidence,d.confidence,d.importance,threadId,signalId,d.reason,t.source);if(reviewId<=0)throw new IllegalStateException("Review persistence failed");
                if(!markSignal(sql,signalId,"review",d))throw new IllegalStateException("signal Review transition failed");
                if(!RelevanceDecisionStatusStore.writeFinal(sql,signalId,"review",d,reviewId,"APPLIED"))throw new IllegalStateException("Review evaluation transition failed");
            }else if(d.durable()){
                long derived=upsertDerived(db,t,threadId,signalId,evidence,d);if(derived<=0)throw new IllegalStateException("durable intelligence persistence failed");
                if(!markSignal(sql,signalId,"derived",d))throw new IllegalStateException("signal durable transition failed");
                if(!RelevanceDecisionStatusStore.writeFinal(sql,signalId,"deterministic+learning",d,0,"APPLIED"))throw new IllegalStateException("durable evaluation transition failed");
            }else{
                // Non-durable unsupported baseline: keep the raw signal filtered without inventing a final semantic transition.
                RelevanceDecisionStatusStore.writeFinal(sql,signalId,"deterministic+learning",d,0,"APPLIED");
            }
            sql.setTransactionSuccessful();applied=true;
        }catch(Throwable e){failure=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
        finally{sql.endTransaction();}

        if(superseded){RelevanceDecisionStatusStore.applyStatus(db,signalId,"SUPERSEDED");DiagnosticsLog.info(db,"ThreadRelevanceEngine","baseline_superseded","safe",0,threadId,signalId,0,0,0,null);}
        else if(!applied){RelevanceDecisionStatusStore.applyStatus(db,signalId,"APPLY_FAILED");DiagnosticsLog.error(db,"ThreadRelevanceEngine","atomic_apply",new IllegalStateException(failure),"THREAD_APPLY_FAILED",0,threadId,signalId,0,0,null);}
        return d;
    }

    /** One open durable item per thread/kind; new supporting messages refresh it instead of duplicating it. */
    private static long upsertDerived(VaultDb db,ThreadSnapshot t,long threadId,long signalId,String evidence,MasterRelevanceFilter.Decision d){
        try{
            SQLiteDatabase sql=db.getWritableDatabase();String kind=d.disposition.name();Cursor c=sql.query("derived_items",new String[]{"id"},"thread_id=? AND kind=? AND state='open'",new String[]{String.valueOf(threadId),kind},null,null,"updated_at DESC","1");long existing=c.moveToFirst()?c.getLong(0):0;c.close();
            JSONObject meta=new JSONObject();meta.put("policy_version",POLICY);meta.put("learning_version",AdaptiveRelevanceLearning.VERSION);meta.put("thread_id",threadId);meta.put("raw_signal_id",signalId);meta.put("reason",d.reason);meta.put("source",t.source);meta.put("confidence",d.confidence);meta.put("context_signal_count",SignalThreadStore.signalCount(db,threadId));String title=empty(t.title)?friendly(kind):t.title+" · "+friendly(kind);long derived=existing;
            if(existing>0){ContentValues v=new ContentValues();v.put("title",title);v.put("body",evidence);v.put("confidence",d.confidence);v.put("importance",d.importance);v.put("metadata_json",meta.toString());v.put("source_key",t.source);v.put("thread_id",threadId);v.put("anchor_signal_id",signalId);v.put("candidate_kind",kind);v.put("updated_at",System.currentTimeMillis());if(sql.update("derived_items",v,"id=?",new String[]{String.valueOf(existing)})<=0)return 0;}
            else{String fp=Fingerprint.text("thread-derived|"+kind+"|"+threadId);derived=CognitiveStore.addDerived(db,kind,title,evidence,"open",d.confidence,d.importance,fp,meta.toString());if(derived<=0)return 0;CognitiveStore.setDerivedRouting(db,derived,t.source,threadId,signalId,kind);CognitiveStore.link(db,CognitiveTypes.ObjectType.THREAD,threadId,CognitiveTypes.ObjectType.DERIVED,derived,"produced",d.confidence,meta.toString());}
            CognitiveStore.link(db,CognitiveTypes.ObjectType.RAW_SIGNAL,signalId,CognitiveTypes.ObjectType.DERIVED,derived,CognitiveTypes.Relation.SUPPORTS,1.0,"");return derived;
        }catch(Throwable e){DiagnosticsLog.error(db,"ThreadRelevanceEngine","derive",e,"THREAD_DERIVE",0,threadId,signalId,0,0,null);return 0;}
    }

    private static boolean markSignal(SQLiteDatabase sql,long signalId,String state,MasterRelevanceFilter.Decision d){ContentValues v=new ContentValues();v.put("state",state);v.put("disposition",d.disposition.name());v.put("importance",d.importance);v.put("confidence",d.confidence);v.put("policy_version",POLICY);v.put("filter_engine","thread_master_filter+context+adaptive_feedback");v.put("reason",d.reason);v.put("updated_at",System.currentTimeMillis());return sql.update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)})>0;}
    private static long latestSignalId(SQLiteDatabase sql,long threadId){Cursor c=sql.rawQuery("SELECT id FROM raw_signals WHERE thread_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1",new String[]{String.valueOf(threadId)});long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    private static ThreadSnapshot load(VaultDb db,long threadId,long signalId){Cursor tc=db.getReadableDatabase().query("signal_threads",new String[]{"kind","source","title"},"id=?",new String[]{String.valueOf(threadId)},null,null,null,"1");if(!tc.moveToFirst()){tc.close();return null;}String kind=n(tc.getString(0)),source=n(tc.getString(1)),title=n(tc.getString(2));tc.close();Cursor sc=db.getReadableDatabase().query("raw_signals",new String[]{"body"},"id=? AND thread_id=?",new String[]{String.valueOf(signalId),String.valueOf(threadId)},null,null,null,"1");String body=sc.moveToFirst()?n(sc.getString(0)):"";sc.close();return new ThreadSnapshot(kind,source,title,body);}
    private static String friendly(String kind){if("ACTION".equals(kind))return"Action";if("WAITING".equals(kind))return"Waiting";if("DECISION".equals(kind))return"Decision";if("MEMORY".equals(kind))return"Memory";return"Update";}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}private static String n(String s){return s==null?"":s.trim();}
    private static final class ThreadSnapshot{final String kind,source,title,latestBody;ThreadSnapshot(String k,String s,String t,String b){kind=k;source=s;title=t;latestBody=b;}}
}
