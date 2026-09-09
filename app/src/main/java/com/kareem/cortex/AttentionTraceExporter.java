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
        root.put("format",VERSION);
        root.put("created_at",System.currentTimeMillis());
        root.put("app_version_name",BuildConfig.VERSION_NAME);
        root.put("app_version_code",BuildConfig.VERSION_CODE);
        root.put("db_version",CognitiveSchema.DB_VERSION);
        root.put("schema_revision",CognitiveSchema.REVISION);
        root.put("processor_version",UniversalEventStore.PROCESSOR_VERSION);
        root.put("policy_version",StatefulMeaningPolicy.VERSION);
        root.put("shadow_version",CognitiveShadowStore.VERSION);
        root.put("capture",capture(db));
        root.put("situations",situations(db));
        root.put("attention_decisions",decisions(db));
        root.put("actual_now",now(db));
        root.put("trace_items",traceItems(db));
        root.put("unlinked_semantic_events",unlinkedSemanticEvents(db));
        root.put("summary",summary(db));
        return root.toString(2);
    }

    private static JSONArray capture(SQLiteDatabase db)throws Exception{
        JSONArray a=new JSONArray();if(!table(db,"ue_raw_observations"))return a;
        Cursor c=db.rawQuery("SELECT r.id,r.occurred_at,r.source_type,r.source_key,r.event_type,r.title,r.body,"+
                "COALESCE(e.id,0),COALESCE(e.semantic_type,''),COALESCE(e.semantic_state,''),COALESCE(e.confidence,0),COALESCE(e.subject,''),COALESCE(e.summary,'') "+
                "FROM ue_raw_observations r LEFT JOIN ue_semantic_events e ON e.id=(SELECT e2.id FROM ue_semantic_events e2 WHERE e2.raw_observation_id=r.id AND e2.superseded_by=0 ORDER BY e2.id DESC LIMIT 1) ORDER BY r.occurred_at DESC LIMIT 500",null);
        try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("capture_id",c.getLong(0));o.put("occurred_at",c.getLong(1));o.put("source_type",s(c,2));o.put("source_key",s(c,3));o.put("event_type",s(c,4));o.put("title",s(c,5));o.put("body",s(c,6));o.put("semantic_event_id",c.getLong(7));o.put("semantic_type",s(c,8));o.put("semantic_state",s(c,9));o.put("confidence",c.getDouble(10));o.put("subject",s(c,11));o.put("semantic_summary",s(c,12));a.put(o);}}finally{c.close();}return a;
    }

    private static JSONArray situations(SQLiteDatabase db)throws Exception{
        JSONArray a=new JSONArray();if(!table(db,"ue_situations"))return a;
        Cursor c=db.rawQuery("SELECT id,situation_key,kind,title,summary,state,priority,confidence,opened_at,last_changed_at,resolved_at FROM ue_situations ORDER BY last_changed_at DESC LIMIT 240",null);
        try{while(c.moveToNext()){JSONObject o=new JSONObject();long id=c.getLong(0);o.put("situation_id",id);o.put("situation_key",s(c,1));o.put("kind",s(c,2));o.put("title",s(c,3));o.put("summary",s(c,4));o.put("state",s(c,5));o.put("priority",c.getInt(6));o.put("confidence",c.getDouble(7));o.put("opened_at",c.getLong(8));o.put("last_changed_at",c.getLong(9));o.put("resolved_at",c.getLong(10));o.put("evidence",evidence(db,id));a.put(o);}}finally{c.close();}return a;
    }

    private static JSONArray evidence(SQLiteDatabase db,long id)throws Exception{
        JSONArray a=new JSONArray();
        if(table(db,"ue_situation_members_v2")&&table(db,"ue_semantic_events")){
            Cursor c=db.rawQuery("SELECT m.semantic_event_id,m.relation,COALESCE(e.raw_observation_id,0),COALESCE(e.semantic_type,''),COALESCE(e.subject,''),COALESCE(e.summary,''),COALESCE(e.confidence,0),COALESCE(e.occurred_at,0) FROM ue_situation_members_v2 m LEFT JOIN ue_semantic_events e ON e.id=m.semantic_event_id WHERE m.situation_id=? ORDER BY m.id",new String[]{String.valueOf(id)});
            try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("semantic_event_id",c.getLong(0));o.put("relation",s(c,1));o.put("capture_id",c.getLong(2));o.put("semantic_type",s(c,3));o.put("subject",s(c,4));o.put("summary",s(c,5));o.put("confidence",c.getDouble(6));o.put("occurred_at",c.getLong(7));a.put(o);}}finally{c.close();}
            return a;
        }
        if(!table(db,"ue_situation_events"))return a;
        Cursor c=db.rawQuery("SELECT semantic_event_id,relation FROM ue_situation_events WHERE situation_id=? ORDER BY id",new String[]{String.valueOf(id)});
        try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("semantic_event_id",c.getLong(0));o.put("relation",s(c,1));a.put(o);}}finally{c.close();}return a;
    }

    private static JSONArray decisions(SQLiteDatabase db)throws Exception{
        JSONArray a=new JSONArray();
        if(table(db,"ue_projection_decisions")){Cursor c=db.rawQuery("SELECT situation_id,semantic_event_id,projection,eligible,reason,policy_version,created_at FROM ue_projection_decisions ORDER BY id DESC LIMIT 500",null);try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("engine","production");o.put("situation_id",c.getLong(0));o.put("semantic_event_id",c.getLong(1));o.put("projection",s(c,2));o.put("eligible",c.getInt(3)==1);o.put("reason",s(c,4));o.put("policy_version",s(c,5));o.put("created_at",c.getLong(6));a.put(o);}}finally{c.close();}}
        if(table(db,"ue_cognitive_shadow_decisions")&&table(db,"ue_cognitive_shadow_runs")){Cursor c=db.rawQuery("SELECT situation_id,legacy_surface,cognitive_surface,cognitive_rank,cognitive_score,delta,legacy_reason,cognitive_reason,created_at FROM ue_cognitive_shadow_decisions WHERE run_id=(SELECT MAX(id) FROM ue_cognitive_shadow_runs WHERE completed_at>0) ORDER BY cognitive_rank,situation_id",null);try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("engine","v70_shadow");o.put("situation_id",c.getLong(0));o.put("legacy_surface",c.getInt(1)==1);o.put("cognitive_surface",c.getInt(2)==1);o.put("cognitive_rank",c.getInt(3));o.put("cognitive_score",c.getDouble(4));o.put("delta",s(c,5));o.put("legacy_reason",s(c,6));o.put("cognitive_reason",s(c,7));o.put("created_at",c.getLong(8));a.put(o);}}finally{c.close();}}
        return a;
    }

    private static JSONArray now(SQLiteDatabase db)throws Exception{
        JSONArray a=new JSONArray();if(!table(db,"ue_attention_items"))return a;
        Cursor c=db.rawQuery("SELECT id,semantic_event_id,situation_id,kind,title,body,state,priority,confidence,source_key,reason,updated_at FROM ue_attention_items WHERE state='open' ORDER BY priority DESC,updated_at DESC LIMIT 50",null);
        int rank=0;try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("rank",++rank);o.put("attention_id",c.getLong(0));o.put("semantic_event_id",c.getLong(1));o.put("situation_id",c.getLong(2));o.put("kind",s(c,3));o.put("title",s(c,4));o.put("body",s(c,5));o.put("state",s(c,6));o.put("priority",c.getInt(7));o.put("confidence",c.getDouble(8));o.put("source_key",s(c,9));o.put("reason",s(c,10));o.put("updated_at",c.getLong(11));a.put(o);}}finally{c.close();}return a;
    }

    /** Situation-centric comparison view so one real-world situation can be followed end-to-end. */
    private static JSONArray traceItems(SQLiteDatabase db)throws Exception{
        JSONArray out=new JSONArray();if(!table(db,"ue_situations"))return out;
        Cursor c=db.rawQuery("SELECT id,situation_key,kind,title,summary,state,priority,confidence,last_changed_at FROM ue_situations ORDER BY last_changed_at DESC LIMIT 240",null);
        try{while(c.moveToNext()){
            long id=c.getLong(0);JSONObject o=new JSONObject();
            o.put("situation_id",id);o.put("situation_key",s(c,1));o.put("kind",s(c,2));o.put("title",s(c,3));o.put("summary",s(c,4));o.put("state",s(c,5));o.put("priority",c.getInt(6));o.put("confidence",c.getDouble(7));o.put("last_changed_at",c.getLong(8));o.put("evidence",evidence(db,id));

            JSONObject production=new JSONObject();production.put("evaluated",false);production.put("surface_now",false);production.put("reason","");production.put("policy_version",StatefulMeaningPolicy.VERSION);
            if(table(db,"ue_projection_decisions")){Cursor p=db.rawQuery("SELECT eligible,reason,policy_version,semantic_event_id,created_at FROM ue_projection_decisions WHERE situation_id=? AND projection='NOW' AND policy_version=? ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(id),StatefulMeaningPolicy.VERSION});try{if(p.moveToFirst()){production.put("evaluated",true);production.put("surface_now",p.getInt(0)==1);production.put("reason",s(p,1));production.put("policy_version",s(p,2));production.put("semantic_event_id",p.getLong(3));production.put("created_at",p.getLong(4));}}finally{p.close();}}
            o.put("production_now_decision",production);

            JSONObject actual=new JSONObject();actual.put("present",false);actual.put("rank",0);
            if(table(db,"ue_attention_items")){Cursor n=db.rawQuery("SELECT id,kind,title,body,priority,confidence,reason,updated_at,(SELECT COUNT(*)+1 FROM ue_attention_items x WHERE x.state='open' AND (x.priority>a.priority OR (x.priority=a.priority AND x.updated_at>a.updated_at))) FROM ue_attention_items a WHERE a.situation_id=? AND a.state='open' ORDER BY a.priority DESC,a.updated_at DESC LIMIT 1",new String[]{String.valueOf(id)});try{if(n.moveToFirst()){actual.put("present",true);actual.put("attention_id",n.getLong(0));actual.put("kind",s(n,1));actual.put("title",s(n,2));actual.put("body",s(n,3));actual.put("priority",n.getInt(4));actual.put("confidence",n.getDouble(5));actual.put("reason",s(n,6));actual.put("updated_at",n.getLong(7));actual.put("rank",n.getInt(8));}}finally{n.close();}}
            o.put("actual_now",actual);

            JSONObject shadow=new JSONObject();shadow.put("available",false);
            if(table(db,"ue_cognitive_shadow_decisions")&&table(db,"ue_cognitive_shadow_runs")){Cursor sh=db.rawQuery("SELECT d.legacy_surface,d.cognitive_surface,d.cognitive_rank,d.cognitive_score,d.delta,d.legacy_reason,d.cognitive_reason,d.created_at FROM ue_cognitive_shadow_decisions d WHERE d.run_id=(SELECT MAX(id) FROM ue_cognitive_shadow_runs WHERE completed_at>0) AND d.situation_id=? LIMIT 1",new String[]{String.valueOf(id)});try{if(sh.moveToFirst()){shadow.put("available",true);shadow.put("legacy_surface",sh.getInt(0)==1);shadow.put("cognitive_surface",sh.getInt(1)==1);shadow.put("cognitive_rank",sh.getInt(2));shadow.put("cognitive_score",sh.getDouble(3));shadow.put("delta",s(sh,4));shadow.put("legacy_reason",s(sh,5));shadow.put("cognitive_reason",s(sh,6));shadow.put("created_at",sh.getLong(7));}}finally{sh.close();}}
            o.put("shadow_attention",shadow);

            String consistency="CONSISTENT";
            if(production.optBoolean("evaluated")&&production.optBoolean("surface_now")&&!actual.optBoolean("present"))consistency="ELIGIBLE_NOT_MATERIALIZED";
            else if(production.optBoolean("evaluated")&&!production.optBoolean("surface_now")&&actual.optBoolean("present"))consistency="MATERIALIZED_WITHOUT_CURRENT_ELIGIBILITY";
            o.put("projection_consistency",consistency);
            out.put(o);
        }}finally{c.close();}return out;
    }

    private static JSONArray unlinkedSemanticEvents(SQLiteDatabase db)throws Exception{
        JSONArray a=new JSONArray();if(!table(db,"ue_semantic_events"))return a;
        String membership=table(db,"ue_situation_members_v2")?"ue_situation_members_v2":(table(db,"ue_situation_events")?"ue_situation_events":null);if(membership==null)return a;
        Cursor c=db.rawQuery("SELECT e.id,e.raw_observation_id,e.occurred_at,e.semantic_type,e.subject,e.summary,e.confidence,e.semantic_state FROM ue_semantic_events e WHERE e.superseded_by=0 AND e.semantic_state='complete' AND NOT EXISTS(SELECT 1 FROM "+membership+" m WHERE m.semantic_event_id=e.id) ORDER BY e.occurred_at DESC LIMIT 200",null);
        try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("semantic_event_id",c.getLong(0));o.put("capture_id",c.getLong(1));o.put("occurred_at",c.getLong(2));o.put("semantic_type",s(c,3));o.put("subject",s(c,4));o.put("summary",s(c,5));o.put("confidence",c.getDouble(6));o.put("semantic_state",s(c,7));a.put(o);}}finally{c.close();}return a;
    }

    private static JSONObject summary(SQLiteDatabase db)throws Exception{
        JSONObject o=new JSONObject();
        o.put("capture_count",count(db,"ue_raw_observations"));
        o.put("semantic_count",count(db,"ue_semantic_events"));
        o.put("situation_count",count(db,"ue_situations"));
        o.put("linked_semantic_count",linkedSemanticCount(db));
        o.put("unlinked_complete_semantic_count",unlinkedCompleteSemanticCount(db));
        o.put("production_now_eligible_count",table(db,"ue_projection_decisions")?scalar(db,"SELECT COUNT(DISTINCT situation_id) FROM ue_projection_decisions WHERE projection='NOW' AND eligible=1 AND policy_version='"+sql(StatefulMeaningPolicy.VERSION)+"'"):0);
        o.put("open_now_count",table(db,"ue_attention_items")?scalar(db,"SELECT COUNT(*) FROM ue_attention_items WHERE state='open'"):0);
        o.put("eligible_not_materialized_count",consistencyCount(db,true,false));
        o.put("materialized_without_current_eligibility_count",consistencyCount(db,false,true));
        o.put("note","Diagnostic trace only. Counts describe pipeline consistency, not whether Cortex made the right human-level attention choice.");
        return o;
    }

    private static long linkedSemanticCount(SQLiteDatabase db){String membership=table(db,"ue_situation_members_v2")?"ue_situation_members_v2":(table(db,"ue_situation_events")?"ue_situation_events":null);return membership==null?0:scalar(db,"SELECT COUNT(DISTINCT semantic_event_id) FROM "+membership);}
    private static long unlinkedCompleteSemanticCount(SQLiteDatabase db){String membership=table(db,"ue_situation_members_v2")?"ue_situation_members_v2":(table(db,"ue_situation_events")?"ue_situation_events":null);if(membership==null||!table(db,"ue_semantic_events"))return 0;return scalar(db,"SELECT COUNT(*) FROM ue_semantic_events e WHERE e.superseded_by=0 AND e.semantic_state='complete' AND NOT EXISTS(SELECT 1 FROM "+membership+" m WHERE m.semantic_event_id=e.id)");}
    private static long consistencyCount(SQLiteDatabase db,boolean eligible,boolean actual){if(!table(db,"ue_situations")||!table(db,"ue_projection_decisions")||!table(db,"ue_attention_items"))return 0;String e=eligible?"1":"0",a=actual?"EXISTS":"NOT EXISTS";return scalar(db,"SELECT COUNT(*) FROM ue_situations s WHERE COALESCE((SELECT pd.eligible FROM ue_projection_decisions pd WHERE pd.situation_id=s.id AND pd.projection='NOW' AND pd.policy_version='"+sql(StatefulMeaningPolicy.VERSION)+"' ORDER BY pd.id DESC LIMIT 1),0)="+e+" AND "+a+"(SELECT 1 FROM ue_attention_items ai WHERE ai.situation_id=s.id AND ai.state='open')");}
    private static long count(SQLiteDatabase db,String t){return table(db,t)?scalar(db,"SELECT COUNT(*) FROM "+t):0;}
    private static long scalar(SQLiteDatabase db,String q){Cursor c=db.rawQuery(q,null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static boolean table(SQLiteDatabase db,String n){Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{n});try{return c.moveToFirst();}finally{c.close();}}
    private static String s(Cursor c,int i){String x=c.getString(i);return x==null?"":x;}
    private static String sql(String x){return x==null?"":x.replace("'","''");}
}
