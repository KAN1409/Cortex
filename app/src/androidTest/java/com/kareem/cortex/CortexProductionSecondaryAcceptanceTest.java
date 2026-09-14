package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Production-secondary user surfaces that are intentionally outside the four primary destinations. */
@RunWith(AndroidJUnit4.class)
public final class CortexProductionSecondaryAcceptanceTest {
    private static final String PKG="com.kareem.cortex";

    @Test public void settingsAndSystemHealthRenderAsInternalUserSurfaces() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertUserSurface(app,device,SettingsActivity.class,"Settings");
        assertUserSurface(app,device,CortexStatusActivity.class,"System Health");
        assertTrue("System Health never produced a user-level state",
                device.wait(Until.hasObject(By.text("HEALTHY")),8000)
                        || device.hasObject(By.text("NEEDS ATTENTION"))
                        || device.hasObject(By.text("DEGRADED")));
    }

    private static void assertUserSurface(Context app,UiDevice device,Class<?> cls,String visibleText) throws Exception {
        ComponentName component=new ComponentName(app,cls);
        ActivityInfo info=app.getPackageManager().getActivityInfo(component,0);
        assertFalse("User-only secondary surface must not be externally exported: "+cls.getSimpleName(),info.exported);
        Intent intent=new Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        app.startActivity(intent);
        assertTrue("Surface package did not render: "+cls.getSimpleName(),device.wait(Until.hasObject(By.pkg(PKG)),8000));
        assertTrue("Expected user-facing title missing: "+visibleText,device.wait(Until.hasObject(By.text(visibleText)),8000));
        assertFalse("Crash dialog while rendering "+cls.getSimpleName(),crashDialog(device));
    }

    private static boolean crashDialog(UiDevice device){
        return device.hasObject(By.textContains("keeps stopping"))
                || device.hasObject(By.textContains("isn't responding"))
                || device.hasObject(By.textContains("has stopped"));
    }
}
