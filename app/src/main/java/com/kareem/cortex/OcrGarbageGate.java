package com.kareem.cortex;

import java.util.*;

/**
 * Single source of truth for OCR evidence quality.
 *
 * Raw OCR is always allowed to remain available to diagnostics. Only accepted evidence should be
 * merged into Cortex memory/search. The gate deliberately prefers an occasional Arabic false
 * negative over poisoning durable memory with wrong-script hallucinations.
 */
public final class OcrGarbageGate {
    private static final String ALLOWED_PUNCT=",.;:!?()[]{}%$€£+-_/\\@#&'\"•→←=×…؛،؟";
    private static final HashSet<String> COMMON_ARABIC=new HashSet<>(Arrays.asList(
            "من","في","على","عن","مع","الى","هو","هي","هذا","هذه","تم","لا","نعم","كان","كانت",
            "انا","انت","احنا","اللي","ده","دي","مش","عشان","بعد","قبل","يوم","اليوم","بكره",
            "بكرة","لو","او","و","ما","كل","عندي","عندك","ممكن","عايز","عاوز","عايزة","عاوزه",
            "الاسم","التاريخ","رقم","محمد","احمد","عبد","الله","شركة","جنيه","مصر","القاهرة"));

    private OcrGarbageGate(){}

    public static final class Quality {
        public final double score,symbolNoise,digitShare,shortTokenShare;
        public final int letters,digits,tokens;
        public final String label,reason;
        Quality(double score,double symbolNoise,double digitShare,double shortTokenShare,int letters,int digits,int tokens,String label,String reason){
            this.score=clamp(score);this.symbolNoise=clamp(symbolNoise);this.digitShare=clamp(digitShare);this.shortTokenShare=clamp(shortTokenShare);
            this.letters=letters;this.digits=digits;this.tokens=tokens;this.label=n(label);this.reason=n(reason);
        }
    }

    public static final class ArabicDecision {
        public final boolean accepted;
        public final int confidencePct;
        public final double arabicShare,arabicWordShare;
        public final int commonWordHits;
        public final String reason;
        public final Quality quality;
        ArabicDecision(boolean accepted,int confidencePct,double arabicShare,double arabicWordShare,int commonWordHits,String reason,Quality quality){
            this.accepted=accepted;this.confidencePct=confidencePct;this.arabicShare=clamp(arabicShare);this.arabicWordShare=clamp(arabicWordShare);
            this.commonWordHits=Math.max(0,commonWordHits);this.reason=n(reason);this.quality=quality;
        }
        public String compactMetrics(){
            return "quality "+fmt(quality.score)+" • Arabic letters "+pct(arabicShare)+" • digits "+pct(quality.digitShare)+" • symbols "+pct(quality.symbolNoise);
        }
    }

    /** General OCR candidate quality used by multipass selection and the OCR lab. */
    public static Quality assessText(String text){
        String x=n(text);if(x.isEmpty())return new Quality(0,0,0,0,0,0,0,"empty","empty");
        int letters=0,digits=0,weird=0;for(int i=0;i<x.length();i++){char c=x.charAt(i);if(Character.isLetter(c))letters++;else if(Character.isDigit(c))digits++;else if(!Character.isWhitespace(c)&&ALLOWED_PUNCT.indexOf(c)<0)weird++;}
        String[] rawTokens=x.split("\\s+");int tokens=0,shortTokens=0;for(String raw:rawTokens){String t=raw.replaceAll("[^\\p{L}\\p{N}]","");if(t.isEmpty())continue;tokens++;if(t.length()<=2)shortTokens++;}
        int nChars=Math.max(1,x.length()),alnum=Math.max(1,letters+digits);double symbolNoise=weird/(double)nChars,digitShare=digits/(double)alnum,shortShare=shortTokens/(double)Math.max(1,tokens),density=(letters+digits)/(double)nChars;
        double score=0.16+density*0.86+Math.min(0.16,letters/700.0)-Math.min(0.42,symbolNoise*2.6)-Math.min(0.12,Math.max(0,shortShare-0.55)*0.45);
        int garbageLines=0;for(String line:x.split("\\r?\\n")){if(line.length()>5&&line.replaceAll("[\\p{L}\\p{N} ]","").length()>line.length()*0.35)garbageLines++;}score-=Math.min(0.25,garbageLines*0.025);score=clamp(score);
        String label,reason;if(score<0.38){label="gibberish-suspected";reason="low OCR structure quality";}else if(score<0.62){label="usable-uncertain";reason="usable but uncertain";}else{label="usable-candidate";reason="structurally usable candidate";}
        return new Quality(score,symbolNoise,digitShare,shortShare,letters,digits,tokens,label,reason);
    }

    /** Arabic-specific evidence decision. confidence may be supplied as 0..1 or Tesseract's 0..100. */
    public static ArabicDecision evaluateArabic(String text,double confidence,String latinEvidence){
        String x=n(text);Quality q=assessText(x);int conf=confidencePct(confidence);if(x.isEmpty())return decision(false,conf,0,0,0,"no Arabic OCR text",q);
        int arabic=0,latin=0,letters=0;for(int i=0;i<x.length();i++){char c=x.charAt(i);if(Character.isLetter(c)){letters++;if(isArabic(c))arabic++;else if(isLatin(c))latin++;}}
        double arabicShare=arabic/(double)Math.max(1,letters);
        int wordTokens=0,arabicWords=0,common=0;for(String raw:x.split("\\s+")){String t=raw.replaceAll("[^\\p{L}\\p{N}]","");if(t.isEmpty())continue;wordTokens++;int ar=0,alnum=0;for(int i=0;i<t.length();i++){char c=t.charAt(i);if(Character.isLetterOrDigit(c)){alnum++;if(isArabic(c))ar++;}}if(alnum>0&&ar/(double)alnum>=0.72&&ar>=2)arabicWords++;String norm=LocalSemanticEmbedder.norm(t);if(COMMON_ARABIC.contains(norm))common++;}
        double arabicWordShare=arabicWords/(double)Math.max(1,wordTokens);int latinEvidenceLetters=countLatin(latinEvidence);

        if(arabic<2)return decision(false,conf,arabicShare,arabicWordShare,common,"no credible Arabic-script evidence",q);
        if(conf<42)return decision(false,conf,arabicShare,arabicWordShare,common,"low Tesseract confidence / wrong-script risk",q);
        if(arabicShare<0.35)return decision(false,conf,arabicShare,arabicWordShare,common,"Arabic script is too sparse for Arabic evidence",q);
        if(q.score<0.32||q.symbolNoise>0.28)return decision(false,conf,arabicShare,arabicWordShare,common,"OCR structure looks corrupted",q);
        if(conf<55&&q.digitShare>0.20)return decision(false,conf,arabicShare,arabicWordShare,common,"digit-heavy low-confidence Arabic hallucination risk",q);
        if(conf<52&&wordTokens>=4&&arabicWordShare<0.45)return decision(false,conf,arabicShare,arabicWordShare,common,"fragmented low-confidence Arabic text",q);
        if(x.length()>120&&conf<65&&common==0&&(q.digitShare>0.10||q.shortTokenShare>0.28))return decision(false,conf,arabicShare,arabicWordShare,common,"long Arabic output lacks language plausibility",q);
        if(latinEvidenceLetters>=40&&x.length()>80&&conf<70&&common==0&&(q.digitShare>0.08||q.shortTokenShare>0.22||q.score<0.65))return decision(false,conf,arabicShare,arabicWordShare,common,"strong Latin OCR conflicts with implausible Arabic output",q);
        return decision(true,conf,arabicShare,arabicWordShare,common,"accepted",q);
    }

    public static String qualityLabel(String text){return assessText(text).label;}
    public static double scoreCandidate(String text){return assessText(text).score;}
    public static String candidateReason(String text){return assessText(text).reason;}

    private static ArabicDecision decision(boolean ok,int conf,double share,double wordShare,int common,String reason,Quality q){return new ArabicDecision(ok,conf,share,wordShare,common,reason,q);}
    private static int confidencePct(double confidence){double c=confidence;if(c>=0&&c<=1)c*=100.0;return Math.max(0,Math.min(100,(int)Math.round(c)));}
    private static int countLatin(String s){int n=0;for(char c:n(s).toCharArray())if(isLatin(c))n++;return n;}
    private static boolean isArabic(char c){return (c>=0x0600&&c<=0x06ff)||(c>=0x0750&&c<=0x077f)||(c>=0x08a0&&c<=0x08ff)||(c>=0xfb50&&c<=0xfdff)||(c>=0xfe70&&c<=0xfeff);}
    private static boolean isLatin(char c){return (c>='A'&&c<='Z')||(c>='a'&&c<='z');}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static String pct(double x){return Math.round(clamp(x)*100)+"%";}
    private static String fmt(double x){return String.format(Locale.US,"%.2f",clamp(x));}
    private static String n(String s){return s==null?"":s.trim();}
}
