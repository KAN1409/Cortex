package com.kareem.cortex;

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
        InputActivity input = Robolectric.buildActivity(InputActivity.class).create().start().resume().get();

        CortexNavigation.openPrimary(input, CaptureOverviewActivity.class);

        Intent launched = Shadows.shadowOf(input).getNextStartedActivity();
        assertNotNull(launched);
        assertEquals(CaptureOverviewActivity.class.getName(), launched.getComponent().getClassName());
        assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
        assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        assertEquals(0, launched.getFlags() & Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        assertTrue(input.isFinishing());
    }

    @Test public void centerInputActionDoesNotUseReorderToFront() {
        NowActivity now = Robolectric.buildActivity(NowActivity.class).create().start().resume().get();

        CortexNavigation.openInput(now);

        Intent launched = Shadows.shadowOf(now).getNextStartedActivity();
        assertNotNull(launched);
        assertEquals(InputActivity.class.getName(), launched.getComponent().getClassName());
        assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        assertEquals(0, launched.getFlags() & Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        assertFalse(now.isFinishing());
    }

    @Test public void selectingCurrentPrimaryDestinationIsNoOp() {
        CaptureOverviewActivity capture = Robolectric.buildActivity(CaptureOverviewActivity.class).create().start().resume().get();

        CortexNavigation.openPrimary(capture, CaptureOverviewActivity.class);

        assertNull(Shadows.shadowOf(capture).getNextStartedActivity());
        assertFalse(capture.isFinishing());
    }
}
