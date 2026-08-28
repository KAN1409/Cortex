package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Validates and applies model Deep Brain output without rewriting Evidence, Memory, Worlds or Facts. */
public final class CognitiveDeepBrainApplyV4 {
    public static final String ORIGIN_CHATGPT_SHARE="chatgpt_plus_share";
    public static final String ORIGIN_GEMINI_AUTONOMOUS="gemini_autonomous";
    private CognitiveDeepBrainApplyV4() {}

    /** Compatibility route for the existing user-triggered ChatGPT share/import flow. */
    public static Result apply(VaultDb db,String rawResponse){return apply(db,rawResponse,ORIGIN_CHATGPT_SHARE);}

    /** Provider-neutral apply boundary. The model may suggest state; Cortex remains the authority. */
    public static Result apply(VaultDb db,String rawResponse,String origin) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveDeepBrainStoreV4.ensure(db);
        return apply(db.getWritableDatabase(), CognitiveDeepBrainProtocolV4.parseResponse(rawResponse), safeOrigin(origin));
    }

    /** Compatibility hook used by existing regression fixtures. */
    static Result apply(SQLiteDatabase sql,CognitiveDeepBrainProtocolV4.ParsedResponse response){return apply(sql,response,ORIGIN_CHATGPT_SHARE);}

    static Result apply(SQLiteDatabase sql,CognitiveDeepBrainProtocolV4.ParsedResponse response,String origin) {
        CognitiveDeepBrainStoreV4.ensure(sql);
        String safeOrigin=safeOrigin(origin);
        CognitiveDeepBrainStoreV4.Request request = CognitiveDeepBrainStoreV4.load(sql, response.requestId);
        if (request == null) throw new IllegalArgumentException("Unknown Cortex request_id");
        if ("APPLIED".equals(request.state)) return new Result(response.requestId, response.answer, 0, 0, 0, 0, true);
        // Priority is a global judgement. If any live Situation changed after this packet was built,
        // the response is already based on an older world-state. Do not let a slow Gemini call or a
        // delayed ChatGPT share overwrite newer canonical/user context; leave the request unapplied
        // so the fresh-context worker/UI can build a new packet instead.
        if(hasNewerCanonicalSituation(sql,request.createdAt))throw new IllegalArgumentException("Cortex context changed after this Deep Brain request was built; refresh reasoning");

        Set<String> allowedSituations = new HashSet<>(request.situationIds);
        Set<String> allowedMemories = new HashSet<>(request.memoryIds);
        Set<String> allowedWorlds = new HashSet<>(request.worldIds);
        long now = System.currentTimeMillis();
        int rankedStored = 0, prioritiesApplied = 0, actionsCreated = 0, skipped = 0, prioritiesSuperseded = 0, actionsSuperseded = 0;
        sql.beginTransaction();
        try {
            JSONArray ranked = CognitiveDeepBrainProtocolV4.array(response.json, "priority_items");
            boolean rankingFieldPresent=response.json.optJSONArray("priority_items")!=null;
            for (int i = 0; i < ranked.length(); i++) {
                JSONObject x = ranked.optJSONObject(i);
                if (x == null) { skipped++; continue; }
                String title = clean(x.optString("title", ""));
                if (title.isEmpty() || title.length() > 300) { skipped++; continue; }
                String situationId = clean(x.optString("situation_id", ""));
                if (!situationId.isEmpty() && (!allowedSituations.contains(situationId) || !exists(sql,"v4_situations",situationId))) situationId = "";
                List<String> memoryIds = allowedIds(sql,"v4_memories",x.optJSONArray("memory_ids"),allowedMemories);
                List<String> worldIds = allowedIds(sql,"v4_worlds",x.optJSONArray("world_ids"),allowedWorlds);
                if (situationId.isEmpty() && memoryIds.isEmpty() && worldIds.isEmpty()) { skipped++; continue; }
                int rank=x.optInt("rank",i+1);if(rank<1||rank>100)rank=i+1;
                double attention=clamp01(x.optDouble("attention_score",Math.max(.1,1.0-(rank-1)*.12)));
                String reason=clip(clean(x.optString("reason","")),700);
                String identity="deep-brain-priority|"+response.requestId+"|"+rank+"|"+title+"|"+situationId+"|"+memoryIds+"|"+worldIds;
                String id=CognitiveIdentityV4.objectId("pri",identity);
                if(CognitiveDeepBrainStoreV4.putPriority(sql,id,response.requestId,rank,title,reason,attention,situationId,memoryIds,worldIds,now))rankedStored++;else skipped++;
            }
            // A non-empty ranking whose every item failed grounding is not a successful reasoning
            // pass. Roll the whole transaction back so the request stays fresh and can be retried;
            // otherwise a hallucinated response could falsely mark all packet Situations covered.
            if(!rankingGrounded(rankingFieldPresent,ranked.length(),rankedStored))throw new IllegalArgumentException("Deep Brain priority_items contained no grounded Cortex IDs");
            // Replace the previous model ranking only when the new ranking section itself is valid.
            // Explicit [] means "none now"; missing field is tolerated for legacy/manual responses.
            if(rankingFieldPresent&&(ranked.length()==0||rankedStored>0))prioritiesSuperseded=supersedePriorPriorities(sql,response.requestId,now);

            JSONArray priorities = CognitiveDeepBrainProtocolV4.array(response.json, "priority_updates");
            for (int i = 0; i < priorities.length(); i++) {
                JSONObject x = priorities.optJSONObject(i);
                if (x == null) { skipped++; continue; }
                String situationId = clean(x.optString("situation_id", ""));
                if (situationId.isEmpty() || !allowedSituations.contains(situationId) || !exists(sql,"v4_situations",situationId)) { skipped++; continue; }
                SituationSnapshot before=snapshot(sql,situationId);if(before==null){skipped++;continue;}
                Double attention=x.has("attention_score")?Double.valueOf(clamp01(x.optDouble("attention_score",before.attention))):null;
                Double interruption=x.has("interruption_score")?Double.valueOf(clamp01(x.optDouble("interruption_score",before.interruption))):null;
                String state = clean(x.optString("state", "")).toUpperCase(Locale.ROOT);
                if (!state.isEmpty() && !safeSituationState(state)) { skipped++; continue; }
                if(attention==null&&interruption==null&&state.isEmpty()){skipped++;continue;}
                ContentValues v = new ContentValues();if(attention!=null)v.put("attention_score",attention);if(interruption!=null)v.put("interruption_score",interruption);if(!state.isEmpty())v.put("state",state);v.put("last_evaluated_at",now);v.put("updated_at",now);
                int changed = sql.update("v4_situations", v, "id=?", new String[]{situationId});
                if (changed > 0) {prioritiesApplied++;String afterState=state.isEmpty()?before.state:state;double afterAttention=attention==null?before.attention:attention.doubleValue();double afterInterruption=interruption==null?before.interruption:interruption.doubleValue();CognitiveDeepBrainStoreV4.recordSituationUpdate(sql,response.requestId,situationId,before.state,before.attention,before.interruption,afterState,afterAttention,afterInterruption,clip(clean(x.optString("reason","")),700),now);} else skipped++;
            }

            JSONArray actions = CognitiveDeepBrainProtocolV4.array(response.json, "suggested_actions");
            boolean actionsFieldPresent=response.json.optJSONArray("suggested_actions")!=null;
            HashSet<String> currentActionKeys=new HashSet<>();
            for (int i = 0; i < actions.length(); i++) {
                JSONObject x = actions.optJSONObject(i);
                if (x == null) { skipped++; continue; }
                String label = clean(x.optString("label", ""));
                if (label.isEmpty() || label.length() > 300) { skipped++; continue; }
                String situationId = clean(x.optString("situation_id", "")); String worldId = clean(x.optString("world_id", ""));
                if (!situationId.isEmpty() && (!allowedSituations.contains(situationId) || !exists(sql,"v4_situations",situationId))) { skipped++; continue; }
                if (!worldId.isEmpty() && (!allowedWorlds.contains(worldId) || !exists(sql,"v4_worlds",worldId))) worldId = "";
                String type = safeActionType(x.optString("type", "CUSTOM")); String risk = safeRisk(type, x.optString("risk", "CONFIRMATION_REQUIRED"));
                String semantic=clean(situationId).toLowerCase(Locale.ROOT)+"|"+clean(worldId).toLowerCase(Locale.ROOT)+"|"+type+"|"+label.toLowerCase(Locale.ROOT);
                if(!currentActionKeys.add(semantic)){skipped++;continue;}
                JSONObject payload = x.optJSONObject("payload"); if (payload == null) payload = new JSONObject();
                try { payload.put("deep_brain_request_id", response.requestId); payload.put("origin", safeOrigin); String reason = clean(x.optString("reason", "")); if (!reason.isEmpty()) payload.put("reason", clip(reason,500)); } catch (Throwable ignored) {}
                String identity = "deep-brain-action|" + response.requestId + "|" + i + "|" + type + "|" + label; String id = CognitiveIdentityV4.objectId("act", identity);
                ContentValues v = new ContentValues(); v.put("id", id); if (!situationId.isEmpty()) v.put("situation_id", situationId); else v.putNull("situation_id"); if (!worldId.isEmpty()) v.put("world_id", worldId); else v.putNull("world_id");
                v.put("action_type", type); v.put("label", label); v.put("risk", risk); v.put("payload_json", payload.toString()); v.put("state", "PROPOSED"); v.put("created_at", now); v.put("updated_at", now);
                long row = sql.insertWithOnConflict("v4_action_proposals", null, v, SQLiteDatabase.CONFLICT_IGNORE); if (row >= 0) actionsCreated++; else skipped++;
            }
            // Deep Brain is one cognitive lane even when providers change. A valid current model
            // action set retires older model proposals, but never touches local/user proposals.
            if(actionsFieldPresent&&(actions.length()==0||actionsCreated>0))actionsSuperseded=supersedePriorDeepBrainActions(sql,response.requestId,now);

            JSONObject summary = new JSONObject();
            try { summary.put("origin",safeOrigin);summary.put("ranked_priorities_stored",rankedStored);summary.put("priorities_superseded",prioritiesSuperseded);summary.put("priority_updates_applied",prioritiesApplied);summary.put("actions_created",actionsCreated);summary.put("actions_superseded",actionsSuperseded);summary.put("skipped",skipped); } catch(Throwable ignored){}
            String responseId = CognitiveIdentityV4.objectId("brr", "deep-brain-response|" + response.requestId + "|" + Fingerprint.text(response.raw));
            CognitiveDeepBrainStoreV4.saveResponse(sql,responseId,response,summary.toString(),now); CognitiveDeepBrainStoreV4.markApplied(sql,response.requestId,now); sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        return new Result(response.requestId,response.answer,rankedStored,prioritiesApplied,actionsCreated,skipped,false);
    }

    static boolean rankingGrounded(boolean fieldPresent,int inputCount,int storedCount){return !fieldPresent||inputCount<=0||storedCount>0;}
    static boolean hasNewerCanonicalSituation(SQLiteDatabase sql,long requestCreatedAt){
        if(sql==null)return false;Cursor c=sql.rawQuery("SELECT 1 FROM v4_situations WHERE state NOT IN ('RESOLVED','CANCELLED','DISMISSED') AND updated_at>? LIMIT 1",new String[]{String.valueOf(requestCreatedAt)});try{return c.moveToFirst();}finally{c.close();}
    }
    private static int supersedePriorPriorities(SQLiteDatabase sql,String currentRequestId,long when){ContentValues v=new ContentValues();v.put("state","SUPERSEDED");v.put("updated_at",when);return sql.update("v4_deep_brain_priority_items",v,"state='ACTIVE' AND request_id<>?",new String[]{currentRequestId});}
    private static int supersedePriorDeepBrainActions(SQLiteDatabase sql,String currentRequestId,long when){ContentValues v=new ContentValues();v.put("state","SUPERSEDED");v.put("updated_at",when);String current="%\"deep_brain_request_id\":\""+currentRequestId+"\"%";return sql.update("v4_action_proposals",v,"state='PROPOSED' AND payload_json LIKE ? AND payload_json NOT LIKE ?",new String[]{"%\"deep_brain_request_id\":\"%",current});}
    private static SituationSnapshot snapshot(SQLiteDatabase sql,String id){Cursor c=sql.rawQuery("SELECT state,attention_score,interruption_score FROM v4_situations WHERE id=? LIMIT 1",new String[]{id});try{return c.moveToFirst()?new SituationSnapshot(c.getString(0),c.getDouble(1),c.getDouble(2)):null;}finally{c.close();}}
    private static List<String> allowedIds(SQLiteDatabase sql,String table,JSONArray raw,Set<String> allowed){ArrayList<String>out=new ArrayList<>();if(raw==null)return out;for(int i=0;i<raw.length()&&out.size()<12;i++){String id=clean(raw.optString(i,""));if(!id.isEmpty()&&allowed.contains(id)&&exists(sql,table,id)&&!out.contains(id))out.add(id);}return out;}
    private static boolean safeSituationState(String state) { return "DETECTED".equals(state)||"RELEVANT".equals(state)||"SURFACED".equals(state)||"DEFERRED".equals(state)||"WAITING".equals(state); }
    private static String safeActionType(String raw){String x=clean(raw).toUpperCase(Locale.ROOT);try{return CognitiveDomainV4.ActionType.valueOf(x).name();}catch(Throwable ignored){return "CUSTOM";}}
    private static String safeRisk(String type,String raw){String requested=clean(raw).toUpperCase(Locale.ROOT);boolean external="REPLY".equals(type)||"CALL".equals(type)||"SEND".equals(type)||"REMIND".equals(type)||"SCHEDULE".equals(type)||"COMPLETE".equals(type);if(external)return"CONFIRMATION_REQUIRED";try{return CognitiveDomainV4.ActionRisk.valueOf(requested).name();}catch(Throwable ignored){return"CONFIRMATION_REQUIRED";}}
    private static boolean exists(SQLiteDatabase sql,String table,String id){Cursor c=sql.rawQuery("SELECT 1 FROM "+table+" WHERE id=? LIMIT 1",new String[]{id});try{return c.moveToFirst();}finally{c.close();}}
    private static double clamp01(double x){if(Double.isNaN(x)||Double.isInfinite(x))return 0.0;return Math.max(0.0,Math.min(1.0,x));}
    private static String safeOrigin(String origin){String x=clean(origin).toLowerCase(Locale.ROOT);if(x.isEmpty())return ORIGIN_CHATGPT_SHARE;return x.replaceAll("[^a-z0-9._-]","_");}
    private static String clean(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ').replaceAll("\\s+"," ").trim();}
    private static String clip(String s,int n){return s.length()<=n?s:s.substring(0,n)+"…";}
    private static final class SituationSnapshot{final String state;final double attention,interruption;SituationSnapshot(String state,double attention,double interruption){this.state=state;this.attention=attention;this.interruption=interruption;}}

    public static final class Result {
        public final String requestId, answer; public final int rankedPrioritiesStored, priorityUpdatesApplied, actionsCreated, skipped; public final boolean alreadyApplied;
        Result(String requestId,String answer,int rankedStored,int prioritiesApplied,int actionsCreated,int skipped,boolean alreadyApplied){this.requestId=requestId;this.answer=answer==null?"":answer;this.rankedPrioritiesStored=rankedStored;this.priorityUpdatesApplied=prioritiesApplied;this.actionsCreated=actionsCreated;this.skipped=skipped;this.alreadyApplied=alreadyApplied;}
        public String human(){if(alreadyApplied)return"This Deep Brain response was already applied.";return rankedPrioritiesStored+" ranked priorities stored · "+priorityUpdatesApplied+" situations updated · "+actionsCreated+" action proposals added"+(skipped>0?" · "+skipped+" skipped safely":"");}
    }
}
