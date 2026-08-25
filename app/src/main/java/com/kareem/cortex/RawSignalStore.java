package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import org.json.JSONObject;

/** Temporary/raw signal layer. Only an applied authoritative decision may enter durable Cortex memory. */
public final class RawSignalStore {
    private static final String FAST_POLICY="relevance_fast_004";
    private RawSignalStore(){}

    public static void ensure(VaultDb db){CognitiveStore.ensure(db);}

    public static long capture(VaultDb db,MasterRelevanceFilter.Signal signal){
        ensure(db);cleanup(db);String contentHash=Fingerprint.text(signal.text());String fp=Fingerprint.text(signal.kind+"|"+signal.source+"|"+signal.title+"|"+signal.body+"|"+(signal.occurredAt/60000));long existing=find(db,fp);if(existing>0)return existing;MasterRelevanceFilter.Decision fast=fastDecision(signal);long now=System.currentTimeMillis(),retention=retentionUntil(now,fast.disposition);
        ContentValues v=new ContentValues();v.put("kind",signal.kind);v.put("source",signal.source);v.put("title",signal.title);v.put("body",signal.body);v.put("metadata_json",signal.metadataJson);v.put("fingerprint",fp);v.put("content_hash",contentHash);v.put("state","filtered");v.put("disposition",fast.disposition.name());v.put("importance",fast.importance);v.put("confidence",fast.confidence);v.put("policy_version",FAST_POLICY);v.put("filter_engine","deterministic_fast_gate");v.put("reason",fast.reason);v.put("occurred_at",signal.occurredAt>0?signal.occurredAt:now);v.put("retention_until",retention);v.put("created_at",now);v.put("updated_at",now);
        long signalId=db.getWritableDatabase().insert("raw_signals",null,v);if(signalId<=0){DiagnosticsLog.warn(db,"RawSignalStore","capture_insert","failed","RAW_SIGNAL_INSERT",0,0,0,0,0,null);return signalId;}

        long threadId=SignalThreadStore.attach(db,signalId,signal);MasterRelevanceFilter.Decision authority=fast;boolean threadAuthority=false;
        if(threadId>0){MasterRelevanceFilter.Decision threaded=ThreadRelevanceEngine.onSignal(db,threadId,signalId);if(threaded!=null){authority=threaded;threadAuthority=true;}}

        // Relevance decides meaning. ContextMemoryGate independently decides lifetime/promotion and
        // links even temporary evidence to the active cognitive situation without polluting Vault.
        ContextMemoryGate.Decision memoryGate=ContextMemoryGate.evaluate(db,signal,authority,threadId);
        ContextMemoryGate.linkEvidence(db,signalId,memoryGate);

        // The fast gate is only authoritative when there is no thread-aware policy. Never promote
        // from a stale fast decision, and never bypass the memory-lifetime gate.
        if(authority.durable()&&memoryGate.durable()&&(!threadAuthority||RelevanceDecisionStatusStore.isApplied(db,signalId)))
            promote(db,signalId,threadId,signal,authority,memoryGate,!threadAuthority);
        return signalId;
    }

    /** Explicit screen understanding is evidence/context only; UI text can never auto-create durable intelligence. */
    private static MasterRelevanceFilter.Decision fastDecision(MasterRelevanceFilter.Signal s){if(s!=null&&"screen_context".equalsIgnoreCase(s.kind))return new MasterRelevanceFilter.Decision(MasterRelevanceFilter.Disposition.CONTEXT,38,"explicit screen evidence; short-lived context until the user asks or promotes it","",0.94);return MasterRelevanceFilter.evaluateFast(s);}

    /**
     * Materialize the raw signal as a knowledge item. Thread-aware policy already owns its derived intelligence,
     * so createDerived=false prevents a second ACTION/WAITING/DECISION from the same notification.
     */
    private static long promote(VaultDb db,long signalId,long threadId,MasterRelevanceFilter.Signal s,MasterRelevanceFilter.Decision d,ContextMemoryGate.Decision gate,boolean createDerived){
        try{
            JSONObject meta=new JSONObject();meta.put("raw_signal_id",signalId);if(threadId>0)meta.put("thread_id",threadId);meta.put("source",s.source);meta.put("occurred_at",s.occurredAt);meta.put("relevance_disposition",d.disposition.name());meta.put("importance",d.importance);meta.put("effective_importance",gate==null?d.importance:gate.effectiveImportance);meta.put("filter_reason",d.reason);meta.put("policy_version",createDerived?FAST_POLICY:"thread_authority");if(gate!=null){meta.put("memory_tier",gate.tier.name());meta.put("memory_gate_reason",gate.reason);if(gate.contextId>0)meta.put("context_id",gate.contextId);}if(!s.metadataJson.isEmpty())meta.put("source_metadata",new JSONObject(s.metadataJson));
            int effectiveImportance=gate==null?d.importance:gate.effectiveImportance;String title=s.title.isEmpty()?friendlyTitle(s):s.title,tags="signal,"+s.kind.toLowerCase()+",importance_"+effectiveImportance;long inserted=db.insert(typeFor(s),s.source,title,s.body,categoryFor(s,d),tags,"",Fingerprint.text("promoted-signal|"+signalId),meta.toString());long itemId=inserted<0?-inserted:inserted;
            if(itemId>0){ContentValues u=new ContentValues();u.put("promoted_item_id",itemId);u.put("state","promoted");u.put("retention_until",0);u.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",u,"id=?",new String[]{String.valueOf(signalId)});CognitiveStore.link(db,"raw_signal",signalId,"memory",itemId,"promoted_to",1.0,"{\"policy\":\""+(createDerived?FAST_POLICY:"thread_authority")+"\",\"memory_gate\":true}");if(threadId>0)CognitiveStore.link(db,"memory",itemId,"thread",threadId,"from_thread",1.0,"");ContextMemoryGate.linkPromotedMemory(db,itemId,gate);
                if(createDerived&&(d.disposition==MasterRelevanceFilter.Disposition.ACTION||d.disposition==MasterRelevanceFilter.Disposition.WAITING||d.disposition==MasterRelevanceFilter.Disposition.DECISION)){long derived=CognitiveStore.addDerived(db,d.disposition.name(),title,s.body,"open",d.confidence,effectiveImportance,Fingerprint.text("derived|"+d.disposition.name()+"|"+signalId),meta.toString());if(derived>0){CognitiveStore.setDerivedRouting(db,derived,s.source,threadId,signalId,d.disposition.name());CognitiveStore.link(db,"raw_signal",signalId,"derived",derived,"supports",1.0,"");CognitiveStore.link(db,"derived",derived,"memory",itemId,"grounded_by",1.0,"");if(threadId>0)CognitiveStore.link(db,"derived",derived,"thread",threadId,"derived_from_thread",1.0,"");if(gate!=null&&gate.contextId>0)CognitiveStore.link(db,"derived",derived,"context",gate.contextId,"belongs_to_context",Math.max(.55,gate.contextConfidence),ContextMemoryGate.provenanceJson(gate));}}
            }
            return itemId;
        }catch(Throwable e){DiagnosticsLog.error(db,"RawSignalStore","promote",e,"RAW_SIGNAL_PROMOTE",0,threadId,signalId,0,0,null);return 0;}
    }

    public static long promotedItemId(VaultDb db,long signalId){ensure(db);Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"promoted_item_id"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    public static long threadId(VaultDb db,long signalId){ensure(db);Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"thread_id"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    public static void cleanup(VaultDb db){ensure(db);long now=System.currentTimeMillis();String where="promoted_item_id=0 AND retention_until>0 AND retention_until<? AND NOT EXISTS (SELECT 1 FROM source_links l JOIN derived_items d ON d.id=l.to_id WHERE l.from_type='raw_signal' AND l.from_id=raw_signals.id AND l.to_type='derived' AND d.state IN ('pending','open'))";db.getWritableDatabase().delete("raw_signals",where,new String[]{String.valueOf(now)});}
    private static long find(VaultDb db,String fp){Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"id"},"fingerprint=?",new String[]{fp},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    private static long retentionUntil(long now,MasterRelevanceFilter.Disposition d){if(d==MasterRelevanceFilter.Disposition.IGNORE)return now+6L*60*60*1000;if(d==MasterRelevanceFilter.Disposition.CONTEXT||d==MasterRelevanceFilter.Disposition.REVIEW)return now+7L*24*60*60*1000;return 0;}
    private static String typeFor(MasterRelevanceFilter.Signal s){return "notification".equalsIgnoreCase(s.kind)?"NOTIFICATION":"SIGNAL";}
    private static String categoryFor(MasterRelevanceFilter.Signal s,MasterRelevanceFilter.Decision d){if(d.disposition==MasterRelevanceFilter.Disposition.ACTION)return"Actions";if(d.disposition==MasterRelevanceFilter.Disposition.WAITING)return"Waiting";if(d.disposition==MasterRelevanceFilter.Disposition.DECISION)return"Decisions";return"Memory";}
    private static String friendlyTitle(MasterRelevanceFilter.Signal s){return "notification".equalsIgnoreCase(s.kind)?"Notification":"Signal";}
}
