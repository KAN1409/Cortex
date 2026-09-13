package com.kareem.cortex;

import org.json.JSONObject;
import java.util.List;

/** Adapts the existing Gmail REST transport to the generic bridge transport boundary. */
public final class GmailApiBridgeTransportAdapter implements BridgeMailTransport {
    private final GmailBridgeTransport delegate;
    public GmailApiBridgeTransportAdapter(GmailBridgeTransport delegate) {
        if (delegate == null) throw new IllegalArgumentException("delegate required");
        this.delegate = delegate;
    }
    @Override public GmailBridgeTransport.SendResult sendEnvelope(JSONObject envelope) { return delegate.sendEnvelope(envelope); }
    @Override public List<JSONObject> fetchCandidateVerdicts(long newerThanEpochMs, int maxResults) throws Exception { return delegate.fetchCandidateVerdicts(newerThanEpochMs, maxResults); }
    @Override public String name() { return "GMAIL_API_OAUTH"; }
}
