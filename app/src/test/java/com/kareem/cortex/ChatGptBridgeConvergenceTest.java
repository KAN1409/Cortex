package com.kareem.cortex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ChatGptBridgeConvergenceTest {
    private Context context;

    @Before public void clean() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        deleteRecursively(new File(context.getFilesDir(), "chatgpt_bridge"));
        context.getSharedPreferences(CortexComparisonTeachingSuite.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void onePollConvergesWhenRemainingVerdictAppearsOnLaterPass() throws Exception {
        String runId = "convergence-run";
        ChatGptBridgeStore store = new ChatGptBridgeStore(context);
        store.beginRun(runId, CortexComparisonTeachingSuite.RUN_KIND, 2);
        context.getSharedPreferences(CortexComparisonTeachingSuite.PREFS, Context.MODE_PRIVATE)
                .edit().putString(CortexComparisonTeachingSuite.ACTIVE_RUN_ID, runId).commit();

        JSONObject r1 = request(runId, "true-comparison-batch-01");
        JSONObject r2 = request(runId, "true-comparison-batch-02");
        store.putPending(r1);
        store.putPending(r2);

        JSONObject v1 = verdictFor(r1);
        JSONObject v2 = verdictFor(r2);
        DelayedTransport transport = new DelayedTransport(v1, v2);
        ChatGptBridgeCoordinator coordinator = new ChatGptBridgeCoordinator(context, transport);

        ChatGptBridgeCoordinator.PollResult result = coordinator.pollVerdicts(0L, 500);

        assertTrue(result.ok);
        assertEquals(2, result.accepted);
        assertTrue(transport.fetches >= 2);
        JSONObject done = coordinator.runStatus(runId);
        assertEquals(2, done.getInt("resolved"));
        assertEquals(0, done.getInt("pending"));
        assertTrue(done.getBoolean("complete"));
    }

    private JSONObject request(String runId, String testId) throws Exception {
        return ChatGptBridgeProtocol.newTestRequest(
                runId, testId,
                new JSONObject().put("input", testId),
                new JSONObject().put("surfaceNow", false),
                new JSONObject().put("expected", "OPEN_JUDGMENT"),
                ChatGptBridgeProtocol.defaultJudgingRules());
    }

    private JSONObject verdictFor(JSONObject request) throws Exception {
        JSONObject payload = ChatGptBridgeProtocol.buildVerdictPayload(
                ChatGptBridgeProtocol.VerdictStatus.PASS,
                ChatGptBridgeProtocol.Severity.NONE,
                request.getString("payloadSha256"),
                new JSONObject().put("answer", "ok"),
                new JSONObject().put("correct", true),
                new JSONObject().put("grounded", true),
                new JSONObject().put("decisionCorrect", true),
                new JSONArray(), new JSONArray(), new JSONArray(), null);
        return ChatGptBridgeProtocol.envelope(
                ChatGptBridgeProtocol.MessageType.CHATGPT_TEST_VERDICT,
                request.getString("requestId"), request.getString("runId"), request.getString("testId"), payload);
    }

    private static final class DelayedTransport implements BridgeMailTransport {
        final JSONObject first;
        final JSONObject second;
        int fetches;

        DelayedTransport(JSONObject first, JSONObject second) {
            this.first = first;
            this.second = second;
        }

        @Override public GmailBridgeTransport.SendResult sendEnvelope(JSONObject envelope) {
            return GmailBridgeTransport.SendResult.ok("fake", "fake");
        }

        @Override public List<JSONObject> fetchCandidateVerdicts(long newerThanEpochMs, int maxResults) {
            fetches++;
            List<JSONObject> out = new ArrayList<>();
            out.add(first);
            if (fetches >= 2) out.add(second);
            return out;
        }

        @Override public String name() { return "TEST_DELAYED_MAILBOX"; }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
