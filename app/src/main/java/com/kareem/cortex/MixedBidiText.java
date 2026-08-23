package com.kareem.cortex;

import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;

/**
 * Single display-only Unicode BiDi boundary for Arabic/English code-switched text.
 * Stored text stays plain Unicode. Directional controls are inserted only when a TextView renders it.
 */
public final class MixedBidiText {
    private MixedBidiText(){}

    /** Format each line independently so one English technical line cannot flip an Arabic paragraph. */
    public static CharSequence format(String input){
        if(input==null||input.isEmpty())return "";
        String clean=stripControls(input).replace("\r\n","\n").replace('\r','\n');
        String[] lines=clean.split("\n",-1);
        StringBuilder out=new StringBuilder(clean.length()+24);
        for(int i=0;i<lines.length;i++){
            if(i>0)out.append('\n');
            String line=lines[i];
            if(line.isEmpty())continue;
            boolean rtl=isArabicDominant(line);
            BidiFormatter formatter=BidiFormatter.getInstance(rtl);
            out.append(formatter.unicodeWrap(line,rtl?TextDirectionHeuristicsCompat.RTL:TextDirectionHeuristicsCompat.LTR,true));
        }
        return out;
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

    private static boolean firstStrongArabic(String s){
        for(int i=0;i<s.length();){int cp=s.codePointAt(i);i+=Character.charCount(cp);Character.UnicodeScript sc=Character.UnicodeScript.of(cp);if(sc==Character.UnicodeScript.ARABIC)return true;if(sc==Character.UnicodeScript.LATIN)return false;}return false;
    }
}
