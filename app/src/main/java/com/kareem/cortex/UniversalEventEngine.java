package com.kareem.cortex;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Hybrid universal event understanding pipeline.
 * Raw evidence is immutable. This layer may extract semantics, but canonical world-state
 * correlation and final attention are owned downstream by StatefulMeaningStore and
 * CortexAttentionJudge.
 */
public final class UniversalEventEngine {
    public static final String VERSION = "universal_event_engine_002";

    public static final class Result {
        public final long rawId,streamId,semanticEventId,situationId,attentionId,memoryItemId,aiJobId;
        public final String semanticType,intent,state,route,reason;
        Result(long raw,long stream,long sem,long sit,long att,long mem,long job,String type,String i,String st,String r,String why){
            rawId=raw;streamId=stream;semanticEventId=sem;situationId=sit;attentionId=att;memoryItemId=mem;aiJobId=job;
            semanticType=n(type);intent=n(i);state=n(st);route=n(r);reason=n(why);
        }
    }

    private UniversalEventEngine(){}

    public static Result processNotification(Context context,VaultDb vault,NotificationEventEngine.Result platform,
                                             String pkg,String appLabel,String eventType,String title,String body,
                                             long occurredAt,JSONObject metadata){
        SQLiteDatabase db=vault.getWritableDatabase();
        UniversalEventStore.ensure(db);
        JSONObject meta=metadata==null?new JSONObject():metadata;
        String sourceKey=n(pkg);
        String technical=platform==null?n(meta.optString("technical_type","")):platform.technicalType;
        String hint=platform==null?n(meta.optString("platform_hint","")):platform.platformHint;
        String external=platform==null
                ?Fingerprint.text(sourceKey+"|"+meta.optString("notification_key","")+"|"+meta.optInt("notification_id",0))
                :String.valueOf(platform.streamId);

        long raw=UniversalEventStore.appendRaw(db,"notification",sourceKey,
                String.valueOf(platform==null?0:platform.rawId),eventType,hint,technical,title,body,meta,occurredAt);
        UniversalEventStore.stage(db,raw,0,"CAPTURED","complete","android_notification_listener",
                "Immutable notification observation stored","");

        boolean meaningful=platform!=null&&platform.semanticEventId>0;
        String hash=Fingerprint.text(title+"\n"+body+"\n"+meta.toString());
        long stream=UniversalEventStore.upsertStream(db,"notification",external,
                "removed".equals(eventType)?"removed":"active",hash,title,body,hint,technical,occurredAt,meaningful,meta);
        UniversalEventStore.stage(db,raw,0,"NORMALIZED","complete",VERSION,
                "Source normalized and attached to stateful stream","");

        if(!meaningful){
            UniversalEventStore.stage(db,raw,0,"COALESCED","complete","notification_state_coalescer",
                    "State update retained as raw evidence; no new semantic event required","");
            StatefulMeaningScheduler.kick(context);
            return new Result(raw,stream,0,0,0,0,0,"technical_state","","complete",
                    "deterministic","coalesced state/noise update");
        }
        return interpretAndQueue(context,vault,raw,stream,sourceKey,eventType,technical,hint,title,body,meta,occurredAt,1,false);
    }

    /** Rebuilds semantic interpretation from immutable evidence; downstream projections remain canonical. */
    public static Result reprocessNotification(Context context,VaultDb vault,long rawId){
        SQLiteDatabase db=vault.getWritableDatabase();
        UniversalEventStore.ensure(db);
        Cursor c=db.rawQuery("SELECT source_key,event_type,platform_hint,technical_type,title,body,payload_json,occurred_at FROM ue_raw_observations WHERE id=? AND source_type='notification' LIMIT 1",
                new String[]{String.valueOf(rawId)});
        if(!c.moveToFirst()){
            c.close();
            return new Result(rawId,0,0,0,0,0,0,"unknown","","failed","none","raw observation not found");
        }
        String source=c.getString(0),eventType=c.getString(1),hint=c.getString(2),tech=c.getString(3),
                title=c.getString(4),body=c.getString(5),payload=c.getString(6);
        long occurred=c.getLong(7);
        c.close();
        JSONObject meta;try{meta=new JSONObject(payload);}catch(Exception e){meta=new JSONObject();}

        long stream=0;
        Cursor s=db.rawQuery("SELECT stream_id FROM ue_semantic_events WHERE raw_observation_id=? ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(rawId)});
        if(s.moveToFirst())stream=s.getLong(0);
        s.close();
        if(stream<=0){
            String external=Fingerprint.text(source+"|"+meta.optString("notification_key","")+"|"+meta.optInt("notification_id",0));
            stream=UniversalEventStore.upsertStream(db,"notification",external,
                    "removed".equals(eventType)?"removed":"active",
                    Fingerprint.text(title+"\n"+body+"\n"+meta.toString()),title,body,hint,tech,occurred,true,meta);
        }

        int revision=1;
        Cursor r=db.rawQuery("SELECT COALESCE(MAX(revision),0)+1 FROM ue_semantic_events WHERE raw_observation_id=?",
                new String[]{String.valueOf(rawId)});
        if(r.moveToFirst())revision=r.getInt(0);
        r.close();

        long now=System.currentTimeMillis();
        db.execSQL("UPDATE ue_attention_items SET state='superseded',resolved_at=?,updated_at=? WHERE semantic_event_id IN " +
                        "(SELECT id FROM ue_semantic_events WHERE raw_observation_id=? AND superseded_by=0)",
                new Object[]{now,now,rawId});
        Result out=interpretAndQueue(context,vault,rawId,stream,source,eventType,tech,hint,title,body,meta,occurred,revision,true);
        ContentValues u=new ContentValues();
        u.put("superseded_by",out.semanticEventId);
        db.update("ue_semantic_events",u,"raw_observation_id=? AND id<>? AND superseded_by=0",
                new String[]{String.valueOf(rawId),String.valueOf(out.semanticEventId)});
        UniversalEventStore.stage(db,rawId,out.semanticEventId,"REPROCESSED","complete","universal_replay",
                "Semantic interpretation rebuilt; canonical state/judgment queued","");
        StatefulMeaningScheduler.kick(context);
        return out;
    }

    private static Result interpretAndQueue(Context context,VaultDb vault,long raw,long stream,String sourceKey,
                                            String eventType,String technical,String hint,String title,String body,
                                            JSONObject meta,long occurredAt,int revision,boolean replay){
        SQLiteDatabase db=vault.getWritableDatabase();
        Interpretation x=interpret(technical,hint,title,body,meta,eventType);
        String semanticState=x.needsModel?"waiting":"complete";
        String route=x.needsModel?routeFor(context):"deterministic";
        long sem=UniversalEventStore.insertSemantic(db,raw,stream,revision,x.type,x.intent,x.subject,x.summary,
                x.confidence,semanticState,true,route,(replay?"reprocessed: ":"")+x.reason,occurredAt);
        UniversalEventStore.stage(db,raw,sem,"COALESCED","complete","notification_state_coalescer",
                replay?"Replay emitted semantic revision":"Meaningful transition emitted","");
        UniversalEventStore.stage(db,raw,sem,"UNDERSTANDING",x.needsModel?"waiting":"complete",route,
                x.needsModel?"Stored safely; waiting for semantic model pass":"High-confidence deterministic semantic interpretation","");

        String person=extractPerson(title,meta);
        if(!person.isEmpty()){
            long entity=UniversalEventStore.entity(db,"PERSON",person,new JSONObject());
            UniversalEventStore.linkEntity(db,sem,entity,"participant",0.96);
        }

        long job=0;
        if(x.needsModel){
            JSONObject input=new JSONObject();
            try{
                input.put("semantic_event_id",sem);
                input.put("raw_observation_id",raw);
                input.put("source_type","notification");
                input.put("technical_type",technical);
                input.put("platform_hint",hint);
                input.put("title",title);
                input.put("body",body);
                input.put("metadata",meta);
            }catch(Exception ignored){}
            job=AiJobStore.create(vault,"semantic_event_understanding",route,input.toString(),55);
            UniversalSemanticScheduler.kick(context);
        }else{
            StatefulMeaningScheduler.kick(context);
        }

        UniversalEventStore.stage(db,raw,sem,"ATTENTION_BOUNDARY","complete",
                "canonical_stateful_pipeline",
                "No direct projection: semantic evidence must pass canonical correlation and CortexAttentionJudge","");
        UniversalEventStore.stage(db,raw,sem,"COMPLETE",x.needsModel?"waiting":"complete",VERSION,
                x.needsModel?"Grounded event stored; model refinement pending":"Grounded event stored; canonical state correlation queued","");
        return new Result(raw,stream,sem,0,0,0,job,x.type,x.intent,semanticState,route,x.reason);
    }

    private static Interpretation interpret(String technical,String hint,String title,String body,JSONObject meta,String eventType){
        String text=(n(title)+"\n"+n(body)).trim(),low=text.toLowerCase(Locale.ROOT),
                tech=n(technical).toLowerCase(Locale.ROOT);
        Interpretation x=new Interpretation();
        x.subject=n(title);
        x.summary=CanonicalPresentation.cleanBody(body.isEmpty()?title:body);
        x.priority=35;
        x.confidence=.78;
        x.reason="meaningful Android notification transition";

        if("conversation_notification".equals(tech)){
            x.type="conversation_message";x.intent="message";x.priority=50;x.confidence=.86;
            ConversationAttentionFeatures.Result request =
                    ConversationAttentionFeatures.evaluate(x.type,x.intent,title,body);
            if(request.explicitRequest || matchesRequest(low)){
                x.type="action_request";x.intent="request";x.priority=90;x.confidence=.94;
                x.reason=request.explicitRequest?request.reason:"explicit incoming request directed to the user";
            }else if(matchesDecision(low)){
                x.type="decision";x.intent="decision";x.priority=76;x.confidence=.88;
                x.reason="explicit decision language";
            }else if(matchesCommitment(low)){
                x.type="commitment";x.intent="commitment";x.priority=72;x.confidence=.86;
                x.reason="explicit commitment/future obligation";
            }else{
                x.needsModel=true;x.confidence=.58;x.reason="conversation meaning needs semantic model/context";
            }
        } else if("progress_state".equals(tech)||"download_state".equals(tech)||"service_state".equals(tech)){
            x.type="technical_state";x.intent="state_change";x.priority=8;x.confidence=.98;
            x.summary=CanonicalPresentation.cleanBody(text);
            x.reason="technical state transition; preserved below attention";
        } else if("call_hint".equals(tech)){
            x.type="call_event";x.intent="call";x.priority=55;x.confidence=.9;
            if(low.contains("missed")||low.contains("فائت")||low.contains("لم يتم الرد")){
                x.type="missed_call";x.intent="required_response";x.priority=78;
                x.reason="missed call may require response";
            }
        } else if("email_hint".equals(tech)){
            x.type="email_event";x.intent="email";x.priority=45;x.confidence=.72;
            x.needsModel=true;x.reason="email content needs semantic/contextual interpretation";
        } else if("alarm".equals(tech)||"reminder".equals(tech)||"event".equals(tech)){
            x.type=tech+"_event";x.intent=tech;x.priority=65;x.confidence=.91;
            x.reason="explicit time-sensitive Android event";
        } else {
            x.type="notification_event";x.intent="notification";x.priority=30;x.confidence=.55;
            x.needsModel=!text.isEmpty();
            x.reason=text.isEmpty()?"metadata-only notification":"generic notification needs semantic/contextual interpretation";
        }
        if("removed".equals(eventType)&&"technical_state".equals(x.type))x.priority=4;
        return x;
    }

    private static boolean matchesRequest(String s){
        return containsAny(s,"ابعت","ابعث","هات ","محتاجك","محتاج منك","لو سمحت","ضروري",
                "يرجى التأكيد","يرجى التاكد","يرجى التأكد","برجاء التأكيد","برجاء الرد",
                "please send","send me","can you","could you","need you to","please ","kindly confirm");
    }
    private static boolean matchesDecision(String s){
        return containsAny(s,"قررنا","قررت","تم الاتفاق","اتفقنا","القرار","we decided","decided that","agreed that","final decision");
    }
    private static boolean matchesCommitment(String s){
        return containsAny(s,"هبعت","هعمل","هكلم","هراجع","هنعمل","هخلص","هتكون","سأرسل","سوف ",
                "i will","i'll ","we will","we'll ","promise","by tomorrow","بكرة","غدا");
    }
    private static boolean containsAny(String s,String... xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String extractPerson(String title,JSONObject meta){
        String p=n(meta.optString("conversation_title",""));if(!p.isEmpty())return p;return n(title);
    }
    private static String routeFor(Context c){
        try{if(LocalModelManager.installed(c))return"local_background_model";}catch(Throwable ignored){}
        return"local_model_pending";
    }
    private static String n(String s){return s==null?"":s.trim();}
    private static final class Interpretation {
        String type="notification_event",intent="notification",subject="",summary="",reason="";
        int priority=30;double confidence=.5;boolean needsModel=false;
    }
}
