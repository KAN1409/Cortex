package com.kareem.cortex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
public class ChatGptBridgeStateMachineTest {
    private Context context;

    @Before public void clean() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        deleteRecursively(new File(context.getFilesDir(), "chatgpt_bridge"));
    }

    @Test public void runManifestClosesOnlyAfterEveryCorrelatedVerdict() throws Exception {
        ChatGptBridgeStore store = new ChatGptBridgeStore(context);
        store.beginRun("run-root", CortexComparisonTeachingSuite.RUN_KIND, 2);

        JSONObject r1 = request("run-root", "true-comparison-batch-01", 1.0d);
        JSONObject r2 = request("run-root", "true-comparison-batch-02", 0.0d);
        store.putPending(r1);
        store.putPending(r2);

        JSONObject before = store.runStatus("run-root");
        assertEquals(0, before.getInt("resolved"));
        assertEquals(2, before.getInt("pending"));
        assertFalse(before.getBoolean("complete"));

        ChatGptBridgeStore.AcceptResult a1 = store.acceptVerdict(verdictFor(r1));
        assertTrue(a1.accepted);
        JSONObject middle = new ChatGptBridgeStore(context).runStatus("run-root");
        assertEquals(1, middle.getInt("resolved"));
        assertEquals(1, middle.getInt("pending"));
        assertFalse(middle.getBoolean("complete"));

        ChatGptBridgeStore.AcceptResult a2 = new ChatGptBridgeStore(context).acceptVerdict(verdictFor(r2));
        assertTrue(a2.accepted);
        JSONObject done = new ChatGptBridgeStore(context).runStatus("run-root");
        assertEquals(2, done.getInt("resolved"));
        assertEquals(0, done.getInt("pending"));
        assertEquals(0, done.getInt("missing"));
        assertTrue(done.getBoolean("complete"));

        ChatGptBridgeStore.AcceptResult duplicate = new ChatGptBridgeStore(context).acceptVerdict(verdictFor(r2));
        assertTrue(duplicate.ignored);
        assertEquals("DUPLICATE_IGNORED", duplicate.detail);
    }

    @Test public void legacyRawPendingRequestMigratesWithoutRehashingWireFingerprint() throws Exception {
        JSONObject legacy = request("legacy-run", "true-comparison-batch-01", 1.0d);
        String wireHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        legacy.put("payloadSha256", wireHash);
        assertFalse(ChatGptBridgeProtocol.validateEnvelope(legacy).ok);

        File pendingDir = new File(context.getFilesDir(), "chatgpt_bridge/pending");
        assertTrue(pendingDir.mkdirs() || pendingDir.isDirectory());
        File legacyFile = new File(pendingDir, legacy.getString("requestId") + ".json");
        try (FileOutputStream out = new FileOutputStream(legacyFile)) {
            out.write(legacy.toString().getBytes(StandardCharsets.UTF_8));
        }

        ChatGptBridgeStore store = new ChatGptBridgeStore(context);
        JSONObject loaded = store.getPending(legacy.getString("requestId"));
        assertEquals(wireHash, loaded.getString("payloadSha256"));

        JSONObject verdictPayload = ChatGptBridgeProtocol.buildVerdictPayload(
                ChatGptBridgeProtocol.VerdictStatus.PASS,
                ChatGptBridgeProtocol.Severity.NONE,
                wireHash,
                new JSONObject().put("ok", true),
                new JSONObject().put("ok", true),
                new JSONObject().put("grounded", true),
                new JSONObject().put("correct", true),
                new JSONArray(), new JSONArray(), new JSONArray(), null);
        JSONObject verdict = ChatGptBridgeProtocol.envelope(
                ChatGptBridgeProtocol.MessageType.CHATGPT_TEST_VERDICT,
                legacy.getString("requestId"), legacy.getString("runId"), legacy.getString("testId"), verdictPayload);

        ChatGptBridgeStore.AcceptResult result = store.acceptVerdict(verdict);
        assertTrue(result.accepted);
    }

    @Test public void unrelatedMailboxVerdictIsIgnoredNotRejected() throws Exception {
        ChatGptBridgeStore store = new ChatGptBridgeStore(context);
        JSONObject payload = ChatGptBridgeProtocol.buildVerdictPayload(
                ChatGptBridgeProtocol.VerdictStatus.PASS,
                ChatGptBridgeProtocol.Severity.NONE,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                new JSONObject(), new JSONObject(), new JSONObject(), new JSONObject(),
                new JSONArray(), new JSONArray(), new JSONArray(), null);
        JSONObject verdict = ChatGptBridgeProtocol.envelope(
                ChatGptBridgeProtocol.MessageType.CHATGPT_TEST_VERDICT,
                "unknown-request", "old-run", "old-test", payload);
        ChatGptBridgeStore.AcceptResult result = store.acceptVerdict(verdict);
        assertTrue(result.ignored);
        assertEquals("UNRELATED_IGNORED", result.detail);
        assertEquals(0, store.summary().getInt("rejected"));
    }

    private JSONObject request(String runId, String testId, double numeric) throws Exception {
        return ChatGptBridgeProtocol.newTestRequest(
                runId, testId,
                new JSONObject().put("numeric", numeric),
                new JSONObject().put("score", numeric),
                new JSONObject().put("expected", numeric),
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

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
