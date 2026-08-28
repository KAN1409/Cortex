package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Validates and applies ChatGPT Deep Brain output without rewriting Evidence, Memory, Worlds or Facts. */
public final class CognitiveDeepBrainApplyV4 {
    private CognitiveDeepBrainApplyV4() {}

    public static Result apply(VaultDb db, String rawResponse) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveDeepBrainStoreV4.ensure(db);
        return apply(db.getWritableDatabase(), CognitiveDeepBrainProtocolV4.parseResponse(rawResponse));
    }

    static Result apply(SQLiteDatabase sql, CognitiveDeepBrainProtocolV4.ParsedResponse response) {
        CognitiveDeepBrainStoreV4.ensure(sql);
        CognitiveDeepBrainStoreV4.Request request = CognitiveDeepBrainStoreV4.load(sql, response.requestId);
        if (request == null) throw new IllegalArgumentException("Unknown Cortex request_id");
        if ("APPLIED".equals(request.state)) return new Result(response.requestId, response.answer, 0, 0, 0, true);

        Set<String> allowedSituations = new HashSet<>(request.situationIds);
        Set<String> allowedWorlds = new HashSet<>(request.worldIds);
        long now = System.currentTimeMillis();
        int prioritiesApplied = 0, actionsCreated = 0, skipped = 0;
        sql.beginTransaction();
        try {
            JSONArray priorities = CognitiveDeepBrainProtocolV4.array(response.json, "priority_updates");
            for (int i = 0; i < priorities.length(); i++) {
                JSONObject x = priorities.optJSONObject(i);
                if (x == null) { skipped++; continue; }
                String situationId = clean(x.optString("situation_id", ""));
                if (situationId.isEmpty() || !allowedSituations.contains(situationId) || !exists(sql,"v4_situations",situationId)) {
                    skipped++; continue;
                }
                ContentValues v = new ContentValues();
                if (x.has("attention_score")) v.put("attention_score", clamp01(x.optDouble("attention_score", 0.0)));
                if (x.has("interruption_score")) v.put("interruption_score", clamp01(x.optDouble("interruption_score", 0.0)));
                String state = clean(x.optString("state", "")).toUpperCase(Locale.ROOT);
                if (!state.isEmpty()) {
                    if (!safeSituationState(state)) { skipped++; continue; }
                    v.put("state", state);
                }
                if (v.size() == 0) { skipped++; continue; }
                v.put("last_evaluated_at", now);
                v.put("updated_at", now);
                int changed = sql.update("v4_situations", v, "id=?", new String[]{situationId});
                if (changed > 0) prioritiesApplied++; else skipped++;
            }

            JSONArray actions = CognitiveDeepBrainProtocolV4.array(response.json, "suggested_actions");
            for (int i = 0; i < actions.length(); i++) {
                JSONObject x = actions.optJSONObject(i);
                if (x == null) { skipped++; continue; }
                String label = clean(x.optString("label", ""));
                if (label.isEmpty() || label.length() > 300) { skipped++; continue; }
                String situationId = clean(x.optString("situation_id", ""));
                String worldId = clean(x.optString("world_id", ""));
                if (!situationId.isEmpty() && (!allowedSituations.contains(situationId) || !exists(sql,"v4_situations",situationId))) { skipped++; continue; }
                if (!worldId.isEmpty() && (!allowedWorlds.contains(worldId) || !exists(sql,"v4_worlds",worldId))) worldId = "";
                String type = safeActionType(x.optString("type", "CUSTOM"));
                String risk = safeRisk(type, x.optString("risk", "CONFIRMATION_REQUIRED"));
                JSONObject payload = x.optJSONObject("payload");
                if (payload == null) payload = new JSONObject();
                try {
                    payload.put("deep_brain_request_id", response.requestId);
                    payload.put("origin", "chatgpt_plus_share");
                    String reason = clean(x.optString("reason", ""));
                    if (!reason.isEmpty()) payload.put("reason", clip(reason,500));
                } catch (Throwable ignored) {}
                String identity = "deep-brain-action|" + response.requestId + "|" + i + "|" + type + "|" + label;
                String id = CognitiveIdentityV4.objectId("act", identity);
                ContentValues v = new ContentValues();
                v.put("id", id);
                if (!situationId.isEmpty()) v.put("situation_id", situationId); else v.putNull("situation_id");
                if (!worldId.isEmpty()) v.put("world_id", worldId); else v.putNull("world_id");
                v.put("action_type", type);
                v.put("label", label);
                v.put("risk", risk);
                v.put("payload_json", payload.toString());
                v.put("state", "PROPOSED");
                v.put("created_at", now);
                v.put("updated_at", now);
                long row = sql.insertWithOnConflict("v4_action_proposals", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                if (row >= 0 || exists(sql,"v4_action_proposals",id)) actionsCreated++; else skipped++;
            }

            JSONObject summary = new JSONObject();
            try { summary.put("priority_updates_applied",prioritiesApplied);summary.put("actions_created",actionsCreated);summary.put("skipped",skipped); } catch(Throwable ignored){}
            String responseId = CognitiveIdentityV4.objectId("brr", "deep-brain-response|" + response.requestId + "|" + Fingerprint.text(response.raw));
            CognitiveDeepBrainStoreV4.saveResponse(sql,responseId,response,summary.toString(),now);
            CognitiveDeepBrainStoreV4.markApplied(sql,response.requestId,now);
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        return new Result(response.requestId,response.answer,prioritiesApplied,actionsCreated,skipped,false);
    }

    private static boolean safeSituationState(String state) {
        return "DETECTED".equals(state)||"RELEVANT".equals(state)||"SURFACED".equals(state)||"DEFERRED".equals(state)||"WAITING".equals(state);
    }
    private static String safeActionType(String raw){String x=clean(raw).toUpperCase(Locale.ROOT);try{return CognitiveDomainV4.ActionType.valueOf(x).name();}catch(Throwable ignored){return "CUSTOM";}}
    private static String safeRisk(String type,String raw){String requested=clean(raw).toUpperCase(Locale.ROOT);boolean external="REPLY".equals(type)||"CALL".equals(type)||"SEND".equals(type)||"REMIND".equals(type)||"SCHEDULE".equals(type)||"COMPLETE".equals(type);if(external)return"CONFIRMATION_REQUIRED";try{return CognitiveDomainV4.ActionRisk.valueOf(requested).name();}catch(Throwable ignored){return"CONFIRMATION_REQUIRED";}}
    private static boolean exists(SQLiteDatabase sql,String table,String id){Cursor c=sql.rawQuery("SELECT 1 FROM "+table+" WHERE id=? LIMIT 1",new String[]{id});try{return c.moveToFirst();}finally{c.close();}}
    private static double clamp01(double x){if(Double.isNaN(x)||Double.isInfinite(x))return 0.0;return Math.max(0.0,Math.min(1.0,x));}
    private static String clean(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ').replaceAll("\\s+"," ").trim();}
    private static String clip(String s,int n){return s.length()<=n?s:s.substring(0,n)+"…";}

    public static final class Result {
        public final String requestId, answer;
        public final int priorityUpdatesApplied, actionsCreated, skipped;
        public final boolean alreadyApplied;
        Result(String requestId,String answer,int prioritiesApplied,int actionsCreated,int skipped,boolean alreadyApplied){this.requestId=requestId;this.answer=answer==null?"":answer;this.priorityUpdatesApplied=prioritiesApplied;this.actionsCreated=actionsCreated;this.skipped=skipped;this.alreadyApplied=alreadyApplied;}
        public String human(){if(alreadyApplied)return"This Deep Brain response was already applied.";return priorityUpdatesApplied+" priorities updated · "+actionsCreated+" action proposals added"+(skipped>0?" · "+skipped+" skipped safely":"");}
    }
}
