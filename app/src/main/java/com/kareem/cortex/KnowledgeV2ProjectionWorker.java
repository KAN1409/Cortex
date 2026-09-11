package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.JSONObject;

public final class KnowledgeV2ProjectionWorker extends Worker {
    public static final String UNIQUE_WORK_NAME="cortex-kv2-projection";

    public KnowledgeV2ProjectionWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}

    @NonNull @Override public Result doWork(){
        if(StartupSafetyGate.active())return Result.success();
        VaultDb db=new VaultDb(getApplicationContext());
        try{
            SQLiteDatabase s=db.getWritableDatabase();
            KnowledgeV2Schema.ensure(s);
            Cursor c=s.rawQuery(
                "SELECT e.id,e.source_uri,e.raw_text,e.captured_at,u.title,u.summary,u.category,u.tags "+
                "FROM kv2_evidence e JOIN kv2_understanding u ON u.evidence_id=e.id "+
                "JOIN kv2_processing p ON p.evidence_id=e.id AND p.stage=? AND p.pipeline_version=? "+
                "WHERE p.state='DONE' AND e.knowledge_eligible=1 AND e.self_reference_score<0.72 "+
                "ORDER BY e.updated_at ASC",
                new String[]{KnowledgeV2EnrichmentWorker.STAGE,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
            while(c.moveToNext()&&!isStopped()){
                long evidenceId=c.getLong(0);
                String uri=n(c.getString(1)),raw=n(c.getString(2)),title=n(c.getString(4)),summary=n(c.getString(5)),category=n(c.getString(6)),tags=n(c.getString(7));
                String fp=Fingerprint.text("kv2-projection|"+evidenceId);
                JSONObject meta=new JSONObject();
                meta.put("canonical","knowledge_v2");
                meta.put("evidence_id",evidenceId);
                meta.put("projection",true);
                long inserted=db.insert(
                        "SCREENSHOT_MEMORY",
                        "knowledge_v2",
                        title.isEmpty()?"Visual memory":title,
                        raw,
                        category,
                        tags,
                        uri,
                        fp,
                        meta.toString()
                );
                long itemId=Math.abs(inserted);
                if(inserted>0){
                    AnalysisResult r=LocalAnalyzer.analyze(raw,"text/plain");
                    r.title=title.isEmpty()?r.title:title;
                    r.summary=summary.isEmpty()?r.summary:summary;
                    r.category=category.isEmpty()?r.category:category;
                    r.tags=tags.isEmpty()?r.tags:tags;
                    r.extractedText=raw;
                    r.engine="knowledge_v2_projection";
                    r.version="1";
                    db.applyAnalysis(itemId,r);
                    CognitiveStore.linkChecked(db,"memory",itemId,"evidence",evidenceId,"PROJECTS_KNOWLEDGE_V2",1.0,"{}");
                }
            }
            c.close();
            return Result.success();
        }catch(Throwable t){return Result.retry();}
        finally{try{db.close();}catch(Throwable ignored){}}
    }

    public static void enqueue(Context c){
        if(c==null||StartupSafetyGate.active())return;
        OneTimeWorkRequest r=new OneTimeWorkRequest.Builder(KnowledgeV2ProjectionWorker.class).build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(UNIQUE_WORK_NAME,ExistingWorkPolicy.KEEP,r);
    }

    private static String n(String s){return s==null?"":s.trim();}
}
