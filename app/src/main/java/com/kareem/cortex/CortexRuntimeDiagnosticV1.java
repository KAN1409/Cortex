package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

/**
 * Interpreted runtime snapshot for the paths that are otherwise invisible in the UI.
 * Raw database rows are still exported by DebugExporter; this section explains pipeline state.
 */
public final class CortexRuntimeDiagnosticV1 {
    public static final String VERSION="cortex_runtime_diagnostic_v1";
    private CortexRuntimeDiagnosticV1(){}

    public static JSONObject snapshot(Context context,VaultDb db)throws Exception{
        JSONObject root=new JSONObject();root.put("version",VERSION);root.put("generated_at_ms",System.currentTimeMillis());
        root.put("analysis",analysis(context,db));
        root.put("relay_tunnel",relay(db));
        root.put("cognitive_v4",cognitive(db));
        root.put("autonomous_gemini",reasoning(context,db));
        root.put("recent_internal_transitions",recentDiagnostics(db,120));
        return root;
    }

    private static JSONObject analysis(Context c,VaultDb db)throws Exception{
        SQLiteDatabase s=db.getReadableDatabase();JSONObject o=new JSONObject();
        o.put("queue_process_running",AnalysisQueue.isRunning());o.put("pending_count",db.pendingCount());o.put("failed_count",db.failedCount());
        o.put("cloud_audio_allowed",PrivacyPolicy.canUseCloud(c,"audio"));o.put("gemini_asr_configured",GeminiKeyStore.has(c));o.put("groq_asr_configured",GroqKeyStore.has(c));
        o.put("audio_by_status",group(s,"SELECT COALESCE(status,''),COUNT(*) FROM knowledge_items WHERE type='AUDIO' GROUP BY status ORDER BY COUNT(*) DESC"));
        Cursor q=s.rawQuery("SELECT id,COALESCE(source,''),COALESCE(title,''),COALESCE(status,''),COALESCE(analysis_error,''),COALESCE(attachment_path,''),LENGTH(COALESCE(extracted_text,'')),LENGTH(COALESCE(summary,'')),created_at,updated_at FROM knowledge_items WHERE type='AUDIO' ORDER BY created_at DESC LIMIT 10",null);JSONArray recent=new JSONArray();
        try{while(q.moveToNext()){JSONObject x=new JSONObject();long id=q.getLong(0);String path=q.getString(5);File f=path==null||path.isEmpty()?null:new File(path);x.put("item_id",id);x.put("source",q.getString(1));x.put("title",q.getString(2));x.put("status",q.getString(3));x.put("analysis_error",q.getString(4));x.put("attachment_exists",f!=null&&f.exists());x.put("attachment_bytes",f!=null&&f.exists()?f.length():0);x.put("extracted_text_chars",q.getInt(6));x.put("summary_chars",q.getInt(7));x.put("created_at",q.getLong(8));x.put("updated_at",q.getLong(9));x.put("latest_analysis",latestAnalysis(s,id));recent.put(x);}}finally{q.close();}
        o.put("recent_audio_items",recent);return o;
    }

    private static JSONObject latestAnalysis(SQLiteDatabase s,long itemId)throws Exception{
        JSONObject o=new JSONObject();Cursor c=s.rawQuery("SELECT COALESCE(engine,''),COALESCE(version,''),created_at,LENGTH(COALESCE(output_json,'')) FROM analyses WHERE item_id=? ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(itemId)});try{if(c.moveToFirst()){o.put("engine",c.getString(0));o.put("version",c.getString(1));o.put("created_at",c.getLong(2));o.put("output_json_chars",c.getInt(3));}else o.put("present",false);}finally{c.close();}return o;
    }

    private static JSONObject relay(VaultDb db)throws Exception{
        SQLiteDatabase s=db.getReadableDatabase();JSONObject o=new JSONObject();
        o.put("client_table_present",table(s,"connector_clients"));o.put("event_table_present",table(s,"connector_ingest_events"));
        if(!table(s,"connector_clients")||!table(s,"connector_ingest_events"))return o;
        Cursor client=s.rawQuery("SELECT connector_id,package_name,COALESCE(capabilities_json,'[]'),source_priority,last_seen_at,last_event_at,accepted_events,rejected_events,updated_at FROM connector_clients ORDER BY updated_at DESC",null);JSONArray clients=new JSONArray();try{while(client.moveToNext()){JSONObject x=new JSONObject();x.put("connector_id",client.getString(0));x.put("package",client.getString(1));x.put("capabilities",jsonOrString(client.getString(2)));x.put("source_priority",client.getInt(3));x.put("last_seen_at",client.getLong(4));x.put("last_event_at",client.getLong(5));x.put("accepted_events",client.getLong(6));x.put("rejected_events",client.getLong(7));x.put("updated_at",client.getLong(8));clients.put(x);}}finally{client.close();}o.put("clients",clients);
        o.put("state_counts",group(s,"SELECT state,COUNT(*) FROM connector_ingest_events GROUP BY state ORDER BY COUNT(*) DESC"));
        Cursor e=s.rawQuery("SELECT event_id,connector_id,connector_package,source_type,source_package,occurred_at,received_at,state,signal_id,COALESCE(detail,''),updated_at FROM connector_ingest_events ORDER BY received_at DESC LIMIT 30",null);JSONArray events=new JSONArray();try{while(e.moveToNext()){JSONObject x=new JSONObject();x.put("event_id",e.getString(0));x.put("connector_id",e.getString(1));x.put("connector_package",e.getString(2));x.put("source_type",e.getString(3));x.put("source_package",e.getString(4));x.put("occurred_at",e.getLong(5));x.put("received_at",e.getLong(6));x.put("state",e.getString(7));x.put("signal_id",e.getLong(8));x.put("detail",e.getString(9));x.put("updated_at",e.getLong(10));events.put(x);}}finally{e.close();}o.put("recent_events",events);
        o.put("raw_signals_total",table(s,"raw_signals")?count(s,"SELECT COUNT(*) FROM raw_signals"):0);return o;
    }

    private static JSONObject cognitive(VaultDb db)throws Exception{
        SQLiteDatabase s=db.getReadableDatabase();JSONObject o=new JSONObject();
        o.put("evidence",table(s,"v4_evidence")?count(s,"SELECT COUNT(*) FROM v4_evidence"):0);o.put("memories",table(s,"v4_memories")?count(s,"SELECT COUNT(*) FROM v4_memories"):0);o.put("situations",table(s,"v4_situations")?count(s,"SELECT COUNT(*) FROM v4_situations"):0);
        if(table(s,"v4_situations")){o.put("situation_states",group(s,"SELECT state,COUNT(*) FROM v4_situations GROUP BY state ORDER BY COUNT(*) DESC"));Cursor c=s.rawQuery("SELECT id,kind,state,headline,relevant_from,relevant_until,attention_score,interruption_score,confidence,updated_at FROM v4_situations ORDER BY updated_at DESC LIMIT 20",null);JSONArray a=new JSONArray();try{while(c.moveToNext()){JSONObject x=new JSONObject();x.put("id",c.getString(0));x.put("kind",c.getString(1));x.put("state",c.getString(2));x.put("headline",c.getString(3));x.put("relevant_from",c.getLong(4));x.put("relevant_until",c.getLong(5));x.put("attention_score",c.getDouble(6));x.put("interruption_score",c.getDouble(7));x.put("confidence",c.getDouble(8));x.put("updated_at",c.getLong(9));a.put(x);}}finally{c.close();}o.put("recent_situations",a);}
        CognitivePulseProjectionV4.Snapshot pulse=CognitivePulseProjectionV4.current(db,20);JSONArray p=new JSONArray();if(pulse!=null)for(CognitivePulseProjectionV4.Item item:pulse.items){JSONObject x=new JSONObject();x.put("situation_id",item.situationId);x.put("kind",item.kind);x.put("state",item.state);x.put("headline",item.headline);x.put("attention_score",item.attentionScore);x.put("relevant_until",item.relevantUntil);x.put("new_since_deep_brain",item.newSinceDeepBrain);x.put("deep_brain_rank",item.deepBrainRank);x.put("changed_at",item.changedAt);p.put(x);}o.put("pulse",p);return o;
    }

    private static JSONObject reasoning(Context c,VaultDb db)throws Exception{
        JSONObject o=new JSONObject();long now=System.currentTimeMillis();o.put("enabled",CognitiveAutoReasoningSettingsV4.enabled(c));o.put("gemini_key_configured",GeminiKeyStore.has(c));o.put("effective_model",GeminiModelConfig.generationModel(c));o.put("last_success_at",CognitiveAutoReasoningSettingsV4.lastSuccessAt(c));
        SharedPreferences pref=c.getSharedPreferences("cortex_auto_reasoning_v4",Context.MODE_PRIVATE);o.put("last_started_at",pref.getLong("last_started",0));o.put("next_allowed_at",pref.getLong("next_allowed",0));o.put("consecutive_failures",pref.getInt("failures",0));o.put("day",pref.getString("day",""));o.put("day_calls",pref.getInt("day_calls",0));o.put("last_started_fingerprint",shortHash(pref.getString("last_started_fp","")));o.put("last_success_fingerprint",shortHash(pref.getString("last_success_fp","")));
        CognitivePulseProjectionV4.Snapshot pulse=CognitivePulseProjectionV4.current(db,20);CognitiveAutoReasoningPolicyV4.Decision decision=CognitiveAutoReasoningPolicyV4.evaluate(pulse,now);o.put("policy_should_run_now",decision.shouldRun);o.put("policy_urgent",decision.urgent);o.put("policy_reason",decision.reason);o.put("policy_fresh_count",decision.freshCount);o.put("policy_max_attention",decision.maxAttention);o.put("policy_fingerprint",shortHash(decision.fingerprint));
        if(decision.shouldRun){CognitiveAutoReasoningSettingsV4.Gate gate=CognitiveAutoReasoningSettingsV4.canStart(c,decision.fingerprint,decision.urgent,now);o.put("runtime_gate_allowed",gate.allowed);o.put("runtime_gate_reason",gate.reason);}else{o.put("runtime_gate_allowed",false);o.put("runtime_gate_reason","policy_did_not_request_run");}
        CognitiveReasoningRunStoreV4.Latest latest=CognitiveReasoningRunStoreV4.latest(db);if(latest!=null){JSONObject r=new JSONObject();r.put("provider",latest.provider);r.put("model",latest.model);r.put("trigger",latest.trigger);r.put("state",latest.state);r.put("started_at",latest.startedAt);r.put("completed_at",latest.completedAt);r.put("duration_ms",latest.durationMs);r.put("error",latest.error);o.put("latest_run",r);}else o.put("latest_run",JSONObject.NULL);
        return o;
    }

    private static JSONArray recentDiagnostics(VaultDb db,int limit)throws Exception{
        DiagnosticsLog.ensure(db);JSONArray out=new JSONArray();Cursor c=db.getReadableDatabase().rawQuery("SELECT id,created_at,severity,component,event,status,error_class,error_code,item_id,thread_id,signal_id,job_id,model_run_id,latency_ms,COALESCE(metadata_json,'{}') FROM diagnostics_log ORDER BY id DESC LIMIT "+Math.max(1,Math.min(500,limit)),null);try{while(c.moveToNext()){JSONObject x=new JSONObject();x.put("id",c.getLong(0));x.put("created_at",c.getLong(1));x.put("severity",c.getString(2));x.put("component",c.getString(3));x.put("event",c.getString(4));x.put("status",c.getString(5));x.put("error_class",c.getString(6));x.put("error_code",c.getString(7));x.put("item_id",c.getLong(8));x.put("thread_id",c.getLong(9));x.put("signal_id",c.getLong(10));x.put("job_id",c.getLong(11));x.put("model_run_id",c.getLong(12));x.put("latency_ms",c.getLong(13));x.put("metadata",jsonOrString(c.getString(14)));out.put(x);}}finally{c.close();}return out;
    }

    private static JSONArray group(SQLiteDatabase s,String sql)throws Exception{JSONArray out=new JSONArray();Cursor c=s.rawQuery(sql,null);try{while(c.moveToNext()){JSONObject x=new JSONObject();x.put("key",c.isNull(0)?"":c.getString(0));x.put("count",c.getLong(1));out.put(x);}}finally{c.close();}return out;}
    private static long count(SQLiteDatabase s,String sql){Cursor c=s.rawQuery(sql,null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static boolean table(SQLiteDatabase s,String name){Cursor c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{name});try{return c.moveToFirst();}finally{c.close();}}
    private static Object jsonOrString(String raw){String x=raw==null?"":raw.trim();if(x.isEmpty())return new JSONObject();try{if(x.startsWith("["))return new JSONArray(x);if(x.startsWith("{"))return new JSONObject(x);}catch(Throwable ignored){}return x;}
    private static String shortHash(String x){String s=x==null?"":x.trim();return s.length()<=16?s:s.substring(0,16);}
}
