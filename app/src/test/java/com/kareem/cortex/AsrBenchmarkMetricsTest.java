package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.*;

public class AsrBenchmarkMetricsTest {
    @Test public void perfectMixedTranscriptScoresZeroEdits() {
        AsrBenchmarkMetrics.Score s = AsrBenchmarkMetrics.score(
                "أنا عاوز افتح ChatGPT بكرة الساعة 10",
                "أنا عاوز افتح ChatGPT بكرة الساعة 10");
        assertEquals(0.0, s.wer, 0.00001);
        assertEquals(0.0, s.cer, 0.00001);
        assertEquals(0.0, s.arabicWer, 0.00001);
        assertEquals(0.0, s.latinWer, 0.00001);
        assertEquals(1.0, s.numberRecall, 0.00001);
        assertEquals(s.referenceScriptSwitches, s.hypothesisScriptSwitches);
    }

    @Test public void missingNumberIsVisibleEvenWhenMostWordsMatch() {
        AsrBenchmarkMetrics.Score s = AsrBenchmarkMetrics.score(
                "كلم أحمد الساعة 10 يوم 12",
                "كلم أحمد الساعة يوم");
        assertEquals(2, s.referenceNumbers);
        assertEquals(0, s.matchedNumbers);
        assertEquals(0.0, s.numberRecall, 0.00001);
        assertTrue(s.wer > 0.0);
    }

    @Test public void englishCorruptionIsSeparatedFromArabicAccuracy() {
        AsrBenchmarkMetrics.Score s = AsrBenchmarkMetrics.score(
                "افتح Cortex وبعدها send message",
                "افتح Cortex وبعدها sand massage");
        assertEquals(0.0, s.arabicWer, 0.00001);
        assertTrue(s.latinWer > 0.0);
    }

    @Test public void adjacentDuplicationIsCounted() {
        AsrBenchmarkMetrics.Score s = AsrBenchmarkMetrics.score(
                "أنا عاوز أجرب Cortex",
                "أنا أنا عاوز أجرب Cortex Cortex");
        assertEquals(2, s.duplicateAdjacentWords);
        assertTrue(s.wer > 0.0);
    }

    @Test public void codeSwitchBoundaryLossIsVisible() {
        AsrBenchmarkMetrics.Score s = AsrBenchmarkMetrics.score(
                "افتح settings وبعدها ارجع Cortex وبعدها ابعت message",
                "افتح settings وبعدها ارجع وبعدها ابعت message");
        assertTrue(s.referenceScriptSwitches > s.hypothesisScriptSwitches);
        assertTrue(s.wer > 0.0);
    }
}
