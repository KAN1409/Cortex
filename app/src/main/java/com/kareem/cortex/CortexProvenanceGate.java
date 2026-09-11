package com.kareem.cortex;

import java.util.Locale;

/**
 * Non-destructive provenance firewall for attention candidates.
 *
 * Evidence is never deleted or rewritten. This gate only decides whether an already-grounded
 * semantic event is allowed to compete for user attention.
 */
public final class CortexProvenanceGate {
    public static final String VERSION="cortex_provenance_gate_001";

    public enum Authority {
        EXTERNAL_EVIDENCE,
        USER_AUTHORED,
        CORTEX_DERIVED,
        CORTEX_INTERNAL
    }

    public static final class Result {
        public final Authority authority;
        public final boolean attentionEligible;
        public final String reason;
        Result(Authority authority,boolean attentionEligible,String reason){
            this.authority=authority;
            this.attentionEligible=attentionEligible;
            this.reason=reason==null?"":reason;
        }
    }

    private CortexProvenanceGate(){}

    public static Result evaluate(String sourceType,String sourceKey,String eventType,
                                  String technicalType,String title,String body,
                                  String semanticType,String intent){
        String source=norm(sourceType+" "+sourceKey+" "+eventType+" "+technicalType);
        String text=norm(title+" "+body+" "+semanticType+" "+intent);

        boolean directUser=contains(source,
                "manual","quick_capture","android_share","user_input","typed","voice_manual");
        if(directUser)return new Result(Authority.USER_AUTHORED,true,"direct user-authored evidence");

        boolean cortexSource=contains(source,
                "com.kareem.cortex","cortex_internal","cortex-generated","cortex_generated",
                "semantic_bridge","cognitive_shadow","teacher","debug","diagnostic");
        boolean internalText=contains(text,
                "cortex is processing",
                "notification hints are evidence",
                "call event",
                "brain memory",
                "semantic waiting",
                "media queued",
                "judged now",
                "legacy shadow",
                "what deserves your attention right now",
                "ask brain to interpret",
                "start semantic index",
                "open knowledge",
                "check important info");

        if(cortexSource && internalText)
            return new Result(Authority.CORTEX_INTERNAL,false,
                    "self-generated Cortex diagnostic/UI content cannot become attention");

        boolean picBrain=contains(source,"picbrain","knowledge_v2","screen_understanding","screen_understand");
        if(picBrain && internalText)
            return new Result(Authority.CORTEX_INTERNAL,false,
                    "PicBrain captured Cortex/PicBrain internal output");

        boolean derived=contains(source,"derived","model","semantic","knowledge_v2","picbrain");
        if(derived)
            return new Result(Authority.CORTEX_DERIVED,true,
                    "derived evidence remains eligible only after quality gates");

        return new Result(Authority.EXTERNAL_EVIDENCE,true,"external grounded evidence");
    }

    public static boolean looksSelfReferential(String source,String title,String body){
        return !evaluate("",source,"","",title,body,"","").attentionEligible;
    }

    private static boolean contains(String s,String...xs){
        for(String x:xs)if(s.contains(x))return true;
        return false;
    }
    private static String norm(String s){
        return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
    }
}
