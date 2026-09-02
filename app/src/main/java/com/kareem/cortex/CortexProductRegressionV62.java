package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.util.Locale;

/** Permanent regressions for product repairs discovered by the v61 report. */
public final class CortexProductRegressionV62 {
    private CortexProductRegressionV62(){}

    public static JSONArray run(Context context,VaultDb db,String runId)throws Exception{
        JSONArray rows=new JSONArray();
        routing(rows);
        providerCircuit(rows,context.getApplicationContext());
        asrScriptSanity(rows);
        transactional(rows,context.getApplicationContext(),db,runId);
        return rows;
    }

    private static void routing(JSONArray rows)throws Exception{
        row(rows,"R62-BRAIN","Global attention remains operational",AskOperationalEngine.globalAttentionForDiagnostics("What should I do?"),"A truly global attention question must still use live operational state",new JSONObject().put("question","What should I do?"));
        row(rows,"R62-BRAIN","Global attention allows day context",AskOperationalEngine.globalAttentionForDiagnostics("What needs my attention today?"),"Today/now wording must not disable the global attention route",new JSONObject().put("question","What needs my attention today?"));
        row(rows,"R62-BRAIN","Scoped English question bypasses global attention",!AskOperationalEngine.globalAttentionForDiagnostics("What should I do for Orion search latency?"),"A named topic/project must continue to semantic grounded retrieval",new JSONObject().put("question","What should I do for Orion search latency?"));
        row(rows,"R62-BRAIN","Scoped Arabic question bypasses global attention",!AskOperationalEngine.globalAttentionForDiagnostics("محتاج أعمل ايه في مشروع Atlas؟"),"A named Arabic project/topic question must continue to semantic grounded retrieval",new JSONObject().put("question","محتاج أعمل ايه في مشروع Atlas؟"));
    }

    private static void providerCircuit(JSONArray rows,Context c)throws Exception{
        String key="r62_diagnostic_provider";ExternalProviderHealthStore.Snapshot before=ExternalProviderHealthStore.snapshot(c,key);
        try{
            ExternalBrainProvider.ProviderException rate=new ExternalBrainProvider.ProviderException("synthetic 429",429,"diagnostic",key,12);
            long wait=ExternalProviderHealthStore.noteFailure(c,key,rate);ExternalProviderHealthStore.Snapshot limited=ExternalProviderHealthStore.snapshot(c,key);
            row(rows,"R62-PROVIDER","429 opens provider circuit breaker",wait>0&&ExternalProviderHealthStore.cooling(c,key)&&"rate_limited".equals(limited.status),"A rate-limited provider must enter cooldown so repeated Brain requests skip it",new JSONObject().put("wait_ms",wait).put("status",limited.status).put("http",limited.httpCode));
            ExternalProviderHealthStore.noteSuccess(c,key,7);ExternalProviderHealthStore.Snapshot healthy=ExternalProviderHealthStore.snapshot(c,key);
            row(rows,"R62-PROVIDER","Provider success closes circuit breaker",!ExternalProviderHealthStore.cooling(c,key)&&"healthy".equals(healthy.status)&&healthy.httpCode==200,"A successful provider response must clear cooldown and restore healthy routing",new JSONObject().put("status",healthy.status).put("http",healthy.httpCode).put("latency_ms",healthy.latencyMs));
        }finally{ExternalProviderHealthStore.restore(c,before);}
    }

    private static void asrScriptSanity(JSONArray rows)throws Exception{
        Method m=AudioAnalyzer.class.getDeclaredMethod("acceptabilityWarning",TranscriptResult.class);m.setAccessible(true);
        TranscriptResult wrongEnglish=new TranscriptResult();wrongEnglish.text="اشتركوا في القناة من فضلكم";wrongEnglish.language="English";wrongEnglish.durationMs=2000;wrongEnglish.processedDurationMs=2000;String en=(String)m.invoke(null,wrongEnglish);
        row(rows,"R62-ASR","English label rejects Arabic-only hallucination",en!=null&&en.contains("declared English"),"An ASR candidate declared English must not silently pass when its transcript is overwhelmingly Arabic script",new JSONObject().put("warning",safe(en)).put("text",wrongEnglish.text).put("language",wrongEnglish.language));

        TranscriptResult wrongArabic=new TranscriptResult();wrongArabic.text="please verify the fallback latency tomorrow";wrongArabic.language="Arabic";wrongArabic.durationMs=2000;wrongArabic.processedDurationMs=2000;String ar=(String)m.invoke(null,wrongArabic);
        row(rows,"R62-ASR","Arabic label rejects Latin-only hallucination",ar!=null&&ar.contains("declared Arabic"),"An ASR candidate declared Arabic must not silently pass when its transcript is overwhelmingly Latin script",new JSONObject().put("warning",safe(ar)).put("text",wrongArabic.text).put("language",wrongArabic.language));

        TranscriptResult mixed=new TranscriptResult();mixed.text="راجع Cortex fallback latency بكرة";mixed.language="English";mixed.durationMs=2000;mixed.processedDurationMs=2000;String mix=(String)m.invoke(null,mixed);
        row(rows,"R62-ASR","Code-switch transcript remains eligible",mix==null,"Arabic/English code-switching must stay eligible; script sanity only rejects overwhelming contradictions",new JSONObject().put("warning",safe(mix)).put("text",mixed.text).put("language",mixed.language));
    }

    private static void transactional(JSONArray rows,Context c,VaultDb db,String runId)throws Exception{
        SQLiteDatabase sql=db.getWritableDatabase();boolean tx=false;
        try{
            sql.beginTransaction();tx=true;
            String token="R62_"+runId+"_"+Long.toHexString(System.nanoTime());

            String text=token+" Project Orion search latency. Next action: verify fallback latency and prepare the summary.";
            long id=db.insert("TEXT","diagnostic_rollback","R62 Orion focal",text,"Diagnostics","r62,brain","",Fingerprint.text(token+"|brain"),new JSONObject().put("synthetic",true).put("run_id",runId).toString());
            db.applyAnalysis(id,LocalAnalyzer.analyze(text,"text/plain"));
            GroundedAnswer answer=SecondBrainEngine.ask(db,"What should I do for "+token+" Orion search latency?");
            boolean found=false;long first=0;JSONArray src=new JSONArray();
            for(int i=0;i<answer.sources.size();i++){SemanticHit h=answer.sources.get(i);if(h==null||h.item==null)continue;if(i==0)first=h.item.id;if(h.item.id==id)found=true;src.put(new JSONObject().put("id",h.item.id).put("title",safe(h.item.title)).put("score",h.score));}
            row(rows,"R62-BRAIN","Scoped semantic question grounds requested topic",found,"Topic-specific 'what should I do' must retrieve the matching Evidence instead of global attention state",new JSONObject().put("seed_id",id).put("first_source_id",first).put("found",found).put("answer",safe(answer.answer)).put("sources",src));

            // Isolate the real screenshot worker policy inside this rollback transaction.
            ContentValues hold=new ContentValues();hold.put("status","r62_hold");sql.update("knowledge_items",hold,"status='queued' AND type IN ('SCREENSHOT','IMAGE')",null);
            long now=System.currentTimeMillis();
            long oldShot=db.insert("SCREENSHOT","screenshot-folder","R62 old screenshot","","Screenshots & Images","r62,shot","/dev/null",Fingerprint.text(token+"|old"),new JSONObject().put("source_modified",now-10L*24L*60L*60L*1000L).put("r62",true).toString());
            long freshShot=db.insert("SCREENSHOT","screenshot-folder","R62 fresh screenshot","","Screenshots & Images","r62,shot","/dev/null",Fingerprint.text(token+"|fresh"),new JSONObject().put("source_modified",now).put("r62",true).toString());
            long foreground=db.insert("IMAGE","manual","R62 foreground image","","Screenshots & Images","r62,foreground","/dev/null",Fingerprint.text(token+"|foreground"),new JSONObject().put("r62",true).toString());

            KnowledgeItem firstItem=ScreenshotAnalysisWorker.nextPrioritized(db);
            row(rows,"R62-QUEUE","Real OCR worker foreground lane wins",firstItem!=null&&firstItem.id==foreground,"The actual ScreenshotAnalysisWorker must process direct/manual image evidence before screenshot-folder catch-up",new JSONObject().put("expected",foreground).put("actual",firstItem==null?0:firstItem.id).put("fresh_screenshot",freshShot).put("old_screenshot",oldShot));

            if(firstItem!=null)db.markAnalyzing(firstItem.id);
            KnowledgeItem secondItem=ScreenshotAnalysisWorker.nextPrioritized(db);
            row(rows,"R62-QUEUE","Real OCR worker newest screenshot wins",secondItem!=null&&secondItem.id==freshShot,"After foreground work, the actual OCR worker must choose the newest original screenshot, not the oldest import row",new JSONObject().put("expected",freshShot).put("actual",secondItem==null?0:secondItem.id).put("old_screenshot",oldShot));

            ScreenshotAnalysisWorker.BacklogStats stats=ScreenshotAnalysisWorker.backlog(db);
            row(rows,"R62-QUEUE","Screenshot backlog telemetry is measurable",stats.total>=2&&stats.screenshots>=2,"The real queue must expose bounded counts and age anchors so backlog growth is observable",new JSONObject().put("total",stats.total).put("foreground",stats.foreground).put("screenshots",stats.screenshots).put("newest_at",stats.newestAt).put("oldest_at",stats.oldestAt));
        }finally{if(tx)try{sql.endTransaction();}catch(Throwable ignored){}}
    }

    private static void row(JSONArray rows,String category,String name,boolean pass,String expected,JSONObject evidence)throws Exception{
        rows.put(new JSONObject().put("id",String.format(Locale.US,"R62-%03d",rows.length()+1)).put("category",category).put("name",name).put("status",pass?CortexExhaustiveVerificationSuite.EXECUTED_PASS:CortexExhaustiveVerificationSuite.EXECUTED_FAIL).put("expected",expected).put("evidence",evidence));
    }
    private static String safe(String s){return s==null?"":s;}
}
