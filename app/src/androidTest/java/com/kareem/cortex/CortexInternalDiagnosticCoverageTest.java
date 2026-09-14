package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * Engineering-only runtime coverage. These activities remain testable, but passing
 * this suite never grants them production navigation or product-surface status.
 */
@RunWith(AndroidJUnit4.class)
public final class CortexInternalDiagnosticCoverageTest {
    private static final String PKG = "com.kareem.cortex";
    private static final String[] INTERNAL_DIAGNOSTICS = {
            "CognitiveShadowActivity",
            "CrashReportActivity",
            "CortexEndToEndActivity",
            "RelevanceEvaluationActivity",
            "CapabilityMatrixActivity",
            "EnvironmentActivity",
            "CortexAuditActivity",
            "ExternalModelCheckActivity",
            "OcrTestActivity",
            "CortexAsrLabActivity"
    };

    @Test public void internalDiagnosticsRemainCoveredAndNonExported() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        File root = app.getExternalFilesDir("internal-diagnostic-coverage");
        assertNotNull("External files directory unavailable", root);
        assertTrue(root.exists() || root.mkdirs());

        int passed = 0;
        for (String simple : INTERNAL_DIAGNOSTICS) {
            ComponentName component = new ComponentName(PKG, PKG + "." + simple);
            ActivityInfo info = app.getPackageManager().getActivityInfo(component, 0);
            assertFalse("Internal diagnostic must not be exported: " + simple, info.exported);

            Intent intent = new Intent().setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            app.startActivity(intent);
            assertTrue("Internal diagnostic did not render: " + simple,
                    device.wait(Until.hasObject(By.pkg(PKG)), 8000));
            device.waitForIdle();
            assertFalse("Crash dialog while rendering internal diagnostic " + simple, crashDialog(device));
            assertTrue("Diagnostic screenshot failed for " + simple,
                    device.takeScreenshot(new File(root, simple + ".png")));
            passed++;
            device.pressBack();
            SystemClock.sleep(120);
        }
        assertEquals(INTERNAL_DIAGNOSTICS.length, passed);
        System.out.println("INTERNAL_DIAGNOSTICS|PASS|count=" + passed);
    }

    private static boolean crashDialog(UiDevice device) {
        return device.hasObject(By.textContains("keeps stopping")) ||
                device.hasObject(By.textContains("isn't responding")) ||
                device.hasObject(By.textContains("has stopped"));
    }
}
