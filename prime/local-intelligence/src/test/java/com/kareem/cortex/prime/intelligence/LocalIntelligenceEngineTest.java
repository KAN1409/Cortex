package com.kareem.cortex.prime.intelligence;

import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class LocalIntelligenceEngineTest {
    @Test
    public void linksNotificationsByGroundedConversation() {
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.NOTIFICATION, 10, "conversation: Ahmed\nmessage: Hi", "notif:a"),
                new EvidenceRecord("e2", EvidenceSource.NOTIFICATION, 20, "conversation: Ahmed\nmessage: meeting Monday 10:30", "notif:b")
        ));
        assertEquals(1, snapshot.threads.size());
        assertEquals(2, snapshot.threads.get(0).evidenceIds.size());
        assertEquals("Ahmed", snapshot.threads.get(0).label);
        assertFalse(snapshot.temporalHints.isEmpty());
        assertFalse(snapshot.taskCandidates.isEmpty());
    }

    @Test
    public void legacyEvidenceStillWorksAfterNormalizerUpgrade() {
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.NOTIFICATION, 10, "title: Ahmed\nbody: Hi", "notif:a")
        ));
        assertEquals(1, snapshot.threads.size());
        assertEquals("Hi", snapshot.threads.get(0).latestSnippet);
    }

    @Test
    public void relationalMessageAloneDoesNotInventAttention() {
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.NOTIFICATION, 10, "conversation: Kareem\nmessage: Haven't seen you in a while", "notif:a")
        ));
        assertEquals(0, snapshot.taskCandidates.size());
        assertEquals(0, snapshot.attentionCandidates.size());
    }

    @Test
    public void explicitRequestCanBecomeAttention() {
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.NOTIFICATION, 10, "conversation: Kareem\nmessage: Can you send the files please?", "notif:a")
        ));
        assertEquals(1, snapshot.attentionCandidates.size());
    }

    @Test
    public void canonicalSystemNotificationDoesNotBecomeConversation() {
        String payload = "{\"packageName\":\"com.openai.chatgpt\",\"category\":\"status\",\"conversationTitle\":\"\",\"messages\":[]}";
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.NOTIFICATION, 10, "conversation: Response ready\nmessage: Tap to return to ChatGPT", "notification://com.openai.chatgpt/x", payload)
        ));
        assertEquals(0, snapshot.threads.size());
        assertEquals(0, snapshot.attentionCandidates.size());
    }

    @Test
    public void canonicalMessageNotificationRemainsConversation() {
        String payload = "{\"packageName\":\"com.whatsapp\",\"category\":\"msg\",\"conversationTitle\":\"Ahmed\",\"messages\":[{\"sender\":\"Ahmed\",\"text\":\"Hi\"}]}";
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.NOTIFICATION, 10, "conversation: Ahmed\nmessage: Hi", "notification://com.whatsapp/x", payload)
        ));
        assertEquals(1, snapshot.threads.size());
    }

    @Test
    public void loneDigitsDoNotBecomeTimes() {
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.TEXT, 10, "3", "manual:text")
        ));
        assertEquals(0, snapshot.temporalHints.size());
    }

    @Test
    public void explicitClockTimeStillBecomesTemporalHint() {
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.TEXT, 10, "send it at 4:14", "manual:text")
        ));
        assertEquals(1, snapshot.temporalHints.size());
    }

    @Test
    public void neverInventsTaskWithoutCue() {
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("e1", EvidenceSource.NOTIFICATION, 10, "conversation: Ahmed\nmessage: Hi", "notif:a")
        ));
        assertEquals(0, snapshot.taskCandidates.size());
    }

    @Test
    public void completedDerivedTranscriptCanFeedGroundedIntelligence() {
        String payload = "{\"schema\":\"CORTEX_PRIME_DERIVED_TRANSCRIPT_V1\",\"status\":\"COMPLETE\",\"parent_evidence_id\":\"voice1\",\"derived\":true,\"immutable_parent\":true}";
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("t1", EvidenceSource.TEXT, 20, "remind me Monday 10:30", "derived-from:voice1", payload)
        ));
        assertEquals(1, snapshot.taskCandidates.size());
        assertEquals(1, snapshot.temporalHints.size());
    }

    @Test
    public void derivedStatusCannotLeakIntoIntelligence() {
        String payload = "{\"schema\":\"CORTEX_PRIME_DERIVED_TRANSCRIPT_STATUS_V1\",\"status\":\"RETRYABLE\",\"parent_evidence_id\":\"voice1\",\"derived\":true,\"immutable_parent\":true}";
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("s1", EvidenceSource.TEXT, 20, "remind me Monday 10:30", "derived-from:voice1", payload)
        ));
        assertEquals(0, snapshot.taskCandidates.size());
        assertEquals(0, snapshot.temporalHints.size());
    }

    @Test
    public void mismatchedDerivedParentCannotFeedIntelligence() {
        String payload = "{\"schema\":\"CORTEX_PRIME_DERIVED_OCR_V1\",\"status\":\"COMPLETE\",\"parent_evidence_id\":\"image-other\",\"derived\":true,\"immutable_parent\":true}";
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(List.of(
                new EvidenceRecord("o1", EvidenceSource.OCR, 20, "appointment Friday 14:00", "derived-from:image1", payload)
        ));
        assertEquals(0, snapshot.taskCandidates.size());
        assertEquals(0, snapshot.temporalHints.size());
    }
}
