package com.kareem.cortex;

import java.util.*;

/** Conservative quality rules for inferred entity labels. Identity confirmation remains separate. */
public final class EntityQualityPolicy {
    private static final HashSet<String> GENERIC_PROJECT=new HashSet<>(Arrays.asList(
            "project","job","site","proposal","call","amount","missing","priority","prio","per","test","self test"));
    private EntityQualityPolicy(){}

    public static String cleanProjectName(String raw){
        String x=n(raw).replaceAll("^[•▪◦*\\-–—]+\\s*","").replaceAll("\\s+"," ").trim();
        x=x.replaceFirst("(?i)^(?:is\\s+for|for|called|named)\\s+","").trim();
        x=x.replaceFirst("^(?:هو|اسم(?:ه|ها)?|اسمه|اسمها)\\s+","").trim();
        x=x.replaceAll("[.,;:!?؟]+$","").trim();
        return x;
    }

    public static boolean plausibleProject(String raw){
        String x=cleanProjectName(raw);if(x.length()<3||x.length()>64)return false;
        String norm=LocalSemanticEmbedder.norm(x);if(norm.isEmpty()||GENERIC_PROJECT.contains(norm))return false;
        String[] words=norm.split("\\s+");if(words.length>7)return false;
        int letters=0,longToken=0,digits=0,weird=0;for(int i=0;i<x.length();i++){char c=x.charAt(i);if(Character.isLetter(c))letters++;else if(Character.isDigit(c))digits++;else if(!Character.isWhitespace(c)&&"-_&/'.".indexOf(c)<0)weird++;}
        for(String w:words)if(w.length()>=3)longToken++;
        if(letters<2||longToken<1||weird>2)return false;
        if(digits>0&&letters<2)return false;
        // A short label is fine; a sentence-like fragment is not.
        if(words.length>=5&&containsVerbish(norm))return false;
        return true;
    }

    private static boolean containsVerbish(String x){return has(x," is "," are "," was "," were "," need "," needs "," missing "," call "," send "," buy "," amount "," لازم "," محتاج "," ابعت "," كلم "," ناقص "," مبلغ ");}
    private static boolean has(String s,String...xs){String z=" "+s+" ";for(String x:xs)if(z.contains(x))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
}
