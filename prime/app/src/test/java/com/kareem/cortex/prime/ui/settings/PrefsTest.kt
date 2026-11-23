package com.kareem.cortex.prime.ui.settings

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun prefsPersistName() {
        assertNull(Prefs(ctx).displayName)
        Prefs(ctx).displayName = "Kareem"
        assertEquals("Kareem", Prefs(ctx).displayName)
    }

    @Test fun reduceMotionPersists() {
        Prefs(ctx).reduceMotion = true
        assertTrue(Prefs(ctx).reduceMotion)
    }
}
