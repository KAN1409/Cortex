package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Materializes non-request semantic obligations from authoritative relevance decisions. */
public final class SemanticOpenLoopStore {
    private SemanticOpenLoopStore(){}

    public static long upsertWaiting(VaultDb db,AttentionSemantics.Result s){return upsert(db,s,"AWAITING_REPLY",OpenLoopStore.WAITING,"OTHER","Awaiting response or result","INCOMING_RESPONSE");}
    public static long upsertDecision(VaultDb db,AttentionSemantics.Result s){return upsert(db,s,"DECISION_PENDING",OpenLoopStore.OPEN,"USER","Decision needs review","EXPLICIT_DECISION");}

    private static long upsert(VaultDb db,AttentionSemantics.Result s,String type,String state,String owner,String fallback,String resolutionKind){if(db==null||s==null)return 0;CortexAttentionSchema.ensure(db);String semantic=DerivedSemanticIdentity.key(type,s.body),scope=s.threadId>0?"thread:"+s.threadId:"source:"+s.source+"|person:"+s.title,fp=Fingerprint.text("open-loop|"+type+"|"+scope+"|"+semantic);SQLiteDatabase sql=db.getWritableDatabase();long now=System.currentTimeMillis();Cursor c=sql.query("open_loops",new String[]{"id","state"},"fingerprint=?",new String[]{fp},null,null,null,"1");long id=0;String prior="";if(c.moveToFirst()){id=c.getLong(0);prior=n(c.getString(1));}c.close();String subject=subject(s,fallback);
        if(id>0&&active(prior)){ContentValues v=new ContentValues();v.put("subject",subject);v.put("anchor_signal_id",s.signalId);v.put("confidence",Math.max(.55,s.confidence));v.put("updated_at",now);sql.update("open_loops",v,"id=?",new String[]{String.valueOf(id)});}else{if(id>0){ContentValues old=new ContentValues();old.put("fingerprint",Fingerprint.text("historical|"+fp+"|"+id));sql.update("open_loops",old,"id=?",new String[]{String.valueOf(id)});}ContentValues v=new ContentValues();v.put("fingerprint",fp);v.put("type",type);v.put("state",state);v.put("subject",subject);v.put("owner",owner);v.put("person_key",s.title);v.put("thread_id",s.threadId);v.put("anchor_signal_id",s.signalId);v.put("confidence",Math.max(.55,s.confidence));v.put("resolution_kind",resolutionKind);v.put("created_at",now);v.put("updated_at",now);id=sql.insertWithOnConflict("open_loops",null,v,SQLiteDatabase.CONFLICT_IGNORE);}if(id>0)OpenLoopStore.attachEvidence(db,id,s.signalId,"semantic_"+type.toLowerCase(),Math.max(.55,s.confidence));return id;}
    private static String subject(AttentionSemantics.Result s,String fallback){String x=n(s.body);if(x.isEmpty())x=n(s.title);if(x.isEmpty())x=fallback;x=x.replace('\n',' ').replaceAll("\\s+"," ").trim();return x.length()<=120?x:x.substring(0,119)+"…";}
    private static boolean active(String s){return OpenLoopStore.OPEN.equals(s)||OpenLoopStore.WAITING.equals(s)||OpenLoopStore.DUE.equals(s)||OpenLoopStore.OVERDUE.equals(s);}
    private static String n(String s){return s==null?"":s.trim();}
}
