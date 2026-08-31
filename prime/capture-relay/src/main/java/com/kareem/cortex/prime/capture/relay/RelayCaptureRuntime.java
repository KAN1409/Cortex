package com.kareem.cortex.prime.capture.relay;

import com.kareem.cortex.prime.evidence.EvidenceRecord;

import java.util.concurrent.atomic.AtomicReference;

public final class RelayCaptureRuntime {
    private static final RelayCaptureSink NO_OP = (evidence, observation) -> {};
    private static final AtomicReference<RelayCaptureSink> SINK = new AtomicReference<>(NO_OP);
    private static volatile boolean captureEnabled = true;

    private RelayCaptureRuntime() {}

    public static void installSink(RelayCaptureSink sink) {
        SINK.set(sink == null ? NO_OP : sink);
    }

    public static void resetSink() {
        SINK.set(NO_OP);
    }

    public static void setCaptureEnabled(boolean enabled) {
        captureEnabled = enabled;
    }

    public static boolean isCaptureEnabled() {
        return captureEnabled;
    }

    static boolean submit(EvidenceRecord evidence, NotificationObservation observation) {
        try {
            SINK.get().onNotificationEvidence(evidence, observation);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
