package com.kareem.cortex;

import android.util.Patterns;
import java.util.*;
import java.util.regex.*;

/** Shared local understanding pass used by every ingest path. */
public final class LocalAnalyzer {
    private LocalAnalyzer(){}

    public static AnalysisResult analyze(String text,String mime){
        String src=text==null?"":text.trim();
        AnalysisResult table=TabularAnalyzer.analyze(src);
        if(table!=null){extractEntities(src,table);extractActions(src,table);return table;}
        AnalysisResult r=new AnalysisResult();
        r.title=AutoClassifier.title(src,mime);
        r.category=AutoClassifier.category(src,mime);
        r.tags=AutoClassifier.tags(src,r.category);
        r.summary=summarize(src);
        extractEntities(src,r);
        extractActions(src,r);
        return r;
    }

    private static String summarize(String t){String one=t.replaceAll("\\s+"," ").trim();if(one.isEmpty())return "No text content.";String[] parts=one.split("(?<=[.!?؟])\\s+");StringBuilder s=new StringBuilder();for(String p:parts){if(p.trim().isEmpty())continue;if(s.length()>0)s.append(' ');s.append(p.trim());if(s.length()>=220||countSentences(s.toString())>=2)break;}String out=s.length()==0?one:s.toString();return out.length()>360?out.substring(0,360)+"…":out;}
    private static int countSentences(String s){int n=0;for(char c:s.toCharArray())if(c=='.'||c=='!'||c=='?'||c=='؟')n++;return n;}

    private static void extractEntities(String t,AnalysisResult r){
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        Matcher url=Patterns.WEB_URL.matcher(t);while(url.find())add(r,seen,"URL",url.group(),.99);
        Matcher email=Patterns.EMAIL_ADDRESS.matcher(t);while(email.find())add(r,seen,"EMAIL",email.group(),.99);
        match(r,seen,t,"DATE","\\b(?:[0-3]?\\d[/-][01]?\\d(?:[/-](?:19|20)?\\d{2})?|(?:19|20)\\d{2}-[01]\\d-[0-3]\\d)\\b",.90);
        match(r,seen,t,"DATE","(?i)\\b(?:today|tomorrow|tonight|next week|next month|sunday|monday|tuesday|wednesday|thursday|friday|saturday)\\b|(?:النهاردة|بكرة|بكره|الليلة|الأسبوع الجاي|الاسبوع الجاي|الشهر الجاي|الأحد|الاتنين|الثلاثاء|الأربعاء|الخميس|الجمعة|السبت)",.82);
        match(r,seen,t,"PHONE","(?<!\\d)(?:\\+?20[- .]?)?(?:0?1[0125])[- .]?\\d{3}[- .]?\\d{4}(?!\\d)",.86);
        match(r,seen,t,"MONEY","(?i)(?:EGP|USD|EUR|GBP|LE|جنيه|دولار)\\s*[0-9][0-9,]*(?:\\.[0-9]+)?|[0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:EGP|USD|EUR|GBP|LE|جنيه|دولار)",.92);
        match(r,seen,t,"HASHTAG","#[\\p{L}\\p{N}_]+",.96);
        matchGroup(r,seen,t,"PERSON","(?i)\\b(?:dr|eng|mr|mrs|ms|prof)\\.?\\s+([A-Z][A-Za-z'-]+(?:\\s+[A-Z][A-Za-z'-]+){0,3})",.88);
        matchGroup(r,seen,t,"PERSON","(?:دكتور|د\\.|م\\.|مهندس|أستاذ|استاذ)\\s+([\\p{IsArabic}]{2,}(?:\\s+[\\p{IsArabic}]{2,}){0,3})",.88);
        matchGroup(r,seen,t,"PERSON","(?:أكلم|اكلم|كلم|ابعت(?:له|لها)?|أبعت(?:له|لها)?|اتابع مع|أتابع مع)\\s+([\\p{IsArabic}]{2,18})",.72);
        matchGroup(r,seen,t,"PROJECT","(?i)\\b(?:project|job|site|proposal)\\s*[:#-]?\\s*([A-Za-z0-9][A-Za-z0-9 _-]{2,48})",.76);
        matchGroup(r,seen,t,"PROJECT","(?:مشروع|موقع|بروجكت)\\s*[:#-]?\\s*([\\p{IsArabic}A-Za-z0-9][\\p{IsArabic}A-Za-z0-9 _-]{2,48})",.78);
    }
    private static void match(AnalysisResult r,Set<String> seen,String t,String kind,String regex,double conf){Matcher m=Pattern.compile(regex).matcher(t);while(m.find())add(r,seen,kind,m.group(),conf);}
    private static void matchGroup(AnalysisResult r,Set<String> seen,String t,String kind,String regex,double conf){Matcher m=Pattern.compile(regex).matcher(t);while(m.find())add(r,seen,kind,m.group(1),conf);}
    private static void add(AnalysisResult r,Set<String> seen,String kind,String value,double conf){if(value==null)return;String v=value.replaceAll("[.,;:!?؟]+$","").replaceAll("\\s+"," ").trim();if(v.length()<2)return;String key=kind+"|"+v.toLowerCase(Locale.US);if(seen.add(key))r.entities.add(new AnalysisResult.Entity(kind,v,conf));}

    private static void extractActions(String t,AnalysisResult r){
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        String normalized=t.replace('\n',' ').replace('\r',' ');
        // Meaningful clause boundaries, not language switches.
        String[] clauses=normalized.split("(?<=[.!?؟;؛])\\s+|\\s+(?:و?بعدها|then|and then)\\s+|\\s+(?=ولو|وإذا|واذا|if\\s)");
        String[] keys={"todo","to-do","remind","follow up","follow-up","need to","must ","should ","call ","send ","book ","schedule ","check ","review ","upload ","fix ","لازم","محتاج","عاوز","عايز","فكرني","افتكر","ابعت","أبعت","كلم","أكلم","اكلم","احجز","راجع","شوف","اشتري","أشتري","أتابع","اتابع","هتابع"};
        for(String clause:clauses){String c=clause.replaceAll("\\s+"," ").trim();if(c.length()<4)continue;String low=c.toLowerCase(Locale.US);boolean hit=false;for(String k:keys)if(low.contains(k.toLowerCase(Locale.US))){hit=true;break;}if(!hit)continue;
            // Split an overloaded clause into actionable chunks around explicit follow-up conditions.
            ArrayList<String> parts=new ArrayList<>();Matcher m=Pattern.compile("(?i)(.+?)(?=\\s+(?:ولو|وإذا|واذا|if\\s)|$)").matcher(c);while(m.find()){String x=m.group(1).trim();if(!x.isEmpty())parts.add(x);}if(parts.isEmpty())parts.add(c);
            for(String a:parts){String action=cleanAction(a);if(action.length()<4||!unique.add(action.toLowerCase(Locale.US)))continue;r.actions.add(new AnalysisResult.Action(action,guessDue(a)));if(r.actions.size()>=12)return;}
        }
    }
    private static String cleanAction(String s){String x=s.trim().replaceAll("^[•▪◦*-]+\\s*","").replaceAll("\\s+"," ");return x.length()>260?x.substring(0,260)+"…":x;}
    private static String guessDue(String s){String low=s.toLowerCase(Locale.US);String[] hints={"today","tomorrow","tonight","next week","next month","sunday","monday","tuesday","wednesday","thursday","friday","saturday","النهاردة","بكرة","بكره","الاسبوع الجاي","الأسبوع الجاي","الشهر الجاي","الأحد","الاتنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت"};for(String h:hints)if(low.contains(h.toLowerCase(Locale.US)))return h;Matcher m=Pattern.compile("\\b[0-3]?\\d[/-][01]?\\d(?:[/-](?:19|20)?\\d{2})?\\b").matcher(s);return m.find()?m.group():"";}
}
