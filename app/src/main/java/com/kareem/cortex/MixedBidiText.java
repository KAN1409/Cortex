package com.kareem.cortex;

/**
 * Display-only Unicode BiDi formatter for Arabic/English code-switched text.
 *
 * It does not translate, reorder, or replace any spoken words. It only inserts
 * Unicode isolation controls so Android renders mixed RTL/LTR runs in a stable,
 * readable order when lines wrap.
 */
public final class MixedBidiText {
    private static final char LRI='\u2066';
    private static final char RLI='\u2067';
    private static final char PDI='\u2069';
    private static final int NONE=0, AR=1, LATIN=2;

    private MixedBidiText(){}

    public static String forDisplay(String input){
        if(input==null||input.isEmpty())return input==null?"":input;
        String clean=stripControls(input);
        String[] lines=clean.split("\\n",-1);
        StringBuilder out=new StringBuilder(clean.length()+24);
        for(int i=0;i<lines.length;i++){
            if(i>0)out.append('\n');
            out.append(formatLine(lines[i]));
        }
        return out.toString();
    }

    public static String stripControls(String s){
        if(s==null||s.isEmpty())return s==null?"":s;
        return s.replace("\u2066","").replace("\u2067","").replace("\u2068","").replace("\u2069","")
                .replace("\u200E","").replace("\u200F","");
    }

    private static String formatLine(String line){
        if(line==null||line.isEmpty())return line==null?"":line;
        int ar=0,latin=0;
        for(int i=0;i<line.length();){int cp=line.codePointAt(i);i+=Character.charCount(cp);int d=script(cp);if(d==AR)ar++;else if(d==LATIN)latin++;}
        if(ar==0||latin==0)return line;

        int base=ar>=latin?AR:LATIN;
        StringBuilder result=new StringBuilder(line.length()+16);
        result.append(base==AR?RLI:LRI);

        StringBuilder run=new StringBuilder();
        int runDir=NONE;
        for(int i=0;i<line.length();){
            int cp=line.codePointAt(i);i+=Character.charCount(cp);
            int d=script(cp);
            if(d!=NONE&&runDir!=NONE&&d!=runDir){appendRun(result,run,runDir,base);run.setLength(0);}
            if(d!=NONE)runDir=d;
            run.appendCodePoint(cp);
        }
        appendRun(result,run,runDir,base);
        result.append(PDI);
        return result.toString();
    }

    private static void appendRun(StringBuilder out,StringBuilder run,int dir,int base){
        if(run.length()==0)return;
        if(dir==NONE||dir==base){out.append(run);return;}
        out.append(dir==AR?RLI:LRI).append(run).append(PDI);
    }

    private static int script(int cp){
        Character.UnicodeScript s=Character.UnicodeScript.of(cp);
        if(s==Character.UnicodeScript.ARABIC)return AR;
        if(s==Character.UnicodeScript.LATIN)return LATIN;
        return NONE;
    }
}
