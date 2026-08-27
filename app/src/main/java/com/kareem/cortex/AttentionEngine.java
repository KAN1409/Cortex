package com.kareem.cortex;

/** Deterministic scoring keeps importance separate from interruption timing. */
public final class AttentionEngine {
    public static final String VERSION="attention_engine_001";
    private AttentionEngine(){}

    public static AttentionModels.Decision evaluate(OpenLoopStore.Loop loop,int importanceHint,long now,double interruptionCost,double contextRelevance){
        if(loop==null)return null;
        double urgency=urgency(loop,now),importance=clamp(importanceHint/100d),actionRequired=(OpenLoopStore.WAITING.equals(loop.state)?0.25:1.0),commitment=loop.userCommitted?1.0:0.82,unresolved=1.0,recency=recency(loop.updatedAt,now),novelty=loop.createdAt==loop.updatedAt?0.85:0.45;
        AttentionModels.Dimensions d=new AttentionModels.Dimensions(urgency,importance,actionRequired,commitment,unresolved,contextRelevance,recency,novelty,interruptionCost);
        double raw=d.urgency*.20+d.importance*.16+d.actionRequired*.18+d.commitmentStrength*.14+d.unresolvedness*.12+d.contextRelevance*.10+d.recency*.05+d.novelty*.05;
        double score=raw*100d,interrupt=score*(1d-d.interruptionCost);AttentionModels.Level level=score>=85?AttentionModels.Level.CRITICAL:score>=70?AttentionModels.Level.HIGH:score>=50?AttentionModels.Level.MEDIUM:score>=25?AttentionModels.Level.LOW:AttentionModels.Level.NONE;
        AttentionModels.Actionability action=actionability(loop);String reason=reason(loop,urgency),suggestion=suggestion(loop,action);AttentionModels.Assessment a=new AttentionModels.Assessment(score,interrupt,loop.confidence,level,d,reason,suggestion,action,urgency>=.70,now);
        AttentionModels.DeliveryMode delivery=score>=85&&interrupt>=70?AttentionModels.DeliveryMode.NOTIFICATION:score>=70?AttentionModels.DeliveryMode.HIGHLIGHT:score>=45?AttentionModels.DeliveryMode.FEED:AttentionModels.DeliveryMode.SILENT_MEMORY;
        boolean surface=score>=45&&!OpenLoopStore.WAITING.equals(loop.state);double rank=score+(action!=AttentionModels.Actionability.NONE?5:0)+(loop.userCommitted?6:0)+(urgency>=.70?8:0);return new AttentionModels.Decision(a,delivery,surface,rank);
    }

    private static AttentionModels.Actionability actionability(OpenLoopStore.Loop l){if(OpenLoopStore.WAITING.equals(l.state))return AttentionModels.Actionability.REVIEW;if("INCOMING_REQUEST".equals(l.type)){String s=l.subject.toLowerCase();if(s.contains("send")||s.contains("ابعت")||s.contains("ملف")||s.contains("plan")||s.contains("بلان")||s.contains("pdf"))return AttentionModels.Actionability.SEND;return AttentionModels.Actionability.REPLY;}return AttentionModels.Actionability.OPEN;}
    private static String suggestion(OpenLoopStore.Loop l,AttentionModels.Actionability a){switch(a){case SEND:return"Open conversation and send the requested item";case REPLY:return"Open conversation and reply";case REVIEW:return"Review when the other side responds";default:return"Open source context";}}
    private static String reason(OpenLoopStore.Loop l,double urgency){if(l.userCommitted)return"You committed to this and it is still unresolved";if(urgency>=.85)return"This unresolved item is due now";if("INCOMING_REQUEST".equals(l.type))return"Someone requested an action and it is still unresolved";return"Unresolved item still needs attention";}
    private static double urgency(OpenLoopStore.Loop l,long now){if(l.dueAt>0){long r=l.dueAt-now;if(r<=0)return 1;if(r<=30*60_000L)return .95;if(r<=2*60*60_000L)return .85;if(r<=6*60*60_000L)return .70;if(r<=24*60*60_000L)return .55;if(r<=3*24*60*60_000L)return .35;return .15;}if(l.followUpAt>0&&l.followUpAt<=now)return .80;return "INCOMING_REQUEST".equals(l.type)?.55:.25;}
    private static double recency(long updated,long now){long age=Math.max(0,now-updated);if(age<=15*60_000L)return 1;if(age<=60*60_000L)return .9;if(age<=3*60*60_000L)return .75;if(age<=12*60*60_000L)return .55;if(age<=24*60*60_000L)return .4;if(age<=3*24*60*60_000L)return .25;return .1;}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
}
