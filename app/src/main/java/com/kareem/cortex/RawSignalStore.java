package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import org.json.JSONObject;

/** Temporary/raw signal layer. Only promoted signals enter durable Cortex memory. */
public final class RawSignalStore {
    private static final String FAST_POLICY="relevance_fast_001";
    private RawSignalStore(){}

    public static void ensure(VaultDb db){CognitiveStore.ensure(db);}

    public static long capture(VaultDb db,MasterRelevanceFilter.Signal signal){
        ensure(db);cleanup(db);
        String contentHash=Fingerprint.text(signal.text());
        String fp=Fingerprint.text(signal.kind+"|"+signal.source+"|"+signal.title+"|"+signal.body+"|"+(signal.occurredAt/60000));
        long existing=find(db,fp);if(existing>0)return existing;
        MasterRelevanceFilter.Decision decision=MasterRelevanceFilter.evaluateFast(signal);
        long now=System.currentTimeMillis();long retention=retentionUntil(now,decision.disposition);
        ContentValues v=new ContentValues();v.put("kind",signal.kind);v.put("source",signal.source);v.put("title",signal.title);v.put("body",signal.body);v.put("metadata_json",signal.metadataJson);v.put("fingerprint",fp);v.put("content_hash",contentHash);v.put("state","filtered");v.put("disposition",decision.disposition.name());v.put("importance",decision.importance);v.put("confidence",decision.confidence);v.put("policy_version",FAST_POLICY);v.put("filter_engine","deterministic_fast_gate");v.put("reason",decision.reason);v.put("occurred_at",signal.occurredAt>0?signal.occurredAt:now);v.put("retention_until",retention);v.put("created_at",now);v.put("updated_at",now);
        long signalId=db.getWritableDatabase().insert("raw_signals",null,v);if(signalId<=0)return signalId;
        long threadId=SignalThreadStore.attach(db,signalId,signal);
        if(threadId>0)ThreadRelevanceEngine.onSignal(db,threadId,signalId);
        if(decision.durable())promote(db,signalId,threadId,signal,decision);
        return signalId;
    }

    private static void promote(VaultDb db,long signalId,long threadId,MasterRelevanceFilter.Signal s,MasterRelevanceFilter.Decision d){
        try{
            JSONObject meta=new JSONObject();meta.put("raw_signal_id",signalId);if(threadId>0)meta.put("thread_id",threadId);meta.put("source",s.source);meta.put("occurred_at",s.occurredAt);meta.put("relevance_disposition",d.disposition.name());meta.put("importance",d.importance);meta.put("filter_reason",d.reason);meta.put("policy_version",FAST_POLICY);if(!s.metadataJson.isEmpty())meta.put("source_metadata",new JSONObject(s.metadataJson));
            String title=s.title.isEmpty()?friendlyTitle(s):s.title;String tags="signal,"+s.kind.toLowerCase()+",importance_"+d.importance;
            long itemId=db.insert(typeFor(s),s.source,title,s.body,categoryFor(s,d),tags,"",Fingerprint.text("promoted-signal|"+signalId),meta.toString());
            if(itemId>0){
                ContentValues u=new ContentValues();u.put("promoted_item_id",itemId);u.put("state","promoted");u.put("retention_until",0);u.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",u,"id=?",new String[]{String.valueOf(signalId)});
                CognitiveStore.link(db,"raw_signal",signalId,"memory",itemId,"promoted_to",1.0,"{\"policy\":\""+FAST_POLICY+"\"}");if(threadId>0)CognitiveStore.link(db,"memory",itemId,"thread",threadId,"from_thread",1.0,"");
                if(d.disposition==MasterRelevanceFilter.Disposition.ACTION||d.disposition==MasterRelevanceFilter.Disposition.WAITING||d.disposition==MasterRelevanceFilter.Disposition.DECISION){
                    long derived=CognitiveStore.addDerived(db,d.disposition.name(),title,s.body,"open",d.confidence,d.importance,Fingerprint.text("derived|"+d.disposition.name()+"|"+signalId),meta.toString());
                    if(derived>0){CognitiveStore.link(db,"raw_signal",signalId,"derived",derived,"supports",1.0,"");CognitiveStore.link(db,"derived",derived,"memory",itemId,"grounded_by",1.0,"");if(threadId>0)CognitiveStore.link(db,"derived",derived,"thread",threadId,"derived_from_thread",1.0,"");}
                }
            }
        }catch(Throwable ignored){}
    }

    public static long promotedItemId(VaultDb db,long signalId){ensure(db);Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"promoted_item_id"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    public static long threadId(VaultDb db,long signalId){ensure(db);Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"thread_id"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}

    /** Expired raw context can be removed only when no unresolved derived/review item still depends on it. */
    public static void cleanup(VaultDb db){
        ensure(db);long now=System.currentTimeMillis();String where="promoted_item_id=0 AND retention_until>0 AND retention_until<? AND NOT EXISTS (SELECT 1 FROM source_links l JOIN derived_items d ON d.id=l.to_id WHERE l.from_type='raw_signal' AND l.from_id=raw_signals.id AND l.to_type='derived' AND d.state IN ('pending','open'))";
        db.getWritableDatabase().delete("raw_signals",where,new String[]{String.valueOf(now)});
    }

    private static long find(VaultDb db,String fp){Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"id"},"fingerprint=?",new String[]{fp},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    private static long retentionUntil(long now,MasterRelevanceFilter.Disposition d){if(d==MasterRelevanceFilter.Disposition.IGNORE)return now+6L*60*60*1000;if(d==MasterRelevanceFilter.Disposition.CONTEXT||d==MasterRelevanceFilter.Disposition.REVIEW)return now+7L*24*60*60*1000;return 0;}
    private static String typeFor(MasterRelevanceFilter.Signal s){return "notification".equalsIgnoreCase(s.kind)?"NOTIFICATION":"SIGNAL";}
    private static String categoryFor(MasterRelevanceFilter.Signal s,MasterRelevanceFilter.Decision d){if(d.disposition==MasterRelevanceFilter.Disposition.ACTION)return"Actions";if(d.disposition==MasterRelevanceFilter.Disposition.WAITING)return"Waiting";if(d.disposition==MasterRelevanceFilter.Disposition.DECISION)return"Decisions";return"Memory";}
    private static String friendlyTitle(MasterRelevanceFilter.Signal s){return "notification".equalsIgnoreCase(s.kind)?"Notification":"Signal";}
}
