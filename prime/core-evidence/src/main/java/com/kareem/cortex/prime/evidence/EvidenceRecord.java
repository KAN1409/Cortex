package com.kareem.cortex.prime.evidence;

import java.util.Objects;

public final class EvidenceRecord {
    public final String id;
    public final EvidenceSource source;
    public final long capturedAtEpochMs;
    public final String rawText;
    public final String sourceRef;
    public final String rawPayloadJson;

    public EvidenceRecord(String id, EvidenceSource source, long capturedAtEpochMs, String rawText, String sourceRef) {
        this(id, source, capturedAtEpochMs, rawText, sourceRef, "");
    }

    public EvidenceRecord(
            String id,
            EvidenceSource source,
            long capturedAtEpochMs,
            String rawText,
            String sourceRef,
            String rawPayloadJson
    ) {
        this.id = Objects.requireNonNull(id);
        this.source = Objects.requireNonNull(source);
        this.capturedAtEpochMs = capturedAtEpochMs;
        this.rawText = rawText == null ? "" : rawText;
        this.sourceRef = sourceRef == null ? "" : sourceRef;
        this.rawPayloadJson = rawPayloadJson == null ? "" : rawPayloadJson;
    }
}
