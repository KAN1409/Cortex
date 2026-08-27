package com.kareem.cortex;

/** Bridges existing raw-signal/thread pipeline into open-loop state, attention scoring and the materialized feed. */
public final class CortexAttentionOrchestrator {
    public static final String VERSION="attention_orchestrator_003";
    private CortexAttentionOrchestrator(){}

    public static void onSignalCaptured(VaultDb db,long signalId,long threadId){
        if(db==null||signalId<=0)return;try{CortexAttentionSchema.ensure(db);AttentionSemantics.Result s=AttentionSemantics.extract(db,signalId,threadId);if(s==null)return;long effectiveThread=s.threadId;
            OpenLoopStore.Loop existing=effectiveThread>0?OpenLoopStore.activeForThread(db,effectiveThread):null;
            if(existing!=null&&s.cancellation){OpenLoopStore.cancel(db,existing.id,signalId,Math.max(.80,s.confidence));closeMaterialized(db,existing.id,"CANCELLED");return;}
            if(existing!=null&&s.intent==AttentionSemantics.Intent.COMMITMENT&&s.outgoing){OpenLoopStore.markUserCommitted(db,existing.id,signalId);evaluateAndPersist(db,OpenLoopStore.get(db,existing.id),Math.max(55,s.importance));return;}
            if(existing!=null&&isLikelyResolution(s,existing)){OpenLoopStore.resolve(db,existing.id,signalId,"matching_outgoing_action",Math.max(.78,s.confidence));closeMaterialized(db,existing.id,"RESOLVED");return;}
            if(s.actionExpected&&(!s.outgoing||s.incoming)){
                long loopId=OpenLoopStore.upsertIncomingRequest(db,signalId,effectiveThread,s.source,s.title,s.body,Math.max(.60,s.confidence));if(loopId>0)evaluateAndPersist(db,OpenLoopStore.get(db,loopId),Math.max(55,s.importance));
            }else if(existing!=null)evaluateAndPersist(db,existing,Math.max(45,s.importance));
        }catch(Throwable e){try{DiagnosticsLog.error(db,"CortexAttentionOrchestrator","signal",e,"ATTENTION_PIPELINE",0,threadId,signalId,0,0,null);}catch(Throwable ignored){}}
    }

    public static void reevaluateThread(VaultDb db,long threadId){if(db==null||threadId<=0)return;OpenLoopStore.Loop l=OpenLoopStore.activeForThread(db,threadId);if(l!=null)evaluateAndPersist(db,l,60);}

    private static boolean isLikelyResolution(AttentionSemantics.Result s,OpenLoopStore.Loop loop){if(!s.outgoing)return false;String x=s.body.toLowerCase(),sub=loop.subject.toLowerCase();boolean attachment=x.contains("pdf")||x.contains("attachment")||x.contains("file")||x.contains("ملف")||x.contains("document");boolean topic=wordOverlap(x,sub)>=1;return s.completion||(attachment&&topic);}
    private static void evaluateAndPersist(VaultDb db,OpenLoopStore.Loop loop,int importance){if(loop==null)return;long now=System.currentTimeMillis();AttentionModels.Decision d=AttentionEngine.evaluate(loop,importance,now,interruptionCost(now),contextRelevance(loop));persistAssessment(db,loop.id,d);AttentionActionStore.replaceForLoop(db,loop.id,AttentionActionPlanner.plan(loop,d.assessment));AttentionFeedStore.upsertLoop(db,loop,d);}
    private static void closeMaterialized(VaultDb db,long loopId,String status){AttentionFeedStore.removeLoop(db,loopId);AttentionActionStore.closeForLoop(db,loopId,status);persistAssessmentRemoval(db,loopId);}

    private static void persistAssessment(VaultDb db,long loopId,AttentionModels.Decision d){if(d==null)return;AttentionModels.Assessment a=d.assessment;android.content.ContentValues v=new android.content.ContentValues();v.put("entity_type","open_loop");v.put("entity_id",loopId);v.put("score",a.score);v.put("interrupt_score",a.interruptScore);v.put("level",a.level.name());v.put("confidence",a.confidence);v.put("urgency",a.dimensions.urgency);v.put("importance",a.dimensions.importance);v.put("action_required",a.dimensions.actionRequired);v.put("commitment_strength",a.dimensions.commitmentStrength);v.put("unresolvedness",a.dimensions.unresolvedness);v.put("context_relevance",a.dimensions.contextRelevance);v.put("recency",a.dimensions.recency);v.put("novelty",a.dimensions.novelty);v.put("interruption_cost",a.dimensions.interruptionCost);v.put("primary_reason",a.primaryReason);v.put("suggested_action",a.suggestedAction);v.put("actionability",a.actionability.name());v.put("is_time_sensitive",a.timeSensitive?1:0);v.put("engine_version",AttentionEngine.VERSION);v.put("evaluated_at",a.evaluatedAt);db.getWritableDatabase().insertWithOnConflict("attention_assessments",null,v,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);}
    private static void persistAssessmentRemoval(VaultDb db,long loopId){db.getWritableDatabase().delete("attention_assessments","entity_type='open_loop' AND entity_id=?",new String[]{String.valueOf(loopId)});}
    private static double interruptionCost(long now){java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(now);int h=c.get(java.util.Calendar.HOUR_OF_DAY);return h>=0&&h<7?.92:.25;}
    private static double contextRelevance(OpenLoopStore.Loop loop){return loop.threadId>0?.55:.35;}
    private static int wordOverlap(String a,String b){java.util.HashSet<String> s=new java.util.HashSet<>();for(String x:b.split("[^\\p{L}\\p{N}]+"))if(x.length()>2)s.add(x);int n=0;for(String x:a.split("[^\\p{L}\\p{N}]+"))if(x.length()>2&&s.contains(x))n++;return n;}
}
