package com.kareem.cortex;
import android.database.sqlite.SQLiteDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static com.kareem.cortex.CortexActionReceipts.*;

/** Opening another app is never completion; completed actions are not run twice. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class CortexActionReceiptsTest {
    SQLiteDatabase db;
    @Before public void setup(){db=SQLiteDatabase.create(null);}
    @After public void close(){db.close();}

    @Test public void handoffIsNotCompletionUntilConfirmed(){
        Receipt r=begin(db,"brain|1|email",7,"EMAIL_DRAFT","Send PO to vendor",0,1000);
        assertEquals(REQUESTED,r.state);
        assertTrue(transition(db,"brain|1|email",HANDED_OFF,"Opened in another app",2000));
        Receipt h=find(db,"brain|1|email");
        assertEquals(HANDED_OFF,h.state);assertFalse(h.completed());assertEquals(2000,h.handedOffAt);
        assertEquals("PREPARED",legacyStatus(HANDED_OFF));
        assertTrue(transition(db,"brain|1|email",USER_CONFIRMED,"Confirmed by you",9000));
        Receipt done=find(db,"brain|1|email");
        assertTrue(done.completed());assertFalse("a user's yes is not a read-back",done.verified());
        assertEquals("DONE",legacyStatus(USER_CONFIRMED));
    }

    @Test public void completedActionIsIdempotentAndFinal(){
        begin(db,"brain|2|task",8,"TASK","Follow up",0,1000);
        assertTrue(transition(db,"brain|2|task",VERIFIED,"Local item 5 read back",1500));
        Receipt again=begin(db,"brain|2|task",8,"TASK","Follow up",0,5000);
        assertEquals(VERIFIED,again.state);assertEquals(1500,again.updatedAt);
        assertFalse(transition(db,"brain|2|task",FAILED,"late failure",6000));
        assertFalse(transition(db,"brain|2|task",HANDED_OFF,null,6000));
        assertEquals(VERIFIED,find(db,"brain|2|task").state);
    }

    @Test public void failedActionCanBeRetriedButNotSkipped(){
        begin(db,"brain|3|call",9,"CALL","Call vendor",0,1000);
        assertTrue(transition(db,"brain|3|call",FAILED,"No dialer",1100));
        assertNull("failed keeps the suggestion READY",legacyStatus(FAILED));
        assertFalse("cannot jump from failed to done",transition(db,"brain|3|call",USER_CONFIRMED,null,1200));
        assertEquals(REQUESTED,begin(db,"brain|3|call",9,"CALL","Call vendor",0,1300).state);
    }

    @Test public void cancelledIsFinal(){
        begin(db,"brain|4|msg",10,"MESSAGE_DRAFT","Tell site",0,1000);
        transition(db,"brain|4|msg",HANDED_OFF,null,1100);
        assertTrue(transition(db,"brain|4|msg",CANCELLED,"Cancelled by you",1200));
        assertEquals(CANCELLED,begin(db,"brain|4|msg",10,"MESSAGE_DRAFT","Tell site",0,2000).state);
        assertEquals("CANCELLED",legacyStatus(CANCELLED));
    }

    @Test public void pendingConfirmationRespectsAwayWindowAndAskedOnce(){
        begin(db,"brain|5|email",11,"EMAIL_DRAFT","Quote reply",0,1000);
        transition(db,"brain|5|email",HANDED_OFF,null,10_000);
        assertNull("returning a moment later is the same trip",pendingConfirmation(db,12_000,4_000,7_200_000));
        assertEquals("brain|5|email",pendingConfirmation(db,20_000,4_000,7_200_000).actionKey);
        assertNull("too old to ask about",pendingConfirmation(db,10_000+7_200_001,4_000,7_200_000));
        markAsked(db,"brain|5|email",20_000);
        assertNull("asked once per handoff",pendingConfirmation(db,30_000,4_000,7_200_000));
        assertEquals(HANDED_OFF,find(db,"brain|5|email").state);
        transition(db,"brain|5|email",HANDED_OFF,null,40_000);
        assertNotNull("a new handoff asks again",pendingConfirmation(db,50_000,4_000,7_200_000));
    }

    @Test public void unknownTransitionsAreRejected(){
        assertFalse(allowed(null,VERIFIED));
        assertFalse(allowed(REQUESTED,USER_CONFIRMED));
        assertFalse(transition(db,"missing",HANDED_OFF,null,1));
    }
}
