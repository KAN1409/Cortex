package com.kareem.cortex.prime.capture.relay;

import com.kareem.cortex.prime.evidence.EvidenceRecord;

/** Process-local capture boundary. No broadcast, binder or Local Bus transport. */
public interface RelayCaptureSink {
    void onNotificationEvidence(EvidenceRecord evidence, NotificationObservation observation);
}
