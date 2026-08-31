package com.kareem.cortex.prime.evidence;

import java.util.List;

/** Append-only raw evidence boundary. */
public interface EvidenceStore {
    /** Returns true only when a new immutable evidence row was inserted. */
    boolean append(EvidenceRecord record);

    List<EvidenceRecord> recent(int limit);
}
