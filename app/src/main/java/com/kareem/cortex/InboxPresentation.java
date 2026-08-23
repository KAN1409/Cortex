package com.kareem.cortex;

import java.util.*;

public final class InboxPresentation {
    private InboxPresentation(){}

    public static String title(KnowledgeItem item,ArrayList<String> openActions){
        if(openActions!=null&&!openActions.isEmpty()){
            ArrayList<String> parts=actionParts(openActions);
            if(!parts.isEmpty())return concise(parts.get(0),72);
        }
        String s=item==null?"":nz(item.title).trim();
        if(s.regionMatches(true,0,"Voice:",0,6))s=s.substring(6).trim();
        if(s.isEmpty()&&item!=null)s=nz(item.summary).trim();
        return concise(s.isEmpty()?"Memory":s,76);
    }

    public static ArrayList<String> actionParts(ArrayList<String> raw){
        ArrayList<String> out=new ArrayList<>();
        if(raw==null)return out;
        for(String source:raw){
            String s=stripDue(nz(source));
            if(s.isEmpty())continue;
            s=s.replaceAll("\\s+[•|]\\s+",". ");
            s=s.replaceAll("(?i)\\s+(?=and then\\b|then\\b|after that\\b|if (?:he|she|they|it)\\b)",". ");
            s=s.replaceAll("\\s+(?=ولو\\b|وبعدها\\b|وبعدين\\b|بعدها\\b|لو ما\\b|لو م[ا]?\\s)",". ");
            String[] clauses=s.split("(?<=[.!؟])\\s+");
            for(String c:clauses){String x=cleanup(c);if(x.length()<3)continue;if(!containsNear(out,x))out.add(concise(x,130));}
        }
        if(out.size()>4)return new ArrayList<>(out.subList(0,4));return out;
    }

    public static String due(ArrayList<String> raw){
        if(raw==null)return "";
        for(String s:raw){int i=nz(s).toLowerCase(Locale.US).lastIndexOf("due:");if(i>=0){String d=nz(s).substring(i+4).replace("•","").trim();if(!d.isEmpty())return TemporalResolver.displayStored(d);}}
        return "";
    }

    public static String preview(KnowledgeItem k,ArrayList<String> actions){
        if(k==null)return "";String p=!blank(k.summary)?k.summary:(!blank(k.extractedText)?k.extractedText:k.rawText);p=cleanup(nz(p));
        if(actions!=null&&!actions.isEmpty()){int stop=sentenceEnd(p);if(stop>25&&stop<170)p=p.substring(0,stop+1);return concise(p,170);}return concise(p,300);
    }
    private static String stripDue(String s){int i=s.toLowerCase(Locale.US).lastIndexOf("due:");if(i>=0){int bullet=s.lastIndexOf('•',i);s=s.substring(0,bullet>=0?bullet:i);}return cleanup(s);}
    private static int sentenceEnd(String s){int best=-1;for(char c:new char[]{'.','؟','!','\n'}){int i=s.indexOf(c);if(i>=0&&(best<0||i<best))best=i;}return best;}
    private static String cleanup(String s){return nz(s).replaceAll("\\s+"," ").replaceAll("^[•.،,\\-]+\\s*","").replaceAll("\\s+[.،,]+$","").trim();}
    private static String concise(String s,int max){String x=cleanup(s);if(x.length()<=max)return x;int cut=x.lastIndexOf(' ',max);if(cut<max/2)cut=max;return x.substring(0,cut).trim()+"…";}
    private static boolean containsNear(ArrayList<String> xs,String x){String n=norm(x);for(String y:xs){String m=norm(y);if(m.equals(n)||m.contains(n)||n.contains(m))return true;}return false;}
    private static String norm(String s){return cleanup(s).toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    private static boolean blank(String s){return s==null||s.trim().isEmpty();}private static String nz(String s){return s==null?"":s;}
}
