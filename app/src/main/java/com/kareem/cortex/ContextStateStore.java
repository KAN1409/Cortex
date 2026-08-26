package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.*;

/** Durable state manager for live contexts, episodes and the active context stack. */
public final class ContextStateStore {
    public static final String ROLE_PRIMARY="PRIMARY",ROLE_BACKGROUND="BACKGROUND",ROLE_AMBIENT="AMBIENT";
    public static final String LIFE_ACTIVE="ACTIVE",LIFE_SUSPENDED="SUSPENDED",LIFE_COMPLETED="COMPLETED",LIFE_ARCHIVED="ARCHIVED";
    private static final long PRIMARY_HOLD_MS=10L*60L*1000L;
    private static final double SWITCH_MIN=0.78,SWITCH_MARGIN=0.12;
    private ContextStateStore(){}

    public static final class ContextState {
        public final long id,lastActiveAt,lastEvidenceAt,lastTransitionAt;
        public final String stableKey,title,scope,lifecycle,goal,summary,metadataJson,role,transitionReason;
        public final double confidence,stackConfidence;public final int priority;
        ContextState(long id,String key,String title,String scope,String life,double confidence,String goal,String summary,String meta,long lastActive,String role,int priority,double stackConfidence,long evidenceAt,long transitionAt,String reason){this.id=id;stableKey=n(key);this.title=n(title);this.scope=n(scope);lifecycle=n(life);this.confidence=confidence;this.goal=n(goal);this.summary=n(summary);metadataJson=n(meta);lastActiveAt=lastActive;this.role=n(role);this.priority=priority;this.stackConfidence=stackConfidence;lastEvidenceAt=evidenceAt;lastTransitionAt=transitionAt;transitionReason=n(reason);}
        public boolean primary(){return ROLE_PRIMARY.equals(role);}
    }

    public static final class Offer {
        public final long contextId;public final boolean becamePrimary;public final String transition;
        Offer(long id,boolean primary,String transition){contextId=id;becamePrimary=primary;this.transition=n(transition);}
    }

    public static long upsert(VaultDb db,String stableKey,String title,String scope,String lifecycle,double confidence,String goal,String summary,String metadataJson,long evidenceAt){
        if(db==null||n(stableKey).isEmpty())return 0;ContextSchema.ensure(db);SQLiteDatabase s=db.getWritableDatabase();long now=System.currentTimeMillis(),when=evidenceAt>0?evidenceAt:now;Cursor c=s.query("contexts",new String[]{"id"},"stable_key=?",new String[]{stableKey},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();ContentValues v=new ContentValues();v.put("stable_key",stableKey);v.put("title",blank(title)?"Current context":title);v.put("scope",blank(scope)?"TASK":scope);v.put("lifecycle",blank(lifecycle)?LIFE_ACTIVE:lifecycle);v.put("confidence",clamp(confidence));v.put("goal",safe(goal));v.put("summary",safe(summary));v.put("metadata_json",blank(metadataJson)?"{}":metadataJson);v.put("updated_at",now);v.put("last_active_at",when);if(id>0){s.update("contexts",v,"id=?",new String[]{String.valueOf(id)});return id;}v.put("created_at",now);id=s.insert("contexts",null,v);return Math.max(0,id);
    }

    /**
     * Conservative hysteresis: a fresh primary is not displaced unless the challenger is strong
     * enough and clearly better, the current context is stale, or the resolver has identified an
     * explicit semantic boundary such as INTERRUPT/RESUME. App switching alone is never a boundary.
     */
    public static Offer offerPrimary(VaultDb db,long contextId,double confidence,int priority,String reason,long evidenceAt,long anchorSignalId){
        if(db==null||contextId<=0)return new Offer(0,false,"REJECTED");ContextSchema.ensure(db);long now=System.currentTimeMillis(),when=evidenceAt>0?evidenceAt:now;ContextState current=primary(db);ContextState candidate=get(db,contextId);
        if(candidate==null)return new Offer(contextId,false,"REJECTED");
        if(current!=null&&current.id==contextId){touchStack(db,contextId,ROLE_PRIMARY,priority,confidence,when,"CONTINUE · "+safe(reason));ensureOpenEpisode(db,contextId,"CONTINUE",reason,confidence,anchorSignalId,when);return new Offer(contextId,true,"CONTINUE");}
        boolean stale=current==null||now-Math.max(current.lastEvidenceAt,current.lastActiveAt)>PRIMARY_HOLD_MS;
        boolean explicitBoundary=ContextBoundaryDetector.strong(reason);
        boolean strong=confidence>=SWITCH_MIN&&(current==null||confidence>=Math.max(SWITCH_MIN,current.stackConfidence+SWITCH_MARGIN));
        if(current==null||stale||strong||explicitBoundary){
            if(current!=null){touchStack(db,current.id,ROLE_BACKGROUND,Math.max(20,current.priority-15),current.stackConfidence,Math.max(current.lastEvidenceAt,current.lastActiveAt),"SUSPEND · "+safe(reason));closeOpenEpisode(db,current.id,"SUSPEND",reason,now);setLifecycle(db,current.id,LIFE_SUSPENDED);}
            setLifecycle(db,contextId,LIFE_ACTIVE);touchStack(db,contextId,ROLE_PRIMARY,priority,confidence,when,current==null?"START_NEW · "+safe(reason):"RESUME_OR_SWITCH · "+safe(reason));ensureOpenEpisode(db,contextId,current==null?"START_NEW":explicitBoundary&&reason.startsWith(ContextBoundaryDetector.INTERRUPT)?"INTERRUPT":"RESUME",reason,confidence,anchorSignalId,when);return new Offer(contextId,true,current==null?"START_NEW":explicitBoundary&&reason.startsWith(ContextBoundaryDetector.INTERRUPT)?"INTERRUPT":explicitBoundary?"RESUME":"SWITCH");
        }
        touchStack(db,contextId,ROLE_BACKGROUND,Math.min(priority,65),confidence,when,"BACKGROUND_CANDIDATE · "+safe(reason));ensureOpenEpisode(db,contextId,"BACKGROUND",reason,confidence,anchorSignalId,when);return new Offer(contextId,false,"BACKGROUND");
    }

    public static void suspendPrimaryIfStale(VaultDb db,long staleMs,String reason){ContextState p=primary(db);if(p==null)return;long now=System.currentTimeMillis(),age=now-Math.max(p.lastEvidenceAt,p.lastActiveAt);if(age<Math.max(60_000L,staleMs))return;touchStack(db,p.id,ROLE_BACKGROUND,Math.max(20,p.priority-20),p.stackConfidence,p.lastEvidenceAt,"SUSPEND_STALE · "+safe(reason));closeOpenEpisode(db,p.id,"SUSPEND",reason,now);setLifecycle(db,p.id,LIFE_SUSPENDED);}
    /** Explicit non-destructive suspension: context remains resumable but is no longer PRIMARY. */
    public static void suspend(VaultDb db,long contextId,String reason){if(db==null||contextId<=0)return;ContextSchema.ensure(db);ContextState x=get(db,contextId);if(x==null)return;long now=System.currentTimeMillis();touchStack(db,contextId,ROLE_BACKGROUND,Math.max(20,x.priority-20),Math.min(.89,x.stackConfidence),Math.max(x.lastEvidenceAt,x.lastActiveAt),"USER_REJECT · "+safe(reason));closeOpenEpisode(db,contextId,"SUSPEND",reason,now);setLifecycle(db,contextId,LIFE_SUSPENDED);}
    public static void complete(VaultDb db,long contextId,String reason){if(db==null||contextId<=0)return;ContextSchema.ensure(db);SQLiteDatabase s=db.getWritableDatabase();s.delete("context_stack_state","context_id=?",new String[]{String.valueOf(contextId)});closeOpenEpisode(db,contextId,"COMPLETE",reason,System.currentTimeMillis());setLifecycle(db,contextId,LIFE_COMPLETED);}

    public static ContextState primary(VaultDb db){ContextSchema.ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT c.id,c.stable_key,c.title,c.scope,c.lifecycle,c.confidence,c.goal,c.summary,c.metadata_json,c.last_active_at,s.role,s.priority,s.confidence,s.last_evidence_at,s.last_transition_at,s.transition_reason FROM context_stack_state s JOIN contexts c ON c.id=s.context_id WHERE s.role='PRIMARY' ORDER BY s.priority DESC,s.last_evidence_at DESC LIMIT 1",null);ContextState x=c.moveToFirst()?from(c):null;c.close();return x;}
    public static ContextState get(VaultDb db,long id){ContextSchema.ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT c.id,c.stable_key,c.title,c.scope,c.lifecycle,c.confidence,c.goal,c.summary,c.metadata_json,c.last_active_at,COALESCE(s.role,''),COALESCE(s.priority,0),COALESCE(s.confidence,0),COALESCE(s.last_evidence_at,0),COALESCE(s.last_transition_at,0),COALESCE(s.transition_reason,'') FROM contexts c LEFT JOIN context_stack_state s ON s.context_id=c.id WHERE c.id=? LIMIT 1",new String[]{String.valueOf(id)});ContextState x=c.moveToFirst()?from(c):null;c.close();return x;}
    public static ArrayList<ContextState> stack(VaultDb db,int limit){ContextSchema.ensure(db);ArrayList<ContextState> out=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT c.id,c.stable_key,c.title,c.scope,c.lifecycle,c.confidence,c.goal,c.summary,c.metadata_json,c.last_active_at,s.role,s.priority,s.confidence,s.last_evidence_at,s.last_transition_at,s.transition_reason FROM context_stack_state s JOIN contexts c ON c.id=s.context_id ORDER BY CASE s.role WHEN 'PRIMARY' THEN 0 WHEN 'BACKGROUND' THEN 1 ELSE 2 END,s.priority DESC,s.last_evidence_at DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,Math.min(20,limit)))});while(c.moveToNext())out.add(from(c));c.close();return out;}

    public static long recordSnapshot(VaultDb db,long contextId,String currentActivity,String openLoop,String nextStep,String evidenceSummary,String privacyJson){ContextState c=get(db,contextId);if(c==null)return 0;long episode=openEpisodeId(db,contextId);ContentValues v=new ContentValues();v.put("context_id",contextId);v.put("episode_id",episode);v.put("title",c.title);v.put("goal",c.goal);v.put("current_activity",safe(currentActivity));v.put("open_loop",safe(openLoop));v.put("next_step",safe(nextStep));v.put("evidence_summary",safe(evidenceSummary));v.put("privacy_json",blank(privacyJson)?"{}":privacyJson);v.put("created_at",System.currentTimeMillis());return db.getWritableDatabase().insert("context_snapshots",null,v);}

    public static void linkEvidence(VaultDb db,String fromType,long fromId,long contextId,String relation,double confidence,JSONObject metadata){if(db==null||fromId<=0||contextId<=0)return;ContextSchema.ensure(db);ContentValues v=new ContentValues();v.put("from_type",blank(fromType)?"unknown":fromType);v.put("from_id",fromId);v.put("to_type","context");v.put("to_id",contextId);v.put("relation",blank(relation)?"supports":relation);v.put("confidence",clamp(confidence));v.put("metadata_json",metadata==null?"{}":metadata.toString());v.put("created_at",System.currentTimeMillis());try{db.getWritableDatabase().insertWithOnConflict("source_links",null,v,SQLiteDatabase.CONFLICT_IGNORE);}catch(Throwable ignored){}
    }

    public static void feedback(VaultDb db,long contextId,long otherContextId,String eventType,JSONObject value){if(db==null)return;ContextSchema.ensure(db);ContentValues v=new ContentValues();v.put("context_id",Math.max(0,contextId));v.put("other_context_id",Math.max(0,otherContextId));v.put("event_type",safe(eventType));v.put("value_json",value==null?"{}":value.toString());v.put("created_at",System.currentTimeMillis());db.getWritableDatabase().insert("context_feedback",null,v);}

    private static void touchStack(VaultDb db,long contextId,String role,int priority,double confidence,long evidenceAt,String reason){ContentValues v=new ContentValues();long now=System.currentTimeMillis();v.put("context_id",contextId);v.put("role",role);v.put("priority",Math.max(0,Math.min(100,priority)));v.put("confidence",clamp(confidence));v.put("last_evidence_at",evidenceAt>0?evidenceAt:now);v.put("last_transition_at",now);v.put("transition_reason",safe(reason));db.getWritableDatabase().insertWithOnConflict("context_stack_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private static void ensureOpenEpisode(VaultDb db,long contextId,String transition,String reason,double confidence,long anchorSignalId,long startedAt){if(openEpisodeId(db,contextId)>0)return;ContentValues v=new ContentValues();v.put("context_id",contextId);v.put("state","ACTIVE");v.put("transition",safe(transition));v.put("reason",safe(reason));v.put("confidence",clamp(confidence));v.put("anchor_signal_id",Math.max(0,anchorSignalId));v.put("started_at",startedAt>0?startedAt:System.currentTimeMillis());v.put("ended_at",0);v.put("metadata_json","{}");db.getWritableDatabase().insert("context_episodes",null,v);}
    private static long openEpisodeId(VaultDb db,long contextId){Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM context_episodes WHERE context_id=? AND state='ACTIVE' AND ended_at=0 ORDER BY started_at DESC LIMIT 1",new String[]{String.valueOf(contextId)});long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    private static void closeOpenEpisode(VaultDb db,long contextId,String transition,String reason,long endedAt){ContentValues v=new ContentValues();v.put("state","ENDED");v.put("transition",safe(transition));v.put("reason",safe(reason));v.put("ended_at",endedAt>0?endedAt:System.currentTimeMillis());db.getWritableDatabase().update("context_episodes",v,"context_id=? AND state='ACTIVE' AND ended_at=0",new String[]{String.valueOf(contextId)});}
    private static void setLifecycle(VaultDb db,long contextId,String lifecycle){ContentValues v=new ContentValues();v.put("lifecycle",lifecycle);v.put("updated_at",System.currentTimeMillis());if(LIFE_ACTIVE.equals(lifecycle))v.put("last_active_at",System.currentTimeMillis());db.getWritableDatabase().update("contexts",v,"id=?",new String[]{String.valueOf(contextId)});}
    private static ContextState from(Cursor c){return new ContextState(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getDouble(5),c.getString(6),c.getString(7),c.getString(8),c.getLong(9),c.getString(10),c.getInt(11),c.getDouble(12),c.getLong(13),c.getLong(14),c.getString(15));}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static boolean blank(String s){return s==null||s.trim().isEmpty();}private static String safe(String s){return s==null?"":s;}private static String n(String s){return safe(s).trim();}
}
