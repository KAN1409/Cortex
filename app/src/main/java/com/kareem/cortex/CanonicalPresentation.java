package com.kareem.cortex;

import java.util.Locale;

/** Deterministic presentation cleanup. Semantic models may suggest subjects; UI titles are built here. */
public final class CanonicalPresentation {
    private CanonicalPresentation(){}

    public static String cleanTitle(String sourceType,String semanticType,String subject,String fallback){
        String s=clean(subject);if(s.isEmpty())s=clean(fallback);s=dedupeWords(s);if(s.length()>88)s=s.substring(0,85).trim()+"…";
        if(!s.isEmpty())return s;
        String type=clean(semanticType).toLowerCase(Locale.ROOT);if(type.contains("voice"))return"Voice note";if(type.contains("image")||type.contains("screenshot"))return"Captured image";if(type.contains("file")||type.contains("document"))return"Captured file";if(type.contains("request"))return"Request";if(type.contains("decision"))return"Decision";if(type.contains("commitment"))return"Commitment";return clean(sourceType).isEmpty()?"Cortex item":clean(sourceType);
    }

    public static String cleanBody(String s){String x=clean(s);return x.replaceAll("[ \\t]+"," ").replaceAll("\\n{3,}","\\n\\n");}

    public static String dedupeWords(String s){String x=clean(s);if(x.isEmpty())return x;String[] p=x.split("\\s+");StringBuilder b=new StringBuilder();String prev="";for(String w:p){String k=w.replaceAll("[^\\p{L}\\p{N}]","").toLowerCase(Locale.ROOT);if(!k.isEmpty()&&k.equals(prev))continue;if(b.length()>0)b.append(' ');b.append(w);prev=k;}return b.toString();}

    public static boolean containsArabic(String s){if(s==null)return false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if((c>=0x0600&&c<=0x06FF)||(c>=0x0750&&c<=0x077F)||(c>=0x08A0&&c<=0x08FF))return true;}return false;}
    private static String clean(String s){return s==null?"":s.trim();}
}
