package com.kareem.cortex;

import java.util.*;
import java.util.regex.*;

/**
 * Chooses between multilingual-auto and English rescue decodes without translating
 * or transliterating the raw ASR text. The only permitted rewrite is replacing an
 * already-Latin token with a better Latin token heard by the English re-decode.
 */
public final class CodeSwitchCandidateSelector {
    private CodeSwitchCandidateSelector(){}

    private static final Set<String> EN_HINTS=new HashSet<>(Arrays.asList(
            "a","an","and","are","as","at","audio","backup","because","before","but","call","chat","chatgpt",
            "code","conversation","cortex","data","debug","do","document","english","file","for","from","have",
            "hello","i","image","in","include","is","it","later","memory","message","model","need","note","of",
            "on","openai","part","please","prompt","record","recording","result","search","send","screenshot",
            "switch","test","text","the","this","to","transcribe","transcription","transcript","voice","want",
            "we","with","you"
    ));

    private static final Pattern LATIN_WORD=Pattern.compile("[A-Za-z][A-Za-z'’-]*");
    private static final Pattern ARABICIZED_EN=Pattern.compile(
            "(?iu)(التكس|تيكست|تكست|ترانسك(?:ر|را|رب|ريب|ريبت|ريبشن)|انجلش|إنجلش|انجليش|إنجليش|انجليزي|إنجليزي|تيست|تست|ريكورد|ريكوردنج|موديل|برومبت)"
    );

    public static boolean shouldEnglishRetry(String autoText){
        String a=norm(autoText);
        if(a.isEmpty())return false;
        if(latinRatio(a)>=0.035)return true;
        return ARABICIZED_EN.matcher(a).find();
    }

    public static String choose(String autoText,String englishText){
        String a=norm(autoText),e=norm(englishText);
        if(a.isEmpty())return e;
        if(e.isEmpty())return a;

        double aArabic=arabicRatio(a),aLatin=latinRatio(a),eLatin=latinRatio(e);
        int aHints=hintHits(a),eHints=hintHits(e);
        boolean suspicious=ARABICIZED_EN.matcher(a).find();

        // A chunk that auto-decoded into mixed Arabic/Latin but contains an Arabicized
        // English token is a strong code-switch failure. A coherent English rescue with
        // substantially more lexical evidence wins the whole acoustic chunk.
        if(aArabic>0.10 && eLatin>=0.78 && suspicious && eHints>=3 && eHints>=aHints+2){
            return e;
        }

        // If auto produced only Latin, prefer the more specific English lexical form.
        if(aArabic<0.04 && aLatin>=0.65 && eLatin>=0.75){
            if(eHints>aHints)return e;
            if(eHints==aHints && lexicalSpecificity(e)>lexicalSpecificity(a)+1)return e;
            return rescueLatinTokens(a,e);
        }

        // In a genuinely mixed Arabic+English chunk keep the Arabic untouched and only
        // allow an English re-decode to correct existing Latin-script tokens.
        if(aArabic>=0.10 && aLatin>0.0){
            return rescueLatinTokens(a,e);
        }

        // Pure Arabic stays Arabic. We do not accept a semantic English translation.
        return a;
    }

    static String rescueLatinTokens(String autoText,String englishText){
        String a=norm(autoText),e=norm(englishText);
        ArrayList<String> rescue=new ArrayList<>();
        Matcher em=LATIN_WORD.matcher(e);
        while(em.find()){
            String w=em.group();
            String low=w.toLowerCase(Locale.US);
            if(EN_HINTS.contains(low)||w.length()>=7)rescue.add(w);
        }
        if(rescue.isEmpty())return a;

        Matcher am=LATIN_WORD.matcher(a);
        StringBuffer out=new StringBuffer();
        while(am.find()){
            String current=am.group();
            String best=current;double bestScore=0.0;
            for(String cand:rescue){
                double sim=similarity(current.toLowerCase(Locale.US),cand.toLowerCase(Locale.US));
                if(sim>bestScore){bestScore=sim;best=cand;}
            }
            if(bestScore>=0.68 && !best.equalsIgnoreCase(current)){
                am.appendReplacement(out,Matcher.quoteReplacement(best));
            }else am.appendReplacement(out,Matcher.quoteReplacement(current));
        }
        am.appendTail(out);
        return norm(out.toString());
    }

    public static String joinVerbatim(String...parts){
        StringBuilder b=new StringBuilder();
        if(parts!=null)for(String p:parts){String x=norm(p);if(x.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(x);}
        return norm(b.toString());
    }

    private static int hintHits(String s){
        int n=0;Matcher m=LATIN_WORD.matcher(s);
        while(m.find())if(EN_HINTS.contains(m.group().toLowerCase(Locale.US)))n++;
        return n;
    }
    private static int lexicalSpecificity(String s){
        int score=0;Matcher m=LATIN_WORD.matcher(s);
        while(m.find()){String w=m.group().toLowerCase(Locale.US);if(EN_HINTS.contains(w))score+=2;if(w.length()>=8)score++;}
        return score;
    }
    private static double latinRatio(String s){int latin=0,letters=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetter(c)){letters++;if((c>='A'&&c<='Z')||(c>='a'&&c<='z'))latin++;}}return letters==0?0:(double)latin/letters;}
    private static double arabicRatio(String s){int ar=0,letters=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetter(c)){letters++;if((c>=0x0600&&c<=0x06FF)||(c>=0x0750&&c<=0x077F)||(c>=0x08A0&&c<=0x08FF))ar++;}}return letters==0?0:(double)ar/letters;}

    private static double similarity(String a,String b){
        if(a.equals(b))return 1.0;if(a.isEmpty()||b.isEmpty())return 0.0;
        int[] prev=new int[b.length()+1],cur=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;
        for(int i=1;i<=a.length();i++){cur[0]=i;for(int j=1;j<=b.length();j++){int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+cost);}int[] t=prev;prev=cur;cur=t;}
        int d=prev[b.length()];return 1.0-(double)d/Math.max(a.length(),b.length());
    }
    private static String norm(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
}
