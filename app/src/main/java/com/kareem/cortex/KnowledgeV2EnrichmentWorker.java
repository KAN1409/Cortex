package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.util.*;

public final class KnowledgeV2EnrichmentWorker extends Worker {
    public static final String UNIQUE_WORK_NAME="cortex-kv2-enrichment";
    public static final String STAGE="KNOWLEDGE_ENRICHMENT";

    public KnowledgeV2EnrichmentWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}

    @NonNull @Override public Result doWork(){
        if(StartupSafetyGate.active())return Result.retry();
        VaultDb db=new VaultDb(getApplicationContext());
        try{
            SQLiteDatabase s=db.getWritableDatabase();
            KnowledgeV2Schema.ensure(s);
            KnowledgeV2Maintenance.prepare(s);
            cleanupKnowledgeUiNoise(s);
            seedQueue(s);
            while(!isStopped()){
                long evidenceId=next(s);
                if(evidenceId<=0)break;
                try{mark(s,evidenceId,"RUNNING",null);enrich(db,s,evidenceId);mark(s,evidenceId,"DONE",null);}
                catch(Throwable t){markFailure(s,evidenceId,t);}
            }
            KnowledgeV2Maintenance.canonicalizeCategories(s);
            KnowledgeV2ProjectionWorker.enqueue(getApplicationContext());
            return Result.success();
        }catch(Throwable t){
            return Result.retry();
        }finally{try{db.close();}catch(Throwable ignored){}}
    }

    private static void cleanupKnowledgeUiNoise(SQLiteDatabase s){
        s.execSQL("UPDATE kv2_categories SET state='hidden' WHERE upper(canonical_name) IN ('URL','DATE','MONEY','PHONE','EMAIL','HASHTAG')");
        Cursor c=s.rawQuery("SELECT id,kind,canonical_name FROM entity_nodes WHERE status='active' AND metadata_json LIKE '%knowledge_v2%'",null);
        ArrayList<Long> hide=new ArrayList<>();
        while(c.moveToNext())if(!EntityQualityPolicy.plausibleEntity(c.getString(1),c.getString(2)))hide.add(c.getLong(0));
        c.close();
        ContentValues v=new ContentValues();v.put("status","filtered");v.put("updated_at",System.currentTimeMillis());
        for(long id:hide)s.update("entity_nodes",v,"id=?",new String[]{String.valueOf(id)});
    }

    private static void seedQueue(SQLiteDatabase s){
        long now=System.currentTimeMillis();
        s.execSQL(
            "INSERT OR IGNORE INTO kv2_processing(evidence_id,stage,pipeline_version,state,attempt_count,updated_at) "+
            "SELECT p.evidence_id,?,?,'PENDING',0,? FROM kv2_processing p "+
            "JOIN kv2_evidence e ON e.id=p.evidence_id "+
            "WHERE p.stage=? AND p.pipeline_version=? AND p.state='DONE' AND e.knowledge_eligible=1 AND e.self_reference_score<0.72",
            new Object[]{STAGE,KnowledgeV2Schema.PIPELINE_VERSION,now,KnowledgeV2Store.STAGE_EXTRACTION,KnowledgeV2Schema.PIPELINE_VERSION}
        );
    }

    private static long next(SQLiteDatabase s){
        Cursor c=s.rawQuery("SELECT evidence_id FROM kv2_processing WHERE stage=? AND pipeline_version=? AND state='PENDING' ORDER BY updated_at ASC LIMIT 1",
                new String[]{STAGE,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
        long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;
    }

    private static void enrich(VaultDb db,SQLiteDatabase s,long evidenceId) throws Exception {
        long now=System.currentTimeMillis();
        LinkedHashMap<Long,String> entities=new LinkedHashMap<>();
        Cursor m=s.rawQuery("SELECT id,mention_kind,mention_text,normalized_text,resolution_confidence FROM kv2_entity_mentions WHERE evidence_id=? ORDER BY id ASC",
                new String[]{String.valueOf(evidenceId)});
        while(m.moveToNext()){
            long mentionId=m.getLong(0);String kind=n(m.getString(1)).toUpperCase(Locale.ROOT),name=EntityQualityPolicy.cleanEntityValue(kind,n(m.getString(2))),norm=norm(name);
            if(name.isEmpty()||norm.isEmpty()||!EntityQualityPolicy.plausibleEntity(kind,name))continue;
            long entityId=resolveEntity(s,kind,name,norm,now);
            if(entityId<=0)continue;
            entities.put(entityId,kind);
            ContentValues mv=new ContentValues();mv.put("mention_text",name);mv.put("normalized_text",norm);mv.put("resolved_entity_id",entityId);mv.put("resolution_confidence",.90);mv.put("resolution_method","normalized_alias_v2");s.update("kv2_entity_mentions",mv,"id=?",new String[]{String.valueOf(mentionId)});
            edge(s,"evidence",evidenceId,"entity",entityId,"MENTIONS",.90,now,"direct mention");
        }
        m.close();

        Cursor u=s.rawQuery("SELECT category,tags,summary FROM kv2_understanding WHERE evidence_id=? LIMIT 1",new String[]{String.valueOf(evidenceId)});
        if(u.moveToFirst()){
            String category=KnowledgeV2Maintenance.canonicalCategory(n(u.getString(0))),tags=n(u.getString(1));
            if(meaningfulCategory(category))assignCategory(s,"EVIDENCE",evidenceId,category,.92,"extractor category",now);
            for(String tag:tags.split(",")){String x=KnowledgeV2Maintenance.canonicalCategory(tag.trim());if(meaningfulCategory(x))assignCategory(s,"EVIDENCE",evidenceId,x,.68,"emergent tag",now);}
        }u.close();

        Cursor facts=s.rawQuery("SELECT f.id,f.predicate,f.object_type,f.object_value,f.confidence FROM kv2_facts f JOIN kv2_fact_evidence l ON l.fact_id=f.id WHERE l.evidence_id=? AND f.state='active'",new String[]{String.valueOf(evidenceId)});
        while(facts.moveToNext()){
            long factId=facts.getLong(0);String predicate=n(facts.getString(1)),type=n(facts.getString(2)).toUpperCase(Locale.ROOT),value=n(facts.getString(3));double factConfidence=facts.getDouble(4);
            if("MONEY".equals(type))assignCategory(s,"FACT",factId,"Money & purchases",.95,"money fact",now);
            else if("DATE".equals(type))assignCategory(s,"FACT",factId,"Dates & deadlines",.88,"date fact",now);
            else if("URL".equals(type))assignCategory(s,"FACT",factId,"Links & references",.84,"link fact",now);
            else if("PHONE".equals(type)||"EMAIL".equals(type))assignCategory(s,"FACT",factId,"Contacts",.86,"contact fact",now);

            for(Map.Entry<Long,String> entity:entities.entrySet()){
                long entityId=entity.getKey();String entityKind=entity.getValue();
                String relation=relationForFact(type,predicate,entityKind,value);
                double confidence=relation.startsWith("IDENTITY_")?Math.min(.94,Math.max(.82,factConfidence)):.66;
                edge(s,"entity",entityId,"fact",factId,relation,confidence,now,"same evidence #"+evidenceId);
            }
        }facts.close();

        Cursor events=s.rawQuery("SELECT ev.id,ev.event_type,ev.title,ev.body,ev.confidence FROM kv2_events ev JOIN kv2_event_evidence ee ON ee.event_id=ev.id WHERE ee.evidence_id=? AND ev.status<>'dismissed'",
                new String[]{String.valueOf(evidenceId)});
        while(events.moveToNext()){
            long eventId=events.getLong(0);String title=n(events.getString(2)),body=n(events.getString(3));double conf=events.getDouble(4);
            assignCategory(s,"EVENT",eventId,"Actions & commitments",.90,"action candidate",now);
            for(long entityId:entities.keySet())edge(s,"event",eventId,"entity",entityId,"ACTION_INVOLVES",.72,now,"same action evidence");
            if(conf>=.72&&usefulAction(title)){
                String fp=Fingerprint.text("kv2-v"+KnowledgeV2Schema.PIPELINE_VERSION+"-derived-action|"+eventId+"|"+LocalSemanticEmbedder.norm(title));
                JSONObject meta=new JSONObject();
                meta.put("source","picbrain");
                meta.put("canonical","knowledge_v2");
                meta.put("pipeline_version",KnowledgeV2Schema.PIPELINE_VERSION);
                meta.put("event_id",eventId);
                meta.put("evidence_id",evidenceId);
                long id=CognitiveStore.addDerived(db,"ACTION",title,body,"open",Math.max(.72,conf),78,fp,meta.toString());
                if(id>0)CognitiveStore.setDerivedRoutingChecked(db,id,"picbrain",0,0,"ACTION","kv2:event:"+eventId);
            }
        }events.close();
    }

    private static String relationForFact(String type,String predicate,String entityKind,String value){
        if(predicate.startsWith("MENTIONS_")&&predicate.endsWith(entityKind))return "IDENTITY_MENTION_SUPPORTED_BY";
        if("MONEY".equals(type))return "CO_MENTIONED_WITH_AMOUNT";
        if("DATE".equals(type))return "CO_MENTIONED_WITH_DATE";
        if("URL".equals(type))return "CO_MENTIONED_WITH_REFERENCE";
        if("PHONE".equals(type)||"EMAIL".equals(type))return "CO_MENTIONED_WITH_CONTACT";
        return "CO_MENTIONED_WITH_FACT";
    }

    private static boolean usefulAction(String title){
        String x=n(title),l=x.toLowerCase(Locale.ROOT);if(x.length()<4||x.length()>260)return false;
        if(l.contains("://")||l.startsWith("www.")||l.contains("automations:failed")||l.contains("stacktrace"))return false;
        if(l.matches(".*\\b(?:green|amber|red|quarantined)\\b.*")&&l.contains("pass"))return false;
        return true;
    }

    private static boolean meaningfulCategory(String raw){
        String x=KnowledgeV2Maintenance.canonicalCategory(n(raw));if(x.length()<3||x.length()>64)return false;String u=x.toUpperCase(Locale.ROOT);
        if(Arrays.asList("URL","DATE","MONEY","PHONE","EMAIL","HASHTAG").contains(u))return false;
        String l=x.toLowerCase(Locale.ROOT);
        if(x.contains("://")||l.startsWith("www.")||l.contains("failed in ")||l.contains("exception"))return false;
        return true;
    }

    private static long resolveEntity(SQLiteDatabase s,String kind,String name,String norm,long now) throws Exception {
        name=EntityQualityPolicy.cleanEntityValue(kind,name);norm=norm(name);
        if(!EntityQualityPolicy.plausibleEntity(kind,name))return 0;
        Cursor a=s.rawQuery("SELECT n.id FROM entity_aliases a JOIN entity_nodes n ON n.id=a.entity_id WHERE a.normalized_alias=? AND n.status='active' ORDER BY a.confidence DESC LIMIT 1",new String[]{norm});
        long id=a.moveToFirst()?a.getLong(0):0;a.close();
        if(id>0)return id;
        String key="kv2|"+kind.toLowerCase(Locale.ROOT)+"|"+norm;
        Cursor c=s.query("entity_nodes",new String[]{"id","status"},"normalized_key=?",new String[]{key},null,null,null,"1");String oldStatus="";if(c.moveToFirst()){id=c.getLong(0);oldStatus=n(c.getString(1));}c.close();
        if(id<=0){
            JSONObject meta=new JSONObject();meta.put("source","knowledge_v2");meta.put("resolution","normalized_v2");meta.put("pipeline_version",KnowledgeV2Schema.PIPELINE_VERSION);
            ContentValues v=new ContentValues();v.put("kind",kind);v.put("canonical_name",name);v.put("normalized_key",key);v.put("status","active");v.put("metadata_json",meta.toString());v.put("created_at",now);v.put("updated_at",now);
            id=s.insertWithOnConflict("entity_nodes",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            if(id<=0){Cursor x=s.query("entity_nodes",new String[]{"id"},"normalized_key=?",new String[]{key},null,null,null,"1");id=x.moveToFirst()?x.getLong(0):0;x.close();}
        }else if(!"active".equals(oldStatus)){
            ContentValues reactivate=new ContentValues();reactivate.put("canonical_name",name);reactivate.put("status","active");reactivate.put("updated_at",now);s.update("entity_nodes",reactivate,"id=?",new String[]{String.valueOf(id)});
        }
        if(id>0){ContentValues av=new ContentValues();av.put("entity_id",id);av.put("source","knowledge_v2");av.put("alias",name);av.put("normalized_alias",norm);av.put("confidence",.90);av.put("metadata_json","{\"pipeline_version\":"+KnowledgeV2Schema.PIPELINE_VERSION+"}");av.put("created_at",now);s.insertWithOnConflict("entity_aliases",null,av,SQLiteDatabase.CONFLICT_IGNORE);}
        return id;
    }

    private static void assignCategory(SQLiteDatabase s,String type,long id,String name,double score,String reason,long now){
        String clean=KnowledgeV2Maintenance.canonicalCategory(name);if(!meaningfulCategory(clean))return;
        long categoryId=category(s,clean,now);if(categoryId<=0)return;
        ContentValues v=new ContentValues();v.put("knowledge_type",type);v.put("knowledge_id",id);v.put("category_id",categoryId);v.put("score",score);v.put("reason",reason);v.put("model_version","dynamic_category_v3");v.put("created_at",now);
        s.insertWithOnConflict("kv2_category_memberships",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static long category(SQLiteDatabase s,String name,long now){
        String canonical=KnowledgeV2Maintenance.canonicalCategory(name);
        Cursor c=s.query("kv2_categories",new String[]{"id"},"lower(canonical_name)=lower(?) AND state<>'merged'",new String[]{canonical},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();
        if(id>0){ContentValues u=new ContentValues();u.put("last_active_at",now);u.put("state","active");s.update("kv2_categories",u,"id=?",new String[]{String.valueOf(id)});return id;}
        ContentValues v=new ContentValues();v.put("canonical_name",canonical);v.put("parent_id",0);v.put("description","Dynamic category grounded in Cortex evidence");v.put("origin","emergent");v.put("confidence",.76);v.put("state","active");v.put("created_at",now);v.put("last_active_at",now);
        id=s.insertWithOnConflict("kv2_categories",null,v,SQLiteDatabase.CONFLICT_IGNORE);return id;
    }

    private static void edge(SQLiteDatabase s,String ft,long fid,String tt,long tid,String rel,double conf,long now,String reason){
        if(fid<=0||tid<=0)return;ContentValues v=new ContentValues();v.put("from_type",ft);v.put("from_id",fid);v.put("to_type",tt);v.put("to_id",tid);v.put("relation",rel);v.put("confidence",conf);v.put("valid_from",0);v.put("valid_to",0);
        JSONObject meta=new JSONObject();try{meta.put("pipeline_version",KnowledgeV2Schema.PIPELINE_VERSION);meta.put("reason",reason);}catch(Exception ignored){}v.put("metadata_json",meta.toString());v.put("created_at",now);s.insertWithOnConflict("kv2_edges",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void mark(SQLiteDatabase s,long id,String state,String error){
        long now=System.currentTimeMillis();
        if("RUNNING".equals(state)){
            s.execSQL("UPDATE kv2_processing SET state='RUNNING',attempt_count=attempt_count+1,last_error=NULL,started_at=?,updated_at=? WHERE evidence_id=? AND stage=? AND pipeline_version=?",
                    new Object[]{now,now,id,STAGE,KnowledgeV2Schema.PIPELINE_VERSION});
            return;
        }
        ContentValues v=new ContentValues();v.put("state",state);v.put("last_error",error);v.put("updated_at",now);if("DONE".equals(state))v.put("completed_at",now);
        s.update("kv2_processing",v,"evidence_id=? AND stage=? AND pipeline_version=?",new String[]{String.valueOf(id),STAGE,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
    }

    private static void markFailure(SQLiteDatabase s,long id,Throwable t){
        String e=t==null?"Unknown enrichment error":n(t.getMessage());
        s.execSQL("UPDATE kv2_processing SET state=CASE WHEN attempt_count>=3 THEN 'FAILED' ELSE 'PENDING' END,last_error=?,updated_at=? WHERE evidence_id=? AND stage=? AND pipeline_version=?",
                new Object[]{e,System.currentTimeMillis(),id,STAGE,KnowledgeV2Schema.PIPELINE_VERSION});
    }

    private static String norm(String s){return n(s).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private static String n(String s){return s==null?"":s.trim();}
}
