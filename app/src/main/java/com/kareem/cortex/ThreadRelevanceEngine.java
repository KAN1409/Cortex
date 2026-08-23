package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;

/**
 * Real-signal thread pass. The policy itself lives in MasterRelevanceFilter so
 * deterministic and future model adjudication share one vocabulary.
 */
public final class ThreadRelevanceEngine {
    private static final String POLICY="thread_master_001";
    private ThreadRelevanceEngine(){}

    public static void onSignal(VaultDb db,long threadId,long signalId){
        if(threadId<=0||signalId<=0)return;ThreadSnapshot t=load(db,threadId,signalId);if(t==null||!("communication".equals(t.kind)||"email".equals(t.kind)))return;
        MasterRelevanceFilter.Decision d=MasterRelevanceFilter.evaluateThread(t.latestBody);
        if(d.disposition==MasterRelevanceFilter.Disposition.CONTEXT||d.disposition==MasterRelevanceFilter.Disposition.IGNORE)return;

        if(d.reviewable()){
            ReviewQueueStore.enqueue(db,d.candidateKind,t.title,t.latestBody,d.confidence,d.importance,threadId,signalId,d.reason,t.source);
            markSignal(db,signalId,"review",d);
            return;
        }
        if(!d.durable())return;

        try{
            JSONObject meta=new JSONObject();meta.put("policy_version",POLICY);meta.put("thread_id",threadId);meta.put("raw_signal_id",signalId);meta.put("reason",d.reason);meta.put("source",t.source);meta.put("confidence",d.confidence);
            String kind=d.disposition.name();String title=empty(t.title)?friendly(kind):t.title+" · "+friendly(kind);
            String fp=Fingerprint.text("thread-derived|"+kind+"|"+threadId+"|"+Fingerprint.text(t.latestBody));
            long derived=CognitiveStore.addDerived(db,kind,title,t.latestBody,"open",d.confidence,d.importance,fp,meta.toString());
            if(derived>0){CognitiveStore.link(db,CognitiveTypes.ObjectType.THREAD,threadId,CognitiveTypes.ObjectType.DERIVED,derived,"produced",d.confidence,meta.toString());CognitiveStore.link(db,CognitiveTypes.ObjectType.RAW_SIGNAL,signalId,CognitiveTypes.ObjectType.DERIVED,derived,CognitiveTypes.Relation.SUPPORTS,1.0,"");}
            markSignal(db,signalId,"derived",d);
        }catch(Exception ignored){}
    }

    private static void markSignal(VaultDb db,long signalId,String state,MasterRelevanceFilter.Decision d){
        try{android.content.ContentValues v=new android.content.ContentValues();v.put("state",state);v.put("disposition",d.disposition.name());v.put("importance",d.importance);v.put("confidence",d.confidence);v.put("policy_version",POLICY);v.put("filter_engine","thread_master_filter");v.put("reason",d.reason);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});}catch(Throwable ignored){}
    }

    private static ThreadSnapshot load(VaultDb db,long threadId,long signalId){
        Cursor tc=db.getReadableDatabase().query("signal_threads",new String[]{"kind","source","title"},"id=?",new String[]{String.valueOf(threadId)},null,null,null,"1");if(!tc.moveToFirst()){tc.close();return null;}String kind=n(tc.getString(0)),source=n(tc.getString(1)),title=n(tc.getString(2));tc.close();
        Cursor sc=db.getReadableDatabase().query("raw_signals",new String[]{"body"},"id=? AND thread_id=?",new String[]{String.valueOf(signalId),String.valueOf(threadId)},null,null,null,"1");String body=sc.moveToFirst()?n(sc.getString(0)):"";sc.close();return new ThreadSnapshot(kind,source,title,body);
    }

    private static String friendly(String kind){if("ACTION".equals(kind))return"Action";if("WAITING".equals(kind))return"Waiting";if("DECISION".equals(kind))return"Decision";return"Update";}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}private static String n(String s){return s==null?"":s.trim();}
    private static final class ThreadSnapshot{final String kind,source,title,latestBody;ThreadSnapshot(String k,String s,String t,String b){kind=k;source=s;title=t;latestBody=b;}}
}
