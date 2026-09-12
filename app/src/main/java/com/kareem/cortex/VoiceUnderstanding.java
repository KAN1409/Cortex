package com.kareem.cortex;

import java.util.Locale;

/** Conservative display understanding for voice notes when local analysis only repeats the transcript. */
public final class VoiceUnderstanding {
    private VoiceUnderstanding(){}

    public static String summarize(String transcript,AnalysisResult r){
        String t=clean(transcript);
        if(t.isEmpty())return "";

        if(r!=null&&VoiceTextPresentation.materiallyDifferent(r.summary,t))
            return clean(r.summary);

        String l=t.toLowerCase(Locale.ROOT);

        if(contains(l,"للاختبار","اختبار","test","testing"))
            return "Test voice note. No explicit follow-up or action was detected.";

        if(r!=null&&!r.actions.isEmpty()){
            String action=clean(r.actions.get(0).text);
            if(!action.isEmpty())return "Actionable voice note: "+action;
        }

        if(t.contains("?")||t.contains("؟")||contains(l,"ممكن","ازاي","إزاي","ليه","what","how","why","can you"))
            return "Voice note contains a question or information request.";

        if(contains(l,"فكرني","افتكر","remind me","remember to"))
            return "Voice note contains something to remember or resurface later.";

        if(contains(l,"لازم","محتاج","محتاجه","عايز","عاوز","need to","have to","must","send","reply","call","buy","pay","book"))
            return "Voice note contains a likely request or next action.";

        return "Personal voice note captured and transcribed. No explicit action was detected.";
    }

    private static boolean contains(String s,String...xs){
        for(String x:xs)if(s.contains(x))return true;
        return false;
    }

    private static String clean(String s){
        return MixedBidiText.stripControls(s==null?"":s).replaceAll("\\s+"," ").trim();
    }
}
