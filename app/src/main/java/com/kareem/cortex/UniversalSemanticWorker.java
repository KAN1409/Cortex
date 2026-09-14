package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/**
 * Refines ambiguous semantic events with a local background-capable model.
 * Execution is lease-based and poison events cannot block the rest of the queue.
 * This worker updates semantic interpretation only; canonical downstream workers own state,
 * memory and attention decisions.
 */
public final class UniversalSemanticWorker extends Worker {
    public static final String VERSION="universal_semantic_worker_003";

    public UniversalSemanticWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        if(StartupSafetyGate.active())return Result.success();
        VaultDb vault=null;
        try{
            Context app=getApplicationContext();
            vault=new VaultDb(app);
            UniversalSemanticQueueStore.ensure(vault.getWritableDatabase());
            UniversalSemanticQueueStore.recoverExpiredClaims(vault.getWritableDatabase());
            if(!LocalModelManager.verified(app))return Result.success();
            if(!LocalModelManager.installed(app)){
                LocalLlmRuntime.maybeAutoSelfTest(app,null);
                LocalLlmRuntime.State st=LocalLlmRuntime.state(app);
                if("failed".equals(st.state)){
                    UniversalSemanticQueueStore.markRuntimeBlocked(vault.getWritableDatabase(),"Local runtime self-test failed: "+st.error);
                    return Result.success();
                }
                return Result.retry();
            }
            UniversalSemanticQueueStore.resumeRuntimeBlocked(vault.getWritableDatabase());

            int processed=0,completed=0;
            while(processed<12){
                UniversalSemanticQueueStore.Row row=UniversalSemanticQueueStore.claimNext(vault.getWritableDatabase());
                if(row==null)break;
                if(isStopped()){
                    UniversalSemanticQueueStore.fail(vault.getWritableDatabase(),row,new InterruptedException("WorkManager stopped semantic worker"));
                    break;
                }
                if(process(vault,row))completed++;
                processed++;
            }
            if(completed>0)StatefulMeaningScheduler.kick(app);
            return UniversalSemanticQueueStore.hasPending(vault.getReadableDatabase())?Result.retry():Result.success();
        }catch(Throwable t){
            return Result.retry();
        }finally{
            if(vault!=null)try{vault.close();}catch(Throwable ignored){}
        }
    }

    private boolean process(VaultDb v,UniversalSemanticQueueStore.Row r){
        UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"UNDERSTANDING","running",
                "local_qwen","Local semantic refinement running • attempt "+r.attempt,"");
        long started=System.currentTimeMillis();
        try{
            String system="Classify ONE already-captured Cortex event using ONLY supplied evidence. Return JSON only with keys semantic_type,intent,subject,summary,attention_kind,priority,memory_score,confidence,reason. attention_kind is ACTION, WAITING, DECISION, INFO or NONE. ACTION is allowed only when the evidence contains an explicit request, required response, task, deadline, payment/security issue, or other concrete user obligation. Social updates, reactions, likes, follows, friend additions, typing indicators, ordinary incoming messages and informational notifications are INFO or NONE unless they contain a concrete request. WAITING requires a real dependency or commitment. DECISION requires an actual decision. Never infer an action merely because the user could optionally respond. memory_score 0..1. Do not invent facts, people, deadlines, translations or actions. Preserve Arabic/English wording. /no_think";
            String prompt="SOURCE: "+r.source+"\nTITLE: "+r.title+"\nBODY: "+r.body+
                    "\nRAW_METADATA: "+clip(r.payload,3500);
            LocalLlmBridge.CompletionResult out=LocalLlmBridge.completeCached(
                    LocalModelManager.modelFile(getApplicationContext()).getAbsolutePath(),prompt,system,180);
            JSONObject j=parseJson(out.getText());
            String type=j.optString("semantic_type",r.type);
            String intent=j.optString("intent","");
            String subject=CanonicalPresentation.cleanTitle("event",type,j.optString("subject",r.subject),r.title);
            String summary=CanonicalPresentation.cleanBody(j.optString("summary",r.summary));
            double conf=clamp(j.optDouble("confidence",0.65));
            String proposed=j.optString("attention_kind","NONE").toUpperCase();
            String guarded=UniversalProjectionPolicy.validateAttention(type,intent,proposed,conf);
            String reason=j.optString("reason","local grounded semantic refinement");
            if(!guarded.equals(proposed))reason="Projection guard changed "+proposed+" to "+guarded+". "+reason;

            ContentValues e=new ContentValues();
            e.put("semantic_type",type);
            e.put("intent",intent);
            e.put("subject",subject);
            e.put("summary",summary);
            e.put("confidence",conf);
            e.put("model_route","local_background_model");
            e.put("reason",reason);
            v.getWritableDatabase().update("ue_semantic_events",e,"id=?",new String[]{String.valueOf(r.eventId)});

            long now=System.currentTimeMillis();
            ContentValues old=new ContentValues();
            old.put("state","suppressed");
            old.put("resolved_at",now);
            old.put("updated_at",now);
            old.put("reason","superseded by canonical state/judgment pipeline");
            v.getWritableDatabase().update("ue_attention_items",old,
                    "semantic_event_id=? AND state='open'",new String[]{String.valueOf(r.eventId)});

            AiJobStore.modelRun(v,0,r.attempt,"semantic_classifier","local",LocalModelManager.MODEL_NAME,
                    "universal_event_semantic","complete",Fingerprint.text(prompt),
                    System.currentTimeMillis()-started,0,out.getTokensGenerated(),conf,j.toString(),"");
            UniversalSemanticQueueStore.complete(v.getWritableDatabase(),r);
            UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"UNDERSTANDING","complete",
                    "local_qwen","Semantic refinement complete on attempt "+r.attempt+"; canonical correlation required","");
            UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"ATTENTION_BOUNDARY","complete",
                    "canonical_stateful_pipeline","No direct projection; CortexAttentionJudge remains final authority","");
            UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"COMPLETE","complete",VERSION,
                    "Semantic event ready for canonical world-state correlation","");
            return true;
        }catch(Throwable t){
            long latency=System.currentTimeMillis()-started;
            UniversalSemanticQueueStore.fail(v.getWritableDatabase(),r,t);
            AiJobStore.modelRun(v,0,r.attempt,"semantic_classifier","local",LocalModelManager.MODEL_NAME,
                    "universal_event_semantic","failed",Fingerprint.text(r.source+"|"+r.title+"|"+r.body),
                    latency,0,0,0,"",t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));
            UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"UNDERSTANDING","failed",
                    "local_qwen","Local semantic refinement failed on attempt "+r.attempt,
                    t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));
            return false;
        }
    }

    private static JSONObject parseJson(String text)throws Exception{
        String s=text==null?"":text.trim();int a=s.indexOf('{'),b=s.lastIndexOf('}');
        if(a<0||b<=a)throw new IllegalArgumentException("model did not return JSON");
        return new JSONObject(s.substring(a,b+1));
    }
    private static double clamp(double d){return Math.max(0,Math.min(1,d));}
    private static String clip(String s,int n){String x=s==null?"":s;return x.length()<=n?x:x.substring(0,n);}
}
