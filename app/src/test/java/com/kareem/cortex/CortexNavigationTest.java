package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class CortexNavigationTest {

    @Test public void primaryNavigationClearsPeerHistoryAndFinishesCaller() {
        Activity caller = Robolectric.buildActivity(Activity.class).create().start().resume().get();

        CortexNavigation.openPrimary(caller, CaptureOverviewActivity.class);

        Intent launched = Shadows.shadowOf(caller).getNextStartedActivity();
        assertNotNull(launched);
        assertEquals(CaptureOverviewActivity.class.getName(), launched.getComponent().getClassName());
        assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
        assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        assertEquals(0, launched.getFlags() & Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        assertTrue(caller.isFinishing());
    }

    @Test public void centerInputActionDoesNotUseReorderToFront() {
        Activity caller = Robolectric.buildActivity(Activity.class).create().start().resume().get();

        CortexNavigation.openInput(caller);

        Intent launched = Shadows.shadowOf(caller).getNextStartedActivity();
        assertNotNull(launched);
        assertEquals(InputActivity.class.getName(), launched.getComponent().getClassName());
        assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        assertEquals(0, launched.getFlags() & Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        assertFalse(caller.isFinishing());
    }

    @Test public void selectingCurrentPrimaryDestinationIsNoOp() {
        Activity caller = Robolectric.buildActivity(Activity.class).create().start().resume().get();

        CortexNavigation.openPrimary(caller, Activity.class);

        assertNull(Shadows.shadowOf(caller).getNextStartedActivity());
        assertFalse(caller.isFinishing());
    }
}
