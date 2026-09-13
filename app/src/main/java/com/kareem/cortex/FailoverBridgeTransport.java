package com.kareem.cortex;

import org.json.JSONObject;
import java.util.List;

/** Uses Gmail API first and falls back only for authentication/registration failures. */
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
        try {
            List<JSONObject> out = primary.fetchCandidateVerdicts(newerThanEpochMs, maxResults);
            lastUsed = primary.name();
            return out;
        } catch (Throwable first) {
            if (fallback == null || !shouldFallback(first.toString())) {
                if (first instanceof Exception) throw (Exception) first;
                throw new Exception(first);
            }
            lastUsed = fallback.name();
            return fallback.fetchCandidateVerdicts(newerThanEpochMs, maxResults);
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
