package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.util.Locale;

public final class KnowledgeV2ExtractionWorker extends Worker {
    public static final String UNIQUE_WORK_NAME="cortex-kv2-extraction";
    private static final int BATCH_SIZE=24;

    public KnowledgeV2ExtractionWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}

    @NonNull @Override public Result doWork(){
        if(StartupSafetyGate.active())return Result.success();
        VaultDb db=new VaultDb(getApplicationContext());
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            KnowledgeV2Schema.ensure(sql);
            int processed=0;
            while(processed<BATCH_SIZE&&!isStopped()){
                Evidence e=nextPending(sql);
                if(e==null)break;
                try{
                    markRunning(sql,e.id);
                    extract(sql,e);
                    markDone(sql,e.id);
                }catch(Throwable t){
                    markFailed(sql,e.id,t);
                }
                processed++;
            }
            if(hasPending(sql)) KnowledgeV2Scheduler.enqueue(getApplicationContext());
            return Result.success();
        }catch(Throwable t){
            return Result.retry();
        }finally{
            try{db.close();}catch(Throwable ignored){}
        }
    }

    private static Evidence nextPending(SQLiteDatabase sql){
        Cursor c=sql.rawQuery(
                "SELECT e.id,e.raw_text,e.observed_at,e.derivation_depth FROM kv2_evidence e "+
                "JOIN kv2_processing p ON p.evidence_id=e.id "+
                "WHERE p.stage=? AND p.pipeline_version=? AND p.state='PENDING' "+
                "AND e.knowledge_eligible=1 AND e.self_reference_score<0.72 "+
                "ORDER BY e.observed_at ASC LIMIT 1",
                new String[]{KnowledgeV2Store.STAGE_EXTRACTION,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
        Evidence out=null;
        if(c.moveToFirst())out=new Evidence(c.getLong(0),n(c.getString(1)),c.getLong(2),c.getInt(3));
        c.close();
        return out;
    }

    private static void extract(SQLiteDatabase sql,Evidence e){
        AnalysisResult r=LocalAnalyzer.analyze(e.rawText,"text/plain");
        long now=System.currentTimeMillis();

        sql.beginTransaction();
        try{
            ContentValues u=new ContentValues();
            u.put("evidence_id",e.id);
            u.put("title",n(r.title));
            u.put("summary",n(r.summary));
            u.put("category",n(r.category));
            u.put("tags",n(r.tags));
            u.put("engine",n(r.engine));
            u.put("extraction_version",KnowledgeV2Schema.PIPELINE_VERSION);
            u.put("confidence",0.80);
            u.put("created_at",now);
            u.put("updated_at",now);
            sql.insertWithOnConflict("kv2_understanding",null,u,SQLiteDatabase.CONFLICT_REPLACE);

            sql.delete("kv2_entity_mentions","evidence_id=?",new String[]{String.valueOf(e.id)});

            for(AnalysisResult.Entity ent:r.entities){
                String kind=n(ent.kind).toUpperCase(Locale.ROOT);
                String value=n(ent.value);
                if(value.isEmpty())continue;

                ContentValues mention=new ContentValues();
                mention.put("evidence_id",e.id);
                mention.put("mention_kind",kind);
                mention.put("mention_text",value);
                mention.put("normalized_text",norm(value));
                mention.put("resolved_entity_id",0);
                mention.put("resolution_confidence",0);
                mention.put("resolution_method","");
                mention.put("created_at",now);
                sql.insert("kv2_entity_mentions",null,mention);

                String predicate="MENTIONS_"+kind;
                String fp=Fingerprint.text("kv2-fact|"+e.id+"|"+predicate+"|"+norm(value));
                long factId=upsertFact(sql,e,predicate,kind,value,ent.confidence,fp,now);
                linkFact(sql,factId,e.id,Math.max(.5,ent.confidence),now);
            }

            for(AnalysisResult.Action action:r.actions){
                String text=n(action.text);
                if(text.isEmpty())continue;
                String fp=Fingerprint.text("kv2-event|"+e.id+"|action|"+norm(text)+"|"+norm(action.dueText));
                long eventId=upsertEvent(sql,e,text,n(action.dueText),fp,now);
                linkEvent(sql,eventId,e.id,.80,now);
            }

            sql.setTransactionSuccessful();
        }finally{
            sql.endTransaction();
        }
    }

    private static long upsertFact(SQLiteDatabase sql,Evidence e,String predicate,String objectType,String value,double confidence,String fingerprint,long now){
        ContentValues v=new ContentValues();
        v.put("subject_type","EVIDENCE");
        v.put("subject_key","evidence:"+e.id);
        v.put("predicate",predicate);
        v.put("object_type",objectType);
        v.put("object_value",value);
        v.put("confidence",clamp(confidence));
        v.put("state","active");
        v.put("valid_from",0);
        v.put("valid_to",0);
        v.put("observed_at",e.observedAt);
        v.put("extraction_version",KnowledgeV2Schema.PIPELINE_VERSION);
        v.put("fingerprint",fingerprint);
        JSONObject meta=new JSONObject();
        try{meta.put("derivation_depth",e.derivationDepth+1);meta.put("source","local_analyzer");}catch(Exception ignored){}
        v.put("metadata_json",meta.toString());
        v.put("created_at",now);
        v.put("updated_at",now);
        long id=sql.insertWithOnConflict("kv2_facts",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id>0)return id;
        Cursor c=sql.query("kv2_facts",new String[]{"id"},"fingerprint=?",new String[]{fingerprint},null,null,null,"1");
        long existing=c.moveToFirst()?c.getLong(0):0;c.close();return existing;
    }

    private static long upsertEvent(SQLiteDatabase sql,Evidence e,String text,String due,String fingerprint,long now){
        ContentValues v=new ContentValues();
        v.put("event_type","ACTION_CANDIDATE");
        v.put("title",text);
        v.put("body",due.isEmpty()?text:text+" • due: "+due);
        v.put("status","observed");
        v.put("event_time_start",0);
        v.put("event_time_end",0);
        v.put("observed_at",e.observedAt);
        v.put("confidence",0.80);
        v.put("fingerprint",fingerprint);
        JSONObject meta=new JSONObject();
        try{meta.put("due_text",due);meta.put("derivation_depth",e.derivationDepth+1);}catch(Exception ignored){}
        v.put("metadata_json",meta.toString());
        v.put("created_at",now);
        v.put("updated_at",now);
        long id=sql.insertWithOnConflict("kv2_events",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id>0)return id;
        Cursor c=sql.query("kv2_events",new String[]{"id"},"fingerprint=?",new String[]{fingerprint},null,null,null,"1");
        long existing=c.moveToFirst()?c.getLong(0):0;c.close();return existing;
    }

    private static void linkFact(SQLiteDatabase sql,long factId,long evidenceId,double confidence,long now){
        if(factId<=0)return;
        ContentValues v=new ContentValues();v.put("fact_id",factId);v.put("evidence_id",evidenceId);v.put("relation","DERIVED_FROM");v.put("confidence",clamp(confidence));v.put("created_at",now);
        sql.insertWithOnConflict("kv2_fact_evidence",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void linkEvent(SQLiteDatabase sql,long eventId,long evidenceId,double confidence,long now){
        if(eventId<=0)return;
        ContentValues v=new ContentValues();v.put("event_id",eventId);v.put("evidence_id",evidenceId);v.put("relation","DERIVED_FROM");v.put("confidence",clamp(confidence));v.put("created_at",now);
        sql.insertWithOnConflict("kv2_event_evidence",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void markRunning(SQLiteDatabase sql,long evidenceId){
        ContentValues v=new ContentValues();v.put("state","RUNNING");v.put("attempt_count","attempt_count+1");v.put("started_at",System.currentTimeMillis());v.put("updated_at",System.currentTimeMillis());
        sql.execSQL("UPDATE kv2_processing SET state='RUNNING',attempt_count=attempt_count+1,started_at=?,updated_at=? WHERE evidence_id=? AND stage=? AND pipeline_version=?",
                new Object[]{System.currentTimeMillis(),System.currentTimeMillis(),evidenceId,KnowledgeV2Store.STAGE_EXTRACTION,KnowledgeV2Schema.PIPELINE_VERSION});
    }

    private static void markDone(SQLiteDatabase sql,long evidenceId){
        sql.execSQL("UPDATE kv2_processing SET state='DONE',last_error=NULL,completed_at=?,updated_at=? WHERE evidence_id=? AND stage=? AND pipeline_version=?",
                new Object[]{System.currentTimeMillis(),System.currentTimeMillis(),evidenceId,KnowledgeV2Store.STAGE_EXTRACTION,KnowledgeV2Schema.PIPELINE_VERSION});
    }

    private static void markFailed(SQLiteDatabase sql,long evidenceId,Throwable t){
        String msg=t==null?"Unknown extraction error":n(t.getMessage());
        if(msg.isEmpty()&&t!=null)msg=t.getClass().getSimpleName();
        sql.execSQL("UPDATE kv2_processing SET state=CASE WHEN attempt_count>=3 THEN 'FAILED' ELSE 'PENDING' END,last_error=?,updated_at=? WHERE evidence_id=? AND stage=? AND pipeline_version=?",
                new Object[]{msg,System.currentTimeMillis(),evidenceId,KnowledgeV2Store.STAGE_EXTRACTION,KnowledgeV2Schema.PIPELINE_VERSION});
    }

    private static boolean hasPending(SQLiteDatabase sql){
        Cursor c=sql.rawQuery("SELECT 1 FROM kv2_processing WHERE stage=? AND pipeline_version=? AND state='PENDING' LIMIT 1",
                new String[]{KnowledgeV2Store.STAGE_EXTRACTION,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
        boolean yes=c.moveToFirst();c.close();return yes;
    }

    private static String norm(String s){return n(s).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static String n(String s){return s==null?"":s.trim();}

    private static final class Evidence{
        final long id,observedAt;final String rawText;final int derivationDepth;
        Evidence(long id,String rawText,long observedAt,int derivationDepth){this.id=id;this.rawText=rawText;this.observedAt=observedAt;this.derivationDepth=derivationDepth;}
    }
}
