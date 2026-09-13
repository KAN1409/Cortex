package com.kareem.cortex;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gmail API remains primary for sends. Reads are discovery-oriented: when a fallback exists,
 * verdict candidates from both transports are merged and deduplicated so a partial successful
 * primary read can never hide verdicts visible through IMAP.
 */
public final class FailoverBridgeTransport implements BridgeMailTransport {
    private final BridgeMailTransport primary;
    private final BridgeMailTransport fallback;
    private volatile String lastUsed;

    public FailoverBridgeTransport(BridgeMailTransport primary, BridgeMailTransport fallback) {
        if (primary == null) throw new IllegalArgumentException("primary required");
        this.primary = primary;
        this.fallback = fallback;
        this.lastUsed = primary.name();
    }

    @Override public GmailBridgeTransport.SendResult sendEnvelope(JSONObject envelope) {
        GmailBridgeTransport.SendResult first = primary.sendEnvelope(envelope);
        if (first.ok || fallback == null || !shouldFallback(first.error)) {
            lastUsed = primary.name();
            return first;
        }
        GmailBridgeTransport.SendResult second = fallback.sendEnvelope(envelope);
        lastUsed = fallback.name();
        if (!second.ok) return GmailBridgeTransport.SendResult.fail("PRIMARY=" + first.error + " | FALLBACK=" + second.error);
        return second;
    }

    @Override public List<JSONObject> fetchCandidateVerdicts(long newerThanEpochMs, int maxResults) throws Exception {
        Throwable primaryError = null;
        Throwable fallbackError = null;
        List<JSONObject> primaryOut = new ArrayList<>();
        List<JSONObject> fallbackOut = new ArrayList<>();

        try {
            List<JSONObject> out = primary.fetchCandidateVerdicts(newerThanEpochMs, maxResults);
            if (out != null) primaryOut = out;
        } catch (Throwable t) {
            primaryError = t;
        }

        if (fallback != null) {
            try {
                List<JSONObject> out = fallback.fetchCandidateVerdicts(newerThanEpochMs, maxResults);
                if (out != null) fallbackOut = out;
            } catch (Throwable t) {
                fallbackError = t;
            }
        }

        if (primaryError != null && (fallback == null || fallbackError != null)) {
            String detail = "PRIMARY=" + primaryError;
            if (fallbackError != null) detail += " | FALLBACK=" + fallbackError;
            throw new Exception(detail);
        }

        Map<String,JSONObject> merged = new LinkedHashMap<>();
        addAllDeduped(merged, primaryOut);
        addAllDeduped(merged, fallbackOut);

        if (!fallbackOut.isEmpty() && !primaryOut.isEmpty()) lastUsed = primary.name() + "+" + fallback.name();
        else if (!fallbackOut.isEmpty()) lastUsed = fallback.name();
        else lastUsed = primary.name();

        int limit = Math.max(1, maxResults);
        ArrayList<JSONObject> result = new ArrayList<>(Math.min(limit, merged.size()));
        for (JSONObject envelope : merged.values()) {
            if (result.size() >= limit) break;
            result.add(envelope);
        }
        return result;
    }

    private static void addAllDeduped(Map<String,JSONObject> target, List<JSONObject> source) {
        if (source == null) return;
        for (JSONObject env : source) {
            if (env == null) continue;
            String key = env.optString("requestId", "") + "|"
                    + env.optString("runId", "") + "|"
                    + env.optString("testId", "") + "|"
                    + env.optString("payloadSha256", "");
            if (!target.containsKey(key)) target.put(key, env);
        }
    }

    @Override public String name() { return lastUsed; }

    public static boolean shouldFallback(String detail) {
        String s = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        return s.contains("unregisteredonapiconsole")
                || s.contains("authenticatorexception")
                || s.contains("auth_required")
                || s.contains("authorization required")
                || s.contains("no gmail oauth token")
                || s.contains("oauth token returned");
    }
}
