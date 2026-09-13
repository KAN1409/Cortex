package com.kareem.cortex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** 1000-case comparison benchmark over the external intelligence bridge. */
public final class CortexComparisonTeachingSuite {
    public static final String VERSION="comparison_teaching_003_run_scoped";
    public static final String RUN_KIND="TRUE_1000_COMPARISON";
    public static final String PREFS="cortex_true_comparison";
    public static final String ACTIVE_RUN_ID="active_run_id";
    public static final int TOTAL_CASES=CortexTrueScenarioCatalog.TOTAL;
    public static final int BATCH_SIZE=100;
    public static final int BATCHES=TOTAL_CASES/BATCH_SIZE;

    private CortexComparisonTeachingSuite() {}

    public static RunResult dispatch(Context context,BridgeMailTransport transport){
        ChatGptBridgeCoordinator coordinator=new ChatGptBridgeCoordinator(context,transport);
        String runId="true-comparison-teaching-"+System.currentTimeMillis();
        JSONArray dispatched=new JSONArray();int sent=0,failed=0;
        try{
            ChatGptBridgeStore store=new ChatGptBridgeStore(context.getApplicationContext());
            store.beginRun(runId,RUN_KIND,BATCHES);
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(ACTIVE_RUN_ID,runId).apply();
        }catch(Throwable t){return new RunResult(runId,0,BATCHES,new JSONArray());}

        for(int batch=0;batch<BATCHES;batch++){
            try{
                JSONArray inputs=new JSONArray(),outputs=new JSONArray(),references=new JSONArray();
                for(int offset=0;offset<BATCH_SIZE;offset++){
                    int index=batch*BATCH_SIZE+offset;CortexTrueScenarioCatalog.Scenario s=CortexTrueScenarioCatalog.get(index);AttentionDecisionEngine.Candidate c=candidate(index,s);CortexAttentionJudge.Judgment j=CortexAttentionJudge.evaluate(context,c,new CortexAttentionJudge.RuntimeContext(s.contextMatch,s.interruptionCost));
                    inputs.put(s.toJson());references.put(new JSONObject().put("caseId",s.caseId).put("referenceTruth",s.referenceTruth));outputs.put(new JSONObject().put("caseId",s.caseId).put("surfaceNow",j.surfaceNow).put("score",j.score).put("threshold",j.threshold).put("reason",j.reason).put("policyVersion",j.policyVersion));
                }

                JSONObject original=new JSONObject().put("kind","CORTEX_TRUE_1000_SCENARIO_COMPARISON").put("suiteVersion",VERSION).put("runId",runId).put("batchIndex",batch).put("batchCount",BATCHES).put("caseCount",inputs.length()).put("cases",inputs).put("instruction","Solve each case independently from original evidence before looking at Cortex output. Compare final judgment to Cortex and reference boundaries. For file cases separately verify receive/open/MIME/permission/parser/version/provenance semantics. Do not invent evidence. Return aggregate metrics plus only material disagreements and top lessons.");
                JSONObject cortex=new JSONObject().put("engine",CortexAttentionJudge.VERSION).put("results",outputs);
                JSONObject reference=new JSONObject().put("caseReferences",references).put("comparisonContract",new JSONObject().put("originalEvidenceIsGroundingSource",true).put("resolvedMustNotInterrupt",true).put("lowConfidenceMustNotInterrupt",true).put("technicalEvidenceMustStayBelowAttention",true).put("unverifiedFileMustNotBeTreatedAsVerified",true).put("noInventedFacts",true).put("chatGptMustSolveBeforeComparison",true)).put("requestedResponse",new JSONObject().put("metrics",new JSONArray().put("totalCases").put("agreementCount").put("agreementRate").put("falseInterruptions").put("missedUrgentCases").put("criticalDisagreements").put("groundingViolations").put("fileCases").put("fileFlowFailures").put("continuityFailures")).put("materialDisagreementsOnly",true).put("topLessonsLimit",10).put("teachingCandidate",true)).put("teachingContract",new JSONObject().put("proposalsOnly",true).put("canonicalWritesForbidden",true).put("executionForbidden",true).put("requireShadowComparison",true).put("requireRollbackSafeCanary",true));

                ChatGptBridgeCoordinator.DispatchResult r=coordinator.dispatchTest(runId,String.format(Locale.US,"true-comparison-batch-%02d",batch+1),original,cortex,reference);
                if(r.sent)sent++;else failed++;
                dispatched.put(new JSONObject().put("batch",batch+1).put("sent",r.sent).put("requestId",r.request==null?"":r.request.optString("requestId","")).put("transport",r.transport).put("error",r.error));
            }catch(Throwable t){failed++;try{dispatched.put(new JSONObject().put("batch",batch+1).put("sent",false).put("error",t.getClass().getSimpleName()+": "+safeMessage(t)));}catch(Throwable ignored){}}
        }
        return new RunResult(runId,sent,failed,dispatched);
    }

    public static String activeRunId(Context context){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(ACTIVE_RUN_ID,"");}

    private static AttentionDecisionEngine.Candidate candidate(int index,CortexTrueScenarioCatalog.Scenario s){long now=1789000000000L+index*60000L;long lastSeen=now-s.ageMinutes*60000L;long deadline=s.deadlineHours<0?0:now+s.deadlineHours*60L*60L*1000L;return new AttentionDecisionEngine.Candidate(index+1L,s.type,s.resolved?"RESOLVED":"OPEN",s.subject,s.summary,s.confidence,s.urgency,s.actionability,s.personalRelevance,s.risk,s.novelty,deadline,now,lastSeen,s.repeatedCount,s.evidenceCount,!s.resolved,s.openCommitment,s.materialChange,s.explicitRequest,s.severeContextImpact);}
    private static String safeMessage(Throwable t){return t.getMessage()==null?"":t.getMessage();}

    public static final class RunResult{public final String runId;public final int sentBatches,failedBatches;public final JSONArray details;RunResult(String runId,int sentBatches,int failedBatches,JSONArray details){this.runId=runId;this.sentBatches=sentBatches;this.failedBatches=failedBatches;this.details=details;}public boolean ok(){return failedBatches==0&&sentBatches==BATCHES;}}
}
