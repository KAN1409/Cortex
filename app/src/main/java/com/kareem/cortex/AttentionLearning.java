package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;

/** Personal calibration over an already-valid attention candidate. */
public final class AttentionLearning {
    public static final String VERSION="attention_learning_001";
    private AttentionLearning(){}

    public static AttentionEngine.Decision apply(VaultDb db,PrimeBriefStore.Item item,AttentionEngine.Decision d){
        if(db==null||item==null||d==null||item.id<=0)return d;CognitiveStore.ensure(db);int positive=0,negative=0,snooze=0,acted=0;
        Cursor c=db.getReadableDatabase().query("feedback_events",new String[]{"event_type"},"target_type='derived' AND target_id=?",new String[]{String.valueOf(item.id)},null,null,"created_at DESC","40");
        while(c.moveToNext()){String e=n(c.getString(0));if("attention_opened".equals(e)||"confirm".equals(e))positive++;else if("attention_acted".equals(e)||"resolved_by_user".equals(e)||"complete".equals(e))acted++;else if("attention_snoozed".equals(e))snooze++;else if("attention_dismissed".equals(e)||"not_important".equals(e)||"dismiss".equals(e)||"ignore_similar".equals(e))negative++;}c.close();
        if(acted>0)return new AttentionEngine.Decision(Math.min(24,d.score),AttentionEngine.Band.QUIET,"You already acted on this, so Cortex is suppressing it unless new evidence reopens the loop.",d.urgency,d.consequence,d.responsibility,d.temporalPressure,d.openLoopPressure,d.novelty,d.confidence);
        int delta=Math.min(8,positive*2)-Math.min(22,negative*8)-Math.min(12,snooze*5);if(delta==0)return d;int score=Math.max(0,Math.min(100,d.score+delta));return new AttentionEngine.Decision(score,band(score),learningReason(d.whyNow,positive,negative,snooze),d.urgency,d.consequence,d.responsibility,d.temporalPressure,d.openLoopPressure,d.novelty,d.confidence);
    }

    public static void record(VaultDb db,long derivedId,String behavior){
        if(db==null||derivedId<=0)return;String e=n(behavior);if(!("opened".equals(e)||"acted".equals(e)||"snoozed".equals(e)||"dismissed".equals(e)))return;try{CognitiveStore.feedback(db,"derived",derivedId,"attention_"+e,new JSONObject().put("behavior",e).toString(),VERSION);}catch(Exception ignored){}
    }

    private static String learningReason(String original,int positive,int negative,int snooze){if(negative>0)return original+" Personal feedback has repeatedly reduced its attention priority.";if(snooze>0)return original+" You previously snoozed this, so Cortex is surfacing it more cautiously.";if(positive>0)return original+" Your prior interactions suggest this kind of item is useful to surface.";return original;}
    private static AttentionEngine.Band band(int s){return s>=72?AttentionEngine.Band.NOW:s>=54?AttentionEngine.Band.LATER:s>=36?AttentionEngine.Band.WATCHING:AttentionEngine.Band.QUIET;}
    private static String n(String s){return s==null?"":s.trim().toLowerCase();}
}
