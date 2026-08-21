package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.*;

public class SystemAudioTranscriberTest {
    @Test public void protectedEnglishWinsPhoneticArabicWindow(){
        String out=SystemAudioTranscriber.mergeForTest(
                "تيست ريكوردنج ترانسكريبت انجليش عربي كونفرسيشن",0.78f,
                "test recording transcript English Arabic conversation",0.84f);
        assertEquals("test recording transcript English Arabic conversation",out);
    }

    @Test public void cleanArabicStaysArabic(){
        String out=SystemAudioTranscriber.mergeForTest(
                "أنا رايح الشغل دلوقتي",0.91f,
                "ana rayeh el shoghl delwa2ty",0.55f);
        assertEquals("أنا رايح الشغل دلوقتي",out);
    }

    @Test public void strongEnglishSpanIsNotTransliterated(){
        String out=SystemAudioTranscriber.mergeForTest(
                "عاوز اعمل ريكوردنج ترانسكريبت",0.73f,
                "recording transcript",0.86f);
        assertEquals("recording transcript",out);
        assertTrue(out.matches("[A-Za-z ]+"));
    }

    @Test public void whisperCppGgmlMagicIsLittleEndian(){
        assertTrue(LocalAsrModelStore.isGgmlHeader(new byte[]{'l','m','g','g'}));
        assertFalse(LocalAsrModelStore.isGgmlHeader(new byte[]{'g','g','m','l'}));
    }
}
