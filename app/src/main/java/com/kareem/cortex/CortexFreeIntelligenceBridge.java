package com.kareem.cortex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/** Free specialist requests routed over the existing Gmail bridge. */
public final class CortexFreeIntelligenceBridge {
    public enum Specialist {
        KNOWLEDGE_AUDIT,
        PLANNING,
        DIAGNOSTIC,
        WORK_VAULT_REASONING,
        CONTINUITY_REVIEW,
        FILE_FLOW_REVIEW
    }

    private CortexFreeIntelligenceBridge() {}

    public static JSONArray dispatchStarterPack(Context context, BridgeMailTransport transport) {
        JSONArray out = new JSONArray();
        for (Specialist specialist : Specialist.values()) {
            try {
                ChatGptBridgeCoordinator.DispatchResult r = dispatch(context, transport, specialist);
                out.put(new JSONObject()
                        .put("specialist", specialist.name())
                        .put("sent", r != null && r.sent)
                        .put("requestId", r == null || r.request == null ? "" : r.request.optString("requestId", ""))
                        .put("error", r == null ? "DISPATCH_FAILED" : r.error));
            } catch (Throwable t) {
                try { out.put(new JSONObject().put("specialist", specialist.name()).put("sent", false).put("error", t.toString())); }
                catch (Throwable ignored) {}
            }
        }
        return out;
    }

    public static ChatGptBridgeCoordinator.DispatchResult dispatch(
            Context context, BridgeMailTransport transport, Specialist specialist) throws Exception {
        JSONObject original = new JSONObject()
                .put("kind", specialist.name())
                .put("instruction", instruction(specialist))
                .put("privacy", "Use only the supplied grounded package. Do not infer or request secrets.");

        JSONObject cortex = new JSONObject()
                .put("activePolicy", CortexPersonalPolicy.current(context))
                .put("judgeVersion", CortexAttentionJudge.VERSION)
                .put("proposalOnly", true);

        JSONObject reference = new JSONObject()
                .put("safety", new JSONObject()
                        .put("canonicalWritesForbidden", true)
                        .put("executionForbidden", true)
                        .put("approvalBypassForbidden", true)
                        .put("inventedEvidenceForbidden", true))
                .put("desiredOutput", desiredOutput(specialist));

        JSONObject rules = ChatGptBridgeProtocol.defaultJudgingRules()
                .put("teachingDisabled", false)
                .put("teachingMode", "PROPOSAL_ONLY")
                .put("specialist", specialist.name());

        return new ChatGptBridgeCoordinator(context, transport).dispatchTestWithRules(
                "free-intelligence-" + System.currentTimeMillis(),
                "specialist-" + specialist.name().toLowerCase(java.util.Locale.ROOT),
                original,
                cortex,
                reference,
                rules);
    }

    private static String instruction(Specialist s) {
        switch (s) {
            case KNOWLEDGE_AUDIT:
                return "Audit supplied grounded knowledge for contradictions, duplicates, staleness, ambiguous entity merges, missing provenance, and unresolved commitments. Return repair proposals only.";
            case PLANNING:
                return "Turn the supplied grounded goal into steps, dependencies, blockers, required evidence, risks, and approval points. Do not execute anything.";
            case DIAGNOSTIC:
                return "Analyze supplied test/build evidence, identify evidence-backed probable causes, rank next tests and minimal fixes, and separate facts from hypotheses.";
            case WORK_VAULT_REASONING:
                return "Reason only over supplied Work Vault evidence. Compare documents, prices, vendors, quantities, changes, and missing evidence without inventing archive facts.";
            case CONTINUITY_REVIEW:
                return "Check whether state, commitments, decisions, and follow-ups remain logically consistent over time. Flag stale or contradictory state and propose safe reconciliation.";
            case FILE_FLOW_REVIEW:
                return "Review file receive/open/share/extract/parse/provenance flow. Compare each produced result with supplied original bytes/hash/metadata and flag integrity breaks.";
            default:
                return "Return bounded advisory analysis only.";
        }
    }

    private static JSONObject desiredOutput(Specialist s) throws Exception {
        JSONObject o = new JSONObject()
                .put("status", "PASS|WARN|FAIL")
                .put("severity", "NONE|P3|P2|P1|P0")
                .put("findings", new JSONArray())
                .put("evidenceIdsUsed", new JSONArray())
                .put("unsupportedAssumptions", new JSONArray())
                .put("suggestedActions", new JSONArray())
                .put("teachingCandidate", "optional bounded proposal");
        if (s == Specialist.FILE_FLOW_REVIEW) {
            o.put("fileChecks", new JSONArray()
                    .put("receive").put("identity/hash").put("mime").put("open")
                    .put("share/FileProvider").put("extract").put("parse").put("provenance"));
        }
        return o;
    }
}
