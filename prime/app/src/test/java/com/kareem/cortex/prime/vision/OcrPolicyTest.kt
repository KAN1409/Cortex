package com.kareem.cortex.prime.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPolicyTest {
    @Test fun arabicPassOnlyWhenNeeded() {
        assertTrue(OcrPolicy.shouldTryArabic("رخصة"))           // Arabic letters seen by the Latin pass
        assertTrue(OcrPolicy.shouldTryArabic("ok"))             // too little text
        assertFalse(OcrPolicy.shouldTryArabic("Your Amazon order has shipped and arrives Thursday"))
    }

    @Test fun neverUpscalesAndShrinksLargeImages() {
        assertEquals(1f, OcrPolicy.scaleFor(1080, 1920), 0.0001f)
        assertEquals(0.5f, OcrPolicy.scaleFor(2160, 4000), 0.0001f)
        assertEquals(1f, OcrPolicy.scaleFor(300, 300), 0.0001f)
    }

    @Test fun mergeDropsDuplicateLines() =
        assertEquals("Order 123\nتاريخ الانتهاء ٣٠/١١", OcrPolicy.merge("Order 123", "Order 123\nتاريخ الانتهاء ٣٠/١١"))

    @Test fun screenshotDetection() {
        assertTrue(isScreenshot("Pictures/Screenshots/", "Screenshot_20261123_220635.png"))
        assertTrue(isScreenshot("DCIM/Screenshots/", "x.jpg"))
        assertFalse(isScreenshot("DCIM/Camera/", "IMG_1.jpg"))
    }
}
