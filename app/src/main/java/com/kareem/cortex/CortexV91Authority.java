package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/**
 * v91 authority/data-hygiene boundary.
 *
 * This class never edits immutable raw evidence. It only retires invalid derived/live projections,
 * preserves their history, and verifies that Now is owned exclusively by CortexAttentionJudge.
 */
public final class CortexV91Authority {
    public static final String VERSION="cortex_v91_authority_001";
    private static final String MIGRATION_KEY="cortex_v91_authority_migration";
    private CortexV91Authority(){}

    public static final class CleanupResult {
        public final int legacyVisual,nonIntentionalProjects,invalidSituations,unauthorizedAttention,visualAttention,mirrorRepairs;
        CleanupResult(int l,int p,int s,int a,int v,int m){legacyVisual=l;nonIntentionalProjects=p;invalidSituations=s;unauthorizedAttention=a;visualAttention=v;mirrorRepairs=m;}
    }

    public static final class Validation {
        public final boolean ok;
        public final int openVisualLegacyActions,nonIntentionalProjectCandidates,invalidOpenSituations,
                unauthorizedOpenAttention,visualOpenAttention,invalidAttentionKinds,policyMismatches,mirrorMismatches,openAttention,maxNowItems;
        Validation(int l,int p,int s,int u,int v,int k,int pm,int mm,int open,int max){
            openVisualLegacyActions=l;nonIntentionalProjectCandidates=p;invalidOpenSituations=s;
            unauthorizedOpenAttention=u;visualOpenAttention=v;invalidAttentionKinds=k;policyMismatches=pm;mirrorMismatches=mm;
            openAttention=open;maxNowItems=max;
            ok=l==0&&p==0&&s==0&&u==0&&v==0&&k==0&&pm==0&&mm==0&&open<=Math.max(1,max);
        }
        public String summary(){return "legacyVisual="+openVisualLegacyActions+", nonIntentionalProjects="+nonIntentionalProjectCandidates+
                ", invalidSituations="+invalidOpenSituations+", unauthorizedAttention="+unauthorizedOpenAttention+
                ", visualAttention="+visualOpenAttention+", invalidKinds="+invalidAttentionKinds+
                ", policyMismatch="+policyMismatches+", mirrorMismatch="+mirrorMismatches+
                ", openAttention="+openAttention+"/"+maxNowItems;}
        public JSONObject toJson(){try{return new JSONObject().put("version",VERSION).put("ok",ok)
                .put("openVisualLegacyActions",openVisualLegacyActions)
                .put("nonIntentionalProjectCandidates",nonIntentionalProjectCandidates)
                .put("invalidOpenSituations",invalidOpenSituations)
                .put("unauthorizedOpenAttention",unauthorizedOpenAttention)
                .put("visualOpenAttention",visualOpenAttention)
                .put("invalidAttentionKinds",invalidAttentionKinds)
                .put("policyMismatches",policyMismatches)
                .put("mirrorMismatches",mirrorMismatches)
                .put("openAttention",openAttention).put("maxNowItems",maxNowItems)
                .put("summary",summary());}catch(Exception e){return new JSONObject();}}
    }

    /** One-time, non-destructive cleanup for historical v90-and-earlier derived state. */
    public static CleanupResult migrate(SQLiteDatabase db){
        if(db==null)return new CleanupResult(0,0,0,0,0,0);
        ensure(db);
        if(done(db))return new CleanupResult(0,0,0,0,0,0);
        boolean own=!db.inTransaction();if(own)db.beginTransaction();
        try{
            long now=System.currentTimeMillis();
            ContentValues filtered=new ContentValues();filtered.put("state","filtered");filtered.put("resolved_at",now);filtered.put("updated_at",now);
            int legacy=db.update("derived_items",filtered,
                    "state IN ('open','pending') AND kind IN ('ACTION','WAITING','DECISION') " +
                    "AND COALESCE(candidate_kind,'')<>'UE_ATTENTION' AND COALESCE(thread_id,0)=0 AND COALESCE(anchor_signal_id,0)=0 " +
                    "AND (LOWER(COALESCE(source_key,'')) LIKE '%picbrain%' OR LOWER(COALESCE(source_key,'')) LIKE '%knowledge_v2%' " +
                    "OR LOWER(COALESCE(source_key,'')) LIKE '%screen_understand%' OR LOWER(COALESCE(source_key,'')) LIKE '%screenshot%')",null);

            int projects=db.update("derived_items",filtered,
                    "state IN ('open','pending') AND kind='PROJECT_CANDIDATE' AND ("+
                    "COALESCE(metadata_json,'') LIKE '%\"intentional\":false%' OR " +
                    "LOWER(COALESCE(source_key,'')) NOT IN ('manual','manual_recording','quick_capture'))",null);

            int invalid=quarantineInvalidSituations(db,now);
            int unauthorized=suppressUnauthorizedAttention(db,now);
            int visual=suppressVisualAttention(db,now);
            int mirrors=repairMirrors(db,now);
            ContentValues mark=new ContentValues();mark.put("key",MIGRATION_KEY);mark.put("value",VERSION);mark.put("updated_at",now);
            db.insertWithOnConflict("schema_meta",null,mark,SQLiteDatabase.CONFLICT_REPLACE);
            if(own)db.setTransactionSuccessful();
            return new CleanupResult(legacy,projects,invalid,unauthorized,visual,mirrors);
        }finally{if(own)db.endTransaction();}
    }

    /** Cheap live invariant enforcement. Safe to run before/after every final materialization. */
    public static CleanupResult enforceLive(SQLiteDatabase db){
        if(db==null)return new CleanupResult(0,0,0,0,0,0);ensure(db);long now=System.currentTimeMillis();
        int unauthorized=suppressUnauthorizedAttention(db,now);
        int visual=suppressVisualAttention(db,now);
        int mirrors=repairMirrors(db,now);
        return new CleanupResult(0,0,0,unauthorized,visual,mirrors);
    }

    public static Validation validate(SQLiteDatabase db,String activePolicyVersion,int maxNowItems){
        if(db==null)return new Validation(1,1,1,1,1,1,1,1,1,Math.max(1,maxNowItems));ensure(db);
        int legacy=count(db,"SELECT COUNT(*) FROM derived_items WHERE state IN ('open','pending') AND kind IN ('ACTION','WAITING','DECISION') AND COALESCE(candidate_kind,'')<>'UE_ATTENTION' AND COALESCE(thread_id,0)=0 AND COALESCE(anchor_signal_id,0)=0 AND (LOWER(COALESCE(source_key,'')) LIKE '%picbrain%' OR LOWER(COALESCE(source_key,'')) LIKE '%knowledge_v2%' OR LOWER(COALESCE(source_key,'')) LIKE '%screen_understand%' OR LOWER(COALESCE(source_key,'')) LIKE '%screenshot%')",null);
        int projects=count(db,"SELECT COUNT(*) FROM derived_items WHERE state IN ('open','pending') AND kind='PROJECT_CANDIDATE' AND (COALESCE(metadata_json,'') LIKE '%\"intentional\":false%' OR LOWER(COALESCE(source_key,'')) NOT IN ('manual','manual_recording','quick_capture'))",null);
        int invalid=countInvalidSituations(db);
        int unauthorized=count(db,"SELECT COUNT(*) FROM ue_attention_items WHERE state='open' AND COALESCE(reason,'') NOT LIKE 'FINAL_JUDGE:%'",null);
        int visual=count(db,"SELECT COUNT(*) FROM ue_attention_items a JOIN ue_semantic_events e ON e.id=a.semantic_event_id JOIN ue_raw_observations r ON r.id=e.raw_observation_id WHERE a.state='open' AND (LOWER(COALESCE(r.source_type,''))='visual_evidence' OR LOWER(COALESCE(r.source_key,'')) LIKE '%picbrain%' OR LOWER(COALESCE(r.source_key,'')) LIKE '%knowledge_v2%' OR LOWER(COALESCE(r.source_key,'')) LIKE '%screen_understand%')",null);
        int kinds=count(db,"SELECT COUNT(*) FROM ue_attention_items WHERE state='open' AND kind NOT IN ('ACTION','WAITING','DECISION')",null);
        int mismatch=count(db,"SELECT COUNT(*) FROM ue_attention_items a WHERE a.state='open' AND NOT EXISTS (SELECT 1 FROM cortex_judgment_trace t WHERE t.id=(SELECT t2.id FROM cortex_judgment_trace t2 WHERE t2.situation_id=a.situation_id ORDER BY t2.id DESC LIMIT 1) AND t.policy_version=? AND t.final_decision='NOW')",new String[]{n(activePolicyVersion)});
        int mirror=count(db,"SELECT COUNT(*) FROM ue_attention_items a LEFT JOIN derived_items d ON d.id=?+a.id AND d.candidate_kind='UE_ATTENTION' WHERE (a.state='open' AND (d.id IS NULL OR d.state<>'open')) OR (a.state<>'open' AND d.state='open')",new String[]{String.valueOf(UniversalEventStore.ATTENTION_COMPAT_OFFSET)});
        int open=count(db,"SELECT COUNT(*) FROM ue_attention_items WHERE state='open'",null);
        return new Validation(legacy,projects,invalid,unauthorized,visual,kinds,mismatch,mirror,open,Math.max(1,Math.min(12,maxNowItems)));
    }

    private static int quarantineInvalidSituations(SQLiteDatabase db,long now){
        Cursor c=db.rawQuery("SELECT s.id,r.source_type,r.source_key,r.technical_type,e.semantic_type,e.intent,e.subject,e.summary,e.confidence " +
                "FROM ue_situations s JOIN ue_situation_members_v2 m ON m.situation_id=s.id " +
                "JOIN ue_semantic_events e ON e.id=m.semantic_event_id JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                "WHERE s.state='open' AND e.superseded_by=0 AND e.id=(SELECT e2.id FROM ue_situation_members_v2 m2 " +
                "JOIN ue_semantic_events e2 ON e2.id=m2.semantic_event_id WHERE m2.situation_id=s.id AND e2.superseded_by=0 " +
                "ORDER BY e2.occurred_at DESC,e2.id DESC LIMIT 1)",null);
        int n=0;while(c.moveToNext()){
            long id=c.getLong(0);CanonicalSemanticQualityGate.Result q=CanonicalSemanticQualityGate.evaluate(c.getString(1),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getDouble(8));
            if(q.eligible)continue;
            ContentValues s=new ContentValues();s.put("state","quarantined_noise");s.put("resolved_at",now);s.put("updated_at",now);if(db.update("ue_situations",s,"id=? AND state='open'",new String[]{String.valueOf(id)})>0)n++;
            ContentValues a=new ContentValues();a.put("state","suppressed");a.put("reason","v91 authority hardening: "+q.reason);a.put("resolved_at",now);a.put("updated_at",now);db.update("ue_attention_items",a,"situation_id=? AND state='open'",new String[]{String.valueOf(id)});
            // World-state working row is disposable; membership/history remains preserved.
            db.delete("ue_situation_state_v2","situation_id=?",new String[]{String.valueOf(id)});
        }c.close();return n;
    }

    private static int countInvalidSituations(SQLiteDatabase db){
        Cursor c=db.rawQuery("SELECT s.id,r.source_type,r.technical_type,e.semantic_type,e.intent,e.subject,e.summary,e.confidence " +
                "FROM ue_situations s JOIN ue_situation_members_v2 m ON m.situation_id=s.id JOIN ue_semantic_events e ON e.id=m.semantic_event_id " +
                "JOIN ue_raw_observations r ON r.id=e.raw_observation_id WHERE s.state='open' AND e.superseded_by=0 AND e.id=(SELECT e2.id FROM ue_situation_members_v2 m2 JOIN ue_semantic_events e2 ON e2.id=m2.semantic_event_id WHERE m2.situation_id=s.id AND e2.superseded_by=0 ORDER BY e2.occurred_at DESC,e2.id DESC LIMIT 1)",null);
        int n=0;while(c.moveToNext()){CanonicalSemanticQualityGate.Result q=CanonicalSemanticQualityGate.evaluate(c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getDouble(7));if(!q.eligible)n++;}c.close();return n;
    }

    private static int suppressUnauthorizedAttention(SQLiteDatabase db,long now){
        ContentValues v=new ContentValues();v.put("state","suppressed");v.put("resolved_at",now);v.put("updated_at",now);v.put("reason","v91 authority hardening: only FINAL_JUDGE may own Now");
        return db.update("ue_attention_items",v,"state='open' AND COALESCE(reason,'') NOT LIKE 'FINAL_JUDGE:%'",null);
    }

    private static int suppressVisualAttention(SQLiteDatabase db,long now){
        ContentValues v=new ContentValues();v.put("state","suppressed");v.put("resolved_at",now);v.put("updated_at",now);v.put("reason","v91 authority hardening: visual evidence cannot establish an attention obligation by itself");
        return db.update("ue_attention_items",v,"state='open' AND semantic_event_id IN (SELECT e.id FROM ue_semantic_events e JOIN ue_raw_observations r ON r.id=e.raw_observation_id WHERE LOWER(COALESCE(r.source_type,''))='visual_evidence' OR LOWER(COALESCE(r.source_key,'')) LIKE '%picbrain%' OR LOWER(COALESCE(r.source_key,'')) LIKE '%knowledge_v2%' OR LOWER(COALESCE(r.source_key,'')) LIKE '%screen_understand%')",null);
    }

    private static int repairMirrors(SQLiteDatabase db,long now){
        ContentValues v=new ContentValues();v.put("state","suppressed");v.put("resolved_at",now);v.put("updated_at",now);
        int n=db.update("derived_items",v,"candidate_kind='UE_ATTENTION' AND state='open' AND EXISTS(SELECT 1 FROM ue_attention_items a WHERE a.id=derived_items.id-? AND a.state<>'open')",new String[]{String.valueOf(UniversalEventStore.ATTENTION_COMPAT_OFFSET)});
        return n;
    }

    private static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS schema_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at INTEGER NOT NULL)");
        UniversalEventStore.ensure(db);StatefulMeaningStore.ensure(db);CortexJudgmentTraceStore.ensure(db);
    }
    private static boolean done(SQLiteDatabase db){Cursor c=db.rawQuery("SELECT value FROM schema_meta WHERE key=? LIMIT 1",new String[]{MIGRATION_KEY});boolean yes=c.moveToFirst()&&VERSION.equals(c.getString(0));c.close();return yes;}
    private static int count(SQLiteDatabase db,String sql,String[] args){Cursor c=db.rawQuery(sql,args);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    private static String n(String s){return s==null?"":s.trim();}
}
