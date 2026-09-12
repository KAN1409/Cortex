package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.core.content.FileProvider;
import android.net.Uri;
import com.kareem.cortex.visualmemory.VisualMemoryRuntime;
import com.kareem.cortex.visualmemory.VisualMemoryStats;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Bounded source -> understanding -> judgment -> surface diagnostic export. */
public final class CortexDiagnosticExporter {
    public static final String VERSION="cortex_diagnostic_004";
    private CortexDiagnosticExporter(){}
    public static final class Exported {public final File file;public final Uri uri;public final int sections;Exported(File f,Uri u,int s){file=f;uri=u;sections=s;}}

    public static Exported export(Context context)throws Exception{
        Context app=context.getApplicationContext();VaultDb vault=new VaultDb(app);
        try{
            CognitiveStore.ensure(vault);SQLiteDatabase db=vault.getWritableDatabase();UniversalEventStore.ensure(db);CortexJudgmentTraceStore.ensure(db);CanonicalStateStore.ensure(db);CortexV91Authority.enforceLive(db);
            JSONObject active=CortexPersonalPolicy.current(app);
            CortexV91Authority.Validation authority=CortexV91Authority.validate(db,active.optString("version","local"),active.optInt("maxNowItems",7));
            JSONObject root=new JSONObject();root.put("schemaVersion",VERSION);root.put("generatedAt",System.currentTimeMillis());root.put("package",app.getPackageName());root.put("versionName",BuildConfig.VERSION_NAME);root.put("versionCode",BuildConfig.VERSION_CODE);root.put("activePolicy",active);
            JSONObject lifecycle=new JSONObject().put("state",CortexPolicyLifecycle.state(app)).put("reason",CortexPolicyLifecycle.reason(app)).put("promotion",CortexPolicyPromotion.status(app)).put("teacherImpactHistorical",CortexTeacherImpact.latest(app)).put("teacherImpactLive",CortexTeacherImpact.liveCurrent(app));root.put("policyLifecycle",lifecycle);
            int sections=0;sections+=putTable(root,db,"rawObservations","ue_raw_observations",500);sections+=putTable(root,db,"semanticEvents","ue_semantic_events",500);sections+=putTable(root,db,"canonicalStates","ue_canonical_states",500);sections+=putTable(root,db,"pipelineStages","ue_pipeline_stages",500);sections+=putTable(root,db,"projectionDecisions","ue_projection_decisions",300);sections+=putTable(root,db,"projectionRevisions","ue_projection_revisions",150);sections+=putTable(root,db,"judgmentTrace","cortex_judgment_trace",300);sections+=putTable(root,db,"sourceLinks","source_links",300);sections+=putTable(root,db,"knowledgeItems","knowledge_items",300);sections+=putTable(root,db,"derivedItems","derived_items",250);sections+=putTable(root,db,"attentionItems","ue_attention_items",250);sections+=putTable(root,db,"situations","ue_situations",250);sections+=putTable(root,db,"memoryPromotions","ue_memory_promotions",250);sections+=putTable(root,db,"legacyClassification","ue_legacy_classification",250);

            long raw=count(db,"ue_raw_observations"),semantic=count(db,"ue_semantic_events"),knowledge=count(db,"knowledge_items"),judgment=count(db,"cortex_judgment_trace"),attention=countWhere(db,"ue_attention_items","state='open'"),situations=countWhere(db,"ue_situations","state='open'"),memory=countWhere(db,"ue_memory_promotions","state='promoted'"),stages=count(db,"ue_pipeline_stages"),derived=count(db,"derived_items"),nowJudgments=countWhere(db,"cortex_judgment_trace","final_decision='NOW'"),promotable=CanonicalMemoryPromoter.countPromotable(db),supported=countWhere(db,"ue_canonical_states","canonical_state IN ('supported','verified')"),rejected=countWhere(db,"ue_canonical_states","canonical_state='rejected'");
            JSONObject counts=new JSONObject().put("rawObservations",raw).put("semanticEvents",semantic).put("canonicalSupported",supported).put("canonicalRejected",rejected).put("pipelineStages",stages).put("knowledgeItems",knowledge).put("derivedItems",derived).put("activeSituations",situations).put("openAttentionItems",attention).put("memoryPromotions",memory).put("judgmentTrace",judgment).put("nowJudgments",nowJudgments).put("promotableSemanticEvents",promotable)
                    .put("openVisualLegacyActions",authority.openVisualLegacyActions).put("nonIntentionalProjectCandidates",authority.nonIntentionalProjectCandidates).put("invalidOpenSituations",authority.invalidOpenSituations).put("unauthorizedOpenAttention",authority.unauthorizedOpenAttention).put("visualOpenAttention",authority.visualOpenAttention).put("policyMismatches",authority.policyMismatches).put("attentionMirrorMismatches",authority.mirrorMismatches);root.put("tableCounts",counts);
            JSONObject visual=visualHealth(app);root.put("visualMemoryHealth",visual);
            JSONObject health=pipelineHealth(raw,semantic,knowledge,stages,situations,attention,judgment,nowJudgments,promotable,memory,visual.optBoolean("modelInstalled",true),visual.optLong("semanticPending",0));
            if(!authority.ok){health.put("state","DEGRADED_AUTHORITY_INVARIANT").put("reason",authority.summary());}
            root.put("pipelineHealth",health);root.put("authorityHealth",authority.toJson());
            root.put("authority",new JSONObject().put("legacyClassification","ARCHIVE_ONLY").put("evidenceAttentionAuthority","NONE").put("triageAuthority",AttentionDecisionEngine.VERSION).put("finalAttentionAuthority",CortexAttentionJudge.VERSION).put("nowReadAuthority","UE_ATTENTION_FINAL_JUDGE_ONLY").put("visualEvidenceObligationAuthority","NONE"));
            root.put("note","Contains bounded recent source content and derived state for debugging. Share only when you intend to expose this diagnostic data.");
            File dir=new File(app.getFilesDir(),"debug_exports");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Could not create debug export directory");File file=new File(dir,"Cortex_Source_Understood_Shown_"+System.currentTimeMillis()+".json");try(FileOutputStream out=new FileOutputStream(file)){out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));}Uri uri=FileProvider.getUriForFile(app,app.getPackageName()+".feedback.files",file);return new Exported(file,uri,sections);
        }finally{try{vault.close();}catch(Throwable ignored){}}
    }

    static JSONObject pipelineHealth(long raw,long semantic,long knowledge,long stages)throws Exception{
        String state="HEALTHY",reason="canonical evidence and semantic event flow observed";if(knowledge>0&&raw==0){state="DEGRADED_NO_CANONICAL_INGEST";reason="knowledge exists but canonical raw observations are empty";}else if(raw>0&&semantic==0){state="DEGRADED_SEMANTIC_STALLED";reason="raw observations exist but no semantic events were produced";}else if(raw>0&&stages==0){state="DEGRADED_NO_STAGE_TRACE";reason="canonical observations exist without pipeline stage observability";}return new JSONObject().put("state",state).put("reason",reason).put("rawObservations",raw).put("semanticEvents",semantic).put("pipelineStages",stages).put("knowledgeItems",knowledge);
    }

    static JSONObject pipelineHealth(long raw,long semantic,long knowledge,long stages,long situations,long attention,long judgment,long nowJudgments,long promotable,long memory,boolean semanticModelInstalled,long semanticPending)throws Exception{
        JSONObject base=pipelineHealth(raw,semantic,knowledge,stages);String state=base.getString("state"),reason=base.getString("reason");if("HEALTHY".equals(state)){if(situations>0&&judgment==0){state="DEGRADED_JUDGMENT_STALLED";reason="active situations exist without final judgment traces";}else if(nowJudgments>0&&attention==0){state="DEGRADED_ATTENTION_NOT_MATERIALIZED";reason="final NOW judgments exist but canonical attention ledger is empty";}else if(promotable>0&&memory==0){state="DEGRADED_MEMORY_PROMOTION_STALLED";reason="durable supported semantics exist but none were promoted";}else if(!semanticModelInstalled&&semanticPending>0){state="DEGRADED_VISUAL_SEMANTIC_MODEL_MISSING";reason="visual semantic backlog exists while the embedding model is unavailable";}}
        JSONObject stagesJson=new JSONObject().put("ingest",raw>0?"HEALTHY":(knowledge>0?"DEGRADED":"IDLE")).put("semantic",semantic>0?"HEALTHY":(raw>0?"DEGRADED":"IDLE")).put("worldState",situations>0?"ACTIVE":"IDLE").put("judgment",judgment>0?"ACTIVE":"IDLE").put("attention",nowJudgments>0?(attention>0?"HEALTHY":"DEGRADED"):"QUIET").put("memory",promotable>0?(memory>0?"HEALTHY":"DEGRADED"):"NO_ELIGIBLE_FACTS").put("visualSemantic",semanticPending>0?(semanticModelInstalled?"INDEXING":"MODEL_MISSING"):"HEALTHY");
        return base.put("state",state).put("reason",reason).put("activeSituations",situations).put("openAttentionItems",attention).put("judgmentTrace",judgment).put("nowJudgments",nowJudgments).put("promotableSemanticEvents",promotable).put("memoryPromotions",memory).put("stages",stagesJson);
    }

    private static JSONObject visualHealth(Context app){try{VisualMemoryStats s=VisualMemoryRuntime.stats(app);return new JSONObject().put("modelInstalled",s.getModelInstalled()).put("semanticIndexed",s.getSemanticIndexed()).put("semanticPending",s.getSemanticPending()).put("semanticFailed",s.getSemanticFailed()).put("semanticSkipped",s.getSemanticSkipped()).put("ocrReady",s.getOcrReady()).put("ocrPending",s.getOcrPending()).put("ocrFailed",s.getOcrFailed());}catch(Throwable t){try{return new JSONObject().put("error",t.getClass().getSimpleName());}catch(Exception ignored){return new JSONObject();}}}
    private static int putTable(JSONObject root,SQLiteDatabase db,String key,String table,int limit)throws Exception{if(!exists(db,table)){root.put(key,new JSONArray());return 0;}JSONArray rows=new JSONArray();Cursor c=null;try{String order=hasColumn(db,table,"id")?" ORDER BY id DESC":"";c=db.rawQuery("SELECT * FROM \""+table.replace("\"","")+"\""+order+" LIMIT "+Math.max(1,limit),null);String[] names=c.getColumnNames();while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<names.length;i++){int type=c.getType(i);if(type==Cursor.FIELD_TYPE_NULL)row.put(names[i],JSONObject.NULL);else if(type==Cursor.FIELD_TYPE_INTEGER)row.put(names[i],c.getLong(i));else if(type==Cursor.FIELD_TYPE_FLOAT)row.put(names[i],c.getDouble(i));else if(type==Cursor.FIELD_TYPE_BLOB)row.put(names[i],"<blob:"+c.getBlob(i).length+" bytes>");else row.put(names[i],clip(c.getString(i),12000));}rows.put(row);}}finally{if(c!=null)c.close();}root.put(key,rows);return 1;}
    private static boolean exists(SQLiteDatabase db,String table){Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{table});try{return c.moveToFirst();}finally{c.close();}}
    private static boolean hasColumn(SQLiteDatabase db,String table,String column){Cursor c=db.rawQuery("PRAGMA table_info(\""+table.replace("\"","")+"\")",null);try{while(c.moveToNext())if(column.equals(c.getString(1)))return true;return false;}finally{c.close();}}
    private static long count(SQLiteDatabase db,String table){if(!exists(db,table))return 0;Cursor c=db.rawQuery("SELECT COUNT(*) FROM \""+table.replace("\"","")+"\"",null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static long countWhere(SQLiteDatabase db,String table,String where){if(!exists(db,table))return 0;Cursor c=db.rawQuery("SELECT COUNT(*) FROM \""+table.replace("\"","")+"\" WHERE "+where,null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static String clip(String s,int n){if(s==null)return"";return s.length()<=n?s:s.substring(0,n)+"…";}
}
