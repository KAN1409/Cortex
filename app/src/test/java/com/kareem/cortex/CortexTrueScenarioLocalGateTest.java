package com.kareem.cortex;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class CortexTrueScenarioLocalGateTest {

    @Test
    public void all1000CasesRespectHardReferenceBoundariesLocally() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        List<String> failures = new ArrayList<>();

        for (int index = 0; index < CortexTrueScenarioCatalog.TOTAL; index++) {
            CortexTrueScenarioCatalog.Scenario s = CortexTrueScenarioCatalog.get(index);
            long now = 1789000000000L + index * 60000L;
            long lastSeen = now - s.ageMinutes * 60000L;
            long deadline = s.deadlineHours < 0 ? 0 : now + s.deadlineHours * 60L * 60L * 1000L;

            AttentionDecisionEngine.Candidate c = new AttentionDecisionEngine.Candidate(
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

            CortexAttentionJudge.Judgment j = CortexAttentionJudge.evaluate(
                    context,
                    c,
                    new CortexAttentionJudge.RuntimeContext(s.contextMatch, s.interruptionCost));

            JSONObject truth = s.referenceTruth;
            String boundary = truth.optString("expectedBoundary", "OPEN_JUDGMENT");
            boolean ok = true;
            switch (boundary) {
                case "MUST_DEFER_RESOLVED":
                case "MUST_DEFER_LOW_CONFIDENCE":
                case "MUST_DEFER_TECHNICAL":
                    ok = !j.surfaceNow;
                    break;
                case "SHOULD_SURFACE_EXPLICIT_ACTION":
                case "SHOULD_SURFACE_DUE_COMMITMENT":
                    ok = j.surfaceNow;
                    break;
                default:
                    break;
            }

            if (!ok) {
                String failure = s.caseId + " | " + s.domain + " | " + s.intent + " | " + s.complication
                        + " | boundary=" + boundary
                        + " | surfaceNow=" + j.surfaceNow
                        + " | score=" + j.score
                        + " | reason=" + j.reason;
                failures.add(failure);
                System.out.println("LOCAL1000_FAIL " + failure);
            }
        }

        System.out.println("LOCAL1000_SUMMARY failures=" + failures.size());
        assertTrue("Hard-boundary failures (" + failures.size() + "):\n" + String.join("\n", failures), failures.isEmpty());
    }
}
