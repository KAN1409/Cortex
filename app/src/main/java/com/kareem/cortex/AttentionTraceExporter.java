package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only diagnostic snapshot: Capture -> semantic -> situation -> attention -> Now. */
public final class AttentionTraceExporter {
    public static final String VERSION="CORTEX_ATTENTION_TRACE_V1";
    private AttentionTraceExporter(){}

    public static String export(SQLiteDatabase db) throws Exception {
        JSONObject root=new JSONObject();
        root.put("format",VERSION);root.put("created_at",System.currentTimeMillis());
        root.put("processor_version",UniversalEventStore.PROCESSOR_VERSION);
        root.put("policy_version",StatefulMeaningPolicy.VERSION);
        root.put("shadow_version",CognitiveShadowStore.VERSION);
        root.put("capture",capture(db));root.put("situations",situations(db));
        root.put("attention_decisions",decisions(db));root.put("actual_now",now(db));
        root.put("summary",summary(db));
        return root.toString(2);
    }

    private static JSONArray capture(SQLiteDatabase db)throws Exception{
        JSONArray a=new JSONArray();if(!table(db,"ue_raw_observations"))return a;
        Cursor c=db.rawQuery("SELECT r.id,r.occurred_at,r.source_type,r.source_key,r.event_type,r.title,r.body,"+
                "COALESCE(e.id,0),COALESCE(e.semantic_type,''),COALESCE(e.semantic_state,''),COALESCE(e.confidence,0),COALESCE(e.subject,''),COALESCE(e.summary,'') "+
                "FROM ue_raw_observations r LEFT JOIN ue_semantic_events e ON e.id=(SELECT e2.id FROM ue_semantic_events e2 WHERE e2.raw_observation_id=r.id AND e2.superseded_by=0 ORDER BY e2.id DESC LIMIT 1) ORDER BY r.occurred_at DESC LIMIT 500",null);
        while(c.moveToNext()){JSONObject o=new JSONObject();o.put("capture_id",c.getLong(0));o.put("occurred_at",c.getLong(1));o.put("source_type",s(c,2));o.put("source_key",s(c,3));o.put("event_type",s(c,4));o.put("title",s(c,5));o.put("body",s(c,6));o.put("semantic_event_id",c.getLong(7));o.put("semantic_type",s(c,8));o.put("semantic_state",s(c,9));o.put("confidence",c.getDouble(10));o.put("subject",s(c,11));o.put("semantic_summary",s(c,12));a.put(o);}c.close();return a;
    }
    private static JSONArray situations(SQLiteDatabase db)throws Exception{
        JSONArray a=new JSONArray();if(!table(db,"ue_situations"))return a;
        Cursor c=db.rawQuery("SELECT id,situation_key,kind,title,summary,state,priority,confidence,opened_at,last_changed_at,resolved_at FROM ue_situations ORDER BY last_changed_at DESC LIMIT 240",null);
        while(c.moveToNext()){JSONObject o=new JSONObject();o.put("situation_id",c.getLong(0));o.put("situation_key",s(c,1));o.put("kind",s(c,2));o.put("title",s(c,3));o.put("summary",s(c,4));o.put("state",s(c,5));o.put("priority",c.getInt(6));o.put("confidence",c.getDouble(7));o.put("opened_at",c.getLong(8));o.put("last_changed_at",c.getLong(9));o.put("resolved_at",c.getLong(10));o.put("evidence",evidence(db,c.getLong(0)));a.put(o);}c.close();return a;
    }
    private static JSONArray evidence(SQLiteDatabase db,long id)throws Exception{
        JSONArray a=new JSONArray();if(!table(db,"ue_situation_events"))return a;Cursor c=db.rawQuery("SELECT semantic_event_id,relation FROM ue_situation_events WHERE situation_id=? ORDER BY id",new String[]{String.valueOf(id)});while(c.moveToNext()){JSONObject o=new JSONObject();o.put("semantic_event_id",c.getLong(0));o.put("relation",s(c,1));a.put(o);}c.close();return a;
    }
    private static JSONArray decisions(SQLiteDatabase db)throws Exception{
        JSONArray a=new JSONArray();if(table(db,"ue_projection_decisions")){Cursor c=db.rawQuery("SELECT situation_id,semantic_event_id,projection,eligible,reason,created_at FROM ue_projection_decisions ORDER BY id DESC LIMIT 500",null);while(c.moveToNext()){JSONObject o=new JSONObject();o.put("engine","production");o.put("situation_id",c.getLong(0));o.put("semantic_event_id",c.getLong(1));o.put("projection",s(c,2));o.put("eligible",c.getInt(3)==1);o.put("reason",s(c,4));o.put("created_at",c.getLong(5));a.put(o);}c.close();}
        if(table(db,"ue_cognitive_shadow_decisions")){Cursor c=db.rawQuery("SELECT situation_id,legacy_surface,cognitive_surface,cognitive_rank,cognitive_score,delta,legacy_reason,cognitive_reason,created_at FROM ue_cognitive_shadow_decisions WHERE run_id=(SELECT MAX(id) FROM ue_cognitive_shadow_runs) ORDER BY cognitive_rank,situation_id",null);while(c.moveToNext()){JSONObject o=new JSONObject();o.put("engine","v70_shadow");o.put("situation_id",c.getLong(0));o.put("legacy_surface",c.getInt(1)==1);o.put("cognitive_surface",c.getInt(2)==1);o.put("cognitive_rank",c.getInt(3));o.put("cognitive_score",c.getDouble(4));o.put("delta",s(c,5));o.put("legacy_reason",s(c,6));o.put("cognitive_reason",s(c,7));o.put("created_at",c.getLong(8));a.put(o);}c.close();}return a;
    }
    private static JSONArray now(SQLiteDatabase db)throws Exception{
        JSONArray a=new JSONArray();if(!table(db,"ue_attention_items"))return a;Cursor c=db.rawQuery("SELECT id,semantic_event_id,situation_id,kind,title,body,state,priority,confidence,source_key,reason,updated_at FROM ue_attention_items WHERE state='open' ORDER BY priority DESC,updated_at DESC LIMIT 50",null);while(c.moveToNext()){JSONObject o=new JSONObject();o.put("attention_id",c.getLong(0));o.put("semantic_event_id",c.getLong(1));o.put("situation_id",c.getLong(2));o.put("kind",s(c,3));o.put("title",s(c,4));o.put("body",s(c,5));o.put("state",s(c,6));o.put("priority",c.getInt(7));o.put("confidence",c.getDouble(8));o.put("source_key",s(c,9));o.put("reason",s(c,10));o.put("updated_at",c.getLong(11));a.put(o);}c.close();return a;
    }
    private static JSONObject summary(SQLiteDatabase db)throws Exception{
        JSONObject o=new JSONObject();o.put("capture_count",count(db,"ue_raw_observations"));o.put("semantic_count",count(db,"ue_semantic_events"));o.put("situation_count",count(db,"ue_situations"));o.put("open_now_count",table(db,"ue_attention_items")?scalar(db,"SELECT COUNT(*) FROM ue_attention_items WHERE state='open'"):0);o.put("note","Diagnostic trace only; no production decision or evidence is modified.");return o;
    }
    private static long count(SQLiteDatabase db,String t){return table(db,t)?scalar(db,"SELECT COUNT(*) FROM "+t):0;}
    private static long scalar(SQLiteDatabase db,String q){Cursor c=db.rawQuery(q,null);long v=c.moveToFirst()?c.getLong(0):0;c.close();return v;}
    private static boolean table(SQLiteDatabase db,String n){Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{n});boolean x=c.moveToFirst();c.close();return x;}
    private static String s(Cursor c,int i){String x=c.getString(i);return x==null?"":x;}
}
