package com.kareem.cortex;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FailoverBridgeTransportUnionTest {
    @Test public void unionsPrimaryAndFallbackVerdictsAndDeduplicates() throws Exception {
        List<JSONObject> all = new ArrayList<>();
        for (int i = 1; i <= 10; i++) all.add(env(i));
        BridgeMailTransport primary = new FakeTransport(all.subList(0, 8), "PRIMARY");
        BridgeMailTransport fallback = new FakeTransport(all, "FALLBACK");
        FailoverBridgeTransport transport = new FailoverBridgeTransport(primary, fallback);

        List<JSONObject> merged = transport.fetchCandidateVerdicts(0L, 500);
        assertEquals(10, merged.size());
    }

    private static JSONObject env(int i) throws Exception {
        String id = String.format("req-%02d", i);
        JSONObject payload = new JSONObject()
                .put("status", "PASS")
                .put("severity", "NONE")
                .put("requestPayloadSha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        return ChatGptBridgeProtocol.envelope(
                ChatGptBridgeProtocol.MessageType.CHATGPT_TEST_VERDICT,
                id,
                "run-1",
                String.format("true-comparison-batch-%02d", i),
                payload);
    }

    private static final class FakeTransport implements BridgeMailTransport {
        private final List<JSONObject> results;
        private final String name;
        FakeTransport(List<JSONObject> results, String name) { this.results = new ArrayList<>(results); this.name = name; }
        @Override public GmailBridgeTransport.SendResult sendEnvelope(JSONObject envelope) { return GmailBridgeTransport.SendResult.ok("x", "x"); }
        @Override public List<JSONObject> fetchCandidateVerdicts(long newerThanEpochMs, int maxResults) { return new ArrayList<>(results); }
        @Override public String name() { return name; }
    }
}
