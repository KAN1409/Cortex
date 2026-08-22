package com.kareem.cortex;

import java.util.*;
import java.util.regex.*;

/**
 * Text selection for local code-switch ASR. Arabic text is immutable. English
 * rescue may replace only an already-Latin span aligned to the same acoustic
 * interval; it must never replace Arabic before or after that span.
 */
public final class CodeSwitchCandidateSelector {
    private CodeSwitchCandidateSelector(){}

    public static final long SHORT_NOTE_PRIMARY_ONLY_MS=28_000L;
    private static final long TAIL_MISSING_MS=900L;

    private static final Set<String> EN_HINTS=new HashSet<>(Arrays.asList(
            "a","an","and","are","as","at","audio","backup","because","before","but","call","chat","chatgpt",
            "code","conversation","cortex","cpu","data","debug","do","document","english","few","file","for","from","have",
            "hello","i","image","in","include","is","it","later","memory","message","model","need","note","of",
            "load","new","on","openai","part","please","prompt","ram","record","recording","result","search","seconds","send","silence","screenshot",
            "switch","test","text","the","this","to","transcribe","transcription","transcript","voice","want",
            "we","with","you"
    ));

    private static final Pattern LATIN_WORD=Pattern.compile("[A-Za-z][A-Za-z'’-]*");
    private static final Pattern ARABICIZED_EN=Pattern.compile(
            "(?iu)(التكس|تيكست|تكست|ترانسك(?:ر|را|رب|ريب|ريبت|ريبشن)|انجلش|إنجلش|انجليش|إنجليش|انجليزي|إنجليزي|تيست|تست|ريكورد|ريكوردنج|موديل|برومبت)"
    );

    private static final class Span {
        int start,end,words;
        Span(int s,int e,int w){start=s;end=e;words=w;}
    }

    public static boolean shouldEnglishRetry(String autoText){
        String a=norm(autoText);
        if(a.isEmpty())return false;
        if(findBestLatinSpan(a)!=null)return true;
        return ARABICIZED_EN.matcher(a).find();
    }

    /** Short notes fit in one Whisper context and must never be fragmented or retried. */
    public static boolean useSinglePromptedPass(long recordingDurationMs){
        return recordingDurationMs>0L&&recordingDurationMs<=SHORT_NOTE_PRIMARY_ONLY_MS;
    }

    /** Retry only when a long-note primary decode actually stopped before the audio tail. */
    public static boolean shouldTailRetry(long recordingDurationMs,long chunkDurationMs,long lastSegmentEndMs){
        if(useSinglePromptedPass(recordingDurationMs)||chunkDurationMs<=2_500L)return false;
        return lastSegmentEndMs<=0L||chunkDurationMs-lastSegmentEndMs>TAIL_MISSING_MS;
    }

    /** Relative text position of the best Latin span; used to localize acoustic rescue. */
    public static double[] englishSpanFractions(String text){
        String a=norm(text);Span s=findBestLatinSpan(a);
        if(s==null||a.isEmpty())return new double[]{-1,-1};
        return new double[]{(double)s.start/a.length(),(double)s.end/a.length()};
    }

    /**
     * Replace only the best existing Latin span. Arabic around it is byte-for-byte
     * preserved apart from whitespace normalization.
     */
    public static String mergeEnglishSpan(String primaryText,String englishText){
        String a=norm(primaryText),e=norm(englishText);
        if(a.isEmpty())return e;if(e.isEmpty())return a;
        Span span=findBestLatinSpan(a);
        if(span==null)return a;

        String current=norm(a.substring(span.start,span.end));
        if(arabicRatio(a)<0.04 && latinRatio(a)>=0.65)return choose(a,e);
        if(latinRatio(e)<0.72)return rescueLatinTokens(a,e);

        String candidate=extractRelevantEnglishWindow(current,e);
        if(candidate.isEmpty())return rescueLatinTokens(a,e);
        int currentHints=hintHits(current),candidateHints=hintHits(candidate);
        if(candidateHints<Math.max(2,currentHints))return rescueLatinTokens(a,e);
        if(wordCount(candidate)<Math.max(2,wordCount(current)-1))return rescueLatinTokens(a,e);

        String merged=a.substring(0,span.start)+candidate+a.substring(span.end);
        return norm(merged);
    }

    public static String choose(String autoText,String englishText){
        String a=norm(autoText),e=norm(englishText);
        if(a.isEmpty())return e;if(e.isEmpty())return a;
        double aArabic=arabicRatio(a),aLatin=latinRatio(a),eLatin=latinRatio(e);
        int aHints=hintHits(a),eHints=hintHits(e);

        if(aArabic<0.04&&aLatin>=0.65&&eLatin>=0.75){
            if(eHints>=aHints+1&&wordCount(e)>=Math.max(2,wordCount(a)-1))return e;
            if(eHints==aHints&&lexicalSpecificity(e)>lexicalSpecificity(a)+1)return e;
            return rescueLatinTokens(a,e);
        }
        if(aArabic>=0.10&&aLatin>0.0)return mergeEnglishSpan(a,e);
        return a;
    }

    static String rescueLatinTokens(String autoText,String englishText){
        String a=norm(autoText),e=norm(englishText);
        ArrayList<String> rescue=new ArrayList<>();
        Matcher em=LATIN_WORD.matcher(e);
        while(em.find()){
            String w=em.group();String low=w.toLowerCase(Locale.US);
            if(EN_HINTS.contains(low)||w.length()>=7)rescue.add(w);
        }
        if(rescue.isEmpty())return a;
        Matcher am=LATIN_WORD.matcher(a);StringBuffer out=new StringBuffer();
        while(am.find()){
            String current=am.group();String best=current;double bestScore=0.0;
            for(String cand:rescue){double sim=similarity(current.toLowerCase(Locale.US),cand.toLowerCase(Locale.US));if(sim>bestScore){bestScore=sim;best=cand;}}
            if(bestScore>=0.68&&!best.equalsIgnoreCase(current))am.appendReplacement(out,Matcher.quoteReplacement(best));
            else am.appendReplacement(out,Matcher.quoteReplacement(current));
        }
        am.appendTail(out);return norm(out.toString());
    }

    /** Append only words not already covered by the end of the primary decode. */
    public static String mergeTail(String primary,String tail){
        String a=norm(primary),b=norm(tail);if(a.isEmpty())return b;if(b.isEmpty())return a;
        String novel=novelTail(a,b);return novel.isEmpty()?a:norm(a+" "+novel);
    }

    public static String novelTail(String primary,String tail){
        String a=norm(primary),b=norm(tail);if(b.isEmpty())return "";if(a.isEmpty())return b;
        String[] aa=a.split(" "),bb=b.split(" ");int max=Math.min(Math.min(aa.length,bb.length),12),overlap=0;
        for(int n=max;n>=1;n--){boolean same=true;for(int i=0;i<n;i++)if(!cleanToken(aa[aa.length-n+i]).equalsIgnoreCase(cleanToken(bb[i]))){same=false;break;}if(same){overlap=n;break;}}
        if(overlap>0){
            if(overlap>=bb.length)return "";
            StringBuilder out=new StringBuilder();for(int i=overlap;i<bb.length;i++){if(out.length()>0)out.append(' ');out.append(bb[i]);}return norm(out.toString());
        }

        // A retry can disagree phonetically (CPU -> spew) while still covering the
        // same acoustic tail. Fuzzy ordered overlap makes that a replacement/no-op,
        // never a blind append that duplicates the end of the note.
        int aStart=Math.max(0,aa.length-16),aLen=aa.length-aStart,bLen=Math.min(bb.length,16);
        int[][] lcs=new int[aLen+1][bLen+1];
        for(int i=1;i<=aLen;i++)for(int j=1;j<=bLen;j++){
            if(tokenEquivalent(aa[aStart+i-1],bb[j-1]))lcs[i][j]=lcs[i-1][j-1]+1;
            else lcs[i][j]=Math.max(lcs[i-1][j],lcs[i][j-1]);
        }
        int matched=lcs[aLen][bLen];
        if(matched<2||(double)matched/Math.max(1,Math.min(aLen,bLen))<0.50)return "";

        int i=aLen,j=bLen,lastMatchedTail=-1;
        while(i>0&&j>0){
            if(tokenEquivalent(aa[aStart+i-1],bb[j-1])){lastMatchedTail=Math.max(lastMatchedTail,j-1);i--;j--;}
            else if(lcs[i-1][j]>=lcs[i][j-1])i--;else j--;
        }
        if(lastMatchedTail<0||lastMatchedTail+1>=bb.length)return "";
        StringBuilder out=new StringBuilder();for(int k=lastMatchedTail+1;k<bb.length;k++){if(out.length()>0)out.append(' ');out.append(bb[k]);}
        return norm(out.toString());
    }

    /** Narrow Egyptian spelling/grammar repair; never translates or rewrites Latin tokens. */
    public static String normalizeEgyptianOutput(String text){
        String out=norm(text);
        out=out.replaceAll("(?<![\\p{L}\\p{M}])أعوزين(?![\\p{L}\\p{M}])","عاوزين");
        out=out.replaceAll("(?<![\\p{L}\\p{M}])أعوز(?![\\p{L}\\p{M}])","عاوز");
        out=out.replaceAll("(?<![\\p{L}\\p{M}])أبراهيم(?![\\p{L}\\p{M}])","إبراهيم");
        out=out.replaceAll("(?<![\\p{L}\\p{M}])اللي هو وجود فيها(?![\\p{L}\\p{M}])","اللي هو موجود فيها");
        return norm(out);
    }

    public static String joinVerbatim(String...parts){
        StringBuilder b=new StringBuilder();if(parts!=null)for(String p:parts){String x=norm(p);if(x.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(x);}return norm(b.toString());
    }

    private static Span findBestLatinSpan(String s){
        Matcher m=LATIN_WORD.matcher(s);ArrayList<int[]> words=new ArrayList<>();while(m.find())words.add(new int[]{m.start(),m.end()});
        if(words.isEmpty())return null;Span best=null;int gs=words.get(0)[0],ge=words.get(0)[1],count=1;
        for(int i=1;i<=words.size();i++){
            boolean join=false;
            if(i<words.size()){
                int ns=words.get(i)[0];String between=s.substring(ge,ns);
                join=!containsLetter(between);
            }
            if(join){ge=words.get(i)[1];count++;continue;}
            boolean leftAttached=gs>0&&isArabicLetter(s.charAt(gs-1));
            boolean rightAttached=ge<s.length()&&isArabicLetter(s.charAt(ge));
            if(!leftAttached&&!rightAttached){Span cur=new Span(gs,ge,count);if(best==null||cur.words>best.words||(cur.words==best.words&&cur.end-cur.start>best.end-best.start))best=cur;}
            if(i<words.size()){gs=words.get(i)[0];ge=words.get(i)[1];count=1;}
        }
        return best;
    }

    private static String extractRelevantEnglishWindow(String current,String rescue){
        String e=norm(rescue);ArrayList<String> ew=words(e),cw=words(current);if(ew.isEmpty())return "";
        // Allow at most two recovered domain words (for example "the text"), but
        // trim unanchored growth such as a hallucinated trailing "so that".
        int base=Math.max(2,cw.size()),wanted=Math.min(10,base+2);
        String first=cw.isEmpty()?"":cw.get(0).toLowerCase(Locale.US);int bestStart=0,bestScore=Integer.MIN_VALUE;
        for(int start=0;start<ew.size();start++){
            if(!first.isEmpty()&&!ew.get(start).equalsIgnoreCase(first))continue;
            int end=Math.min(ew.size(),start+wanted);List<String> window=ew.subList(start,end);int score=0;
            HashSet<String> target=new HashSet<>();for(String w:cw)target.add(w.toLowerCase(Locale.US));
            for(String w:window){String low=w.toLowerCase(Locale.US);if(target.contains(low))score+=3;if(EN_HINTS.contains(low))score++;if("include".equals(low)||"english".equals(low)||"text".equals(low))score+=2;}
            score-=Math.abs(window.size()-wanted);
            if(score>bestScore){bestScore=score;bestStart=start;}
        }
        int end=Math.min(ew.size(),bestStart+wanted);ArrayList<String> candidate=new ArrayList<>(ew.subList(bestStart,end));
        while(candidate.size()>base&&!EN_HINTS.contains(candidate.get(candidate.size()-1).toLowerCase(Locale.US)))candidate.remove(candidate.size()-1);
        return String.join(" ",candidate);
    }

    private static ArrayList<String> words(String s){ArrayList<String> out=new ArrayList<>();Matcher m=LATIN_WORD.matcher(s);while(m.find())out.add(m.group());return out;}
    private static int wordCount(String s){return words(s).size();}
    private static int hintHits(String s){int n=0;Matcher m=LATIN_WORD.matcher(s);while(m.find())if(EN_HINTS.contains(m.group().toLowerCase(Locale.US)))n++;return n;}
    private static int lexicalSpecificity(String s){int score=0;Matcher m=LATIN_WORD.matcher(s);while(m.find()){String w=m.group().toLowerCase(Locale.US);if(EN_HINTS.contains(w))score+=2;if(w.length()>=8)score++;}return score;}
    private static double latinRatio(String s){int latin=0,letters=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetter(c)){letters++;if((c>='A'&&c<='Z')||(c>='a'&&c<='z'))latin++;}}return letters==0?0:(double)latin/letters;}
    private static double arabicRatio(String s){int ar=0,letters=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetter(c)){letters++;if(isArabicLetter(c))ar++;}}return letters==0?0:(double)ar/letters;}
    private static boolean isArabicLetter(char c){return Character.isLetter(c)&&((c>=0x0600&&c<=0x06FF)||(c>=0x0750&&c<=0x077F)||(c>=0x08A0&&c<=0x08FF));}
    private static boolean containsLetter(String s){for(int i=0;i<s.length();i++)if(Character.isLetter(s.charAt(i)))return true;return false;}
    private static String cleanToken(String s){return s==null?"":s.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$","");}
    private static boolean tokenEquivalent(String a,String b){String x=cleanToken(a).toLowerCase(Locale.ROOT),y=cleanToken(b).toLowerCase(Locale.ROOT);return !x.isEmpty()&&!y.isEmpty()&&(x.equals(y)||similarity(x,y)>=0.78);}
    private static double similarity(String a,String b){if(a.equals(b))return 1.0;if(a.isEmpty()||b.isEmpty())return 0.0;int[] prev=new int[b.length()+1],cur=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;for(int i=1;i<=a.length();i++){cur[0]=i;for(int j=1;j<=b.length();j++){int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+cost);}int[] t=prev;prev=cur;cur=t;}int d=prev[b.length()];return 1.0-(double)d/Math.max(a.length(),b.length());}
    private static String norm(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
}
