package com.kareem.cortex.prime.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptCleanerTest {
    @Test fun removesArtefactsAndFillers() {
        assertEquals("لازم أكلم ماما بكرة", TranscriptCleaner.clean("[Music] يعني لازم   أكلم ماما بكرة (موسيقى)"))
        assertEquals("I need to renew the licence", TranscriptCleaner.clean("um I need to uh renew the licence [BLANK_AUDIO]"))
    }
    @Test fun mixedLanguageKept() =
        assertEquals("بكرة meeting الساعة ٣", TranscriptCleaner.clean("  بكرة meeting  الساعة ٣ "))
    @Test fun onlyArtefactsIsEmpty() = assertEquals("", TranscriptCleaner.clean("[Music] (تصفيق) [silence]"))
    @Test fun keepsBracketsWithRealWords() = assertEquals("(call the bank)", TranscriptCleaner.clean("(call the bank)"))
}
