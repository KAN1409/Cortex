package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorkVaultBrowseActivityTest {
    @Test public void normalizesSupportedModesAndDefaultsSafely(){
        assertEquals(WorkVaultBrowseActivity.MODE_PROJECTS,WorkVaultBrowseActivity.normalizeMode(null));
        assertEquals(WorkVaultBrowseActivity.MODE_PROJECTS,WorkVaultBrowseActivity.normalizeMode("unknown"));
        assertEquals(WorkVaultBrowseActivity.MODE_PROJECTS,WorkVaultBrowseActivity.normalizeMode(" PROJECTS "));
        assertEquals(WorkVaultBrowseActivity.MODE_FILES,WorkVaultBrowseActivity.normalizeMode("FILES"));
        assertEquals(WorkVaultBrowseActivity.MODE_PRICES,WorkVaultBrowseActivity.normalizeMode("prices"));
    }
}
