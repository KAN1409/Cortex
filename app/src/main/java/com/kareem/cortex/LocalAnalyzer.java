package com.kareem.cortex;

import android.util.Patterns;
import java.util.*;
import java.util.regex.*;

public final class LocalAnalyzer {
    private LocalAnalyzer(){}

    public static AnalysisResult analyze(String text,String mime){
        String src=text==null?"":text.trim();
        AnalysisResult table=TabularAnalyzer.analyze(src);
        if(table!=null){ extractEntities(src,table); extractActions(src,table); return table; }
        AnalysisResult r=new AnalysisResult();
        r.title=AutoClassifier.title(src,mime);
        r.category=AutoClassifier.category(src,mime);
        r.tags=AutoClassifier.tags(src,r.category);
        r.summary=summarize(src);
        extractEntities(src,r);
        extractActions(src,r);
        return r;
    }

    private static String summarize(String t){
        String one=t.replaceAll("\\s+"," ").trim();
        if(one.isEmpty()) return "No text content.";
        String[] parts=one.split("(?<=[.!?؟])\\s+");
        StringBuilder s=new StringBuilder();
        for(String p:parts){
            if(p.trim().isEmpty()) continue;
            if(s.length()>0)s.append(' '); s.append(p.trim());
            if(s.length()>=220 || countSentences(s.toString())>=2) break;
        }
        String out=s.length()==0?one:s.toString();
        return out.length()>360?out.substring(0,360)+"…":out;
    }
    private static int countSentences(String s){int n=0;for(char c:s.toCharArray())if(c=='.'||c=='!'||c=='?'||c=='؟')n++;return n;}

    private static void extractEntities(String t,AnalysisResult r){
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        Matcher url=Patterns.WEB_URL.matcher(t); while(url.find()) add(r,seen,"URL",url.group(),0.99);
        Matcher email=Patterns.EMAIL_ADDRESS.matcher(t); while(email.find()) add(r,seen,"EMAIL",email.group(),0.99);
        match(r,seen,t,"DATE","\\b(?:[0-3]?\\d[/-][01]?\\d(?:[/-](?:19|20)?\\d{2})?|(?:19|20)\\d{2}-[01]\\d-[0-3]\\d)\\b",0.90);
        match(r,seen,t,"PHONE","(?<!\\d)(?:\\+?20[- .]?)?(?:0?1[0125])[- .]?\\d{3}[- .]?\\d{4}(?!\\d)",0.86);
        match(r,seen,t,"MONEY","(?i)(?:EGP|USD|EUR|GBP|LE|جنيه|دولار)\\s*[0-9][0-9,]*(?:\\.[0-9]+)?|[0-9][0-9,]*(?:\\.[0-9]+)?\\s*(?:EGP|USD|EUR|GBP|LE|جنيه|دولار)",0.92);
        match(r,seen,t,"HASHTAG","#[\\p{L}\\p{N}_]+",0.96);
    }
    private static void match(AnalysisResult r,Set<String> seen,String t,String kind,String regex,double conf){
        Matcher m=Pattern.compile(regex).matcher(t);while(m.find())add(r,seen,kind,m.group(),conf);
    }
    private static void add(AnalysisResult r,Set<String> seen,String kind,String value,double conf){
        String key=kind+"|"+value.toLowerCase();if(seen.add(key))r.entities.add(new AnalysisResult.Entity(kind,value,conf));
    }

    private static void extractActions(String t,AnalysisResult r){
        String[] lines=t.split("[\\n\\r]+|(?<=[.!?؟])\\s+");
        String[] keys={"todo","to-do","remind","follow up","follow-up","need to","must ","should ","call ","send ","book ","schedule ","check ","review ","upload ","fix ","لازم","فكرني","افتكر","ابعت","أبعت","كلم","احجز","راجع","شوف","اشتري","أشتري"};
        for(String line:lines){
            String l=line.trim(); if(l.length()<4) continue; String low=l.toLowerCase(); boolean hit=false;
            for(String k:keys) if(low.contains(k.toLowerCase())){hit=true;break;}
            if(hit){r.actions.add(new AnalysisResult.Action(l,guessDue(l))); if(r.actions.size()>=12)break;}
        }
    }
    private static String guessDue(String s){
        String low=s.toLowerCase();
        String[] hints={"today","tomorrow","tonight","next week","next month","sunday","monday","tuesday","wednesday","thursday","friday","saturday","النهاردة","بكرة","بكره","الاسبوع الجاي","الأسبوع الجاي","الشهر الجاي","الأحد","الاتنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت"};
        for(String h:hints) if(low.contains(h.toLowerCase())) return h;
        Matcher m=Pattern.compile("\\b[0-3]?\\d[/-][01]?\\d(?:[/-](?:19|20)?\\d{2})?\\b").matcher(s);return m.find()?m.group():"";
    }
}
