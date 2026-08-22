package com.kareem.cortex;

import java.util.*;

public final class LanguageBlockFormatter {
    private LanguageBlockFormatter(){}

    // Display-only formatter. It never changes the words, only inserts paragraph breaks
    // when the dominant script switches between Arabic and Latin.
    public static String format(String input){
        if(input==null)return "";
        String s=input.replace("\r\n","\n").replace('\r','\n').trim();
        if(s.isEmpty())return s;
        StringBuilder out=new StringBuilder();
        String[] paras=s.split("\\n+");
        for(String para:paras){
            String p=para.trim();if(p.isEmpty())continue;
            String f=formatParagraph(p);
            if(out.length()>0)out.append("\n\n");
            out.append(f);
        }
        return out.toString();
    }

    private static String formatParagraph(String p){
        String[] tokens=p.split("\\s+");
        ArrayList<String> blocks=new ArrayList<>();
        StringBuilder current=new StringBuilder();
        int dir=0; // 1 Arabic, 2 Latin, 0 neutral/unknown
        for(String token:tokens){
            int td=direction(token);
            if(td==0)td=dir;
            if(dir!=0&&td!=0&&td!=dir){
                blocks.add(current.toString().trim());
                current.setLength(0);
            }
            if(current.length()>0)current.append(' ');
            current.append(token);
            if(td!=0)dir=td;
        }
        if(current.length()>0)blocks.add(current.toString().trim());
        StringBuilder out=new StringBuilder();
        for(String b:blocks){if(b.isEmpty())continue;if(out.length()>0)out.append("\n\n");out.append(b);}
        return out.toString();
    }

    private static int direction(String s){
        int ar=0,la=0;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if((c>=0x0600&&c<=0x06ff)||(c>=0x0750&&c<=0x077f)||(c>=0x08a0&&c<=0x08ff))ar++;
            else if((c>='A'&&c<='Z')||(c>='a'&&c<='z'))la++;
        }
        if(ar==0&&la==0)return 0;
        return ar>=la?1:2;
    }
}
