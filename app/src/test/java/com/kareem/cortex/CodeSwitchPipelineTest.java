package com.kareem.cortex;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CodeSwitchPipelineTest {
    @Test public void recordingsUpToTwentyEightSecondsUseOnePromptedPass(){
        assertTrue(CodeSwitchCandidateSelector.useSinglePromptedPass(20_840L));
        assertTrue(CodeSwitchCandidateSelector.useSinglePromptedPass(28_000L));
        assertFalse(CodeSwitchCandidateSelector.useSinglePromptedPass(28_001L));
    }

    @Test public void shortNoteNeverRunsTailRetry(){
        assertFalse(CodeSwitchCandidateSelector.shouldTailRetry(20_840L,18_300L,13_000L));
        assertFalse(CodeSwitchCandidateSelector.shouldTailRetry(28_000L,27_000L,0L));
    }

    @Test public void longNoteRetriesOnlyWhenPrimaryReallyMissedTail(){
        assertFalse(CodeSwitchCandidateSelector.shouldTailRetry(40_000L,25_000L,24_250L));
        assertTrue(CodeSwitchCandidateSelector.shouldTailRetry(40_000L,25_000L,23_900L));
    }

    @Test public void spanLocalEnglishRescuePreservesArabicReturn(){
        String primary="هنسجل جزء عربي and then code English part عشان نجرب الـtranscription";
        String rescue="and include English part in the text";
        String out=CodeSwitchCandidateSelector.mergeEnglishSpan(primary,rescue);
        assertEquals("هنسجل جزء عربي and include English part in the text عشان نجرب الـtranscription",out);
        assertTrue(out.contains("هنسجل جزء عربي"));
        assertTrue(out.contains("and include English part in the text"));
        assertTrue(out.contains("عشان نجرب الـtranscription"));
        assertFalse(out.contains("and then code"));
    }

    @Test public void tailRetryRestoresClosingArabicEnglishSwitchWithoutDuplication(){
        String primary="هنسجل جزء عربي and include English part";
        String tail="English part in the text عشان نجرب الـtranscription";
        String out=CodeSwitchCandidateSelector.mergeTail(primary,tail);
        assertEquals("هنسجل جزء عربي and include English part in the text عشان نجرب الـtranscription",out);
        assertFalse(out.contains("English part English part"));
    }

    @Test public void spanRescueCannotAppendHallucinatedEnglishSuffix(){
        String primary="ممكن silence for a few seconds عشان أنا عاوز أجرب";
        String rescue="silence for a few seconds so that";
        String out=CodeSwitchCandidateSelector.mergeEnglishSpan(primary,rescue);
        assertEquals("ممكن silence for a few seconds عشان أنا عاوز أجرب",out);
        assertFalse(out.contains("so that"));
    }

    @Test public void fuzzyTailOverlapRejectsDuplicatedWorseRetry(){
        String primary="عاوزين نجرب الـ RAM load و الـ CPU load";
        String retry="بل ال ram load و ال spew load";
        assertEquals("",CodeSwitchCandidateSelector.novelTail(primary,retry));
        assertEquals(primary,CodeSwitchCandidateSelector.mergeTail(primary,retry));
    }

    @Test public void dialectNormalizationIsNarrowAndScriptPreserving(){
        assertEquals("أنا عاوز أجرب وإحنا عاوزين نجرب English",
                CodeSwitchCandidateSelector.normalizeEgyptianOutput("أنا أعوز أجرب وإحنا أعوزين نجرب English"));
    }

    @Test public void genuineArabicChunkIsNeverReplacedByEnglishTranslation(){
        String out=CodeSwitchCandidateSelector.choose("هنسجل جزء عربي","we will record an Arabic part");
        assertEquals("هنسجل جزء عربي",out);
    }

    @Test public void shortVoiceNoteMergesThreeSpeechIslandsIntoOneContextRange(){
        int rate=16000;
        short[] audio=new short[(int)(9.46*rate)];
        tone(audio,rate,2.20,3.50,5200,210);
        tone(audio,rate,4.15,6.20,5000,230);
        tone(audio,rate,7.25,8.70,3600,190); // lower-energy trailing Arabic phrase
        ArrayList<long[]> ranges=WavSpeechChunker.detectRanges(audio,rate);
        assertEquals("nearby speech islands should be merged before ASR",1,ranges.size());
        long onset=ranges.get(0)[0],end=ranges.get(0)[1];
        assertTrue("speech onset must not collapse to 00:00",onset>=1500);
        assertTrue("500ms pre-roll should keep onset close to ~1.7s",onset<=1900);
        assertTrue("lower-energy final island must survive VAD",end>=9000);
    }

    @Test public void englishRescueFractionsIgnoreMixedArabicArticleToken(){
        String text="هنسجل جزء عربي and then code English part عشان نجرب الـtranscription";
        double[] f=CodeSwitchCandidateSelector.englishSpanFractions(text);
        assertTrue(f[0]>0.15);
        assertTrue(f[1]<0.78); // must target middle English span, not الـtranscription
        assertTrue(f[1]>f[0]);
    }

    private static void tone(short[] audio,int rate,double from,double to,int amp,int hz){
        int a=(int)(from*rate),z=Math.min(audio.length,(int)(to*rate));
        for(int i=a;i<z;i++)audio[i]=(short)(amp*Math.sin(2*Math.PI*hz*i/rate));
    }
}
