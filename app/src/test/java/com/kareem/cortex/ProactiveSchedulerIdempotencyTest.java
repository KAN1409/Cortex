package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProactiveSchedulerIdempotencyTest {
    @Test public void reminderWorkNameIsStablePerAction(){
        assertEquals("cortex_reminder_42",ProactiveScheduler.reminderWorkName(42));
        assertEquals(ProactiveScheduler.reminderWorkName(42),ProactiveScheduler.reminderWorkName(42));
        assertNotEquals(ProactiveScheduler.reminderWorkName(42),ProactiveScheduler.reminderWorkName(43));
    }

    @Test public void invalidNegativeActionIdsCannotCreateNegativeWorkNames(){
        assertEquals("cortex_reminder_0",ProactiveScheduler.reminderWorkName(-7));
    }
}
