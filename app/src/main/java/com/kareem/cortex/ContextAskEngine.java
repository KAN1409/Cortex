package com.kareem.cortex;

import java.util.*;

/** Deterministic local answers for questions about the user's current/resumable context. */
public final class ContextAskEngine {
    private ContextAskEngine(){}
    public static GroundedAnswer tryAnswer(VaultDb db,String question){
        if(db==null||!matches(question))return null;ContextPacketBuilder.Packet p=ContextPacketBuilder.buildLocal(db,420);if(!p.available())return null;ContextActionEngine.Move move=ContextActionEngine.primary(db,p.contextId);StringBuilder a=new StringBuilder();a.append("You’re currently in: ").append(p.title).append('.');if(!p.goal.isEmpty())a.append("\n\nGoal: ").append(p.goal);if(!p.currentActivity.isEmpty())a.append("\n\nCurrent activity: ").append(p.currentActivity);if(!p.openLoops.isEmpty())a.append("\n\nOpen loop: ").append(p.openLoops);if(!p.nextStep.isEmpty())a.append("\n\nNext: ").append(p.nextStep);else if(move.available()&&!move.detail.isEmpty())a.append("\n\nResume from: ").append(move.detail);if((!p.openLoops.isEmpty()||!p.nextStep.isEmpty())&&!p.resumeProvenance.isEmpty())a.append("\nGrounding: ").append(p.resumeProvenance).append(" (local only; no model inference)");if(!p.background.isEmpty()){a.append("\n\nBackground / suspended: ");for(int i=0;i<p.background.size()&&i<3;i++){if(i>0)a.append(" · ");a.append(p.background.get(i));}}if(p.openLoops.isEmpty()&&p.nextStep.isEmpty()&&!move.available())a.append("\n\nCortex does not have a grounded open loop or next step for this context yet, so it will not borrow one from another context.");return new GroundedAnswer(question,a.toString(),Math.max(.72,p.confidence),new ArrayList<>(),lines(p.openLoops),new ArrayList<>());
    }
    private static boolean matches(String q){String n=LocalSemanticEmbedder.norm(q==null?"":q);String[] xs={"current context","what am i doing","what was i doing","what was i working on","where did i leave off","where was i","resume my work","continue where i left off","continue what i was doing","what should i continue","what am i working on","context now","انا بعمل ايه","أنا بعمل إيه","كنت بعمل ايه","كنت بعمل إيه","كنت شغال على ايه","كنت شغال على إيه","اكمل منين","أكمل منين","واقف فين","كنت واقف فين","السياق الحالي","انا شغال على ايه","أنا شغال على إيه"};for(String x:xs)if(n.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static ArrayList<String> lines(String s){ArrayList<String> out=new ArrayList<>();if(s==null||s.trim().isEmpty())return out;for(String x:s.split("\\|")){String t=x.trim();if(!t.isEmpty())out.add(t);}return out;}
}
