package com.kareem.cortex.rebuild;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Durable application of model decisions. Evidence remains the source of truth and every derived
 * cognitive product keeps an explicit evidence link. */
public final class BrainStore {
    private BrainStore() {}

    public static void ensure(CortexDb vault) {
        SQLiteDatabase db=vault.getWritableDatabase();
        db.execSQL("CREATE TABLE IF NOT EXISTS brain_runs(evidence_id INTEGER PRIMARY KEY,status TEXT NOT NULL,provider TEXT NOT NULL DEFAULT '',model TEXT NOT NULL DEFAULT '',decision_json TEXT NOT NULL DEFAULT '{}',reason TEXT NOT NULL DEFAULT '',error TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_brain_runs_status ON brain_runs(status,updated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS brain_situation_keys(canonical_key TEXT PRIMARY KEY,situation_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS brain_entity_keys(canonical_key TEXT PRIMARY KEY,entity_id INTEGER NOT NULL)");
    }

    public static boolean applied(CortexDb vault,long evidenceId){ensure(vault);Cursor c=vault.getReadableDatabase().rawQuery("SELECT 1 FROM brain_runs WHERE evidence_id=? AND status='applied' LIMIT 1",new String[]{String.valueOf(evidenceId)});try{return c.moveToFirst();}finally{c.close();}}

    public static void markRunning(CortexDb vault,long evidenceId){ensure(vault);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("evidence_id",evidenceId);v.put("status","running");v.put("created_at",now);v.put("updated_at",now);vault.getWritableDatabase().insertWithOnConflict("brain_runs",null,v,SQLiteDatabase.CONFLICT_IGNORE);ContentValues u=new ContentValues();u.put("status","running");u.put("updated_at",now);u.put("error","");vault.getWritableDatabase().update("brain_runs",u,"evidence_id=?",new String[]{String.valueOf(evidenceId)});vault.markEvidenceState(evidenceId,"brain_thinking",null);}

    public static void markFailed(CortexDb vault,long evidenceId,Exception e){ensure(vault);long now=System.currentTimeMillis();ContentValues u=new ContentValues();u.put("status","failed");u.put("error",compact(e==null?"Brain intake failed":e.getMessage(),360));u.put("updated_at",now);int n=vault.getWritableDatabase().update("brain_runs",u,"evidence_id=?",new String[]{String.valueOf(evidenceId)});if(n==0){u.put("evidence_id",evidenceId);u.put("created_at",now);vault.getWritableDatabase().insert("brain_runs",null,u);}vault.markEvidenceState(evidenceId,"brain_failed",null);}

    public static ApplyResult apply(CortexDb vault,long evidenceId,BrainIntakeEngine.Decision d){
        ensure(vault);SQLiteDatabase db=vault.getWritableDatabase();db.beginTransaction();long memoryId=0,situationId=0;ArrayList<Long> entityIds=new ArrayList<>();
        try{
            if(d.memoryCreate){Cursor c=db.rawQuery("SELECT id FROM memories WHERE evidence_id=? ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(evidenceId)});try{if(c.moveToFirst())memoryId=c.getLong(0);}finally{c.close();}if(memoryId==0){ContentValues m=new ContentValues();m.put("evidence_id",evidenceId);m.put("title",d.memoryTitle);m.put("body",d.memoryBody);m.put("created_at",System.currentTimeMillis());memoryId=db.insertOrThrow("memories",null,m);}}
            if(d.situationCreate){situationId=upsertSituation(db,evidenceId,d);}
            for(BrainIntakeEngine.Entity e:d.entities){long id=upsertEntity(db,evidenceId,e);if(id>0)entityIds.add(id);}
            ContentValues run=new ContentValues();run.put("evidence_id",evidenceId);run.put("status","applied");run.put("provider",d.provider);run.put("model",d.model);run.put("decision_json",d.rawDecisionJson);run.put("reason",d.reason);run.put("error","");long now=System.currentTimeMillis();run.put("created_at",now);run.put("updated_at",now);db.insertWithOnConflict("brain_runs",null,run,SQLiteDatabase.CONFLICT_REPLACE);
            ContentValues ev=new ContentValues();ev.put("state",d.evidenceOnly&&!d.memoryCreate&&!d.situationCreate&&entityIds.isEmpty()?"brain_evidence_only":"brain_applied");db.update("evidence",ev,"id=?",new String[]{String.valueOf(evidenceId)});
            db.setTransactionSuccessful();return new ApplyResult(memoryId,situationId,entityIds,d.evidenceOnly,d.reason);
        }finally{db.endTransaction();}
    }

    private static long upsertSituation(SQLiteDatabase db,long evidenceId,BrainIntakeEngine.Decision d){long id=0;Cursor c=db.rawQuery("SELECT situation_id FROM brain_situation_keys WHERE canonical_key=? LIMIT 1",new String[]{d.situationKey});try{if(c.moveToFirst())id=c.getLong(0);}finally{c.close();}ContentValues v=new ContentValues();v.put("title",d.situationTitle);v.put("summary",d.situationSummary);v.put("state","active");v.put("attention",d.attention);v.put("updated_at",System.currentTimeMillis());if(id>0){int changed=db.update("situations",v,"id=?",new String[]{String.valueOf(id)});if(changed==0)id=0;}if(id==0){id=db.insertOrThrow("situations",null,v);ContentValues k=new ContentValues();k.put("canonical_key",d.situationKey);k.put("situation_id",id);db.insertWithOnConflict("brain_situation_keys",null,k,SQLiteDatabase.CONFLICT_REPLACE);}ContentValues link=new ContentValues();link.put("situation_id",id);link.put("evidence_id",evidenceId);link.put("relation","supports");db.insertWithOnConflict("situation_evidence",null,link,SQLiteDatabase.CONFLICT_IGNORE);return id;}

    private static long upsertEntity(SQLiteDatabase db,long evidenceId,BrainIntakeEngine.Entity e){long id=0;Cursor c=db.rawQuery("SELECT entity_id FROM brain_entity_keys WHERE canonical_key=? LIMIT 1",new String[]{e.canonicalKey});try{if(c.moveToFirst())id=c.getLong(0);}finally{c.close();}ContentValues v=new ContentValues();v.put("entity_type",e.type);v.put("name",e.name);v.put("summary",e.summary);v.put("state","active");v.put("updated_at",System.currentTimeMillis());if(id>0){int changed=db.update("world_entities",v,"id=?",new String[]{String.valueOf(id)});if(changed==0)id=0;}if(id==0){id=db.insertOrThrow("world_entities",null,v);ContentValues k=new ContentValues();k.put("canonical_key",e.canonicalKey);k.put("entity_id",id);db.insertWithOnConflict("brain_entity_keys",null,k,SQLiteDatabase.CONFLICT_REPLACE);}ContentValues link=new ContentValues();link.put("entity_id",id);link.put("evidence_id",evidenceId);link.put("relation","supports");db.insertWithOnConflict("world_entity_evidence",null,link,SQLiteDatabase.CONFLICT_IGNORE);return id;}

    public static String contextSnapshot(CortexDb vault,int limit){ensure(vault);try{JSONObject root=new JSONObject();root.put("situations",rows(vault.activeSituations(limit)));root.put("memories",rows(vault.recentMemories(limit)));root.put("world",rows(vault.worldEntities(limit)));return root.toString();}catch(Exception e){return "{}";}}
    private static JSONArray rows(List<CortexDb.Row> rows)throws Exception{JSONArray a=new JSONArray();for(CortexDb.Row r:rows){JSONObject j=new JSONObject();j.put("id",r.id);j.put("title",r.title);j.put("body",r.body);j.put("type",r.type);a.put(j);}return a;}

    /** Recover every successfully transcribed voice that does not yet have an applied brain decision.
     * brain_thinking is included so process death cannot strand a capture forever. */
    public static List<Long> pendingVoiceEvidence(CortexDb vault,int limit){ensure(vault);ArrayList<Long> out=new ArrayList<>();Cursor c=vault.getReadableDatabase().rawQuery("SELECT e.id FROM evidence e LEFT JOIN brain_runs b ON b.evidence_id=e.id WHERE e.kind LIKE 'AUDIO%' AND e.state IN ('transcribed','brain_failed','brain_thinking') AND (b.status IS NULL OR b.status!='applied') ORDER BY e.id ASC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});try{while(c.moveToNext())out.add(c.getLong(0));}finally{c.close();}return out;}

    public static String transcript(CortexDb vault,long evidenceId){Cursor c=vault.getReadableDatabase().rawQuery("SELECT transcript FROM voice_transcripts WHERE evidence_id=? LIMIT 1",new String[]{String.valueOf(evidenceId)});try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}

    /** Product-facing audit of deliberate voice captures. Evidence-only is a legitimate brain
     * destination and must remain visible instead of appearing to vanish. */
    public static List<BrainOutcome> recentVoiceOutcomes(CortexDb vault,int limit){
        ensure(vault);ArrayList<BrainOutcome> out=new ArrayList<>();
        Cursor c=vault.getReadableDatabase().rawQuery(
                "SELECT e.id,e.occurred_at,COALESCE(v.transcript,e.body),e.state,"+
                "COALESCE(b.status,''),COALESCE(b.reason,''),COALESCE(b.error,''),"+
                "EXISTS(SELECT 1 FROM memories m WHERE m.evidence_id=e.id),"+
                "EXISTS(SELECT 1 FROM situation_evidence se WHERE se.evidence_id=e.id),"+
                "EXISTS(SELECT 1 FROM world_entity_evidence we WHERE we.evidence_id=e.id) " +
                "FROM evidence e LEFT JOIN voice_transcripts v ON v.evidence_id=e.id " +
                "LEFT JOIN brain_runs b ON b.evidence_id=e.id " +
                "WHERE e.kind LIKE 'AUDIO%' ORDER BY e.id DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1,limit))});
        try{while(c.moveToNext())out.add(new BrainOutcome(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getInt(7)!=0,c.getInt(8)!=0,c.getInt(9)!=0));}finally{c.close();}
        return out;
    }

    public static BrainOutcome outcome(CortexDb vault,long evidenceId){
        ensure(vault);Cursor c=vault.getReadableDatabase().rawQuery(
                "SELECT e.id,e.occurred_at,COALESCE(v.transcript,e.body),e.state,"+
                "COALESCE(b.status,''),COALESCE(b.reason,''),COALESCE(b.error,''),"+
                "EXISTS(SELECT 1 FROM memories m WHERE m.evidence_id=e.id),"+
                "EXISTS(SELECT 1 FROM situation_evidence se WHERE se.evidence_id=e.id),"+
                "EXISTS(SELECT 1 FROM world_entity_evidence we WHERE we.evidence_id=e.id) " +
                "FROM evidence e LEFT JOIN voice_transcripts v ON v.evidence_id=e.id LEFT JOIN brain_runs b ON b.evidence_id=e.id WHERE e.id=? LIMIT 1",
                new String[]{String.valueOf(evidenceId)});
        try{return c.moveToFirst()?new BrainOutcome(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getInt(7)!=0,c.getInt(8)!=0,c.getInt(9)!=0):null;}finally{c.close();}
    }

    private static String compact(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}

    public static final class ApplyResult{
        public final long memoryId,situationId;public final List<Long> entityIds;public final boolean evidenceOnly;public final String reason;
        ApplyResult(long memoryId,long situationId,List<Long> entityIds,boolean evidenceOnly,String reason){this.memoryId=memoryId;this.situationId=situationId;this.entityIds=entityIds;this.evidenceOnly=evidenceOnly;this.reason=reason==null?"":reason;}
        public String destination(){ArrayList<String> x=new ArrayList<>();if(situationId>0)x.add("Now");if(memoryId>0)x.add("Memory");if(entityIds!=null&&!entityIds.isEmpty())x.add("World");if(x.isEmpty())x.add("Evidence");return android.text.TextUtils.join(" + ",x);}
    }

    public static final class BrainOutcome{
        public final long evidenceId,occurredAt;public final String transcript,evidenceState,brainStatus,reason,error;public final boolean memory,situation,world;
        BrainOutcome(long evidenceId,long occurredAt,String transcript,String evidenceState,String brainStatus,String reason,String error,boolean memory,boolean situation,boolean world){this.evidenceId=evidenceId;this.occurredAt=occurredAt;this.transcript=transcript==null?"":transcript;this.evidenceState=evidenceState==null?"":evidenceState;this.brainStatus=brainStatus==null?"":brainStatus;this.reason=reason==null?"":reason;this.error=error==null?"":error;this.memory=memory;this.situation=situation;this.world=world;}
        public String destination(){ArrayList<String>x=new ArrayList<>();if(situation)x.add("Now");if(memory)x.add("Memory");if(world)x.add("World");if(x.isEmpty()&&"applied".equals(brainStatus))x.add("Evidence only");if(x.isEmpty()&&"failed".equals(brainStatus))x.add("Brain retry pending");if(x.isEmpty()&&"running".equals(brainStatus))x.add("Brain thinking");if(x.isEmpty())x.add("Awaiting brain");return android.text.TextUtils.join(" + ",x);}
    }
}
