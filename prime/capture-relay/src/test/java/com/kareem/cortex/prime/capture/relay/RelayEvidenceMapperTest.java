package com.kareem.cortex.prime.capture.relay;

import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class RelayEvidenceMapperTest {
    @Test
    public void preservesTextMetadataAndStableRevisionIdentity() {
        NotificationObservation first = observation("Bring the drawings");
        EvidenceRecord a = RelayEvidenceMapper.toEvidence(first);
        EvidenceRecord b = RelayEvidenceMapper.toEvidence(first);

        assertEquals(EvidenceSource.NOTIFICATION, a.source);
        assertEquals(a.id, b.id);
        assertEquals("notification://com.whatsapp/notif-key-42", a.sourceRef);
        assertTrue(a.rawText.contains("Alice: Bring the drawings"));
        assertTrue(a.rawPayloadJson.contains("\"groupKey\":\"chat:42\""));
        assertTrue(a.rawPayloadJson.contains("\"channelId\":\"messages\""));
    }

    @Test
    public void updatedNotificationCreatesNewImmutableRevisionUnderSameSourceRef() {
        EvidenceRecord first = RelayEvidenceMapper.toEvidence(observation("Bring the drawings"));
        EvidenceRecord updated = RelayEvidenceMapper.toEvidence(observation("Bring the drawings by 3 PM"));

        assertEquals(first.sourceRef, updated.sourceRef);
        assertNotEquals(first.id, updated.id);
    }

    private static NotificationObservation observation(String messageText) {
        return new NotificationObservation(
                1_777_000_000_000L,
                "com.whatsapp",
                "notif-key-42",
                42,
                "tag",
                "chat:42",
                true,
                false,
                "msg",
                "messages",
                "Alice",
                messageText,
                messageText,
                "Project group",
                Collections.singletonList(new NotificationMessage("Alice", messageText, 1_777_000_000_000L))
        );
    }
}
