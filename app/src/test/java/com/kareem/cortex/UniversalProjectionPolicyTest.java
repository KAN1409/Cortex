package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class UniversalProjectionPolicyTest {
    @Test public void socialFriendAdditionCannotBecomeAction(){assertEquals("INFO",UniversalProjectionPolicy.validateAttention("social_notification","friend_addition","ACTION",0.95));}
    @Test public void optionalMessageCannotBecomeAction(){assertEquals("INFO",UniversalProjectionPolicy.validateAttention("conversation_message","message_received","ACTION",0.95));}
    @Test public void explicitRequestCanBecomeAction(){assertEquals("ACTION",UniversalProjectionPolicy.validateAttention("action_request","request","ACTION",0.90));}
    @Test public void commitmentCanBecomeWaiting(){assertEquals("WAITING",UniversalProjectionPolicy.validateAttention("commitment","waiting","WAITING",0.91));}
    @Test public void actualDecisionCanBecomeDecision(){assertEquals("DECISION",UniversalProjectionPolicy.validateAttention("decision","approved","DECISION",0.88));}
    @Test public void genericNotificationIsNotUserFacing(){assertFalse(UniversalProjectionPolicy.userFacingSemantic("notification_event","complete",0.95,"notification_event",""));}
    @Test public void waitingSemanticIsNotUserFacingMeaning(){assertFalse(UniversalProjectionPolicy.userFacingSemantic("conversation_message","waiting",0.95,"A","B"));}
    @Test public void groundedConversationCanBeUserFacing(){assertTrue(UniversalProjectionPolicy.userFacingSemantic("conversation_message","complete",0.91,"Minas","Message body"));}
}
