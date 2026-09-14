package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class LegacySurfaceAliasTest {
    private final Context context=ApplicationProvider.getApplicationContext();

    @Test public void duplicatePresentationComponentsResolveToOneImplementationPerFamily() throws Exception {
        assertAlias(NowActivity.class,CortexShellActivity.class);
        assertAlias(SatinBriefActivity.class,ProposalBriefActivity.class);
        assertAlias(CortexOrbBriefActivity.class,ProposalBriefActivity.class);
        assertAlias(PremiumHomeActivity.class,ProposalBriefActivity.class);
        assertAlias(CaptureActivity.class,ProposalCaptureActivity.class);
        assertAlias(SatinCaptureActivity.class,ProposalCaptureActivity.class);
        assertAlias(PeopleProjectsActivity.class,ProposalPeopleProjectsActivity.class);
        assertAlias(AskCortexActivity.class,ProposalAskCortexActivity.class);
    }

    private void assertAlias(Class<?> alias,Class<?> target) throws Exception {
        ActivityInfo info=context.getPackageManager().getActivityInfo(new ComponentName(context,alias),PackageManager.GET_META_DATA);
        assertNotNull("Expected manifest alias for "+alias.getSimpleName(),info.targetActivity);
        assertEquals(target.getName(),info.targetActivity);
        assertFalse("Legacy alias must stay internal: "+alias.getSimpleName(),info.exported);
    }
}
