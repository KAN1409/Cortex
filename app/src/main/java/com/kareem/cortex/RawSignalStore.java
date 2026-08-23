package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Temporary/raw signal layer. Only promoted signals enter knowledge_items/Vault. */
public final class RawSignalStore {
    private RawSignalStore(){}

    public static void ensure(VaultDb db){
        SQLiteDatabase x=db.getWritableDatabase();
        x.execSQL("CREATE TABLE IF NOT EXISTS raw_signals(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,source TEXT,title TEXT,body TEXT,metadata_json TEXT,fingerprint TEXT UNIQUE,state TEXT DEFAULT 'filtered',disposition TEXT,importance INTEGER DEFAULT 0,reason TEXT,promoted_item_id INTEGER DEFAULT 0,occurred_at INTEGER NOT NULL,retention_until INTEGER DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        x.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_signal_time ON raw_signals(occurred_at DESC)");
        x.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_signal_disposition ON raw_signals(disposition,occurred_at DESC)");
        x.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_signal_source ON raw_signals(source,occurred_at DESC)");
    }

    public static long capture(VaultDb db,MasterRelevanceFilter.Signal signal){
        ensure(db);cleanup(db);
        String fp=Fingerprint.text(signal.kind+"|"+signal.source+"|"+signal.title+"|"+signal.body+"|"+(signal.occurredAt/60000));
        long existing=find(db,fp);if(existing>0)return existing;
        MasterRelevanceFilter.Decision decision=MasterRelevanceFilter.evaluateFast(signal);
        long now=System.currentTimeMillis();long retention=retentionUntil(now,decision.disposition);
        ContentValues v=new ContentValues();v.put("kind",signal.kind);v.put("source",signal.source);v.put("title",signal.title);v.put("body",signal.body);v.put("metadata_json",signal.metadataJson);v.put("fingerprint",fp);v.put("state","filtered");v.put("disposition",decision.disposition.name());v.put("importance",decision.importance);v.put("reason",decision.reason);v.put("occurred_at",signal.occurredAt>0?signal.occurredAt:now);v.put("retention_until",retention);v.put("created_at",now);v.put("updated_at",now);
        long signalId=db.getWritableDatabase().insert("raw_signals",null,v);if(signalId<=0)return signalId;
        if(decision.durable())promote(db,signalId,signal,decision);
        return signalId;
    }

    private static void promote(VaultDb db,long signalId,MasterRelevanceFilter.Signal s,MasterRelevanceFilter.Decision d){
        try{
            JSONObject meta=new JSONObject();meta.put("raw_signal_id",signalId);meta.put("source",s.source);meta.put("occurred_at",s.occurredAt);meta.put("relevance_disposition",d.disposition.name());meta.put("importance",d.importance);meta.put("filter_reason",d.reason);if(!s.metadataJson.isEmpty())meta.put("source_metadata",new JSONObject(s.metadataJson));
            String title=s.title.isEmpty()?friendlyTitle(s):s.title;String tags="signal,"+s.kind.toLowerCase()+",importance_"+d.importance;
            long itemId=db.insert(typeFor(s),s.source,title,s.body,categoryFor(s,d),tags,"",Fingerprint.text("promoted-signal|"+signalId),meta.toString());
            if(itemId>0){ContentValues u=new ContentValues();u.put("promoted_item_id",itemId);u.put("state","promoted");u.put("retention_until",0);u.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",u,"id=?",new String[]{String.valueOf(signalId)});}
        }catch(Throwable ignored){}
    }

    public static long promotedItemId(VaultDb db,long signalId){ensure(db);Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"promoted_item_id"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}

    public static void cleanup(VaultDb db){
        ensure(db);long now=System.currentTimeMillis();
        db.getWritableDatabase().delete("raw_signals","promoted_item_id=0 AND retention_until>0 AND retention_until<?",new String[]{String.valueOf(now)});
    }

    private static long find(VaultDb db,String fp){Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"id"},"fingerprint=?",new String[]{fp},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    private static long retentionUntil(long now,MasterRelevanceFilter.Disposition d){if(d==MasterRelevanceFilter.Disposition.IGNORE)return now+6L*60*60*1000;if(d==MasterRelevanceFilter.Disposition.CONTEXT)return now+7L*24*60*60*1000;return 0;}
    private static String typeFor(MasterRelevanceFilter.Signal s){return "notification".equalsIgnoreCase(s.kind)?"NOTIFICATION":"SIGNAL";}
    private static String categoryFor(MasterRelevanceFilter.Signal s,MasterRelevanceFilter.Decision d){if(d.disposition==MasterRelevanceFilter.Disposition.ACTION)return"Actions";if(d.disposition==MasterRelevanceFilter.Disposition.WAITING)return"Waiting";if(d.disposition==MasterRelevanceFilter.Disposition.DECISION)return"Decisions";return"Memory";}
    private static String friendlyTitle(MasterRelevanceFilter.Signal s){return "notification".equalsIgnoreCase(s.kind)?"Notification":"Signal";}
}
