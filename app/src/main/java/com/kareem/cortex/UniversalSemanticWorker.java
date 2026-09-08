package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/** Refines ambiguous semantic events with a local background-capable model. Never promotes raw evidence directly. */
public final class UniversalSemanticWorker extends Worker {
    public UniversalSemanticWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        VaultDb vault=null;try{
            Context app=getApplicationContext();vault=new VaultDb(app);UniversalEventStore.ensure(vault.getWritableDatabase());
            if(!LocalModelManager.verified(app))return Result.success();
            if(!LocalModelManager.installed(app)){
                // A verified 2.5 GB model must not leave semantic events waiting forever merely
                // because the runtime self-test has not been started yet. Start it automatically
                // and retry this durable WorkManager job after the runtime reaches READY.
                LocalLlmRuntime.maybeAutoSelfTest(app,null);
                LocalLlmRuntime.State st=LocalLlmRuntime.state(app);
                if("failed".equals(st.state)){
                    markQueueBlocked(vault,"Local runtime self-test failed: "+st.error);
                    return Result.success();
                }
                return Result.retry();
            }
            int processed=0;
            while(processed<12){Row row=next(vault);if(row==null)break;process(vault,row);processed++;}
            // If more grounded semantic work remains, retry quickly instead of depending on a new
            // notification to kick the queue.
            return hasWaiting(vault)?Result.retry():Result.success();
        }catch(Throwable t){return Result.retry();}finally{if(vault!=null)try{vault.close();}catch(Throwable ignored){}}
    }

    private boolean hasWaiting(VaultDb v){Cursor c=v.getReadableDatabase().rawQuery("SELECT 1 FROM ue_semantic_events WHERE semantic_state='waiting' AND superseded_by=0 LIMIT 1",null);boolean yes=c.moveToFirst();c.close();return yes;}
    private void markQueueBlocked(VaultDb v,String detail){ContentValues e=new ContentValues();e.put("semantic_state","blocked");e.put("model_route","local_background_model");e.put("reason",detail);v.getWritableDatabase().update("ue_semantic_events",e,"semantic_state='waiting' AND superseded_by=0",null);}

    private Row next(VaultDb v){Cursor c=v.getReadableDatabase().rawQuery("SELECT e.id,e.raw_observation_id,e.stream_id,e.semantic_type,e.subject,e.summary,r.source_key,r.title,r.body,r.payload_json,r.occurred_at FROM ue_semantic_events e JOIN ue_raw_observations r ON r.id=e.raw_observation_id WHERE e.semantic_state='waiting' AND e.superseded_by=0 ORDER BY e.occurred_at ASC LIMIT 1",null);Row r=null;if(c.moveToFirst())r=new Row(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),c.getLong(10));c.close();return r;}

    private void process(VaultDb v,Row r){
        UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"UNDERSTANDING","running","local_qwen","Local semantic refinement running","");long started=System.currentTimeMillis();
        try{
            String system="You classify ONE already-captured Cortex event. Use only supplied evidence. Return JSON only with keys semantic_type,intent,subject,summary,attention_kind,priority,memory_score,confidence,reason. attention_kind must be ACTION,WAITING,DECISION,INFO or NONE. memory_score 0..1. Do not invent facts, people, deadlines, or actions. Preserve Arabic/English wording. /no_think";
            String prompt="SOURCE: "+r.source+"\nTITLE: "+r.title+"\nBODY: "+r.body+"\nRAW_METADATA: "+clip(r.payload,3500);
            LocalLlmBridge.CompletionResult out=LocalLlmBridge.completeCached(LocalModelManager.modelFile(getApplicationContext()).getAbsolutePath(),prompt,system,180);
            JSONObject j=parseJson(out.getText());String type=j.optString("semantic_type",r.type),intent=j.optString("intent",""),subject=CanonicalPresentation.cleanTitle("event",type,j.optString("subject",r.subject),r.title),summary=CanonicalPresentation.cleanBody(j.optString("summary",r.summary));double conf=clamp(j.optDouble("confidence",0.65)),memoryScore=clamp(j.optDouble("memory_score",0));int priority=Math.max(0,Math.min(100,j.optInt("priority",35)));String att=j.optString("attention_kind","NONE").toUpperCase(),reason=j.optString("reason","local grounded semantic refinement");
            ContentValues e=new ContentValues();e.put("semantic_type",type);e.put("intent",intent);e.put("subject",subject);e.put("summary",summary);e.put("confidence",conf);e.put("semantic_state","complete");e.put("model_route","local_background_model");e.put("reason",reason);v.getWritableDatabase().update("ue_semantic_events",e,"id=?",new String[]{String.valueOf(r.eventId)});
            long situation=projectSituation(v,r,type,subject,summary,priority,conf,att,reason);
            if(!"NONE".equals(att)&&!"INFO".equals(att))UniversalEventStore.attention(v.getWritableDatabase(),r.eventId,situation,att,subject,summary,priority,conf,r.source,reason);
            if(memoryScore>=0.86&&conf>=0.82&&durableType(type)){promote(v,r,type,subject,summary,memoryScore,reason);}
            AiJobStore.modelRun(v,0,1,"semantic_classifier","local",LocalModelManager.MODEL_NAME,"universal_event_semantic","complete",Fingerprint.text(prompt),System.currentTimeMillis()-started,0,out.getTokensGenerated(),conf,j.toString(),"");
            UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"UNDERSTANDING","complete","local_qwen","Semantic refinement complete","");UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"COMPLETE","complete","universal_event_engine","Event projections refreshed","");
        }catch(Throwable t){UniversalEventStore.stage(v.getWritableDatabase(),r.rawId,r.eventId,"UNDERSTANDING","failed","local_qwen","Local semantic refinement failed",t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));throw new RuntimeException(t);}
    }

    private long projectSituation(VaultDb v,Row r,String type,String subject,String summary,int priority,double conf,String attention,String reason){
        String t=type==null?"":type.toLowerCase();boolean open=!"NONE".equals(att)&&!"INFO".equals(att)||t.contains("commitment")||t.contains("request")||t.contains("waiting")||t.contains("decision");if(!open)return 0;
        String kind=t.contains("decision")?"decision":(t.contains("commitment")?"commitment":(t.contains("request")?"open_request":"context"));String key=Fingerprint.text("ue-situation|"+r.source+"|"+kind+"|"+CanonicalPresentation.cleanBody(subject).toLowerCase());JSONObject m=new JSONObject();try{m.put("semantic_event_id",r.eventId).put("reason",reason);}catch(Exception ignored){}
        long id=UniversalEventStore.upsertSituation(v.getWritableDatabase(),key,kind,subject,summary,"open",priority,conf,r.occurredAt,m);UniversalEventStore.linkSituationEvent(v.getWritableDatabase(),id,r.eventId,"supports");return id;
    }

    private void promote(VaultDb v,Row r,String type,String title,String summary,double score,String reason){Cursor c=v.getReadableDatabase().rawQuery("SELECT knowledge_item_id FROM ue_memory_promotions WHERE semantic_event_id=? AND state='promoted' LIMIT 1",new String[]{String.valueOf(r.eventId)});long old=c.moveToFirst()?c.getLong(0):0;c.close();if(old>0)return;JSONObject m=new JSONObject();try{m.put("semantic_event_id",r.eventId).put("source",r.source).put("semantic_type",type).put("promotion_policy","memory_promotion_003_local_semantic");}catch(Exception ignored){}long x=v.insert("MEMORY","universal_memory",title,summary,"Memory","semantic,"+type,"",Fingerprint.text("ue-memory|"+r.eventId),m.toString());long item=x<0?-x:x;ContentValues p=new ContentValues();p.put("semantic_event_id",r.eventId);p.put("knowledge_item_id",item);p.put("state",item>0?"promoted":"failed");p.put("policy_version","memory_promotion_003_local_semantic");p.put("score",score);p.put("reason",reason);p.put("created_at",System.currentTimeMillis());p.put("updated_at",System.currentTimeMillis());v.getWritableDatabase().insertWithOnConflict("ue_memory_promotions",null,p,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);}

    private static boolean durableType(String x){String s=x==null?"":x.toLowerCase();return s.contains("decision")||s.contains("commitment")||s.contains("fact")||s.contains("relationship")||s.contains("preference")||s.contains("project");}
    private static JSONObject parseJson(String text)throws Exception{String s=text==null?"":text.trim();int a=s.indexOf('{'),b=s.lastIndexOf('}');if(a<0||b<=a)throw new IllegalArgumentException("model did not return JSON");return new JSONObject(s.substring(a,b+1));}
    private static double clamp(double d){return Math.max(0,Math.min(1,d));}private static String clip(String s,int n){String x=s==null?"":s;return x.length()<=n?x:x.substring(0,n);}
    private static final class Row{final long eventId,rawId,streamId,occurredAt;final String type,subject,summary,source,title,body,payload;Row(long e,long r,long s,String t,String sub,String sum,String src,String ti,String b,String p,long at){eventId=e;rawId=r;streamId=s;type=t==null?"":t;subject=sub==null?"":sub;summary=sum==null?"":sum;source=src==null?"":src;title=ti==null?"":ti;body=b==null?"":b;payload=p==null?"{}":p;occurredAt=at;}}
}
