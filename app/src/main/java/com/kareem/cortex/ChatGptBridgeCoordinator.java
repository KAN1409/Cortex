package com.kareem.cortex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/** Coordinates test-case export, mail delivery, verdict polling, and local acceptance. */
public final class ChatGptBridgeCoordinator {
    private final ChatGptBridgeStore store;
    private final BridgeMailTransport transport;

    public ChatGptBridgeCoordinator(Context context, BridgeMailTransport transport) {
        if (transport == null) throw new IllegalArgumentException("transport required");
        this.store = new ChatGptBridgeStore(context.getApplicationContext());
        this.transport = transport;
    }

    public DispatchResult dispatchTest(
            String runId,
            String testId,
            JSONObject originalInput,
            JSONObject cortexOutput,
            JSONObject referenceEvidence) {
        try {
            return dispatchTestWithRules(runId,testId,originalInput,cortexOutput,referenceEvidence,
                    ChatGptBridgeProtocol.defaultJudgingRules());
        } catch (Throwable t) {
            return DispatchResult.failed(null, null, t.getClass().getSimpleName() + ": " + safeMessage(t), transport.name());
        }
    }

    public DispatchResult dispatchTestWithRules(
            String runId,
            String testId,
            JSONObject originalInput,
            JSONObject cortexOutput,
            JSONObject referenceEvidence,
            JSONObject judgingRules) {
        try {
            JSONObject request = ChatGptBridgeProtocol.newTestRequest(
                    runId,
                    testId,
                    originalInput,
                    cortexOutput,
                    referenceEvidence,
                    judgingRules == null ? ChatGptBridgeProtocol.defaultJudgingRules() : judgingRules);
            File persisted = store.putPending(request);
            GmailBridgeTransport.SendResult sent = transport.sendEnvelope(request);
            if (!sent.ok) return DispatchResult.failed(request, persisted, sent.error, transport.name());
            return DispatchResult.sent(request, persisted, sent.messageId, sent.threadId, transport.name());
        } catch (Throwable t) {
            return DispatchResult.failed(null, null, t.getClass().getSimpleName() + ": " + safeMessage(t), transport.name());
        }
    }

    public PollResult pollVerdicts(long newerThanEpochMs, int maxResults) {
        int seen = 0, accepted = 0, rejected = 0;
        JSONArray details = new JSONArray();
        try {
            List<JSONObject> candidates = transport.fetchCandidateVerdicts(newerThanEpochMs, maxResults);
            for (JSONObject verdict : candidates) {
                seen++;
                ChatGptBridgeStore.AcceptResult result = store.acceptVerdict(verdict);
                if (result.accepted) accepted++; else if (!"DUPLICATE_IGNORED".equals(result.detail)) rejected++;
                details.put(new JSONObject()
                        .put("requestId", verdict.optString("requestId", ""))
                        .put("accepted", result.accepted)
                        .put("detail", result.detail));
            }
            return new PollResult(true, seen, accepted, rejected, details, "", transport.name());
        } catch (Throwable t) {
            return new PollResult(false, seen, accepted, rejected, details,
                    t.getClass().getSimpleName() + ": " + safeMessage(t), transport.name());
        }
    }

    public JSONObject status() {
        try { return store.summary().put("transport", transport.name()); }
        catch (JSONException e) { return new JSONObject(); }
    }

    public List<JSONObject> pending() { return store.listPending(); }
    public List<JSONObject> verdicts() { return store.listVerdicts(); }

    private static String safeMessage(Throwable t) { return t.getMessage() == null ? "" : t.getMessage(); }

    public static final class DispatchResult {
        public final boolean sent;
        public final JSONObject request;
        public final File persistedFile;
        public final String gmailMessageId;
        public final String gmailThreadId;
        public final String error;
        public final String transport;
        private DispatchResult(boolean sent, JSONObject request, File persistedFile,
                               String gmailMessageId, String gmailThreadId, String error, String transport) {
            this.sent = sent; this.request = request; this.persistedFile = persistedFile;
            this.gmailMessageId = gmailMessageId; this.gmailThreadId = gmailThreadId;
            this.error = error; this.transport = transport;
        }
        static DispatchResult sent(JSONObject request, File file, String id, String threadId, String transport) {
            return new DispatchResult(true, request, file, id, threadId, "", transport);
        }
        static DispatchResult failed(JSONObject request, File file, String error, String transport) {
            return new DispatchResult(false, request, file, "", "", error == null ? "UNKNOWN" : error, transport);
        }
    }

    public static final class PollResult {
        public final boolean ok;
        public final int seen, accepted, rejected;
        public final JSONArray details;
        public final String error;
        public final String transport;
        PollResult(boolean ok, int seen, int accepted, int rejected, JSONArray details, String error, String transport) {
            this.ok = ok; this.seen = seen; this.accepted = accepted; this.rejected = rejected;
            this.details = details; this.error = error; this.transport = transport;
        }
    }
}
