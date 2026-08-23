package com.kareem.cortex;

import java.util.Locale;

/** Display-only cleanup for voice items. Never use this to mutate stored transcript evidence. */
public final class VoiceTextPresentation {
    private VoiceTextPresentation(){}

    public static String compactTitle(String raw){
        String s=clean(raw);
        if(s.regionMatches(true,0,"Voice:",0,6))s=s.substring(6).trim();
        if(s.isEmpty())return "Voice note";

        // Conservative title-only cleanup for a common mixed-ASR artifact: duplicated Arabic article.
        s=s.replaceAll("(?iu)(^|\\s)(ال(?:ـ)?)\\s+ال(?:ـ)?(?=\\s)","$1$2");

        int boundary=firstBoundary(s,18,62);
        if(boundary>0)s=s.substring(0,boundary).trim();
        else if(s.length()>62){
            int cut=s.lastIndexOf(' ',62);
            if(cut<28)cut=62;
            s=s.substring(0,cut).trim()+"…";
        }
        return s;
    }

    /** False when Summary is effectively just another copy of the transcript. */
    public static boolean materiallyDifferent(String summary,String evidence){
        String a=compareForm(summary),b=compareForm(evidence);
        if(a.isEmpty())return false;
        if(b.isEmpty())return true;
        if(a.equals(b))return false;
        int min=Math.min(a.length(),b.length()),max=Math.max(a.length(),b.length());
        if(max>0&&((double)min/(double)max)>=0.90&&(a.contains(b)||b.contains(a)))return false;
        return true;
    }

    private static int firstBoundary(String s,int min,int max){
        int limit=Math.min(max,s.length());
        for(int i=Math.min(min,s.length());i<limit;i++){
            char c=s.charAt(i);
            if(c=='؟'||c=='?'||c=='!'||c=='.')return i+1;
        }
        return -1;
    }

    private static String clean(String raw){
        return MixedBidiText.stripControls(raw==null?"":raw).replaceAll("\\s+"," ").trim();
    }

    private static String compareForm(String raw){
        return clean(raw).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+","");
    }
}
