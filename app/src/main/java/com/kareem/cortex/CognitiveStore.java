package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Small write API over the unified cognitive schema. */
public final class CognitiveStore {
    private CognitiveStore(){}

    /**
     * Keep the legacy cognitive schema operational while also materializing the additive V4
     * canonical tables. No existing write/read path is redirected by this bootstrap.
     */
    public static void ensure(VaultDb db){
        CognitiveSchema.ensure(db.getWritableDatabase());
        CognitiveStateBackfillV2.ensure(db.getWritableDatabase());
        CognitiveSchemaV4.ensure(db.getWritableDatabase());
    }

    public static void link(VaultDb db,String fromType,long fromId,String toType,long toId,String relation,double confidence,String metadataJson){linkChecked(db,fromType,fromId,toType,toId,relation,confidence,metadataJson);}
    public static boolean linkChecked(VaultDb db,String fromType,long fromId,String toType,long toId,String relation,double confidence,String metadataJson){
        if(fromId<=0||toId<=0||empty(fromType)||empty(toType)||empty(relation))return false;
        ensure(db);ContentValues v=new ContentValues();v.put("from_type",fromType);v.put("from_id",fromId);v.put("to_type",toType);v.put("to_id",toId);v.put("relation",relation);v.put("confidence",confidence);v.put("metadata_json",n(metadataJson));v.put("created_at",System.currentTimeMillis());
        long id=db.getWritableDatabase().insertWithOnConflict("source_links",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id>0)return true;
        Cursor c=db.getReadableDatabase().query("source_links",new String[]{"id"},"from_type=? AND from_id=? AND to_type=? AND to_id=? AND relation=?",new String[]{fromType,String.valueOf(fromId),toType,String.valueOf(toId),relation},null,null,null,"1");boolean exists=c.moveToFirst();c.close();return exists;
    }

    public static long addDerived(VaultDb db,String kind,String title,String body,String state,double confidence,int importance,String fingerprint,String metadataJson){
        ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("kind",n(kind).toUpperCase());v.put("title",empty(title)?friendly(kind):title.trim());v.put("body",n(body));v.put("state",empty(state)?"open":state);v.put("confidence",confidence);v.put("importance",importance);v.put("fingerprint",n(fingerprint));v.put("metadata_json",n(metadataJson));v.put("created_at",now);v.put("updated_at",now);
        SQLiteDatabase sql=db.getWritableDatabase();long id=sql.insertWithOnConflict("derived_items",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id>0)return id;
        if(empty(fingerprint))return id;

        Cursor c=sql.query("derived_items",new String[]{"id","state"},"fingerprint=?",new String[]{fingerprint},null,null,null,"1");long existing=0;String existingState="";if(c.moveToFirst()){existing=c.getLong(0);existingState=n(c.getString(1));}c.close();if(existing<=0)return 0;
        if(active(existingState))return existing;

        // The same semantic obligation may legitimately recur after completion/expiry.
        // Archive the historical fingerprint, then let the canonical fingerprint identify the new active occurrence.
        ContentValues history=new ContentValues();history.put("fingerprint",Fingerprint.text("historical-derived|"+fingerprint+"|"+existing));history.put("updated_at",now);if(sql.update("derived_items",history,"id=? AND fingerprint=?",new String[]{String.valueOf(existing),fingerprint})<=0)return 0;
        long retry=sql.insertWithOnConflict("derived_items",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(retry>0)return retry;
        Cursor current=sql.query("derived_items",new String[]{"id","state"},"fingerprint=?",new String[]{fingerprint},null,null,null,"1");long currentId=0;if(current.moveToFirst()&&active(n(current.getString(1))))currentId=current.getLong(0);current.close();return currentId;
    }

    /**
     * Canonical V2 persistence boundary. Brain providers never touch SQLite; they return a
     * CognitiveItem and this store materializes/upserts the durable derived intelligence with
     * typed routing fields and provenance.
     */
    public static CognitiveItemWrite addCognitiveItem(VaultDb db,CognitiveItem item,long signalId,long threadId,long modelRunId,
                                                       String sourceKey,double confidence,int priorityScore,String metadataJson){
        if(db==null||item==null||item.kind==null||signalId<=0||item.summary.trim().isEmpty())return CognitiveItemWrite.failed("invalid cognitive item persistence arguments");
        try{
            ensure(db);SQLiteDatabase sql=db.getWritableDatabase();long now=System.currentTimeMillis();String kind=item.kind.name();
            String semanticKey=DerivedSemanticIdentity.key(kind,item.summary);long existing=0;
            if(threadId>0&&!semanticKey.isEmpty()){
                Cursor c=sql.query("derived_items",new String[]{"id"},"thread_id=? AND kind=? AND state='open' AND COALESCE(semantic_key,'')=?",new String[]{String.valueOf(threadId),kind,semanticKey},null,null,"updated_at DESC","1");try{existing=c.moveToFirst()?c.getLong(0):0;}finally{c.close();}
            }
            String fingerprint=Fingerprint.text("cognitive-v2-derived|"+kind+"|"+(threadId>0?"thread:"+threadId+"|"+semanticKey:"signal:"+signalId+"|"+semanticKey));
            long derivedId=existing;
            if(derivedId<=0)derivedId=addDerived(db,kind,item.summary,item.summary,"open",confidence,item.importance,fingerprint,metadataJson);
            if(derivedId<=0)return CognitiveItemWrite.failed("derived item insert failed");

            ContentValues v=new ContentValues();v.put("kind",kind);v.put("title",item.summary);v.put("body",item.summary);v.put("state","open");v.put("confidence",Math.max(0,Math.min(1,confidence)));v.put("importance",Math.max(0,Math.min(100,item.importance)));v.put("metadata_json",n(metadataJson));v.put("source_key",n(sourceKey));v.put("thread_id",Math.max(0,threadId));v.put("anchor_signal_id",signalId);v.put("candidate_kind",kind);v.put("semantic_key",semanticKey);v.put("urgency",Math.max(0,Math.min(100,item.urgency)));v.put("person_key",item.person);v.put("due_at",Math.max(0,item.dueAt));v.put("requires_user_action",item.requiresUserAction?1:0);v.put("requires_follow_up",item.requiresFollowUp?1:0);v.put("requires_content_extraction",item.requiresContentExtraction?1:0);v.put("cognitive_run_id",Math.max(0,modelRunId));v.put("priority_score",Math.max(0,Math.min(100,priorityScore)));v.put("updated_at",now);
            if(sql.update("derived_items",v,"id=?",new String[]{String.valueOf(derivedId)})<=0)return CognitiveItemWrite.failed("derived typed-field update failed");
            if(!linkChecked(db,"raw_signal",signalId,"derived",derivedId,"supports",1.0,"{\"cognitive_run_id\":"+Math.max(0,modelRunId)+"}"))return CognitiveItemWrite.failed("raw signal provenance link failed");
            if(threadId>0&&!linkChecked(db,"thread",threadId,"derived",derivedId,"produced",confidence,""))return CognitiveItemWrite.failed("thread provenance link failed");
            return new CognitiveItemWrite(true,derivedId,semanticKey,priorityScore,"");
        }catch(Throwable e){return CognitiveItemWrite.failed(e.getClass().getSimpleName()+": "+n(e.getMessage()));}
    }

    public static final class CognitiveItemWrite{
        public final boolean success;public final long derivedId;public final String semanticKey,detail;public final int priorityScore;
        CognitiveItemWrite(boolean success,long derivedId,String semanticKey,int priorityScore,String detail){this.success=success;this.derivedId=derivedId;this.semanticKey=n(semanticKey);this.priorityScore=priorityScore;this.detail=n(detail);}
        static CognitiveItemWrite failed(String detail){return new CognitiveItemWrite(false,0,"",0,detail);}
    }

    /** Hot routing fields stay typed/indexed; metadata_json remains flexible provenance. */
    public static void setDerivedRouting(VaultDb db,long derivedId,String sourceKey,long threadId,long anchorSignalId,String candidateKind){setDerivedRoutingChecked(db,derivedId,sourceKey,threadId,anchorSignalId,candidateKind,"");}
    public static boolean setDerivedRoutingChecked(VaultDb db,long derivedId,String sourceKey,long threadId,long anchorSignalId,String candidateKind,String semanticKey){
        if(db==null||derivedId<=0)return false;ensure(db);ContentValues v=new ContentValues();v.put("source_key",n(sourceKey));v.put("thread_id",Math.max(0,threadId));v.put("anchor_signal_id",Math.max(0,anchorSignalId));v.put("candidate_kind",n(candidateKind).toUpperCase());if(!empty(semanticKey))v.put("semantic_key",semanticKey);v.put("updated_at",System.currentTimeMillis());return db.getWritableDatabase().update("derived_items",v,"id=?",new String[]{String.valueOf(derivedId)})>0;
    }

    public static void feedback(VaultDb db,String targetType,long targetId,String eventType,String valueJson,String policyVersion){
        if(targetId<=0||empty(targetType)||empty(eventType))return;ensure(db);String source="",candidate="";
        if("derived".equalsIgnoreCase(targetType)){
            Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"source_key","candidate_kind","kind"},"id=?",new String[]{String.valueOf(targetId)},null,null,null,"1");
            if(c.moveToFirst()){source=n(c.getString(0));candidate=n(c.getString(1));if(candidate.isEmpty()&&"REVIEW".equalsIgnoreCase(n(c.getString(2))))candidate="UNKNOWN";}c.close();
        }
        ContentValues v=new ContentValues();v.put("target_type",targetType);v.put("target_id",targetId);v.put("event_type",eventType);v.put("value_json",n(valueJson));v.put("policy_version",n(policyVersion));v.put("source_key",source);v.put("candidate_kind",candidate.toUpperCase());v.put("scope_key",source+"|"+candidate.toUpperCase());v.put("created_at",System.currentTimeMillis());db.getWritableDatabase().insert("feedback_events",null,v);
    }

    public static String schemaRevision(VaultDb db){ensure(db);Cursor c=db.getReadableDatabase().query("schema_meta",new String[]{"value"},"key='cognitive_schema'",null,null,null,null,"1");String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}

    private static boolean active(String state){return"open".equalsIgnoreCase(n(state))||"pending".equalsIgnoreCase(n(state));}
    private static String friendly(String kind){String x=n(kind).toLowerCase().replace('_',' ');return x.isEmpty()?"Derived intelligence":Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String n(String s){return s==null?"":s;}
}
