package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/** Acceptance metrics for the no-silent-signal Cognitive V2 contract. */
public final class CognitiveInvariantDiagnosticsV2 {
    private CognitiveInvariantDiagnosticsV2(){}

    public static JSONObject snapshot(Context context,VaultDb db)throws Exception{
        CognitiveStore.ensure(db);CognitiveRunStoreV2.ensure(db);SQLiteDatabase s=db.getReadableDatabase();JSONObject o=new JSONObject();
        o.put("schema_revision",CognitiveStore.schemaRevision(db));
        o.put("v2_enabled",CognitiveFeatureFlags.enabled(context));
        o.put("v2_mode",CognitiveFeatureFlags.mode(context).name());
        o.put("raw_signals_total",count(s,"SELECT COUNT(*) FROM raw_signals"));
        o.put("signals_without_cognitive_state",count(s,"SELECT COUNT(*) FROM raw_signals WHERE cognitive_state IS NULL OR TRIM(cognitive_state)=''"));
        o.put("legacy_pending_alias_count",count(s,"SELECT COUNT(*) FROM raw_signals WHERE cognitive_state='PENDING_ADJUDICATION'"));
        o.put("notification_signals_total",count(s,"SELECT COUNT(*) FROM raw_signals WHERE kind='notification'"));
        o.put("notification_model_failed",count(s,"SELECT COUNT(*) FROM raw_signals WHERE kind='notification' AND cognitive_state='MODEL_FAILED'"));
        o.put("notification_review_required",count(s,"SELECT COUNT(*) FROM raw_signals WHERE kind='notification' AND cognitive_state='REVIEW_REQUIRED'"));
        o.put("state_counts",group(s,"SELECT COALESCE(cognitive_state,''),COUNT(*) FROM raw_signals GROUP BY cognitive_state ORDER BY COUNT(*) DESC"));
        o.put("derived_kind_counts",group(s,"SELECT kind,COUNT(*) FROM derived_items GROUP BY kind ORDER BY COUNT(*) DESC"));
        o.put("active_local_or_deep_runs",count(s,"SELECT COUNT(*) FROM model_runs WHERE role=? AND state IN ('QUEUED','RUNNING','ESCALATED')",new String[]{CognitiveRunStoreV2.role()}));
        o.put("acceptance_zero_blank_states",count(s,"SELECT COUNT(*) FROM raw_signals WHERE cognitive_state IS NULL OR TRIM(cognitive_state)='' ")==0);
        return o;
    }

    private static long count(SQLiteDatabase s,String sql){return count(s,sql,null);}
    private static long count(SQLiteDatabase s,String sql,String[] args){Cursor c=s.rawQuery(sql,args);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static JSONArray group(SQLiteDatabase s,String sql)throws Exception{JSONArray a=new JSONArray();Cursor c=s.rawQuery(sql,null);try{while(c.moveToNext()){JSONObject x=new JSONObject();x.put("key",c.isNull(0)?"":c.getString(0));x.put("count",c.getLong(1));a.put(x);}}finally{c.close();}return a;}
}
