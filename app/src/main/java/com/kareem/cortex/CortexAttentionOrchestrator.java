package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;

/** Bridges existing raw-signal/thread pipeline into open-loop state, attention scoring and the materialized feed. */
public final class CortexAttentionOrchestrator {
    public static final String VERSION="attention_orchestrator_001";
    private CortexAttentionOrchestrator(){}

    public static void onSignalCaptured(VaultDb db,long signalId,long threadId){
        if(db==null||signalId<=0)return;try{CortexAttentionSchema.ensure(db);SignalSnapshot s=load(db,signalId,threadId);if(s==null)return;
            OpenLoopStore.Loop existing=threadId>0?OpenLoopStore.activeForThread(db,threadId):null;
            if(existing!=null&&isCancellation(s.body)){OpenLoopStore.cancel(db,existing.id,signalId,.88);AttentionFeedStore.removeLoop(db,existing.id);persistAssessmentRemoval(db,existing.id);return;}
            if(existing!=null&&isLikelyOutgoingAcknowledgement(s)){OpenLoopStore.markUserCommitted(db,existing.id,signalId);evaluateAndPersist(db,OpenLoopStore.get(db,existing.id),s.importance);return;}
            if(existing!=null&&isLikelyResolution(s,existing)){OpenLoopStore.resolve(db,existing.id,signalId,"matching_outgoing_action",.82);AttentionFeedStore.removeLoop(db,existing.id);persistAssessmentRemoval(db,existing.id);return;}
            if(isActionableIncomingRequest(s)){
                long loopId=OpenLoopStore.upsertIncomingRequest(db,signalId,threadId,s.source,s.title,s.body,Math.max(.60,s.confidence));if(loopId>0)evaluateAndPersist(db,OpenLoopStore.get(db,loopId),Math.max(55,s.importance));
            }else if(existing!=null)evaluateAndPersist(db,existing,Math.max(45,s.importance));
        }catch(Throwable e){try{DiagnosticsLog.error(db,"CortexAttentionOrchestrator","signal",e,"ATTENTION_PIPELINE",0,threadId,signalId,0,0,null);}catch(Throwable ignored){}}
    }

    public static void reevaluateThread(VaultDb db,long threadId){if(db==null||threadId<=0)return;OpenLoopStore.Loop l=OpenLoopStore.activeForThread(db,threadId);if(l!=null)evaluateAndPersist(db,l,60);}

    private static void evaluateAndPersist(VaultDb db,OpenLoopStore.Loop loop,int importance){if(loop==null)return;long now=System.currentTimeMillis();AttentionModels.Decision d=AttentionEngine.evaluate(loop,importance,now,interruptionCost(now),contextRelevance(loop));persistAssessment(db,loop.id,d);AttentionFeedStore.upsertLoop(db,loop,d);}

    private static void persistAssessment(VaultDb db,long loopId,AttentionModels.Decision d){if(d==null)return;AttentionModels.Assessment a=d.assessment;android.content.ContentValues v=new android.content.ContentValues();v.put("entity_type","open_loop");v.put("entity_id",loopId);v.put("score",a.score);v.put("interrupt_score",a.interruptScore);v.put("level",a.level.name());v.put("confidence",a.confidence);v.put("urgency",a.dimensions.urgency);v.put("importance",a.dimensions.importance);v.put("action_required",a.dimensions.actionRequired);v.put("commitment_strength",a.dimensions.commitmentStrength);v.put("unresolvedness",a.dimensions.unresolvedness);v.put("context_relevance",a.dimensions.contextRelevance);v.put("recency",a.dimensions.recency);v.put("novelty",a.dimensions.novelty);v.put("interruption_cost",a.dimensions.interruptionCost);v.put("primary_reason",a.primaryReason);v.put("suggested_action",a.suggestedAction);v.put("actionability",a.actionability.name());v.put("is_time_sensitive",a.timeSensitive?1:0);v.put("engine_version",AttentionEngine.VERSION);v.put("evaluated_at",a.evaluatedAt);db.getWritableDatabase().insertWithOnConflict("attention_assessments",null,v,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);}
    private static void persistAssessmentRemoval(VaultDb db,long loopId){db.getWritableDatabase().delete("attention_assessments","entity_type='open_loop' AND entity_id=?",new String[]{String.valueOf(loopId)});}

    private static boolean isActionableIncomingRequest(SignalSnapshot s){if(s.ongoing||s.body.isEmpty())return false;String x=s.body.toLowerCase();boolean request=hasAny(x,"ابعتلي","ابعته","ممكن تبعت","محتاج منك","عاوز منك","كلمني","فكرني","send me","please send","can you send","could you send","call me","remind me");boolean question=x.contains("?")||x.contains("؟");String disposition=s.disposition.toUpperCase();return request||("ACTION".equals(disposition)&&!question);}
    private static boolean isCancellation(String body){String x=n(body).toLowerCase();return hasAny(x,"خلاص سيبك","مش محتاج","ولا يهمك","cancel that","never mind","nevermind","don't need it","do not need it");}
    private static boolean isLikelyOutgoingAcknowledgement(SignalSnapshot s){String x=s.body.toLowerCase();boolean ack=hasAny(x,"حاضر","تمام ه","هبعت","هعمل","هكلم","okay i'll","ok i'll","i will","will send");return ack&&looksOutgoing(s);}
    private static boolean isLikelyResolution(SignalSnapshot s,OpenLoopStore.Loop loop){if(!looksOutgoing(s))return false;String x=s.body.toLowerCase(),sub=loop.subject.toLowerCase();boolean explicit=hasAny(x,"بعت","اتبعث","sent it","done","خلصت","تم");boolean attachment=x.contains("pdf")||x.contains("attachment")||x.contains("file")||x.contains("ملف")||x.contains("document");boolean topic=wordOverlap(x,sub)>=1;return explicit||(attachment&&topic);}
    private static boolean looksOutgoing(SignalSnapshot s){try{JSONObject o=new JSONObject(s.metadata);String d=o.optString("direction","");if("outgoing".equalsIgnoreCase(d)||"sent".equalsIgnoreCase(d))return true;}catch(Exception ignored){}String x=s.source.toLowerCase();return x.contains("outgoing")||x.contains("sent");}

    private static SignalSnapshot load(VaultDb db,long signalId,long fallbackThread){Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"source","title","body","metadata_json","disposition","importance","confidence","thread_id"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");if(!c.moveToFirst()){c.close();return null;}SignalSnapshot s=new SignalSnapshot();s.source=n(c.getString(0));s.title=n(c.getString(1));s.body=n(c.getString(2));s.metadata=n(c.getString(3));s.disposition=n(c.getString(4));s.importance=c.getInt(5);s.confidence=c.getDouble(6);s.threadId=c.getLong(7)>0?c.getLong(7):fallbackThread;try{s.ongoing=new JSONObject(s.metadata).optBoolean("ongoing",false);}catch(Exception ignored){}c.close();return s;}
    private static double interruptionCost(long now){java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(now);int h=c.get(java.util.Calendar.HOUR_OF_DAY);return h>=0&&h<7?.92:.25;}
    private static double contextRelevance(OpenLoopStore.Loop loop){return loop.threadId>0?.55:.35;}
    private static boolean hasAny(String x,String... parts){for(String p:parts)if(x.contains(p.toLowerCase()))return true;return false;}
    private static int wordOverlap(String a,String b){java.util.HashSet<String> s=new java.util.HashSet<>();for(String x:b.split("[^\\p{L}\\p{N}]+"))if(x.length()>2)s.add(x);int n=0;for(String x:a.split("[^\\p{L}\\p{N}]+"))if(x.length()>2&&s.contains(x))n++;return n;}
    private static String n(String s){return s==null?"":s.trim();}
    private static final class SignalSnapshot{String source,title,body,metadata,disposition;int importance;double confidence;long threadId;boolean ongoing;}
}
