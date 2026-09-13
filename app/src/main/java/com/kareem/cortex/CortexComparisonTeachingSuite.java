package com.kareem.cortex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Free comparison/teaching benchmark carried over the existing Gmail bridge.
 *
 * The suite never writes canonical knowledge and never changes policy directly. It builds
 * deterministic scenario batches, records CortexAttentionJudge output, and asks ChatGPT to solve
 * the same inputs independently before comparing results and proposing bounded teaching.
 */
public final class CortexComparisonTeachingSuite {
    public static final String VERSION = "comparison_teaching_001";
    public static final int TOTAL_CASES = 1000;
    public static final int BATCH_SIZE = 100;
    public static final int BATCHES = TOTAL_CASES / BATCH_SIZE;

    private CortexComparisonTeachingSuite() {}

    public static RunResult dispatch(Context context, BridgeMailTransport transport) {
        ChatGptBridgeCoordinator coordinator = new ChatGptBridgeCoordinator(context, transport);
        String runId = "comparison-teaching-" + System.currentTimeMillis();
        JSONArray dispatched = new JSONArray();
        int sent = 0;
        int failed = 0;

        for (int batch = 0; batch < BATCHES; batch++) {
            try {
                JSONObject original = new JSONObject();
                JSONObject cortex = new JSONObject();
                JSONObject reference = new JSONObject();
                JSONArray inputs = new JSONArray();
                JSONArray outputs = new JSONArray();

                for (int offset = 0; offset < BATCH_SIZE; offset++) {
                    int index = batch * BATCH_SIZE + offset;
                    Scenario s = scenario(index);
                    AttentionDecisionEngine.Candidate c = candidate(index, s);
                    CortexAttentionJudge.RuntimeContext runtime =
                            new CortexAttentionJudge.RuntimeContext(s.contextMatch, s.interruptionCost);
                    CortexAttentionJudge.Judgment judgment = CortexAttentionJudge.evaluate(context, c, runtime);

                    inputs.put(s.toJson(index));
                    outputs.put(new JSONObject()
                            .put("caseId", caseId(index))
                            .put("surfaceNow", judgment.surfaceNow)
                            .put("score", judgment.score)
                            .put("threshold", judgment.threshold)
                            .put("reason", judgment.reason)
                            .put("policyVersion", judgment.policyVersion));
                }

                original.put("kind", "CORTEX_1000_SCENARIO_COMPARISON")
                        .put("suiteVersion", VERSION)
                        .put("batchIndex", batch)
                        .put("batchCount", BATCHES)
                        .put("caseCount", inputs.length())
                        .put("cases", inputs)
                        .put("instruction",
                                "Solve every case independently from the original inputs before looking at Cortex outputs. " +
                                "For each case decide NOW or SILENT/DEFER, explain material disagreements, and do not invent evidence.");

                cortex.put("engine", CortexAttentionJudge.VERSION)
                        .put("results", outputs);

                reference.put("comparisonContract", new JSONObject()
                                .put("resolvedMustNotInterrupt", true)
                                .put("lowConfidenceMustNotInterrupt", true)
                                .put("technicalEvidenceMustStayBelowAttention", true)
                                .put("sameInputMustBeJudgedFromSameEvidence", true)
                                .put("noInventedFacts", true)
                                .put("chatGptMustSolveBeforeComparison", true))
                        .put("teachingContract", new JSONObject()
                                .put("proposalsOnly", true)
                                .put("canonicalWritesForbidden", true)
                                .put("executionForbidden", true)
                                .put("boundedPolicyOnly", true)
                                .put("requireShadowComparison", true)
                                .put("requireRollbackSafeCanary", true))
                        .put("requestedResponse", new JSONObject()
                                .put("perCaseComparison", true)
                                .put("aggregateAgreementMetrics", true)
                                .put("criticalDisagreements", true)
                                .put("falseInterruptions", true)
                                .put("missedUrgentCases", true)
                                .put("teachingCandidate", true));

                ChatGptBridgeCoordinator.DispatchResult result = coordinator.dispatchTest(
                        runId,
                        String.format(Locale.US, "comparison-batch-%02d", batch + 1),
                        original,
                        cortex,
                        reference);
                if (result.sent) sent++; else failed++;
                dispatched.put(new JSONObject()
                        .put("batch", batch + 1)
                        .put("sent", result.sent)
                        .put("requestId", result.request == null ? "" : result.request.optString("requestId", ""))
                        .put("transport", result.transport)
                        .put("error", result.error));
            } catch (Throwable t) {
                failed++;
                try {
                    dispatched.put(new JSONObject()
                            .put("batch", batch + 1)
                            .put("sent", false)
                            .put("error", t.getClass().getSimpleName() + ": " + safeMessage(t)));
                } catch (Throwable ignored) {}
            }
        }
        return new RunResult(runId, sent, failed, dispatched);
    }

    private static AttentionDecisionEngine.Candidate candidate(int index, Scenario s) {
        long now = 1789000000000L + index * 60000L;
        long lastSeen = now - s.ageMinutes * 60000L;
        long deadline = s.deadlineHours < 0 ? 0 : now + s.deadlineHours * 60L * 60L * 1000L;
        return new AttentionDecisionEngine.Candidate(
                index + 1L,
                s.type,
                s.resolved ? "RESOLVED" : "OPEN",
                s.subject,
                s.summary,
                s.confidence,
                s.urgency,
                s.actionability,
                s.personalRelevance,
                s.risk,
                s.novelty,
                deadline,
                now,
                lastSeen,
                s.repeatedCount,
                s.evidenceCount,
                !s.resolved,
                s.openCommitment,
                s.materialChange,
                s.explicitRequest,
                s.severeContextImpact);
    }

    /** Deterministic broad matrix: every 20 cases rotates one archetype with numeric perturbations. */
    private static Scenario scenario(int i) {
        int archetype = i % 20;
        double jitter = ((i * 37) % 17) / 100.0;
        int age = (i * 13) % 1440;
        int repeats = i % 5;
        int evidence = 1 + (i % 4);
        switch (archetype) {
            case 0: return s("SECURITY", "Password compromised", "Unauthorized sign-in detected", .95, .90, .88, .92, .96, .80, 1, age, repeats, evidence, false,false,true,true,true,.90,.15);
            case 1: return s("MESSAGE", "Can you call me now?", "Explicit request from a relevant person", .92, .82, .90, .88, .20, .65, 2, age, repeats, evidence, false,false,true,true,false,.92,.20);
            case 2: return s("COMMITMENT", "Supplier quotation", "Open commitment is due before meeting", .94, .78, .86, .85, .25, .55, 4, age, repeats, evidence, false,true,true,false,false,.85,.20);
            case 3: return s("SOCIAL", "Story reaction", "Someone reacted to a story", .96, .08, .08, .30, .02, .20, -1, age, repeats, evidence, false,false,false,false,false,.30,.75);
            case 4: return s("TECHNICAL", "Worker log", "Background worker completed successfully", .99, .05, .05, .10, .02, .10, -1, age, repeats, evidence, false,false,false,false,false,.20,.70);
            case 5: return s("MESSAGE", "Resolved request", "The requested item was already delivered", .95, .70, .75, .80, .10, .30, -1, age, repeats, evidence, true,false,false,false,false,.80,.20);
            case 6: return s("INFERENCE", "Possible issue", "Weak evidence suggests something may need attention", .45 + jitter, .75, .65, .70, .40, .55, 3, age, repeats, evidence, false,false,false,false,false,.65,.30);
            case 7: return s("WEATHER", "Normal weather", "Routine forecast with no severe contextual impact", .98, .20, .10, .30, .05, .15, -1, age, repeats, evidence, false,false,false,false,false,.35,.65);
            case 8: return s("DEADLINE", "Submission due soon", "Unresolved deliverable due within two hours", .94, .86, .90, .95, .35, .65, 1, age, repeats, evidence, false,true,true,false,false,.90,.15);
            case 9: return s("PROMOTION", "Limited sale", "Promotional discount notification", .98, .30, .20, .20, .02, .45, -1, age, repeats, evidence, false,false,false,false,false,.25,.80);
            case 10:return s("HEALTH_CONTEXT", "Context materially changed", "A severe current context makes follow-up urgent", .90, .78, .75, .95, .72, .60, 2, age, repeats, evidence, false,false,true,false,true,.90,.10);
            case 11:return s("FILE", "Document received", "A requested work file arrived and is actionable", .93, .55, .82, .88, .10, .72, 8, age, repeats, evidence, false,true,true,false,false,.82,.25);
            case 12:return s("DUPLICATE", "Repeated notification", "Same low-value notification repeated several times", .96, .18, .12, .35, .04, .05, -1, age, 4, evidence, false,false,false,false,false,.30,.75);
            case 13:return s("FINANCE", "Payment deadline", "Known bill is due today and requires action", .95, .80, .88, .90, .50, .45, 6, age, repeats, evidence, false,true,true,false,false,.88,.20);
            case 14:return s("MESSAGE", "FYI", "Informational message with no request or obligation", .97, .18, .15, .45, .05, .25, -1, age, repeats, evidence, false,false,false,false,false,.40,.60);
            case 15:return s("SECURITY", "Old security alert", "High-risk alert but evidence is stale", .91, .85, .75, .90, .92, .55, -1, 4320 + age, repeats, evidence, false,false,false,false,false,.70,.35);
            case 16:return s("COMMITMENT", "Future follow-up", "Valid commitment but deadline is next week", .95, .30, .60, .80, .10, .35, 120, age, repeats, evidence, false,true,false,false,false,.65,.55);
            case 17:return s("ACTION", "Approval needed", "Explicit approval request blocks next step", .96, .76, .94, .93, .25, .70, 3, age, repeats, evidence, false,true,true,true,false,.95,.10);
            case 18:return s("CALENDAR", "Meeting changed", "Material schedule change affects an imminent meeting", .97, .75, .82, .88, .20, .82, 2, age, repeats, evidence, false,false,true,false,true,.90,.18);
            default:return s("AMBIENT", "Background context", "Useful context but no current action or risk", .92, .10, .12, .50, .05, .18, -1, age, repeats, evidence, false,false,false,false,false,.45,.70);
        }
    }

    private static Scenario s(String type,String subject,String summary,double confidence,double urgency,
                              double actionability,double relevance,double risk,double novelty,int deadlineHours,
                              int ageMinutes,int repeatedCount,int evidenceCount,boolean resolved,boolean openCommitment,
                              boolean materialChange,boolean explicitRequest,boolean severeContextImpact,
                              double contextMatch,double interruptionCost) {
        Scenario x = new Scenario();
        x.type=type; x.subject=subject; x.summary=summary; x.confidence=confidence; x.urgency=urgency;
        x.actionability=actionability; x.personalRelevance=relevance; x.risk=risk; x.novelty=novelty;
        x.deadlineHours=deadlineHours; x.ageMinutes=ageMinutes; x.repeatedCount=repeatedCount;
        x.evidenceCount=evidenceCount; x.resolved=resolved; x.openCommitment=openCommitment;
        x.materialChange=materialChange; x.explicitRequest=explicitRequest; x.severeContextImpact=severeContextImpact;
        x.contextMatch=contextMatch; x.interruptionCost=interruptionCost;
        return x;
    }

    private static String caseId(int index) { return String.format(Locale.US, "case-%04d", index + 1); }
    private static String safeMessage(Throwable t) { return t.getMessage() == null ? "" : t.getMessage(); }

    private static final class Scenario {
        String type,subject,summary;
        double confidence,urgency,actionability,personalRelevance,risk,novelty,contextMatch,interruptionCost;
        int deadlineHours,ageMinutes,repeatedCount,evidenceCount;
        boolean resolved,openCommitment,materialChange,explicitRequest,severeContextImpact;
        JSONObject toJson(int index) throws Exception {
            return new JSONObject()
                    .put("caseId", caseId(index)).put("type",type).put("subject",subject).put("summary",summary)
                    .put("confidence",confidence).put("urgency",urgency).put("actionability",actionability)
                    .put("personalRelevance",personalRelevance).put("risk",risk).put("novelty",novelty)
                    .put("deadlineHours",deadlineHours).put("ageMinutes",ageMinutes).put("repeatedCount",repeatedCount)
                    .put("evidenceCount",evidenceCount).put("resolved",resolved).put("openCommitment",openCommitment)
                    .put("materialChange",materialChange).put("explicitRequest",explicitRequest)
                    .put("severeContextImpact",severeContextImpact).put("contextMatch",contextMatch)
                    .put("interruptionCost",interruptionCost);
        }
    }

    public static final class RunResult {
        public final String runId;
        public final int sentBatches;
        public final int failedBatches;
        public final JSONArray details;
        RunResult(String runId,int sentBatches,int failedBatches,JSONArray details){
            this.runId=runId;this.sentBatches=sentBatches;this.failedBatches=failedBatches;this.details=details;
        }
        public boolean ok(){return failedBatches==0 && sentBatches==BATCHES;}
    }
}
