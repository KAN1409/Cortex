package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationAttentionFeaturesTest {
    @Test public void shipmentConfirmationIsExplicitGroundedRequest() {
        String text = "Dear Customer : Kareem Abdel Nasser Your shipment is on the way to its final destination. " +
                "Kindly confirm: Is this your shipment? Yes, it is mine No, it is not mine " +
                "شحنتك في طريقها الي وجهتها الاخيرة يرجي التأكد: هل هذه شحنتك؟";
        ConversationAttentionFeatures.Result r =
                ConversationAttentionFeatures.evaluate("conversation_message", "message", "J&T Express Egypt", text);
        assertTrue(r.explicitRequest);
        assertTrue(r.responseExpected);
        assertTrue(r.actionabilityFloor >= .80);
        assertTrue(r.relevanceFloor >= .45);
    }

    @Test public void arabicConfirmationIsExplicitRequest() {
        ConversationAttentionFeatures.Result r =
                ConversationAttentionFeatures.evaluate("conversation_message", "message",
                        "J&T Express Egypt", "يرجى التأكد: هل هذه شحنتك؟ نعم / لا");
        assertTrue(r.explicitRequest);
        assertTrue(r.responseExpected);
    }

    @Test public void socialConversationIsNotPromoted() {
        ConversationAttentionFeatures.Result r =
                ConversationAttentionFeatures.evaluate("conversation_message", "message",
                        "Mohamed Hammad", "وياريت في ميدان عام عشان كلوا يتعظ");
        assertFalse(r.explicitRequest);
    }

    @Test public void genericQuestionIsNotEnough() {
        ConversationAttentionFeatures.Result r =
                ConversationAttentionFeatures.evaluate("conversation_message", "message",
                        "Friend", "How are you?");
        assertFalse(r.explicitRequest);
    }

    @Test public void screenshotChromeIsNotRequest() {
        ConversationAttentionFeatures.Result r =
                ConversationAttentionFeatures.evaluate("notification_event", "notification",
                        "Screenshot saved", "Tap here to see your screenshot. Share Edit Delete");
        assertFalse(r.explicitRequest);
    }

    @Test public void uiActionsAloneAreNotRequest() {
        ConversationAttentionFeatures.Result r =
                ConversationAttentionFeatures.evaluate("conversation_message", "message",
                        "Screen", "Share Edit Delete Open Save Cancel");
        assertFalse(r.explicitRequest);
    }
}
