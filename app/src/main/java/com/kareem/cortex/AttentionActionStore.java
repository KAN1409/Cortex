package com.kareem.cortex;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Persists planner output so UI and execution layers consume the same action contract. */
public final class AttentionActionStore {
    public static final String VERSION="attention_actions_001";
    private AttentionActionStore(){}

    public static void replaceForLoop(VaultDb db,long loopId,List<AttentionActionPlanner.Proposal> proposals){if(db==null||loopId<=0)return;CortexAttentionSchema.ensure(db);SQLiteDatabase sql=db.getWritableDatabase();long now=System.currentTimeMillis();sql.beginTransaction();try{sql.delete("attention_action_proposals","entity_type='open_loop' AND entity_id=? AND status='AVAILABLE'",new String[]{String.valueOf(loopId)});if(proposals!=null)for(AttentionActionPlanner.Proposal p:proposals){ContentValues v=new ContentValues();v.put("entity_type","open_loop");v.put("entity_id",loopId);v.put("action_type",p.type.name());v.put("label",p.label);v.put("reason",p.reason);v.put("expected_outcome",p.expectedOutcome);v.put("risk",p.risk.name());v.put("confidence",p.confidence);v.put("status","AVAILABLE");v.put("planner_version",VERSION);v.put("created_at",now);v.put("updated_at",now);sql.insertWithOnConflict("attention_action_proposals",null,v,SQLiteDatabase.CONFLICT_REPLACE);}sql.setTransactionSuccessful();}finally{sql.endTransaction();}}
    public static void closeForLoop(VaultDb db,long loopId,String status){if(db==null||loopId<=0)return;ContentValues v=new ContentValues();v.put("status",status==null?"CLOSED":status);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("attention_action_proposals",v,"entity_type='open_loop' AND entity_id=? AND status='AVAILABLE'",new String[]{String.valueOf(loopId)});}
}
