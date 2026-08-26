package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.*;

/**
 * Clean-slate interpretation boundary.
 *
 * Raw evidence is never rewritten here. The Event Engine only projects evidence into canonical
 * events, then into conservative Truth Objects. Re-running it is safe and idempotent.
 */
public final class EventEngine {
    private EventEngine(){}

    public static long onRawSignal(VaultDb db,long signalId,long threadId,MasterRelevanceFilter.Signal signal){
        if(db==null||signal==null||signalId<=0)return 0;TruthSchema.ensure(db);
        String text=join(signal.title,signal.body),type=eventType(text,signal.source);
        long eventId=storeEvent(db,"raw_signal:"+signalId,"RAW_SIGNAL",signalId,signalId,0,threadId,type,
                signal.source,signal.title,signal.body,signal.occurredAt,.98,signal.metadataJson);
        if(eventId>0)derive(db,eventId,signalId,0,threadId,signal.source,signal.title,signal.body,signal.occurredAt,.95,signal.metadataJson,userOwnedSource(signal.source));
        return eventId;
    }

    public static long onKnowledgeInserted(VaultDb db,long itemId){
        if(db==null||itemId<=0)return 0;KnowledgeItem k=db.getById(itemId);if(!eligibleKnowledge(k))return 0;
        return onKnowledge(db,k,false);
    }

    public static long onKnowledgeAnalyzed(VaultDb db,long itemId){
        if(db==null||itemId<=0)return 0;KnowledgeItem k=db.getById(itemId);if(!eligibleKnowledge(k))return 0;
        return onKnowledge(db,k,true);
    }

    private static long onKnowledge(VaultDb db,KnowledgeItem k,boolean analyzed){
        String body=bestBody(k,analyzed),title=n(k.title),text=join(title,body);
        long eventId=storeEvent(db,"memory:"+k.id,"MEMORY",k.id,0,k.id,0,eventType(text,k.source),k.source,title,body,
                k.createdAt,.96,k.metadataJson);
        if(eventId>0)derive(db,eventId,0,k.id,0,k.source,title,body,k.createdAt,analyzed ? .96 : .90,k.metadataJson,userOwned(k));
        return eventId;
    }

    /**
     * Bounded compatibility bridge for data captured before the clean-slate truth layer existed.
     * It reads evidence only; no legacy derived row is trusted as truth.
     */
    public static void backfillRecent(VaultDb db,int limit){
        if(db==null)return;TruthSchema.ensure(db);int n=Math.max(20,Math.min(500,limit));
        Cursor c=null;
        try{
            c=db.getReadableDatabase().rawQuery(
                "SELECT id,kind,source,title,body,metadata_json,occurred_at,thread_id FROM raw_signals ORDER BY occurred_at DESC LIMIT ?",
                new String[]{String.valueOf(n)});
            while(c.moveToNext()){
                long id=c.getLong(0),thread=c.getLong(7);
                MasterRelevanceFilter.Signal s=new MasterRelevanceFilter.Signal(c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6),false);
                onRawSignal(db,id,thread,s);
            }
        }catch(Throwable ignored){}finally{if(c!=null)c.close();}
        try{
            int seen=0;for(KnowledgeItem k:db.lexicalSearch("",Math.min(600,n*3))){
                if(!eligibleKnowledge(k))continue;onKnowledge(db,k,"analyzed".equalsIgnoreCase(n(k.status)));if(++seen>=n)break;
            }
        }catch(Throwable ignored){}
    }

    private static long storeEvent(VaultDb db,String eventKey,String originType,long originId,long signalId,long memoryId,long threadId,
                                   String eventType,String source,String title,String body,long occurredAt,double confidence,String metadataJson){
        TruthSchema.ensure(db);SQLiteDatabase s=db.getWritableDatabase();long now=System.currentTimeMillis(),when=occurredAt>0?occurredAt:now;
        Cursor c=s.query("truth_events",new String[]{"id"},"event_key=?",new String[]{eventKey},null,null,null,"1");
        long existing=c.moveToFirst()?c.getLong(0):0;c.close();
        ContentValues v=new ContentValues();v.put("origin_type",originType);v.put("origin_id",Math.max(0,originId));v.put("signal_id",Math.max(0,signalId));v.put("memory_id",Math.max(0,memoryId));
        v.put("thread_id",Math.max(0,threadId));v.put("event_type",n(eventType));v.put("source_key",n(source));v.put("title",n(title));v.put("body",n(body));v.put("occurred_at",when);
        v.put("confidence",clamp(confidence));v.put("metadata_json",n(metadataJson));v.put("updated_at",now);
        if(existing>0){s.update("truth_events",v,"id=?",new String[]{String.valueOf(existing)});return existing;}
        v.put("event_key",eventKey);v.put("created_at",now);long id=s.insertWithOnConflict("truth_events",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id>0)return id;c=s.query("truth_events",new String[]{"id"},"event_key=?",new String[]{eventKey},null,null,null,"1");id=c.moveToFirst()?c.getLong(0):0;c.close();return id;
    }

    private static void derive(VaultDb db,long eventId,long signalId,long memoryId,long threadId,String source,String title,String body,
                               long occurredAt,double confidence,String metadataJson,boolean userOwnedEvidence){
        String text=join(title,body);LinkedHashSet<String> supported=new LinkedHashSet<>();
        if(text.isEmpty()||CortexTruthPolicy.ambientContext(text,source)){TruthObjectStore.reconcileEventKinds(db,eventId,supported);return;}

        boolean action=CortexTruthPolicy.confirmedAction(title,body,source,confidence);
        boolean waiting=CortexTruthPolicy.confirmedWaiting(title,body,source,confidence);
        boolean decision=userOwnedEvidence&&CortexTruthPolicy.confirmedDecision(title,body,source);

        if(action){supported.add(TruthObjectStore.ACTION);upsert(db,eventId,TruthObjectStore.ACTION,title,body,source,confidence,76,threadId,signalId,memoryId,occurredAt,metadataJson);}
        if(waiting){supported.add(TruthObjectStore.WAITING);upsert(db,eventId,TruthObjectStore.WAITING,title,body,source,confidence,70,threadId,signalId,memoryId,occurredAt,metadataJson);}
        if(decision){supported.add(TruthObjectStore.DECISION);upsert(db,eventId,TruthObjectStore.DECISION,title,body,source,Math.max(.90,confidence),74,threadId,signalId,memoryId,occurredAt,metadataJson);}

        // Important is informational truth, never a fallback label for an uncertain obligation/decision.
        if(!action&&!waiting&&!decision&&importantEvent(text,source)){
            supported.add(TruthObjectStore.IMPORTANT);
            upsert(db,eventId,TruthObjectStore.IMPORTANT,title,body,source,Math.max(.82,confidence),importantScore(text,source),threadId,signalId,memoryId,occurredAt,metadataJson);
        }
        TruthObjectStore.reconcileEventKinds(db,eventId,supported);
    }

    private static long upsert(VaultDb db,long eventId,String kind,String title,String body,String source,double confidence,int importance,
                               long threadId,long signalId,long memoryId,long occurredAt,String metadataJson){
        String semantic=semanticKey(kind,source,threadId,title,body);
        String meta=metadataJson;
        try{
            JSONObject m=empty(metadataJson)?new JSONObject():new JSONObject(metadataJson);
            m.put("truth_engine","event_truth_001");m.put("grounded_event_id",eventId);m.put("grounded_signal_id",signalId);m.put("grounded_memory_id",memoryId);
            meta=m.toString();
        }catch(Throwable ignored){}
        return TruthObjectStore.upsertFromEvent(db,eventId,kind,title,body,source,confidence,importance,semantic,threadId,signalId,memoryId,occurredAt,meta);
    }

    static boolean eligibleKnowledge(KnowledgeItem k){
        if(k==null)return false;String s=low(k.source),t=n(k.type).toUpperCase(Locale.ROOT);
        if("CONTACT".equals(t)||"NOTIFICATION".equals(t))return false;
        return s.equals("manual")||s.equals("manual_recording")||s.equals("android_share")||s.equals("audio_import")||
               s.equals("quick_capture")||s.equals("screen_understanding")||s.equals("screen_understand");
    }

    private static boolean userOwned(KnowledgeItem k){return k!=null&&userOwnedSource(k.source);}
    private static boolean userOwnedSource(String source){
        String s=low(source);return s.equals("manual")||s.equals("manual_recording")||s.equals("quick_capture");
    }

    private static String bestBody(KnowledgeItem k,boolean analyzed){
        if(k==null)return"";if(analyzed&&!empty(k.extractedText))return k.extractedText;if(!empty(k.rawText))return k.rawText;if(!empty(k.extractedText))return k.extractedText;if(!empty(k.summary))return k.summary;return"";
    }

    private static String eventType(String text,String source){
        if(CortexTruthPolicy.ambientContext(text,source))return"AMBIENT";
        if(CortexTruthPolicy.externalApprovalOrRejection(text))return"OUTCOME";
        if(importantEvent(text,source))return"IMPORTANT_EVENT";
        return"EVIDENCE";
    }

    static boolean importantEvent(String text,String source){
        String t=LocalSemanticEmbedder.norm(n(text));if(t.isEmpty()||CortexTruthPolicy.ambientContext(t,source))return false;
        if(CortexTruthPolicy.externalApprovalOrRejection(t))return true;
        return has(t,"appointment","meeting confirmed","booking confirmed","deadline","due today","due tomorrow","delivery completed","delivered",
                "security alert","new login","password changed","payment failed","transaction declined","missed call",
                "موعد","تم الحجز","تأكيد الحجز","اجتماع","آخر موعد","اخر موعد","تم التوصيل","تم التسليم","تنبيه أمني","تنبيه امني","تسجيل دخول جديد","تم تغيير كلمة المرور","فشل الدفع","تم رفض المعاملة","مكالمة فائتة");
    }

    private static int importantScore(String text,String source){
        String t=LocalSemanticEmbedder.norm(text);
        if(has(t,"security alert","new login","password changed","تنبيه أمني","تنبيه امني","تسجيل دخول جديد","تم تغيير كلمة المرور"))return 82;
        if(has(t,"deadline","due today","آخر موعد","اخر موعد"))return 78;
        return 66;
    }

    private static String semanticKey(String kind,String source,long threadId,String title,String body){
        String x=LocalSemanticEmbedder.norm(join(title,body));if(x.length()>420)x=x.substring(0,420);
        String scope=threadId>0?"thread:"+threadId:"source:"+low(source);
        return Fingerprint.text("truth|"+kind+"|"+scope+"|"+x);
    }
    private static String join(String a,String b){String x=n(a),y=n(b);return x+(x.isEmpty()||y.isEmpty()?"":"\n")+y;}
    private static boolean has(String t,String...xs){String n=LocalSemanticEmbedder.norm(t);for(String x:xs)if(n.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String low(String s){return n(s).toLowerCase(Locale.ROOT);}
    private static String n(String s){return s==null?"":s.trim();}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
}
