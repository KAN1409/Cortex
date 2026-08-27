package com.kareem.cortex;

/** Re-evaluates unresolved loops as time passes, without requiring new evidence. */
public final class AttentionMaintenance {
    private AttentionMaintenance(){}
    public static void refresh(VaultDb db){
        if(db==null)return;CortexAttentionSchema.ensure(db);long now=System.currentTimeMillis();
        for(OpenLoopStore.Loop loop:OpenLoopStore.active(db,200)){
            long snoozed=AttentionFeedbackStore.snoozedUntil(db,loop.id);if(snoozed>now){AttentionFeedStore.removeLoop(db,loop.id);continue;}
            String target=loop.state;if(loop.dueAt>0&&loop.dueAt<=now)target=OpenLoopStore.OVERDUE;else if(loop.followUpAt>0&&loop.followUpAt<=now)target=OpenLoopStore.DUE;else if(OpenLoopStore.DUE.equals(loop.state)||OpenLoopStore.OVERDUE.equals(loop.state))target=OpenLoopStore.OPEN;
            if(!target.equals(loop.state))OpenLoopStore.setState(db,loop.id,target);
            OpenLoopStore.Loop current=OpenLoopStore.get(db,loop.id);if(current!=null&&current.threadId>0)CortexAttentionOrchestrator.reevaluateThread(db,current.threadId);
        }
    }
}
