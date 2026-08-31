package com.kareem.cortex.prime.capture.relay;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NotificationTextCleanerTest {
    @Test
    public void stripsMessageCountAndSplitsRepeatedSender() {
        List<String> messages = NotificationTextCleaner.semanticMessages(
                "",
                "Kareem Abdel Nasser",
                "3 new messages",
                "Haven't seen you in a whileKareem Abdel Nasser: Hey manKareem Abdel Nasser: I really miss u",
                List.of()
        );
        assertEquals(List.of("Haven't seen you in a while", "Hey man", "I really miss u"), messages);
    }

    @Test
    public void simplifiesDecoratedNotificationTitle() {
        assertEquals(
                "Makxx Aakash",
                NotificationTextCleaner.conversationLabel("", "Makxx Aakash: ⭐ Aakash | Photo Editor & Presets")
        );
    }

    @Test
    public void recognizesNotificationCountSummaries() {
        assertTrue(NotificationTextCleaner.isSummary("2 new messages"));
        assertTrue(NotificationTextCleaner.isSummary("3 messages"));
        assertFalse(NotificationTextCleaner.isSummary("Thank you"));
    }
}
