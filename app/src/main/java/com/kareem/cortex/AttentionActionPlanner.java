package com.kareem.cortex;

import java.util.*;

/** Plans executable help separately from reasoning. Planning never performs external mutation. */
public final class AttentionActionPlanner {
    private AttentionActionPlanner(){}
    public enum Risk { SAFE, CONFIRMATION_REQUIRED, SENSITIVE, BLOCKED }
    public enum Type { OPEN_SOURCE, OPEN_CONVERSATION, DRAFT_REPLY, SEND_ITEM, REVIEW_DECISION, CREATE_REMINDER, MARK_RESOLVED, ASK_BRAIN }
    public static final class Proposal {
        public final Type type;public final String label,reason,expectedOutcome;public final Risk risk;public final double confidence;
        Proposal(Type type,String label,String reason,String expectedOutcome,Risk risk,double confidence){this.type=type;this.label=n(label);this.reason=n(reason);this.expectedOutcome=n(expectedOutcome);this.risk=risk;this.confidence=Math.max(0,Math.min(1,confidence));}
    }

    public static List<Proposal> plan(OpenLoopStore.Loop loop,AttentionModels.Assessment assessment){ArrayList<Proposal> out=new ArrayList<>();if(loop==null||assessment==null)return out;if(loop.anchorSignalId>0)out.add(new Proposal(Type.OPEN_SOURCE,"Open source","Review the evidence that created this attention item","Open grounded source context",Risk.SAFE,1));
        if(assessment.actionability==AttentionModels.Actionability.REPLY)out.add(new Proposal(Type.DRAFT_REPLY,"Draft reply","A response appears to be the next useful step","Prepare a reply without sending it",Risk.CONFIRMATION_REQUIRED,Math.max(.65,assessment.confidence)));
        else if(assessment.actionability==AttentionModels.Actionability.SEND)out.add(new Proposal(Type.SEND_ITEM,"Send requested item","The unresolved request appears to ask for an item or file","Open the relevant context and prepare the requested send action",Risk.CONFIRMATION_REQUIRED,Math.max(.60,assessment.confidence)));
        else if(assessment.actionability==AttentionModels.Actionability.REVIEW)out.add(new Proposal(Type.CREATE_REMINDER,"Set follow-up","This item is waiting rather than actionable now","Prepare a future follow-up reminder",Risk.CONFIRMATION_REQUIRED,assessment.confidence));
        else if(assessment.actionability==AttentionModels.Actionability.DECIDE)out.add(new Proposal(Type.REVIEW_DECISION,"Review decision","A decision remains unresolved","Open the grounded context and compare the available choice before committing",Risk.SAFE,assessment.confidence));
        out.add(new Proposal(Type.ASK_BRAIN,"Ask Brain","More context may improve the next-step choice","Generate a grounded, executable suggestion",Risk.SAFE,assessment.confidence));out.add(new Proposal(Type.MARK_RESOLVED,"Mark resolved","Use only when the real-world obligation is already complete","Close this open loop",Risk.SAFE,1));return out;}
    private static String n(String s){return s==null?"":s.trim();}
}
