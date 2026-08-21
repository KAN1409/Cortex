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

    @Test public void ibrahimWhisperSmallQ8HeaderAndSizeAreAccepted(){
        WhisperGgmlModel.Profile p=WhisperGgmlModel.inspect(header(768,12,12,768,12,12),264_464_624L);
        assertNotNull(p);assertEquals("small_q8_0",p.id);
    }

    @Test public void existingWhisperMediumQ8RemainsAcceptedAfterUpdate(){
        WhisperGgmlModel.Profile p=WhisperGgmlModel.inspect(header(1024,16,24,1024,16,24),823_000_000L);
        assertNotNull(p);assertEquals("medium_q8_0",p.id);
    }

    @Test public void truncatedSmallModelIsRejectedEvenWithValidHeader(){
        assertNull(WhisperGgmlModel.inspect(header(768,12,12,768,12,12),120_000_000L));
    }

    private static byte[] header(int as,int ah,int al,int ts,int th,int tl){
        int[] values={WhisperGgmlModel.GGML_MAGIC,51865,1500,as,ah,al,448,ts,th,tl,80,WhisperGgmlModel.Q8_0_FILE_TYPE};
        byte[] out=new byte[values.length*4];for(int i=0;i<values.length;i++)putLe32(out,i*4,values[i]);return out;
    }
    private static void putLe32(byte[] out,int at,int v){out[at]=(byte)v;out[at+1]=(byte)(v>>>8);out[at+2]=(byte)(v>>>16);out[at+3]=(byte)(v>>>24);}
}
