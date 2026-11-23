package com.kareem.cortex.prime.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokensTest {
    @Test fun textMeetsContrastOnBlack() {
        assertTrue(contrastRatio(CortexColors.TextPrimary, CortexColors.Bg) >= 4.5)
        assertTrue(contrastRatio(CortexColors.TextSecondary, CortexColors.Bg) >= 4.5)
        assertTrue(contrastRatio(CortexColors.Label, CortexColors.Bg) >= 4.5)
    }

    @Test fun contrastOfWhiteOnBlackIs21() =
        assertEquals(21.0, contrastRatio(0xFFFFFFFF, 0xFF000000), 0.01)
}
