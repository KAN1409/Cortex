package com.kareem.cortex;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Permanent product regressions added after the v60 exhaustive run exposed false actions,
 * lost focal grounding, impossible ASR coverage and screenshot-backlog priority inversion.
 *
 * These tests deliberately exercise production code paths. No assertion is allowed to rewrite,
 * normalize or forgive a product failure after the fact.
 */
public final class CortexProductRegressionV61 {
    private CortexProductRegressionV61(){}

    public static JSONArray run(Context context,VaultDb db,String runId)throws Exception{
        JSONArray rows=new JSONArray();

        action(rows,"English positive action","I need to call Ahmed tomorrow.",true);
        action(rows,"English negative action","I don't need to call Ahmed.",false);
        action(rows,"English direct prohibition","Don't call Ahmed.",false);
        action(rows,"English reminder exception","Don't forget to call Ahmed tomorrow.",true);
        action(rows,"Arabic positive action","لازم أكلم أحمد بكرة.",true);
        action(rows,"Arabic negative action","مش لازم أكلم أحمد.",false);
        action(rows,"Arabic direct prohibition","ماتكلمش أحمد.",false);
        action(rows,"Arabic reminder exception","متنساش أكلم أحمد بكرة.",true);
        contrast(rows);
        asrCoverage(rows);
        providerClassification(rows);
        transactionalProductPaths(rows,context.getApplicationContext(),db,runId);
        return rows;
    }

    private static void action(JSONArray rows,String name,String text,boolean expectAction)throws Exception{
        AnalysisResult r=LocalAnalyzer.analyze(text,"text/plain");boolean has=r!=null&&r.actions!=null&&!r.actions.isEmpty();
        row(rows,"R61-ACTION",name,has==expectAction,
            expectAction?"Positive intent must create an action":"Negated intent must remain evidence and create zero actions",
            new JSONObject().put("text",text).put("expected_action",expectAction).put("actual_action_count",r==null||r.actions==null?0:r.actions.size()).put("analysis",r==null?JSONObject.NULL:new JSONObject(r.toJson())));
    }

    private static void contrast(JSONArray rows)throws Exception{
        AnalysisResult r=LocalAnalyzer.analyze("Don't call Ahmed, but send the Atlas summary tomorrow.","text/plain");
        boolean send=false,call=false;JSONArray found=new JSONArray();
        if(r!=null&&r.actions!=null)for(AnalysisResult.Action a:r.actions){String x=safe(a.text).toLowerCase(Locale.ROOT);found.put(a.text);if(x.contains("send"))send=true;if(x.contains("call"))call=true;}
        row(rows,"R61-ACTION","Contrast clause polarity",send&&!call,"Negative clause must be suppressed while the positive contrast clause survives",new JSONObject().put("actions",found).put("send_found",send).put("call_found",call));
    }

    private static void asrCoverage(JSONArray rows)throws Exception{
        Method m=AudioAnalyzer.class.getDeclaredMethod("acceptabilityWarning",TranscriptResult.class);m.setAccessible(true);
        TranscriptResult impossible=new TranscriptResult();impossible.text="Cortex known test phrase";impossible.durationMs=1200;impossible.processedDurationMs=29980;
        String bad=(String)m.invoke(null,impossible);
        row(rows,"R61-ASR","Impossible timestamp coverage rejection",bad!=null&&bad.toLowerCase(Locale.ROOT).contains("exceeds source duration"),"A transcript claiming ~30s coverage for a 1.2s source must be rejected before local analysis",new JSONObject().put("source_ms",impossible.durationMs).put("processed_ms",impossible.processedDurationMs).put("warning",safe(bad)));

        TranscriptResult sane=new TranscriptResult();sane.text="Cortex known test phrase";sane.durationMs=5000;sane.processedDurationMs=5000;
        String good=(String)m.invoke(null,sane);
        row(rows,"R61-ASR","Sane timestamp coverage acceptance",good==null,"A complete transcript with physically possible coverage must remain eligible",new JSONObject().put("source_ms",sane.durationMs).put("processed_ms",sane.processedDurationMs).put("warning",safe(good)));
    }

    private static void providerClassification(JSONArray rows)throws Exception{
        ExternalBrainProvider.ProviderException rate=new ExternalBrainProvider.ProviderException("rate",429,"test","test",1);
        ExternalBrainProvider.ProviderException badRequest=new ExternalBrainProvider.ProviderException("bad",400,"test","test",1);
        row(rows,"R61-PROVIDER","429 is retryable/rate-limited",rate.rateLimited()&&rate.retryable(),"Provider HTTP 429 must be classified as rate limited and retryable so routing can fall back or cool down",new JSONObject().put("http",429).put("rate_limited",rate.rateLimited()).put("retryable",rate.retryable()));
        row(rows,"R61-PROVIDER","400 is not retryable",!badRequest.rateLimited()&&!badRequest.retryable(),"Permanent request errors must not enter retry loops",new JSONObject().put("http",400).put("rate_limited",badRequest.rateLimited()).put("retryable",badRequest.retryable()));
    }

    private static void transactionalProductPaths(JSONArray rows,Context c,VaultDb db,String runId)throws Exception{
        SQLiteDatabase sql=db.getWritableDatabase();boolean tx=false;
        try{
            sql.beginTransaction();tx=true;String token="R61_"+runId+"_"+System.nanoTime();

            String focalText=token+" Project Atlas fallback latency. Next action: verify the fallback latency and draft the summary.";
            long focal=db.insert("TEXT","manual","R61 focal",focalText,"Diagnostics","r61,focal","",Fingerprint.text(token+"|focal"),new JSONObject().put("r61",true).put("run_id",runId).toString());
            AnalysisResult fa=LocalAnalyzer.analyze(focalText,"text/plain");db.applyAnalysis(focal,fa);

            String distractorText=token+" Project Atlas old unrelated note. Next action: archive the old design screenshots.";
            long distractor=db.insert("TEXT","manual","R61 distractor",distractorText,"Diagnostics","r61,distractor","",Fingerprint.text(token+"|distractor"),new JSONObject().put("r61",true).put("run_id",runId).toString());
            db.applyAnalysis(distractor,LocalAnalyzer.analyze(distractorText,"text/plain"));

            LocalAskRouter.Result brain=BrainRouter.fast(c,db,"What should I do next?","your_data",focal,null);
            long firstSource=brain==null||brain.grounded==null||brain.grounded.sources.isEmpty()||brain.grounded.sources.get(0).item==null?0:brain.grounded.sources.get(0).item.id;
            boolean focalFirst=firstSource==focal;
            row(rows,"R61-BRAIN","Your Data focal grounding survives router",focalFirst,"Ask Brain about this must carry the exact focal evidence through BrainRouter → LocalAskRouter → SecondBrainEngine as M1",new JSONObject().put("focal_id",focal).put("distractor_id",distractor).put("first_source_id",firstSource).put("provider",brain==null?"":safe(brain.provider)).put("answer",brain==null?"":safe(brain.answer)));

            GroundedAnswer direct=SecondBrainEngine.ask(db,"What should I do next?",focal);
            long directFirst=direct.sources.isEmpty()||direct.sources.get(0).item==null?0:direct.sources.get(0).item.id;
            row(rows,"R61-BRAIN","Required evidence is deterministic M1",directFirst==focal,"Required focal evidence must be M1 even when another memory is semantically plausible",new JSONObject().put("focal_id",focal).put("first_source_id",directFirst).put("source_count",direct.sources.size()).put("answer",safe(direct.answer)));

            long now=System.currentTimeMillis();
            long oldShot=db.insert("SCREENSHOT","screenshot-folder","old historical screenshot","","Screenshots & Images","screenshot,auto_import","/dev/null",Fingerprint.text(token+"|oldshot"),new JSONObject().put("source_modified",now-7L*24L*60L*60L*1000L).put("r61",true).toString());
            long freshShot=db.insert("SCREENSHOT","screenshot-folder","fresh screenshot","","Screenshots & Images","screenshot,auto_import","/dev/null",Fingerprint.text(token+"|freshshot"),new JSONObject().put("source_modified",now).put("r61",true).toString());
            long directId=db.insert("TEXT","manual","fresh direct capture",token+" direct capture","Diagnostics","r61,foreground","",Fingerprint.text(token+"|direct"),new JSONObject().put("r61",true).toString());

            KnowledgeItem first=AnalysisQueue.nextPendingPrioritized(db);
            row(rows,"R61-QUEUE","Foreground evidence outranks screenshot backlog",first!=null&&first.id==directId,"A current manual/share/voice/text capture must not wait behind historical screenshot OCR",new JSONObject().put("expected_id",directId).put("selected_id",first==null?0:first.id).put("old_screenshot_id",oldShot).put("fresh_screenshot_id",freshShot));
            if(first!=null&&first.id==directId)db.markAnalyzing(directId);
            KnowledgeItem second=AnalysisQueue.nextPendingPrioritized(db);
            row(rows,"R61-QUEUE","Newest real screenshot wins catch-up",second!=null&&second.id==freshShot,"Screenshot catch-up must rank by original source_modified timestamp rather than import insertion age",new JSONObject().put("expected_id",freshShot).put("selected_id",second==null?0:second.id).put("old_screenshot_id",oldShot));
        }finally{if(tx)try{sql.endTransaction();}catch(Throwable ignored){}}
    }

    private static void row(JSONArray rows,String category,String name,boolean pass,String expected,JSONObject evidence)throws Exception{
        rows.put(new JSONObject().put("id",String.format(Locale.US,"R61-%03d",rows.length()+1)).put("category",category).put("name",name).put("status",pass?CortexExhaustiveVerificationSuite.EXECUTED_PASS:CortexExhaustiveVerificationSuite.EXECUTED_FAIL).put("expected",expected).put("evidence",evidence));
    }
    private static String safe(String s){return s==null?"":s;}
}
