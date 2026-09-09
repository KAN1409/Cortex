package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Incrementally rebuilds stateful identity/correlation/projection tables from immutable UE evidence. */
public final class StatefulMeaningRebuilder {
    public static final String PROCESSOR_VERSION="stateful_rebuilder_001";
    private StatefulMeaningRebuilder(){}

    public static int run(VaultDb vault,int maxRows){
        if(vault==null)return 0;SQLiteDatabase db=vault.getWritableDatabase();StatefulMeaningStore.ensure(db);int budget=Math.max(1,Math.min(500,maxRows)),done=0;
        done+=rebuildObservations(db,budget-done);if(done<budget)done+=rebuildSemantic(db,budget-done);return done;
    }

    private static int rebuildObservations(SQLiteDatabase db,int limit){if(limit<=0)return 0;Cursor c=db.rawQuery("SELECT r.id,r.source_key,r.technical_type,r.event_type,r.title,r.body,r.occurred_at,r.payload_json FROM ue_raw_observations r WHERE r.source_type='notification' AND NOT EXISTS(SELECT 1 FROM ue_source_transitions t WHERE t.raw_observation_id=r.id) ORDER BY r.id ASC LIMIT ?",new String[]{String.valueOf(limit)});int n=0;while(c.moveToNext()){JSONObject meta=parse(c.getString(7));StatefulMeaningStore.observeNotification(db,c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6),meta);n++;}c.close();return n;}

    private static int rebuildSemantic(SQLiteDatabase db,int limit){if(limit<=0)return 0;String sql="SELECT e.id,e.raw_observation_id,e.semantic_type,e.intent,e.subject,e.summary,e.confidence,e.occurred_at,r.source_key,COALESCE(t.source_instance_id,0) FROM ue_semantic_events e JOIN ue_raw_observations r ON r.id=e.raw_observation_id LEFT JOIN ue_source_transitions t ON t.raw_observation_id=e.raw_observation_id WHERE e.semantic_state='complete' AND e.superseded_by=0 AND NOT EXISTS(SELECT 1 FROM ue_projection_decisions p WHERE p.semantic_event_id=e.id AND p.policy_version=?) ORDER BY e.id ASC LIMIT ?";Cursor c=db.rawQuery(sql,new String[]{StatefulMeaningPolicy.VERSION,String.valueOf(limit)});int n=0;while(c.moveToNext()){long eventId=c.getLong(0),instanceId=c.getLong(9),at=c.getLong(7);String type=c.getString(2),intent=c.getString(3),subject=c.getString(4),summary=c.getString(5),source=c.getString(8);double conf=c.getDouble(6);long situation=StatefulMeaningStore.correlate(db,eventId,instanceId,source,type,subject,summary,conf,at);String transition=latestTransition(db,situation,eventId);int repeats=memberCount(db,situation);StatefulMeaningPolicy.ProjectionDecision d=StatefulMeaningPolicy.projection(type,intent,conf,transition,repeats,subject,summary);StatefulMeaningStore.recordProjectionDecision(db,eventId,situation,d);n++;}c.close();return n;}

    public static boolean hasBacklog(VaultDb vault){SQLiteDatabase db=vault.getReadableDatabase();StatefulMeaningStore.ensure(db);Cursor c=db.rawQuery("SELECT 1 FROM ue_raw_observations r WHERE r.source_type='notification' AND NOT EXISTS(SELECT 1 FROM ue_source_transitions t WHERE t.raw_observation_id=r.id) LIMIT 1",null);boolean raw=c.moveToFirst();c.close();if(raw)return true;c=db.rawQuery("SELECT 1 FROM ue_semantic_events e WHERE e.semantic_state='complete' AND e.superseded_by=0 AND NOT EXISTS(SELECT 1 FROM ue_projection_decisions p WHERE p.semantic_event_id=e.id AND p.policy_version=?) LIMIT 1",new String[]{StatefulMeaningPolicy.VERSION});boolean sem=c.moveToFirst();c.close();return sem;}

    private static String latestTransition(SQLiteDatabase db,long situationId,long eventId){if(situationId<=0)return "";Cursor c=db.rawQuery("SELECT kind FROM ue_situation_transitions WHERE situation_id=? AND semantic_event_id=? ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(situationId),String.valueOf(eventId)});String x=c.moveToFirst()?c.getString(0):"SUPPORTING_EVIDENCE";c.close();return x==null?"":x;}
    private static int memberCount(SQLiteDatabase db,long situationId){if(situationId<=0)return 0;Cursor c=db.rawQuery("SELECT member_count FROM ue_situation_state_v2 WHERE situation_id=?",new String[]{String.valueOf(situationId)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    private static JSONObject parse(String s){try{return new JSONObject(s==null?"{}":s);}catch(Exception ignored){return new JSONObject();}}
}
