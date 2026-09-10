package com.kareem.cortex;

import java.text.Normalizer;
import java.util.*;

/** Provider-neutral scoring. Reference text is the only authority; no lexical hints live here. */
public final class AsrBenchmarkMetrics {
    public static final class Score {
        public final int referenceWords,wordEdits,referenceChars,charEdits,arabicReferenceWords,arabicWordEdits,latinReferenceWords,latinWordEdits,referenceNumbers,matchedNumbers,referenceScriptSwitches,hypothesisScriptSwitches,duplicateAdjacentWords;
        public final double wer,cer,arabicWer,latinWer,numberRecall;
        Score(int rw,int we,double w,int rc,int ce,double c,int ar,int ae,double aw,int lr,int le,double lw,int rn,int mn,double nr,int rs,int hs,int d){referenceWords=rw;wordEdits=we;wer=w;referenceChars=rc;charEdits=ce;cer=c;arabicReferenceWords=ar;arabicWordEdits=ae;arabicWer=aw;latinReferenceWords=lr;latinWordEdits=le;latinWer=lw;referenceNumbers=rn;matchedNumbers=mn;numberRecall=nr;referenceScriptSwitches=rs;hypothesisScriptSwitches=hs;duplicateAdjacentWords=d;}
    }
    private enum Script{ARABIC,LATIN,NUMBER,OTHER}
    private AsrBenchmarkMetrics(){}
    public static Score score(String reference,String hypothesis){
        String ref=normalize(reference),hyp=normalize(hypothesis);List<String> rw=tokens(ref),hw=tokens(hyp);int we=edit(rw,hw);String rc=ref.replace(" ",""),hc=hyp.replace(" ","");int ce=editChars(rc,hc);
        List<String> ra=filter(rw,Script.ARABIC),ha=filter(hw,Script.ARABIC),rl=filter(rw,Script.LATIN),hl=filter(hw,Script.LATIN),rn=filter(rw,Script.NUMBER),hn=filter(hw,Script.NUMBER);int ae=edit(ra,ha),le=edit(rl,hl),mn=matches(rn,hn);
        return new Score(rw.size(),we,ratio(we,rw.size()),rc.length(),ce,ratio(ce,rc.length()),ra.size(),ae,ratio(ae,ra.size()),rl.size(),le,ratio(le,rl.size()),rn.size(),mn,rn.isEmpty()?1.0:(double)mn/rn.size(),switches(rw),switches(hw),duplicates(hw));
    }
    static String normalize(String s){if(s==null)return"";String o=Normalizer.normalize(s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('\u0640',' ');o=o.replaceAll("[\\p{Punct}\\p{S}]+"," ");return o.replaceAll("\\s+"," ").trim();}
    private static List<String> tokens(String s){ArrayList<String>o=new ArrayList<>();if(!s.isEmpty())for(String p:s.split(" "))if(!p.isEmpty())o.add(p);return o;}
    private static List<String> filter(List<String>w,Script wanted){ArrayList<String>o=new ArrayList<>();for(String x:w)if(script(x)==wanted)o.add(x);return o;}
    private static Script script(String t){boolean a=false,l=false,d=false;for(int i=0;i<t.length();i++){char c=t.charAt(i);if(Character.isDigit(c))d=true;Character.UnicodeBlock b=Character.UnicodeBlock.of(c);if(b==Character.UnicodeBlock.ARABIC||b==Character.UnicodeBlock.ARABIC_SUPPLEMENT||b==Character.UnicodeBlock.ARABIC_EXTENDED_A)a=true;if((c>='a'&&c<='z')||(c>='A'&&c<='Z'))l=true;}if(d&&!a&&!l)return Script.NUMBER;if(a&&!l)return Script.ARABIC;if(l&&!a)return Script.LATIN;return Script.OTHER;}
    private static int switches(List<String>w){Script p=null;int n=0;for(String x:w){Script s=script(x);if(s!=Script.ARABIC&&s!=Script.LATIN)continue;if(p!=null&&p!=s)n++;p=s;}return n;}
    private static int duplicates(List<String>w){int n=0;for(int i=1;i<w.size();i++)if(w.get(i).equals(w.get(i-1)))n++;return n;}
    private static int matches(List<String>a,List<String>b){ArrayList<String>r=new ArrayList<>(b);int n=0;for(String x:a){int i=r.indexOf(x);if(i>=0){n++;r.remove(i);}}return n;}
    private static double ratio(int e,int n){return n==0?(e==0?0:1):(double)e/n;}
    private static int edit(List<String>a,List<String>b){int[]p=new int[b.size()+1],c=new int[b.size()+1];for(int j=0;j<=b.size();j++)p[j]=j;for(int i=1;i<=a.size();i++){c[0]=i;for(int j=1;j<=b.size();j++){int x=a.get(i-1).equals(b.get(j-1))?0:1;c[j]=Math.min(Math.min(c[j-1]+1,p[j]+1),p[j-1]+x);}int[]s=p;p=c;c=s;}return p[b.size()];}
    private static int editChars(String a,String b){int[]p=new int[b.length()+1],c=new int[b.length()+1];for(int j=0;j<=b.length();j++)p[j]=j;for(int i=1;i<=a.length();i++){c[0]=i;for(int j=1;j<=b.length();j++){int x=a.charAt(i-1)==b.charAt(j-1)?0:1;c[j]=Math.min(Math.min(c[j-1]+1,p[j]+1),p[j-1]+x);}int[]s=p;p=c;c=s;}return p[b.length()];}
}
