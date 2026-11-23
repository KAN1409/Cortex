package com.kareem.cortex.prime.understand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OpenLoopExtractorTest {
    private val now = LocalDateTime.of(2026, 11, 23, 21, 0)
    private fun ex(s: String, src: SourceKind = SourceKind.VOICE) = OpenLoopExtractor.extract(s, now, src)

    @Test fun twoCommitmentsInOneMixedVoiceNote() {
        val text = "لازم أجدد رخصة العربية قبل يوم تلاتين، وكمان أكلم ماما بكرة عشان موضوع الجمعة"
        val loops = ex(text)
        assertEquals(2, loops.size)
        assertEquals(LocalDate.of(2026, 11, 30), loops[0].due!!.date)
        assertTrue(text.substring(loops[0].start, loops[0].end).contains("رخصة"))
        assertEquals(LocalDate.of(2026, 11, 24), loops[1].due!!.date)
        assertTrue(loops.all { it.confidence >= 0.8 })
    }

    @Test fun englishCommitment() {
        val l = ex("I need to call the bank tomorrow about the card").single()
        assertEquals(LocalDate.of(2026, 11, 24), l.due!!.date); assertTrue(l.confidence >= 0.8)
    }

    @Test fun screenshotDocumentCue() {
        val l = ex("TRAFFIC DEPARTMENT\nLicence No. 12345\nExpiry date: 30/11/2026", SourceKind.SCREENSHOT).single()
        assertEquals(LocalDate.of(2026, 11, 30), l.due!!.date); assertTrue(l.confidence >= 0.8)
    }

    @Test fun weakSignalIsUnconfirmedNotDropped() {
        val l = ex("maybe the dentist on Friday").single()
        assertTrue(l.confidence in 0.5..0.79)
    }

    @Test fun noCommitmentNoLoop() {
        assertTrue(ex("What a beautiful sunset today").isEmpty())
        assertTrue(ex("فكرة حلوة لرحلة الصيف").isEmpty())
    }

    @Test fun actionTextIsCleaned() =
        assertEquals("أجدد رخصة العربية", ex("لازم أجدد رخصة العربية قبل يوم تلاتين").single().action)
}
