package com.kareem.cortex;

import java.util.Locale;

/**
 * Dynamic, read-only "what matters now" policy for canonical Situations.
 *
 * <p>The Situation table keeps durable cognitive state. This policy deliberately does not rewrite
 * that state just because time passed. Instead it derives a current score at projection time from
 * canonical attention, confidence, temporal proximity, state, actionability and the freshness of
 * any Deep Brain judgement.</p>
 */
public final class CognitiveNowPolicyV4 {
    static final long HOUR_MS=60L*60L*1000L;
    static final long DAY_MS=24L*HOUR_MS;
    private CognitiveNowPolicyV4(){}

    public static Evaluation evaluate(String kind,String state,double canonicalAttention,double interruption,double confidence,
                                      long relevantFrom,long relevantUntil,int brainRank,long brainCreatedAt,boolean hasAction,long now){
        String k=clean(kind),s=clean(state);
        double attention=clamp01(canonicalAttention),interrupt=clamp01(interruption),conf=clamp01(confidence);
        double score=.62*attention+.10*conf+.08*interrupt;

        // Semantic importance: these are small priors, never substitutes for Evidence/Deep Brain.
        if("RISK".equals(k))score+=.13;
        else if("DEADLINE".equals(k))score+=.11;
        else if("UPCOMING_EVENT".equals(k))score+=.07;
        else if("COMMITMENT".equals(k))score+=.05;
        else if("FOLLOW_UP".equals(k))score+=.025;

        if("RELEVANT".equals(s)||"SURFACED".equals(s))score+=.045;
        else if("WAITING".equals(s))score-=.055;
        else if("DEFERRED".equals(s))score-=.42;

        if(hasAction)score+=.045;

        boolean expired=false;
        if(relevantUntil>0){
            long delta=relevantUntil-now;
            if(delta>=0){
                if(delta<=2L*HOUR_MS)score+=.20;
                else if(delta<=24L*HOUR_MS)score+=.14;
                else if(delta<=3L*DAY_MS)score+=.07;
            }else{
                long overdue=-delta;
                if("DEADLINE".equals(k)){
                    if(overdue<=DAY_MS)score+=.16;
                    else if(overdue<=3L*DAY_MS)score+=.08;
                    else if(overdue>7L*DAY_MS)score-=.18;
                }else if("UPCOMING_EVENT".equals(k)){
                    if(overdue<=3L*HOUR_MS)score+=.025;
                    else if(overdue>12L*HOUR_MS){score-=.32;expired=true;}
                }else if(overdue>2L*DAY_MS){score-=.18;expired=true;}
            }
        }else if(relevantFrom>0){
            long age=Math.max(0,now-relevantFrom);
            if(age<=12L*HOUR_MS)score+=.045;
            else if(age>14L*DAY_MS)score-=.16;
            else if(age>7L*DAY_MS)score-=.08;
        }

        double brainFreshness=brainFreshness(brainCreatedAt,now);
        if(brainRank>0&&brainFreshness>0&&!("DEFERRED".equals(s))){
            double rankStrength=brainRank==1?.24:brainRank==2?.20:brainRank==3?.16:brainRank<=5?.11:.07;
            score+=rankStrength*brainFreshness;
        }

        // Low-confidence local detections should not crowd out better grounded situations.
        if(conf<.55)score-=.06;
        score=clamp01(score);

        boolean eligible=!("DEFERRED".equals(s));
        if(expired&&brainFreshness<.65)eligible=false;
        boolean currentBrain=brainRank>0&&brainFreshness>=.65&&eligible;
        return new Evaluation(score,eligible,currentBrain,brainFreshness);
    }

    static double brainFreshness(long createdAt,long now){
        if(createdAt<=0||now<=0||createdAt>now+5L*60L*1000L)return 0;
        long age=now-createdAt;
        if(age<=DAY_MS)return 1.0;
        if(age<=3L*DAY_MS)return .65;
        if(age<=7L*DAY_MS)return .30;
        return 0;
    }

    private static String clean(String s){return s==null?"":s.trim().toUpperCase(Locale.ROOT);}
    private static double clamp01(double x){if(Double.isNaN(x)||Double.isInfinite(x))return 0;return Math.max(0,Math.min(1,x));}

    public static final class Evaluation{
        public final double nowScore,brainFreshness;
        public final boolean eligible,currentDeepBrain;
        Evaluation(double score,boolean eligible,boolean currentDeepBrain,double brainFreshness){
            this.nowScore=score;this.eligible=eligible;this.currentDeepBrain=currentDeepBrain;this.brainFreshness=brainFreshness;
        }
    }
}
