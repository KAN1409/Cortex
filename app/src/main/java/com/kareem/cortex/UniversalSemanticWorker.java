package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/**
 * Refines ambiguous semantic events with a local background-capable model.
 * This worker updates semantic interpretation only. It never materializes a situation, memory,
 * or attention item directly; canonical downstream workers own those decisions.
 */
public final class UniversalSemanticWorker extends Worker {
    public static final String VERSION="universal_semantic_worker_002";

    public UniversalSemanticWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        if(StartupSafetyGate.active())return Result.success();
        VaultDb vault=null;
        try{
            Context app=getApplicationContext();
            vault=new VaultDb(app);
            UniversalEventStore.ensure(vault.getWritableDatabase());
            if(!LocalModelManager.verified(app))return Result.success();
            if(!LocalModelManager.installed(app)){
                LocalLlmRuntime.maybeAutoSelfTest(app,null);
                LocalLlmRuntime.State st=LocalLlmRuntime.state(app);
                if("failed".equals(st.state)){
                    markQueueBlocked(vault,"Local runtime self-test failed: "+st.error);
                    return Result.success();
                }
                return Result.retry();
            }
            ContentValues recover=new ContentValues();
            recover.put("semantic_state","waiting");
            recover.put("reason","Local runtime recovered; semantic refinement resumed");
            vault.getWritableDatabase().update("ue_semantic_events",recover,
                    "semantic_state='blocked' AND superseded_by=0",null);

            int processed=0;
            while(processed<12){
                Row row=next(vault);
                if(row==null)break;
                process(vault,row);
                processed++;
            }
            if(processed>0)StatefulMeaningScheduler.kick(app);
            return hasWaiting(vault)?Result.retry():Result.success();
        }catch(Throwable t){
            return Result.retry();
        }finally{
            if(vault!=null)try{vault.close();}catch(Throwable ignored){}
        }
    }

    private boolean hasWaiting(VaultDb v){
        Cursor c=v.getReadableDatabase().rawQuery(
                "SELECT 1 FROM ue_semantic_events WHERE semantic_state='waiting' AND superseded_by=0 LIMIT 1",null);
        boolean yes=c.moveToFirst();c.close();return yes;
    }

    private void markQueueBlocked(VaultDb v,String detail){
        ContentValues e=new ContentValues();
        e.put("semantic_state","blocked");
        e.put("model_route","local_background_model");
        e.put("reason",detail);
        v.getWritableDatabase().update("ue_semantic_events",e,
                "semantic_state='waiting' AND superseded_by=0",null);
    }

    private Row next(VaultDb v){
        Cursor c=v.getReadableDatabase().rawQuery(
                "SELECT e.id,e.raw_observation_id,e.stream_id,e.semantic_type,e.subject,e.summary," +
                        "r.source_key,r.title,r.body,r.payload_json,r.occurred_at " +
                        "FROM ue_semantic_events e JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                        "WHERE e.semantic_state='waiting' AND e.superseded_by=0 ORDER BY e.occurred_at ASC LIMIT 1",null);
        Row r=null;
        if(c.moveToFirst())r=new Row(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),
                c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),c.getLong(10));
        c.close();return r;
    }

    private void process(VaultDb v,Row r){
        UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"UNDERSTANDING","running",
                "local_qwen","Local semantic refinement running","");
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
            e.put("semantic_state","complete");
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

            AiJobStore.modelRun(v,0,1,"semantic_classifier","local",LocalModelManager.MODEL_NAME,
                    "universal_event_semantic","complete",Fingerprint.text(prompt),
                    System.currentTimeMillis()-started,0,out.getTokensGenerated(),conf,j.toString(),"");
            UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"UNDERSTANDING","complete",
                    "local_qwen","Semantic refinement complete; canonical correlation required","");
            UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"ATTENTION_BOUNDARY","complete",
                    "canonical_stateful_pipeline","No direct projection; CortexAttentionJudge remains final authority","");
            UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"COMPLETE","complete",VERSION,
                    "Semantic event ready for canonical world-state correlation","");
        }catch(Throwable t){
            UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"UNDERSTANDING","failed",
                    "local_qwen","Local semantic refinement failed",
                    t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));
            throw new RuntimeException(t);
        }
    }

    private static JSONObject parseJson(String text)throws Exception{
        String s=text==null?"":text.trim();int a=s.indexOf('{'),b=s.lastIndexOf('}');
        if(a<0||b<=a)throw new IllegalArgumentException("model did not return JSON");
        return new JSONObject(s.substring(a,b+1));
    }
    private static double clamp(double d){return Math.max(0,Math.min(1,d));}
    private static String clip(String s,int n){String x=s==null?"":s;return x.length()<=n?x:x.substring(0,n);}

    private static final class Row{
        final long eventId,rawId,streamId,occurredAt;
        final String type,subject,summary,source,title,body,payload;
        Row(long e,long r,long s,String t,String sub,String sum,String src,String ti,String b,String p,long at){
            eventId=e;rawId=r;streamId=s;type=t==null?"":t;subject=sub==null?"":sub;summary=sum==null?"":sum;
            source=src==null?"":src;title=ti==null?"":ti;body=b==null?"":b;payload=p==null?"{}":p;occurredAt=at;
        }
    }
}
