package com.kareem.cortex.prime.ui.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaSheetStateTest {
    @Test fun blankIdeaCannotBeSaved() {
        assertFalse(ideaSaveEnabled("   "))
        assertFalse(ideaSaveEnabled("\n\t"))
        assertTrue(ideaSaveEnabled("call mum"))
        assertTrue(ideaSaveEnabled("لازم أكلم ماما"))
    }
}
