package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/** Regression contract for recovery startup and WorkManager bootstrap safety. */
@RunWith(RobolectricTestRunner.class)
public class ColdStartQuarantineTest {
    @Test public void quarantineIsCompileTimeActive() {
        assertTrue(StartupSafetyGate.active());
        assertEquals("startup_quarantine_001", StartupSafetyGate.VERSION);
        assertEquals("native runtime quarantined", LocalLlmRuntime.runtimeVersion());
    }

    @Test public void cortexAppProvidesWorkManagerConfigurationForPersistedSystemJobs() {
        assertTrue(Configuration.Provider.class.isAssignableFrom(CortexApp.class));
        Configuration configuration=new CortexApp().getWorkManagerConfiguration();
        assertNotNull(configuration);
    }

    @Test public void allStartupSchedulersAreSafeUnderQuarantine() {
        Context c=ApplicationProvider.getApplicationContext();
        StartupMaintenance.schedule(c);
        PhoneContextScheduler.schedule(c);
        StatefulMeaningScheduler.kick(c);
        UniversalSemanticScheduler.kick(c);
        ProactiveScheduler.enableDaily(c);
        ScreenshotDeepOcrScheduler.kick(c);
        ScreenshotWorkScheduler.kick(c);
        VisualIntelligenceScheduler.kick(c);
        assertTrue(StartupSafetyGate.active());
    }

    @Test public void launcherCanReachResumedStateUnderQuarantine() {
        try(ActivityController<InputActivity> controller=Robolectric.buildActivity(InputActivity.class).create().start().resume().visible()) {
            InputActivity activity=controller.get();
            assertNotNull(activity);
            assertFalse(activity.isFinishing());
        }
    }
}
