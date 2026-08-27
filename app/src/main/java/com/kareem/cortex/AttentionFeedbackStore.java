package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Explicit/implicit attention feedback. Snooze means relevant, but not now; dismiss means suppress this occurrence. */
public final class AttentionFeedbackStore {
    private AttentionFeedbackStore(){}

    public static void opened(VaultDb db,long loopId){mutate(db,loopId,"opened",0,false);}
    public static void acted(VaultDb db,long loopId){mutate(db,loopId,"acted",0,false);}
    public static void snooze(VaultDb db,long loopId,long until){mutate(db,loopId,"snoozed",Math.max(System.currentTimeMillis(),until),false);AttentionFeedStore.removeLoop(db,loopId);}
    public static void dismiss(VaultDb db,long loopId){mutate(db,loopId,"dismissed",0,true);OpenLoopStore.dismiss(db,loopId);AttentionFeedStore.removeLoop(db,loopId);}

    public static long snoozedUntil(VaultDb db,long loopId){CortexAttentionSchema.ensure(db);Cursor c=db.getReadableDatabase().query("attention_history",new String[]{"snoozed_until"},"entity_type='open_loop' AND entity_id=?",new String[]{String.valueOf(loopId)},null,null,null,"1");long x=c.moveToFirst()?c.getLong(0):0;c.close();return x;}

    private static void mutate(VaultDb db,long loopId,String event,long snooze,boolean dismissed){if(db==null||loopId<=0)return;CortexAttentionSchema.ensure(db);SQLiteDatabase sql=db.getWritableDatabase();long now=System.currentTimeMillis();Cursor c=sql.query("attention_history",new String[]{"first_surfaced_at","last_surfaced_at","surface_count","opened_count","dismissed_count","snoozed_until","last_action_at"},"entity_type='open_loop' AND entity_id=?",new String[]{String.valueOf(loopId)},null,null,null,"1");long first=0,last=0,oldSnooze=0,lastAction=0;int surfaces=0,opened=0,dismissCount=0;if(c.moveToFirst()){first=c.getLong(0);last=c.getLong(1);surfaces=c.getInt(2);opened=c.getInt(3);dismissCount=c.getInt(4);oldSnooze=c.getLong(5);lastAction=c.getLong(6);}c.close();ContentValues v=new ContentValues();v.put("entity_type","open_loop");v.put("entity_id",loopId);v.put("first_surfaced_at",first);v.put("last_surfaced_at",last);v.put("surface_count",surfaces);v.put("opened_count",opened+("opened".equals(event)?1:0));v.put("dismissed_count",dismissCount+(dismissed?1:0));v.put("snoozed_until",snooze>0?snooze:oldSnooze);v.put("last_action_at","acted".equals(event)?now:lastAction);sql.insertWithOnConflict("attention_history",null,v,SQLiteDatabase.CONFLICT_REPLACE);try{CognitiveStore.feedback(db,"open_loop",loopId,event,new JSONObject().put("snoozed_until",snooze).toString(),AttentionEngine.VERSION);}catch(Throwable ignored){}
    }
}
