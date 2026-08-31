package com.kareem.cortex.prime.intelligence;

import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class LocalIntelligenceEngineTest {
    @Test
    public void linksNotificationsByGroundedTitle() {
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.NOTIFICATION, 10, "title: Ahmed\nbody: Hi", "notif:a"),
                new EvidenceRecord("e2", EvidenceSource.NOTIFICATION, 20, "title: Ahmed\nbody: meeting Monday 10:30", "notif:b")
        ));
        assertEquals(1, snapshot.threads.size());
        assertEquals(2, snapshot.threads.get(0).evidenceIds.size());
        assertEquals("Ahmed", snapshot.threads.get(0).label);
        assertFalse(snapshot.temporalHints.isEmpty());
        assertFalse(snapshot.taskCandidates.isEmpty());
    }

    @Test
    public void neverInventsTaskWithoutCue() {
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.NOTIFICATION, 10, "title: Ahmed\nbody: Hi", "notif:a")
        ));
        assertEquals(0, snapshot.taskCandidates.size());
    }
}
