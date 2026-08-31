package com.kareem.cortex.prime.capture.relay;

public final class NotificationMessage {
    public final String sender;
    public final String text;
    public final long timestampEpochMs;

    public NotificationMessage(String sender, String text, long timestampEpochMs) {
        this.sender = sender == null ? "" : sender;
        this.text = text == null ? "" : text;
        this.timestampEpochMs = timestampEpochMs;
    }
}
