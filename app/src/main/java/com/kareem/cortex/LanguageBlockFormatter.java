package com.kareem.cortex;

import java.util.*;

public final class LanguageBlockFormatter {
    private LanguageBlockFormatter(){}

    // Display-only formatter. Raw transcript text is never changed in storage.
    // Short code-switched spans stay inline with Unicode directional isolation and
    // Android is allowed to wrap the sentence naturally. Only a genuinely long
    // opposite-language span gets a deliberate compact line break.
    private static final char LRI='\u2066';
    private static final char RLI='\u2067';
    private static final char PDI='\u2069';
    private static final int LONG_SWITCH_WORDS=5;
    private static final int LONG_SWITCH_CHARS=34;

    private static final class Run {
        int dir; // 1 Arabic, 2 Latin, 0 neutral
        String text;
        Run(int d,String t){dir=d;text=t;}
    }

    public static String format(String input){
        if(input==null)return "";
        String s=input.replace("\r\n","\n").replace('\r','\n').trim();
        if(s.isEmpty())return s;
        StringBuilder out=new StringBuilder();
        String[] paras=s.split("\\n+");
        for(String para:paras){
            String p=para.trim();if(p.isEmpty())continue;
            String f=formatParagraph(p);
            if(out.length()>0)out.append('\n');
            out.append(f);
        }
        return out.toString();
    }

    private static String formatParagraph(String p){
        int dominant=dominantDirection(p);
        if(dominant==0)return compactWords(p);
        ArrayList<Run> rs=runs(p);
        StringBuilder out=new StringBuilder();
        StringBuilder line=new StringBuilder();

        for(Run r:rs){
            if(r.text==null||r.text.trim().isEmpty())continue;
            String clean=compactWords(r.text);
            boolean opposite=r.dir!=0&&r.dir!=dominant;
            boolean longSwitch=opposite&&(wordCount(clean)>=LONG_SWITCH_WORDS||visibleLength(clean)>LONG_SWITCH_CHARS);
            String piece=isolate(clean,r.dir);

            if(longSwitch){
                flush(out,line);
                if(out.length()>0&&out.charAt(out.length()-1)!='\n')out.append('\n');
                out.append(piece);
                out.append('\n');
            }else{
                if(line.length()>0)line.append(' ');
                line.append(piece);
            }
        }
        flush(out,line);
        while(out.length()>0&&out.charAt(out.length()-1)=='\n')out.setLength(out.length()-1);
        return out.toString().trim();
    }

    private static ArrayList<Run> runs(String p){
        ArrayList<Run> out=new ArrayList<>();String[] tokens=p.split("\\s+");StringBuilder cur=new StringBuilder();int dir=0;
        for(String token:tokens){
            int td=direction(token);
            if(td==0)td=dir;
            if(dir!=0&&td!=0&&td!=dir){out.add(new Run(dir,cur.toString().trim()));cur.setLength(0);}
            if(cur.length()>0)cur.append(' ');cur.append(token);if(td!=0)dir=td;
        }
        if(cur.length()>0)out.add(new Run(dir,cur.toString().trim()));return out;
    }

    private static int dominantDirection(String s){
        int ar=0,la=0;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(isArabic(c))ar++;else if(isLatin(c))la++;
        }
        if(ar==0&&la==0)return 0;
        return ar>=la?1:2;
    }

    private static String isolate(String s,int dir){
        if(dir==1)return ""+RLI+s+PDI;
        if(dir==2)return ""+LRI+s+PDI;
        return s;
    }

    private static String compactWords(String s){return s.replaceAll("\\s+"," ").trim();}
    private static int wordCount(String s){String x=s.trim();return x.isEmpty()?0:x.split("\\s+").length;}
    private static int visibleLength(String s){int n=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c!=LRI&&c!=RLI&&c!=PDI)n++;}return n;}
    private static void flush(StringBuilder out,StringBuilder line){if(line.length()==0)return;if(out.length()>0&&out.charAt(out.length()-1)!='\n')out.append('\n');out.append(line.toString().trim());line.setLength(0);}

    private static int direction(String s){
        int ar=0,la=0;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);if(isArabic(c))ar++;else if(isLatin(c))la++;
        }
        if(ar==0&&la==0)return 0;return ar>=la?1:2;
    }
    private static boolean isArabic(char c){return (c>=0x0600&&c<=0x06ff)||(c>=0x0750&&c<=0x077f)||(c>=0x08a0&&c<=0x08ff);}
    private static boolean isLatin(char c){return (c>='A'&&c<='Z')||(c>='a'&&c<='z');}
}
