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
        if(StartupSafetyGate.active())return Result.success();
        VaultDb db=new VaultDb(getApplicationContext());
        try{
            SQLiteDatabase s=db.getWritableDatabase();
            KnowledgeV2Schema.ensure(s);
            seedQueue(s);
            while(!isStopped()){
                long evidenceId=next(s);
                if(evidenceId<=0)break;
                try{mark(s,evidenceId,"RUNNING",null);enrich(db,s,evidenceId);mark(s,evidenceId,"DONE",null);}
                catch(Throwable t){markFailure(s,evidenceId,t);}
            }
            KnowledgeV2ProjectionWorker.enqueue(getApplicationContext());
            return Result.success();
        }catch(Throwable t){
            return Result.retry();
        }finally{try{db.close();}catch(Throwable ignored){}}
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

    private static void enrich(VaultDb db,SQLiteDatabase s,long evidenceId){
        long now=System.currentTimeMillis();
        ArrayList<Long> entities=new ArrayList<>();
        Cursor m=s.rawQuery("SELECT id,mention_kind,mention_text,normalized_text,resolution_confidence FROM kv2_entity_mentions WHERE evidence_id=? ORDER BY id ASC",
                new String[]{String.valueOf(evidenceId)});
        while(m.moveToNext()){
            long mentionId=m.getLong(0);String kind=n(m.getString(1)).toUpperCase(Locale.ROOT),name=n(m.getString(2)),norm=n(m.getString(3));
            if(name.isEmpty()||norm.isEmpty())continue;
            long entityId=resolveEntity(s,kind,name,norm,now);
            if(entityId<=0)continue;
            entities.add(entityId);
            ContentValues mv=new ContentValues();mv.put("resolved_entity_id",entityId);mv.put("resolution_confidence",.88);mv.put("resolution_method","normalized_alias");s.update("kv2_entity_mentions",mv,"id=?",new String[]{String.valueOf(mentionId)});
            edge(s,"evidence",evidenceId,"entity",entityId,"MENTIONS",.88,now);
        }
        m.close();

        for(int i=0;i<entities.size();i++)for(int j=i+1;j<entities.size();j++)if(!entities.get(i).equals(entities.get(j))){
            edge(s,"entity",entities.get(i),"entity",entities.get(j),"CO_OCCURS_WITH",.55,now);
            edge(s,"entity",entities.get(j),"entity",entities.get(i),"CO_OCCURS_WITH",.55,now);
        }

        Cursor u=s.rawQuery("SELECT category,tags,summary FROM kv2_understanding WHERE evidence_id=? LIMIT 1",new String[]{String.valueOf(evidenceId)});
        if(u.moveToFirst()){
            String category=n(u.getString(0)),tags=n(u.getString(1));
            if(!category.isEmpty())assignCategory(s,"EVIDENCE",evidenceId,category,.92,"extractor category",now);
            for(String tag:tags.split(",")){String x=tag.trim();if(x.length()>=3&&x.length()<=64)assignCategory(s,"EVIDENCE",evidenceId,x,.68,"emergent tag",now);}
        }u.close();

        Cursor facts=s.rawQuery("SELECT f.id,f.object_type,f.object_value FROM kv2_facts f JOIN kv2_fact_evidence l ON l.fact_id=f.id WHERE l.evidence_id=?",new String[]{String.valueOf(evidenceId)});
        while(facts.moveToNext()){
            long factId=facts.getLong(0);String type=n(facts.getString(1)),value=n(facts.getString(2));
            if(!type.isEmpty())assignCategory(s,"FACT",factId,type,.72,"fact type",now);
            for(long entityId:entities)edge(s,"fact",factId,"entity",entityId,"ABOUT",.62,now);
            if("MONEY".equalsIgnoreCase(type))assignCategory(s,"FACT",factId,"Money",.95,"money entity",now);
            if("DATE".equalsIgnoreCase(type))assignCategory(s,"FACT",factId,"Dates & deadlines",.88,"date entity",now);
            if("URL".equalsIgnoreCase(type))assignCategory(s,"FACT",factId,"Links & references",.84,"url entity",now);
            if("PHONE".equalsIgnoreCase(type)||"EMAIL".equalsIgnoreCase(type))assignCategory(s,"FACT",factId,"Contacts",.86,"contact entity",now);
        }facts.close();

        Cursor events=s.rawQuery("SELECT ev.id,ev.event_type,ev.title,ev.body,ev.confidence FROM kv2_events ev JOIN kv2_event_evidence ee ON ee.event_id=ev.id WHERE ee.evidence_id=?",
                new String[]{String.valueOf(evidenceId)});
        while(events.moveToNext()){
            long eventId=events.getLong(0);String type=n(events.getString(1)),title=n(events.getString(2)),body=n(events.getString(3));double conf=events.getDouble(4);
            assignCategory(s,"EVENT",eventId,"Actions & commitments",.90,"action candidate",now);
            for(long entityId:entities)edge(s,"event",eventId,"entity",entityId,"INVOLVES",.68,now);
            if(conf>=.72&&!title.isEmpty()){
                String fp=Fingerprint.text("kv2-derived-action|"+eventId+"|"+LocalSemanticEmbedder.norm(title));
                long id=CognitiveStore.addDerived(db,"ACTION",title,body,"open",Math.max(.72,conf),78,fp,
                        "{"source":"picbrain","canonical":"knowledge_v2","event_id":"+eventId+","evidence_id":"+evidenceId+"}");
                if(id>0)CognitiveStore.setDerivedRoutingChecked(db,id,"picbrain",0,0,"ACTION","kv2:event:"+eventId);
            }
        }events.close();
    }

    private static long resolveEntity(SQLiteDatabase s,String kind,String name,String norm,long now){
        Cursor a=s.rawQuery("SELECT n.id FROM entity_aliases a JOIN entity_nodes n ON n.id=a.entity_id WHERE a.normalized_alias=? AND n.status='active' ORDER BY a.confidence DESC LIMIT 1",new String[]{norm});
        long id=a.moveToFirst()?a.getLong(0):0;a.close();
        if(id>0)return id;
        String key="kv2|"+kind.toLowerCase(Locale.ROOT)+"|"+norm;
        Cursor c=s.query("entity_nodes",new String[]{"id"},"normalized_key=?",new String[]{key},null,null,null,"1");id=c.moveToFirst()?c.getLong(0):0;c.close();
        if(id<=0){
            ContentValues v=new ContentValues();v.put("kind",kind);v.put("canonical_name",name);v.put("normalized_key",key);v.put("status","active");v.put("metadata_json","{"source":"knowledge_v2","resolution":"normalized"}");v.put("created_at",now);v.put("updated_at",now);
            id=s.insertWithOnConflict("entity_nodes",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            if(id<=0){Cursor x=s.query("entity_nodes",new String[]{"id"},"normalized_key=?",new String[]{key},null,null,null,"1");id=x.moveToFirst()?x.getLong(0):0;x.close();}
        }
        if(id>0){ContentValues av=new ContentValues();av.put("entity_id",id);av.put("source","knowledge_v2");av.put("alias",name);av.put("normalized_alias",norm);av.put("confidence",.88);av.put("metadata_json","{}");av.put("created_at",now);s.insertWithOnConflict("entity_aliases",null,av,SQLiteDatabase.CONFLICT_IGNORE);}
        return id;
    }

    private static void assignCategory(SQLiteDatabase s,String type,long id,String name,double score,String reason,long now){
        String clean=name.replaceAll("\\s+"," ").trim();if(clean.length()<2)return;
        long categoryId=category(s,clean,now);if(categoryId<=0)return;
        ContentValues v=new ContentValues();v.put("knowledge_type",type);v.put("knowledge_id",id);v.put("category_id",categoryId);v.put("score",score);v.put("reason",reason);v.put("model_version","dynamic_category_v1");v.put("created_at",now);
        s.insertWithOnConflict("kv2_category_memberships",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static long category(SQLiteDatabase s,String name,long now){
        Cursor c=s.query("kv2_categories",new String[]{"id"},"lower(canonical_name)=lower(?)",new String[]{name},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();
        if(id>0){ContentValues u=new ContentValues();u.put("last_active_at",now);s.update("kv2_categories",u,"id=?",new String[]{String.valueOf(id)});return id;}
        ContentValues v=new ContentValues();v.put("canonical_name",name);v.put("parent_id",0);v.put("description","Emergent category learned from Cortex evidence");v.put("origin","emergent");v.put("confidence",.72);v.put("state","active");v.put("created_at",now);v.put("last_active_at",now);
        id=s.insertWithOnConflict("kv2_categories",null,v,SQLiteDatabase.CONFLICT_IGNORE);return id;
    }

    private static void edge(SQLiteDatabase s,String ft,long fid,String tt,long tid,String rel,double conf,long now){
        if(fid<=0||tid<=0)return;ContentValues v=new ContentValues();v.put("from_type",ft);v.put("from_id",fid);v.put("to_type",tt);v.put("to_id",tid);v.put("relation",rel);v.put("confidence",conf);v.put("valid_from",0);v.put("valid_to",0);v.put("metadata_json","{}");v.put("created_at",now);s.insertWithOnConflict("kv2_edges",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void mark(SQLiteDatabase s,long id,String state,String error){
        ContentValues v=new ContentValues();v.put("state",state);v.put("last_error",error);v.put("updated_at",System.currentTimeMillis());if("RUNNING".equals(state)){v.put("started_at",System.currentTimeMillis());v.put("attempt_count","attempt_count+1");}
        if("DONE".equals(state))v.put("completed_at",System.currentTimeMillis());
        s.update("kv2_processing",v,"evidence_id=? AND stage=? AND pipeline_version=?",new String[]{String.valueOf(id),STAGE,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
        if("RUNNING".equals(state))s.execSQL("UPDATE kv2_processing SET attempt_count=attempt_count+1 WHERE evidence_id=? AND stage=? AND pipeline_version=?",new Object[]{id,STAGE,KnowledgeV2Schema.PIPELINE_VERSION});
    }
    private static void markFailure(SQLiteDatabase s,long id,Throwable t){String e=t==null?"Unknown enrichment error":n(t.getMessage());s.execSQL("UPDATE kv2_processing SET state=CASE WHEN attempt_count>=3 THEN 'FAILED' ELSE 'PENDING' END,last_error=?,updated_at=? WHERE evidence_id=? AND stage=? AND pipeline_version=?",new Object[]{e,System.currentTimeMillis(),id,STAGE,KnowledgeV2Schema.PIPELINE_VERSION});}
    private static String n(String s){return s==null?"":s.trim();}
}
