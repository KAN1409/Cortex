package com.kareem.cortex;

import android.app.ApplicationExitInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProcessExitRecorderTest {
    @Test public void mapsCriticalExitReasons() {
        assertEquals("CRASH", ProcessExitRecorder.reasonName(ApplicationExitInfo.REASON_CRASH));
        assertEquals("CRASH_NATIVE", ProcessExitRecorder.reasonName(ApplicationExitInfo.REASON_CRASH_NATIVE));
        assertEquals("ANR", ProcessExitRecorder.reasonName(ApplicationExitInfo.REASON_ANR));
        assertEquals("LOW_MEMORY", ProcessExitRecorder.reasonName(ApplicationExitInfo.REASON_LOW_MEMORY));
        assertEquals("INITIALIZATION_FAILURE", ProcessExitRecorder.reasonName(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE));
        assertEquals("EXCESSIVE_RESOURCE_USAGE", ProcessExitRecorder.reasonName(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE));
    }
}
