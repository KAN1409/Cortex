package com.kareem.cortex;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CodeSwitchPipelineTest {
    @Test public void exactArabicEnglishRegressionIsPreservedVerbatim(){
        String arabic1="هنسجل جزء عربي";
        String englishPhrase=CodeSwitchCandidateSelector.choose(
                "و نضيف English part في التكس لإنجليزي",
                "and include English part in the text");
        String arabic2="عشان نجرب";
        String englishTail=CodeSwitchCandidateSelector.choose("Transcript","transcription");
        String out=CodeSwitchCandidateSelector.joinVerbatim(arabic1,englishPhrase,arabic2,englishTail);
        assertEquals("هنسجل جزء عربي and include English part in the text عشان نجرب transcription",out);
        assertFalse(out.contains("و نضيف"));
        assertFalse(out.contains("في التكس"));
        assertFalse(out.contains("لإنجليزي"));
        assertFalse(out.endsWith("Transcript"));
    }

    @Test public void genuineArabicChunkIsNeverReplacedByEnglishTranslation(){
        String out=CodeSwitchCandidateSelector.choose("هنسجل جزء عربي","we will record an Arabic part");
        assertEquals("هنسجل جزء عربي",out);
    }

    @Test public void vadRestoresNonZeroSpeechOnset(){
        int rate=16000;
        short[] audio=new short[(int)(4.2*rate)];
        // 2.2 seconds silence/background, then a clearly voiced synthetic region.
        for(int i=(int)(2.2*rate);i<(int)(3.35*rate);i++)audio[i]=(short)(5200*Math.sin(2*Math.PI*210*i/rate));
        ArrayList<long[]> ranges=WavSpeechChunker.detectRanges(audio,rate);
        assertFalse(ranges.isEmpty());
        long onset=ranges.get(0)[0];
        assertTrue("speech onset should not collapse to 00:00",onset>=1900);
        assertTrue("speech onset should stay close to the real ~2.2s start",onset<=2250);
    }
}
