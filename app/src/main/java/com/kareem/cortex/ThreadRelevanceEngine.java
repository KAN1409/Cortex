package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;

/**
 * Real-signal thread pass. The newest signal anchors the decision while recent
 * chronological context can clarify split/ambiguous communication.
 */
public final class ThreadRelevanceEngine {
    private static final String POLICY="thread_master_003";
    private static final int CONTEXT_SIGNALS=8;
    private ThreadRelevanceEngine(){}

    public static MasterRelevanceFilter.Decision onSignal(VaultDb db,long threadId,long signalId){
        if(threadId<=0||signalId<=0)return null;ThreadSnapshot t=load(db,threadId,signalId);if(t==null||!("communication".equals(t.kind)||"email".equals(t.kind)))return null;
        String context=SignalThreadStore.recentContext(db,threadId,CONTEXT_SIGNALS);String evidence=context.isEmpty()?t.latestBody:context;
        MasterRelevanceFilter.Decision base=MasterRelevanceFilter.evaluateThread(t.latestBody,context);
        MasterRelevanceFilter.Decision d=AdaptiveRelevanceLearning.adapt(db,t.source,base);
        if(d.disposition==MasterRelevanceFilter.Disposition.CONTEXT||d.disposition==MasterRelevanceFilter.Disposition.IGNORE){markSignal(db,signalId,"context",d);return d;}

        if(d.reviewable()){
            ReviewQueueStore.enqueue(db,d.candidateKind,t.title,evidence,d.confidence,d.importance,threadId,signalId,d.reason,t.source);
            markSignal(db,signalId,"review",d);return d;
        }
        if(!d.durable())return d;

        try{
            JSONObject meta=new JSONObject();meta.put("policy_version",POLICY);meta.put("learning_version",AdaptiveRelevanceLearning.VERSION);meta.put("thread_id",threadId);meta.put("raw_signal_id",signalId);meta.put("reason",d.reason);meta.put("source",t.source);meta.put("confidence",d.confidence);meta.put("context_signal_count",SignalThreadStore.signalCount(db,threadId));
            String kind=d.disposition.name();String title=empty(t.title)?friendly(kind):t.title+" · "+friendly(kind);
            String fp=Fingerprint.text("thread-derived|"+kind+"|"+threadId+"|"+signalId);
            long derived=CognitiveStore.addDerived(db,kind,title,evidence,"open",d.confidence,d.importance,fp,meta.toString());
            if(derived>0){CognitiveStore.link(db,CognitiveTypes.ObjectType.THREAD,threadId,CognitiveTypes.ObjectType.DERIVED,derived,"produced",d.confidence,meta.toString());CognitiveStore.link(db,CognitiveTypes.ObjectType.RAW_SIGNAL,signalId,CognitiveTypes.ObjectType.DERIVED,derived,CognitiveTypes.Relation.SUPPORTS,1.0,"");}
            markSignal(db,signalId,"derived",d);
        }catch(Exception ignored){}
        return d;
    }

    private static void markSignal(VaultDb db,long signalId,String state,MasterRelevanceFilter.Decision d){try{android.content.ContentValues v=new android.content.ContentValues();v.put("state",state);v.put("disposition",d.disposition.name());v.put("importance",d.importance);v.put("confidence",d.confidence);v.put("policy_version",POLICY);v.put("filter_engine","thread_master_filter+context+adaptive_feedback");v.put("reason",d.reason);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});}catch(Throwable ignored){}}

    private static ThreadSnapshot load(VaultDb db,long threadId,long signalId){
        Cursor tc=db.getReadableDatabase().query("signal_threads",new String[]{"kind","source","title"},"id=?",new String[]{String.valueOf(threadId)},null,null,null,"1");if(!tc.moveToFirst()){tc.close();return null;}String kind=n(tc.getString(0)),source=n(tc.getString(1)),title=n(tc.getString(2));tc.close();
        Cursor sc=db.getReadableDatabase().query("raw_signals",new String[]{"body"},"id=? AND thread_id=?",new String[]{String.valueOf(signalId),String.valueOf(threadId)},null,null,null,"1");String body=sc.moveToFirst()?n(sc.getString(0)):"";sc.close();return new ThreadSnapshot(kind,source,title,body);
    }

    private static String friendly(String kind){if("ACTION".equals(kind))return"Action";if("WAITING".equals(kind))return"Waiting";if("DECISION".equals(kind))return"Decision";return"Update";}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}private static String n(String s){return s==null?"":s.trim();}
    private static final class ThreadSnapshot{final String kind,source,title,latestBody;ThreadSnapshot(String k,String s,String t,String b){kind=k;source=s;title=t;latestBody=b;}}
}
