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

    public static boolean plausibleEntity(String kind,String raw){
        String k=n(kind).toUpperCase(Locale.ROOT),x=n(raw);
        if("PROJECT".equals(k))return plausibleProject(x);
        if("PERSON".equals(k))return plausiblePerson(x);
        if("ORGANIZATION".equals(k)||"ORG".equals(k)||"PRODUCT".equals(k)||"PLACE".equals(k))return plausibleNamedThing(x);
        // URLs, dates, money, phones, e-mails and hashtags are structured facts, not graph identities.
        return false;
    }

    public static boolean plausibleProject(String raw){
        String x=cleanProjectName(raw);if(x.length()<3||x.length()>64||technicalNoise(x))return false;
        String norm=LocalSemanticEmbedder.norm(x);if(norm.isEmpty()||GENERIC_PROJECT.contains(norm))return false;
        String[] words=norm.split("\\s+");if(words.length>7)return false;
        int letters=0,longToken=0,digits=0,weird=0;for(int i=0;i<x.length();i++){char c=x.charAt(i);if(Character.isLetter(c))letters++;else if(Character.isDigit(c))digits++;else if(!Character.isWhitespace(c)&&"-_&/'.".indexOf(c)<0)weird++;}
        for(String w:words)if(w.length()>=3)longToken++;
        if(letters<2||longToken<1||weird>2)return false;
        if(digits>0&&letters<2)return false;
        if(words.length>=5&&containsVerbish(norm))return false;
        return true;
    }

    public static boolean plausiblePerson(String raw){
        String x=n(raw).replaceAll("\\s+"," ").trim();if(x.length()<2||x.length()>72||technicalNoise(x))return false;
        String[] words=x.split("\\s+");if(words.length>5)return false;
        int letters=0,digits=0;for(int i=0;i<x.length();i++){char c=x.charAt(i);if(Character.isLetter(c))letters++;else if(Character.isDigit(c))digits++;}
        return letters>=2&&digits==0;
    }

    private static boolean plausibleNamedThing(String raw){
        String x=n(raw).replaceAll("\\s+"," ").trim();if(x.length()<2||x.length()>96||technicalNoise(x))return false;
        int letters=0;for(int i=0;i<x.length();i++)if(Character.isLetter(x.charAt(i)))letters++;
        return letters>=2&&x.split("\\s+").length<=10;
    }

    private static boolean technicalNoise(String raw){
        String x=n(raw),l=x.toLowerCase(Locale.ROOT);
        if(l.contains("://")||l.startsWith("www.")||l.startsWith("http")||l.contains("@"))return true;
        // Host names, short-link tokens and OCR fragments such as paige.prompts / 3zq.co/abc
        // belong to structured URL/reference facts, never to the identity graph.
        if(l.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?:/.*)?"))return true;
        if(l.contains("/")&&l.contains("."))return true;
        if(l.contains("automations:failed")||l.contains("failed in ")||l.contains("exception")||l.contains("stacktrace"))return true;
        if(l.endsWith(".com")||l.endsWith(".net")||l.endsWith(".org")||l.endsWith(".ae")||l.endsWith(".co"))return true;
        return false;
    }

    private static boolean containsVerbish(String x){return has(x," is "," are "," was "," were "," need "," needs "," missing "," failed "," call "," send "," buy "," amount "," لازم "," محتاج "," ابعت "," كلم "," ناقص "," مبلغ ");}
    private static boolean has(String s,String...xs){String z=" "+s+" ";for(String x:xs)if(z.contains(x))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
}
