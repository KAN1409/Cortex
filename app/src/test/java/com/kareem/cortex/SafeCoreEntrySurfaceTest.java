package com.kareem.cortex;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SafeCoreEntrySurfaceTest {
    @After public void cleanup(){SafeCoreRuntime.resetForTests();}

    @Test public void actualLauncherIsNowActivityAndItsResumeArmsSafeCore(){
        Context context=ApplicationProvider.getApplicationContext();
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("Cortex must expose a launcher intent",launch);
        ComponentName component=launch.getComponent();
        assertNotNull("Launcher component must resolve",component);
        assertEquals(NowActivity.class.getName(),component.getClassName());

        SafeCoreRuntime.resetForTests();
        NowActivity activity=Robolectric.buildActivity(NowActivity.class).get();
        new SafeCoreLifecycle().onActivityResumed(activity);
        assertTrue("The actual launcher resume must arm SafeCoreRuntime",SafeCoreRuntime.armedForTests());
    }

    @Test public void migrationGenerationForEntrySurfaceFixIsV102(){
        assertEquals("v102_reindexed_after_entry_surface_fix",DocumentIntelligenceMigration.preferenceKeyForTests());
    }
}
