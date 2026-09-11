package com.kareem.cortex;

import java.util.*;

/** Conservative quality rules for inferred entity labels. Identity confirmation remains separate. */
public final class EntityQualityPolicy {
    private static final HashSet<String> GENERIC_PROJECT=new HashSet<>(Arrays.asList(
            "project","projects","job","site","proposal","call","amount","missing","priority","prio","per","test","self test","action","actions","note","notes","data"));
    private static final HashSet<String> PERSON_NOISE=new HashSet<>(Arrays.asList(
            "egp","usd","eur","gbp","le","url","date","money","phone","email","project","fix","failed","error","unknown","null"));
    private EntityQualityPolicy(){}

    public static String cleanEntityValue(String kind,String raw){
        String k=n(kind).toUpperCase(Locale.ROOT);
        if("PERSON".equals(k))return cleanPersonName(raw);
        if("PROJECT".equals(k))return cleanProjectName(raw);
        return n(raw).replaceAll("\\s+"," ").replaceAll("[.,;:!?؟]+$","").trim();
    }

    public static String cleanProjectName(String raw){
        String x=n(raw).replaceAll("^[•▪◦*\\-–—]+\\s*","").replaceAll("\\s+"," ").trim();
        x=x.replaceFirst("(?i)^(?:is\\s+for|for|called|named)\\s+","").trim();
        x=x.replaceFirst("^(?:هو|اسم(?:ه|ها)?|اسمه|اسمها)\\s+","").trim();
        x=x.replaceAll("[.,;:!?؟]+$","").trim();
        return x;
    }

    public static String cleanPersonName(String raw){
        String x=n(raw).replaceAll("^[•▪◦*\\-–—]+\\s*","").replaceAll("\\s+"," ").trim();
        x=x.replaceFirst("(?i)^(?:dr|eng|mr|mrs|ms|prof)\\.?\\s+","").trim();
        x=x.replaceFirst("^(?:دكتور|د\\.|م\\.|مهندس|أستاذ|استاذ)\\s+","").trim();
        // OCR/title extraction occasionally glues surrounding context onto a real name.
        x=x.replaceFirst("(?i)\\s+(?:at|from|via)\\s+.*$","").trim();
        x=x.replaceFirst("(?i)\\s+(?:EGP|USD|EUR|GBP|LE)\\b.*$","").trim();
        x=x.replaceAll("[.,;:!?؟]+$","").trim();
        return x;
    }

    public static boolean plausibleEntity(String kind,String raw){
        String k=n(kind).toUpperCase(Locale.ROOT),x=cleanEntityValue(k,raw);
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
        if(startsLikeInstruction(norm)||looksLikeStatusLine(norm))return false;
        int letters=0,longToken=0,digits=0,weird=0;for(int i=0;i<x.length();i++){char c=x.charAt(i);if(Character.isLetter(c))letters++;else if(Character.isDigit(c))digits++;else if(!Character.isWhitespace(c)&&"-_&/'.".indexOf(c)<0)weird++;}
        for(String w:words)if(w.length()>=3)longToken++;
        if(letters<2||longToken<1||weird>2)return false;
        if(digits>0&&letters<2)return false;
        if(words.length>=4&&containsVerbish(norm))return false;
        return true;
    }

    public static boolean plausiblePerson(String raw){
        String x=cleanPersonName(raw);if(x.length()<2||x.length()>72||technicalNoise(x))return false;
        String norm=LocalSemanticEmbedder.norm(x);if(norm.isEmpty())return false;
        String[] words=norm.split("\\s+");if(words.length>5)return false;
        for(String w:words)if(PERSON_NOISE.contains(w))return false;
        if(norm.contains(" view details")||norm.contains(" failed ")||norm.contains(" resolution "))return false;
        int letters=0,digits=0;for(int i=0;i<x.length();i++){char c=x.charAt(i);if(Character.isLetter(c))letters++;else if(Character.isDigit(c))digits++;}
        return letters>=2&&digits==0;
    }

    private static boolean plausibleNamedThing(String raw){
        String x=n(raw).replaceAll("\\s+"," ").trim();if(x.length()<2||x.length()>96||technicalNoise(x))return false;
        String norm=LocalSemanticEmbedder.norm(x);if(looksLikeStatusLine(norm))return false;
        int letters=0;for(int i=0;i<x.length();i++)if(Character.isLetter(x.charAt(i)))letters++;
        return letters>=2&&x.split("\\s+").length<=10;
    }

    private static boolean technicalNoise(String raw){
        String x=n(raw),l=x.toLowerCase(Locale.ROOT);
        if(l.contains("://")||l.startsWith("www.")||l.startsWith("http")||l.contains("@"))return true;
        // Host names, short-link tokens and OCR fragments belong to structured URL/reference facts,
        // never to the identity graph.
        if(l.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?:/.*)?"))return true;
        if(l.contains("/")&&l.contains("."))return true;
        if(l.matches(".*\\b(?:com|net|org|ae|io|co|site|app)\\b.*")&&l.contains("."))return true;
        if(l.contains("automations:failed")||l.contains("failed in ")||l.contains("exception")||l.contains("stacktrace"))return true;
        if(l.endsWith(".com")||l.endsWith(".net")||l.endsWith(".org")||l.endsWith(".ae")||l.endsWith(".co")||l.endsWith(".site"))return true;
        if(l.matches("com\\.[a-z0-9_.-]+"))return true;
        return false;
    }

    private static boolean startsLikeInstruction(String x){
        String y=" "+x+" ";
        String[] starts={"fix ","make ","add ","restore ","review ","check ","send ","call ","buy ","install ","download ","open ","close ","start ","stop ","create ","update ","delete ","resolve ","run ","test ","ensure ","retry ","grant ","capture ","sync ","search ","صلح ","اعمل ","راجع ","ابعت ","كلم ","ثبت ","افتح ","اقفل ","شغل ","ابدأ "};
        String z=x.trim();for(String s:starts)if(z.startsWith(s))return true;
        return y.contains(" make sure ")||y.contains(" view details ");
    }

    private static boolean looksLikeStatusLine(String x){
        String z=" "+x+" ";
        return z.contains(" failed in ")||z.contains(" passed in ")||z.contains(" processing ")||z.contains(" queued ")||z.contains(" warning ")||z.contains(" error ")||z.contains(" retry ")||z.contains(" view details ");
    }

    private static boolean containsVerbish(String x){return has(x," is "," are "," was "," were "," need "," needs "," missing "," failed "," call "," send "," buy "," fix "," review "," check "," make "," add "," install "," amount "," لازم "," محتاج "," ابعت "," كلم "," ناقص "," مبلغ "," صلح "," راجع ");}
    private static boolean has(String s,String...xs){String z=" "+s+" ";for(String x:xs)if(z.contains(x))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
}
