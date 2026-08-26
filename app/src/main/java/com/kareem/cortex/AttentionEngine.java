package com.kareem.cortex;

import java.util.Locale;

/**
 * Dynamic attention layer. Relevance says what is worth retaining; Attention says
 * what deserves the user's limited attention now.
 *
 * This is intentionally deterministic and explainable. Model adjudication can refine
 * ambiguous candidates later, but Today should never depend on an opaque score alone.
 */
public final class AttentionEngine {
    private AttentionEngine(){}

    public enum Band { NOW, LATER, WATCHING, QUIET }

    public static final class Decision {
        public final int score;
        public final Band band;
        public final String whyNow;
        public final double urgency,consequence,responsibility,temporalPressure,openLoopPressure,novelty,confidence;
        Decision(int score,Band band,String whyNow,double urgency,double consequence,double responsibility,double temporalPressure,double openLoopPressure,double novelty,double confidence){
            this.score=score;this.band=band;this.whyNow=whyNow;this.urgency=urgency;this.consequence=consequence;this.responsibility=responsibility;this.temporalPressure=temporalPressure;this.openLoopPressure=openLoopPressure;this.novelty=novelty;this.confidence=confidence;
        }
    }

    public static Decision evaluate(PrimeBriefStore.Item x,long now){
        if(x==null)return quiet("No usable attention candidate");
        String kind=n(x.kind).toUpperCase(Locale.ROOT), text=norm(x.title+" "+x.body);
        long age=Math.max(0,now-x.updatedAt);
        double hours=age/3600000.0;

        double urgency=baseUrgency(kind,text);
        double consequence=clamp(x.importance/100.0);
        double responsibility=responsibility(kind,text);
        double temporal=temporalPressure(text,hours);
        double loop=openLoopPressure(kind,hours);
        double novelty=novelty(hours);
        double certainty=clamp(x.confidence);

        // Importance is deliberately only one input. Time, ownership and unresolved-state
        // pressure can raise or lower the current attention independently.
        double raw=
            0.20*urgency+
            0.17*consequence+
            0.18*responsibility+
            0.18*temporal+
            0.14*loop+
            0.07*novelty+
            0.06*certainty;

        // Low-confidence guesses cannot dominate Today merely because an old classifier
        // assigned them high importance.
        if(certainty<0.55)raw-=0.10;
        if(kind.equals("DECISION")&&certainty>=0.75)raw+=0.04;
        if(kind.equals("ACTION")&&responsibility>=0.80)raw+=0.06;
        if(kind.equals("WAITING")&&temporal>=0.70)raw+=0.06;

        int score=(int)Math.round(clamp(raw)*100.0);
        Band band=score>=72?Band.NOW:score>=54?Band.LATER:score>=36?Band.WATCHING:Band.QUIET;
        return new Decision(score,band,reason(kind,text,hours,urgency,responsibility,temporal,loop),urgency,consequence,responsibility,temporal,loop,novelty,certainty);
    }

    private static double baseUrgency(String kind,String t){
        if(has(t,"urgent","asap","today","tonight","now","immediately","ضروري","النهارده","اليوم","دلوقتي","حالاً","حالا"))return 0.95;
        if(has(t,"tomorrow","بكره","بكرة"))return 0.75;
        if(kind.equals("ACTION"))return 0.66;
        if(kind.equals("WAITING"))return 0.52;
        if(kind.equals("DECISION"))return 0.58;
        return 0.32;
    }

    private static double responsibility(String kind,String t){
        if(kind.equals("ACTION")){
            if(has(t,"need you","please send","please review","please confirm","محتاج منك","ابعتلي","ابعت لي","راجع","أكد","اكد"))return 0.95;
            return 0.80;
        }
        if(kind.equals("WAITING"))return 0.28;
        if(kind.equals("DECISION"))return 0.58;
        return 0.25;
    }

    private static double temporalPressure(String t,double ageHours){
        if(has(t,"today","tonight","النهارده","اليوم","دلوقتي"))return ageHours<=24?0.95:1.0;
        if(has(t,"tomorrow","بكره","بكرة"))return ageHours<=24?0.72:0.88;
        if(has(t,"overdue","late","متأخر","متاخر","فات معاده","فات موعده"))return 1.0;
        if(ageHours<4)return 0.36;
        if(ageHours<24)return 0.44;
        if(ageHours<72)return 0.58;
        if(ageHours<168)return 0.68;
        return 0.48; // old unresolved items decay instead of staying permanently urgent
    }

    private static double openLoopPressure(String kind,double ageHours){
        if(!(kind.equals("ACTION")||kind.equals("WAITING")||kind.equals("DECISION")))return 0.18;
        if(ageHours<6)return 0.35;
        if(ageHours<24)return 0.48;
        if(ageHours<72)return 0.66;
        if(ageHours<168)return 0.78;
        return 0.58;
    }

    private static double novelty(double ageHours){
        if(ageHours<2)return 0.90;if(ageHours<8)return 0.72;if(ageHours<24)return 0.55;if(ageHours<72)return 0.38;return 0.18;
    }

    private static String reason(String kind,String t,double ageHours,double urgency,double responsibility,double temporal,double loop){
        if(kind.equals("ACTION")&&responsibility>=0.80&&temporal>=0.70)return "You appear responsible for this and its timing is becoming important.";
        if(kind.equals("WAITING")&&temporal>=0.70)return "You are waiting on something unresolved and the follow-up window is getting closer.";
        if(kind.equals("DECISION")&&temporal>=0.65)return "An unresolved decision is becoming time-sensitive.";
        if(has(t,"today","tonight","النهارده","اليوم"))return "This is tied to today, so its attention value has increased.";
        if(loop>=0.70)return "This open loop has remained unresolved long enough to deserve another look.";
        if(ageHours<4)return "This is new, but Cortex has not seen enough pressure yet to treat it as urgent.";
        if(urgency<0.45)return "Relevant context, but there is weak evidence that it needs action now.";
        return "Current relevance, timing and unresolved-state pressure make this worth surfacing.";
    }

    private static Decision quiet(String why){return new Decision(0,Band.QUIET,why,0,0,0,0,0,0,0);}
    private static String norm(String s){return MasterRelevanceFilter.ruleNorm(s==null?"":s);}
    private static boolean has(String s,String... xs){for(String x:xs)if(s.contains(norm(x)))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
}
