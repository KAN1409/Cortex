package com.kareem.cortex;

import java.util.Locale;

/**
 * Deterministic local "next move" layer for the active Context.
 *
 * This engine never calls a model and never manufactures an external action. It only turns an
 * already-grounded Context snapshot or linked durable obligation into a resumable UI affordance.
 * Execution remains elsewhere and keeps the normal preview/approval boundary.
 */
public final class ContextActionEngine {
    public static final String RESUME="RESUME",REVIEW_ACTION="REVIEW_ACTION",CHECK_WAITING="CHECK_WAITING",REVIEW_DECISION="REVIEW_DECISION",NONE="NONE";

    public static final class Move {
        public final long contextId,derivedId;
        public final String kind,label,detail,provenance;
        public final double confidence;
        Move(long contextId,long derivedId,String kind,String label,String detail,String provenance,double confidence){this.contextId=contextId;this.derivedId=derivedId;this.kind=n(kind);this.label=n(label);this.detail=n(detail);this.provenance=n(provenance);this.confidence=clamp(confidence);}
        public boolean available(){return contextId>0&&!NONE.equals(kind)&&!label.isEmpty();}
        /** Safe local query that ContextAskEngine resolves before any external provider path. */
        public String localBrainQuery(){return"Resume my work";}
    }

    private ContextActionEngine(){}

    public static Move primary(VaultDb db,long contextId){
        ContextOpenLoopResolver.State s=ContextOpenLoopResolver.resolve(db,contextId);if(s==null||contextId<=0)return none(contextId);
        String detail=ContextOpenLoopResolver.resumeLabel(s),provenance=s.provenance();
        if(!s.nextStep.isEmpty())return new Move(contextId,s.derivedId,RESUME,"Resume from here",detail,provenance,Math.max(.72,s.confidence));
        if(s.derivedId>0){
            if("WAITING".equals(s.kind))return new Move(contextId,s.derivedId,CHECK_WAITING,"Review what you’re waiting on",detail,provenance,Math.max(.68,s.confidence));
            if("DECISION".equals(s.kind))return new Move(contextId,s.derivedId,REVIEW_DECISION,"Revisit the pending decision",detail,provenance,Math.max(.68,s.confidence));
            if("ACTION".equals(s.kind))return new Move(contextId,s.derivedId,REVIEW_ACTION,"Continue the open action",detail,provenance,Math.max(.68,s.confidence));
        }
        if(!s.openLoop.isEmpty())return new Move(contextId,0,RESUME,"Resume the open loop",detail,provenance,Math.max(.72,s.confidence));
        return none(contextId);
    }

    public static String kindLabel(Move m){if(m==null)return"";if(CHECK_WAITING.equals(m.kind))return"WAITING";if(REVIEW_DECISION.equals(m.kind))return"DECISION";if(REVIEW_ACTION.equals(m.kind))return"ACTION";if(RESUME.equals(m.kind))return"RESUME";return m.kind.toUpperCase(Locale.ROOT);}
    private static Move none(long id){return new Move(Math.max(0,id),0,NONE,"","","",0);}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static String n(String s){return s==null?"":s.trim();}
}
