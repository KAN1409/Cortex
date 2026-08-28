package com.kareem.cortex;

import java.util.ArrayList;
import java.util.List;

/** Strict semantic boundary between model output and Cortex persistence. */
public final class CognitiveResultValidator {
    private CognitiveResultValidator(){}

    public static Outcome validate(CognitiveInput input,CognitiveResult raw){
        if(raw==null||raw.disposition==null)return Outcome.error("MISSING_RESULT");
        if(raw.items.size()>5)return Outcome.error("TOO_MANY_ITEMS");
        if(raw.disposition!=CognitiveDisposition.DERIVE&&!raw.items.isEmpty())return Outcome.error("NON_DERIVE_HAS_ITEMS");
        if(raw.disposition==CognitiveDisposition.DERIVE&&raw.items.isEmpty())return Outcome.error("DERIVE_EMPTY");

        ArrayList<CognitiveItem> fixed=new ArrayList<>();String evidence=textEvidence(input);
        for(CognitiveItem item:raw.items){
            if(item==null||item.kind==null)return Outcome.error("MISSING_KIND");
            String summary=item.summary==null?"":item.summary.trim();if(summary.isEmpty()||summary.length()>240)return Outcome.error("INVALID_SUMMARY");
            boolean user=item.requiresUserAction,follow=item.requiresFollowUp,extract=item.requiresContentExtraction;
            if(item.kind==CognitiveKind.ACTION)user=true;
            if(item.kind==CognitiveKind.WAITING)follow=true;
            if(item.kind==CognitiveKind.CONTENT&&contentCue(evidence))extract=true;
            long due=item.dueAt;
            if(due>0&&(!timeCue(evidence)||!plausible(due,input==null?0:input.occurredAt)))due=0;
            fixed.add(new CognitiveItem(item.kind,summary,item.importance,item.urgency,item.person,due,user,follow,extract));
        }
        return Outcome.ok(new CognitiveResult(raw.disposition,raw.confidence,raw.reason,fixed));
    }

    private static boolean plausible(long due,long occurred){long anchor=occurred>0?occurred:System.currentTimeMillis();return due>=anchor-7L*24L*60L*60L*1000L&&due<=anchor+730L*24L*60L*60L*1000L;}
    private static boolean timeCue(String s){String x=MasterRelevanceFilter.ruleNorm(s);return x.matches(".*\\b\\d{1,2}[:/]\\d{1,2}.*")||x.matches(".*\\b\\d{1,2}\\s*(am|pm).*" )||has(x,"today","tomorrow","tonight","monday","tuesday","wednesday","thursday","friday","saturday","sunday","بكره","بكرة","غدا","غداً","النهارده","اليوم","الليله","الليلة","الساعة","الساعه","موعد");}
    private static boolean contentCue(String s){String x=MasterRelevanceFilter.ruleNorm(s);return has(x,"voice note","voice message","audio message","reel","document","file","photo","image","video","link","لينك","ملف","صوره","صورة","فيديو","فويس");}
    private static String textEvidence(CognitiveInput input){if(input==null)return"";StringBuilder b=new StringBuilder(input.latestText);for(String x:input.recentContext)b.append('\n').append(x);return b.toString();}
    private static boolean has(String s,String...xs){for(String x:xs)if(s.contains(MasterRelevanceFilter.ruleNorm(x)))return true;return false;}

    public static final class Outcome{
        public final CognitiveResult result;public final String error;
        private Outcome(CognitiveResult r,String e){result=r;error=e;}
        public boolean valid(){return result!=null;}
        static Outcome ok(CognitiveResult r){return new Outcome(r,"");}
        static Outcome error(String e){return new Outcome(null,e==null?"":e);}
    }
}
