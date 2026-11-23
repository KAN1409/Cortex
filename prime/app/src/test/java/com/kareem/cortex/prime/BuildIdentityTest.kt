package com.kareem.cortex.prime

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildIdentityTest {
    @Test fun applicationIdIsSideBySidePackage() =
        assertEquals("com.kareem.cortex.prime", BuildConfig.APPLICATION_ID)
}
