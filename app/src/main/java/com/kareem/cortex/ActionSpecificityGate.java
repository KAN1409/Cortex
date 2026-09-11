package com.kareem.cortex;

import java.util.Locale;

/**
 * Cheap deterministic action-quality gate.
 * Imperative grammar alone is not enough to make an item actionable.
 */
public final class ActionSpecificityGate {
    public static final String VERSION="action_specificity_gate_001";

    public static final class Result {
        public final double specificity;
        public final boolean eligible;
        public final String reason;
        Result(double specificity,boolean eligible,String reason){
            this.specificity=Math.max(0,Math.min(1,specificity));
            this.eligible=eligible;
            this.reason=reason==null?"":reason;
        }
    }

    private ActionSpecificityGate(){}

    public static Result evaluate(String semanticType,String intent,String subject,String summary,
                                  boolean explicitRequest,boolean linkedCommitment){
        String type=norm(semanticType), in=norm(intent), s=norm(subject), body=norm(summary);
        String all=(type+" "+in+" "+s+" "+body).trim();

        if(all.isEmpty())return new Result(0,false,"empty candidate");

        double score=0;
        if(explicitRequest)score+=.28;
        if(linkedCommitment)score+=.22;
        if(s.length()>=4)score+=.18;
        if(body.length()>=24)score+=.12;
        if(hasConcreteObject(all))score+=.10;
        if(hasTimeAnchor(all))score+=.10;

        boolean generic=genericImperative(all);
        if(generic)score-=.35;

        boolean eligible=score>=.28 || explicitRequest || linkedCommitment;
        if(generic&&!explicitRequest&&!linkedCommitment)eligible=false;

        String reason=generic?"generic action without grounded object":
                (eligible?"specific enough for attention triage":"insufficient action specificity");
        return new Result(score,eligible,reason);
    }

    private static boolean genericImperative(String x){
        return x.equals("check important info") ||
                x.equals("check important info.") ||
                x.equals("review important information") ||
                x.equals("take action") ||
                x.equals("do this") ||
                x.equals("follow up") ||
                x.matches("^(check|review|see|open|look at)\\s+(important\\s+)?(info|information|details)\\.?$");
    }

    private static boolean hasConcreteObject(String x){
        String[] tokens=x.split("[^\\p{L}\\p{N}@._-]+");
        int meaningful=0;
        for(String t:tokens){
            if(t.length()<3)continue;
            if(STOP.contains(t))continue;
            meaningful++;
        }
        return meaningful>=3;
    }

    private static boolean hasTimeAnchor(String x){
        return x.matches(".*\\b(today|tomorrow|tonight|due|deadline|am|pm|\\d{1,2}:\\d{2})\\b.*") ||
                x.contains("النهارده")||x.contains("بكره")||x.contains("بكرة")||
                x.contains("موعد")||x.contains("ميعاد")||x.contains("قبل ");
    }

    private static final java.util.Set<String> STOP=new java.util.HashSet<>(java.util.Arrays.asList(
            "check","important","info","information","details","please","this","that","with","from",
            "action","review","open","look","see","the","and","for","your","you","need",
            "راجع","مهم","مهمة","معلومات","تفاصيل","شوف","افتح","اعمل","لازم"));

    private static String norm(String s){
        return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
    }
}
