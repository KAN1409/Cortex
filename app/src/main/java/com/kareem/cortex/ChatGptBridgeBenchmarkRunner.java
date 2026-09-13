package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dispatches a batch of grounded Cortex benchmark scenarios to ChatGPT through the bridge.
 * No synthetic scenario is written into canonical Cortex evidence stores.
 */
public final class ChatGptBridgeBenchmarkRunner {
    private final ChatGptBridgeCoordinator coordinator;

    public ChatGptBridgeBenchmarkRunner(ChatGptBridgeCoordinator coordinator) {
        if (coordinator == null) throw new IllegalArgumentException("coordinator required");
        this.coordinator = coordinator;
    }

    public RunResult dispatch(List<ChatGptBridgeScenario> scenarios) {
        String runId = "chatgpt-benchmark-" + UUID.randomUUID();
        JSONArray items = new JSONArray();
        int sent = 0;
        int failed = 0;
        if (scenarios == null) scenarios = new ArrayList<>();
        for (ChatGptBridgeScenario scenario : scenarios) {
            try {
                ChatGptBridgeCoordinator.DispatchResult result = coordinator.dispatchTest(
                        runId,
                        scenario.id,
                        scenario.asRequestInput(),
                        scenario.asCortexOutput(),
                        scenario.asReferenceEvidence());
                if (result.sent) sent++; else failed++;
                items.put(new JSONObject()
                        .put("testId", scenario.id)
                        .put("category", scenario.category)
                        .put("sent", result.sent)
                        .put("requestId", result.request == null ? "" : result.request.optString("requestId", ""))
                        .put("gmailMessageId", result.gmailMessageId)
                        .put("error", result.error));
            } catch (Throwable t) {
                failed++;
                try {
                    items.put(new JSONObject()
                            .put("testId", scenario == null ? "null" : scenario.id)
                            .put("sent", false)
                            .put("error", t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage())));
                } catch (Throwable ignored) {}
            }
        }
        return new RunResult(runId, scenarios.size(), sent, failed, items);
    }

    public static final class RunResult {
        public final String runId;
        public final int total;
        public final int sent;
        public final int failed;
        public final JSONArray items;
        RunResult(String runId, int total, int sent, int failed, JSONArray items) {
            this.runId = runId;
            this.total = total;
            this.sent = sent;
            this.failed = failed;
            this.items = items;
        }

        public JSONObject toJson() {
            try {
                return new JSONObject()
                        .put("runId", runId)
                        .put("total", total)
                        .put("sent", sent)
                        .put("failed", failed)
                        .put("items", items);
            } catch (Throwable t) {
                return new JSONObject();
            }
        }
    }
}
