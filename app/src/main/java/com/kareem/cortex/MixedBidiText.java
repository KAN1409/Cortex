package com.kareem.cortex;

import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;
import java.util.ArrayList;

/**
 * Single display-only Unicode BiDi boundary for Arabic/English code-switched text.
 * Stored text stays plain Unicode. Directional controls are inserted only when a TextView renders it.
 */
public final class MixedBidiText {
    private static final int NONE=0, AR=1, LATIN=2;
    private MixedBidiText(){}

    /**
     * Format each line using the document's reading direction. This matters for Arabic answers that
     * begin individual lines with Latin medical/product tokens (for example "8mg/2ml ... من INAD").
     * The whole answer remains RTL while consecutive Latin runs are isolated and stay readable LTR.
     */
    public static CharSequence format(String input){
        if(input==null||input.isEmpty())return "";
        String clean=stripControls(input).replace("\r\n","\n").replace('\r','\n');
        boolean documentRtl=isArabicDominant(clean);
        String[] lines=clean.split("\n",-1);
        StringBuilder out=new StringBuilder(clean.length()+32);
        for(int i=0;i<lines.length;i++){
            if(i>0)out.append('\n');
            out.append(formatLine(lines[i],documentRtl));
        }
        return out;
    }

    private static CharSequence formatLine(String line,boolean documentRtl){
        if(line==null||line.isEmpty())return "";
        boolean lineHasArabic=containsArabic(line);
        boolean baseRtl=documentRtl||lineHasArabic;
        String trimmed=line.trim();
        if(trimmed.isEmpty())return line;
        String[] tokens=trimmed.split("\\s+");
        ArrayList<Run> runs=new ArrayList<>();
        StringBuilder current=new StringBuilder();
        int currentDir=NONE;
        for(String token:tokens){
            int dir=tokenDirection(token);
            if(dir==NONE)dir=currentDir!=NONE?currentDir:(baseRtl?AR:LATIN);
            if(currentDir!=NONE&&dir!=currentDir){
                runs.add(new Run(currentDir,current.toString()));
                current.setLength(0);
            }
            if(current.length()>0)current.append(' ');
            current.append(token);
            currentDir=dir;
        }
        if(current.length()>0)runs.add(new Run(currentDir,current.toString()));

        BidiFormatter formatter=BidiFormatter.getInstance(baseRtl);
        StringBuilder result=new StringBuilder(line.length()+24);
        for(int i=0;i<runs.size();i++){
            if(i>0)result.append(' ');
            Run r=runs.get(i);
            result.append(formatter.unicodeWrap(r.text,r.dir==AR?TextDirectionHeuristicsCompat.RTL:TextDirectionHeuristicsCompat.LTR,true));
        }
        return result;
    }

    /** Backward-compatible name used by older UI code. */
    public static String forDisplay(String input){return format(input).toString();}

    /** Never persist display controls; also cleans legacy records that already contain them. */
    public static String stripControls(String s){
        if(s==null||s.isEmpty())return s==null?"":s;
        StringBuilder out=new StringBuilder(s.length());
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='\u200E'||c=='\u200F'||(c>='\u202A'&&c<='\u202E')||(c>='\u2066'&&c<='\u2069'))continue;
            out.append(c);
        }
        return out.toString();
    }

    public static boolean isArabicDominant(String text){
        if(text==null||text.isEmpty())return false;
        int ar=0,latin=0;for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);Character.UnicodeScript sc=Character.UnicodeScript.of(cp);if(sc==Character.UnicodeScript.ARABIC)ar++;else if(sc==Character.UnicodeScript.LATIN)latin++;}
        if(ar==latin&&ar>0)return firstStrongArabic(text);
        return ar>0&&ar>latin;
    }

    private static boolean containsArabic(String text){
        if(text==null||text.isEmpty())return false;
        for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);if(Character.UnicodeScript.of(cp)==Character.UnicodeScript.ARABIC)return true;}
        return false;
    }

    private static int tokenDirection(String token){
        int ar=0,latin=0;
        for(int i=0;i<token.length();){int cp=token.codePointAt(i);i+=Character.charCount(cp);Character.UnicodeScript sc=Character.UnicodeScript.of(cp);if(sc==Character.UnicodeScript.ARABIC)ar++;else if(sc==Character.UnicodeScript.LATIN)latin++;}
        if(ar==0&&latin==0)return NONE;
        if(ar==latin)return firstStrongArabic(token)?AR:LATIN;
        return ar>latin?AR:LATIN;
    }

    private static boolean firstStrongArabic(String s){
        for(int i=0;i<s.length();){int cp=s.codePointAt(i);i+=Character.charCount(cp);Character.UnicodeScript sc=Character.UnicodeScript.of(cp);if(sc==Character.UnicodeScript.ARABIC)return true;if(sc==Character.UnicodeScript.LATIN)return false;}return false;
    }

    private static final class Run{final int dir;final String text;Run(int d,String t){dir=d;text=t;}}
}
