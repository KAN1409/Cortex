package com.kareem.cortex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/** Coordinates test-case export, Gmail delivery, verdict polling, and local acceptance. */
public final class ChatGptBridgeCoordinator {
    private final ChatGptBridgeStore store;
    private final GmailBridgeTransport transport;

    public ChatGptBridgeCoordinator(Context context, GmailBridgeTransport transport) {
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
            JSONObject request = ChatGptBridgeProtocol.newTestRequest(
                    runId,
                    testId,
                    originalInput,
                    cortexOutput,
                    referenceEvidence,
                    ChatGptBridgeProtocol.defaultJudgingRules());
            File persisted = store.putPending(request);
            GmailBridgeTransport.SendResult sent = transport.sendEnvelope(request);
            if (!sent.ok) {
                return DispatchResult.failed(request, persisted, sent.error);
            }
            return DispatchResult.sent(request, persisted, sent.messageId, sent.threadId);
        } catch (Throwable t) {
            return DispatchResult.failed(null, null, t.getClass().getSimpleName() + ": " + safeMessage(t));
        }
    }

    /**
     * Fetches candidate Gmail verdicts and accepts only strictly correlated responses.
     * Unknown/malformed responses remain diagnostic-only via ChatGptBridgeStore.rejected.
     */
    public PollResult pollVerdicts(long newerThanEpochMs, int maxResults) {
        int seen = 0;
        int accepted = 0;
        int rejected = 0;
        JSONArray details = new JSONArray();
        try {
            List<JSONObject> candidates = transport.fetchCandidateVerdicts(newerThanEpochMs, maxResults);
            for (JSONObject verdict : candidates) {
                seen++;
                ChatGptBridgeStore.AcceptResult result = store.acceptVerdict(verdict);
                if (result.accepted) accepted++; else rejected++;
                details.put(new JSONObject()
                        .put("requestId", verdict.optString("requestId", ""))
                        .put("accepted", result.accepted)
                        .put("detail", result.detail));
            }
            return new PollResult(true, seen, accepted, rejected, details, "");
        } catch (Throwable t) {
            return new PollResult(false, seen, accepted, rejected, details,
                    t.getClass().getSimpleName() + ": " + safeMessage(t));
        }
    }

    public JSONObject status() {
        try { return store.summary(); }
        catch (JSONException e) {
            return new JSONObject();
        }
    }

    public List<JSONObject> pending() { return store.listPending(); }
    public List<JSONObject> verdicts() { return store.listVerdicts(); }

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? "" : t.getMessage();
    }

    public static final class DispatchResult {
        public final boolean sent;
        public final JSONObject request;
        public final File persistedFile;
        public final String gmailMessageId;
        public final String gmailThreadId;
        public final String error;
        private DispatchResult(boolean sent, JSONObject request, File persistedFile,
                               String gmailMessageId, String gmailThreadId, String error) {
            this.sent = sent;
            this.request = request;
            this.persistedFile = persistedFile;
            this.gmailMessageId = gmailMessageId;
            this.gmailThreadId = gmailThreadId;
            this.error = error;
        }
        static DispatchResult sent(JSONObject request, File file, String id, String threadId) {
            return new DispatchResult(true, request, file, id, threadId, "");
        }
        static DispatchResult failed(JSONObject request, File file, String error) {
            return new DispatchResult(false, request, file, "", "", error == null ? "UNKNOWN" : error);
        }
    }

    public static final class PollResult {
        public final boolean ok;
        public final int seen;
        public final int accepted;
        public final int rejected;
        public final JSONArray details;
        public final String error;
        PollResult(boolean ok, int seen, int accepted, int rejected, JSONArray details, String error) {
            this.ok = ok;
            this.seen = seen;
            this.accepted = accepted;
            this.rejected = rejected;
            this.details = details;
            this.error = error;
        }
    }
}
