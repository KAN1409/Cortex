package com.kareem.cortex;

/**
 * Transport boundary for Cortex <-> ChatGPT bridge messages.
 *
 * A Gmail implementation must use authenticated Gmail API access. The transport
 * is intentionally separated from adjudication logic so email credentials never
 * leak into the judge or test harness.
 */
public interface ChatGptBridgeTransport {
    void send(ChatGptBridgeEnvelope envelope) throws Exception;

    interface Listener {
        void onEnvelope(ChatGptBridgeEnvelope envelope);
        void onTransportError(Throwable error);
    }

    void start(Listener listener) throws Exception;
    void stop();
}
