package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/**
 * Canonical grounded state above events.
 * Truth Objects are never source evidence themselves: every active object must point back to one or more truth_events.
 */
public final class TruthObjectStore {
    public static final String ACTION="ACTION",WAITING="WAITING",DECISION="DECISION",IMPORTANT="IMPORTANT";
    public static final String OPEN="OPEN",CONFIRMED="CONFIRMED",RESOLVED="RESOLVED",DISMISSED="DISMISSED";

    public static final class Item {
        public final long id,threadId,eventId,signalId,memoryId,firstSeenAt,lastSeenAt,resolvedAt;
        public final String kind,state,title,body,source,semanticKey,metadataJson;
        public final double confidence;
        public final int importance;
        Item(long id,String kind,String state,String title,String body,String source,double confidence,int importance,
             String semanticKey,long threadId,long eventId,long signalId,long memoryId,long firstSeenAt,long lastSeenAt,
             long resolvedAt,String metadataJson){
            this.id=id;this.kind=n(kind);this.state=n(state);this.title=n(title);this.body=n(body);this.source=n(source);
            this.confidence=confidence;this.importance=importance;this.semanticKey=n(semanticKey);this.threadId=threadId;
            this.eventId=eventId;this.signalId=signalId;this.memoryId=memoryId;this.firstSeenAt=firstSeenAt;this.lastSeenAt=lastSeenAt;
            this.resolvedAt=resolvedAt;this.metadataJson=n(metadataJson);
        }
        public String text(){return body.isEmpty()?title:body;}
    }

    private TruthObjectStore(){}

    public static long upsertFromEvent(VaultDb db,long eventId,String kind,String title,String body,String source,
                                       double confidence,int importance,String semanticKey,long threadId,
                                       long signalId,long memoryId,long occurredAt,String metadataJson){
        if(db==null||eventId<=0||empty(kind)||empty(semanticKey))return 0;
        TruthSchema.ensure(db);SQLiteDatabase s=db.getWritableDatabase();String k=n(kind).toUpperCase(Locale.ROOT);
        long now=System.currentTimeMillis(),seen=occurredAt>0?occurredAt:now;
        long id=findActiveBySemantic(s,k,semanticKey);
        if(id<=0)id=findActiveByEvent(s,k,eventId);
        String initial=(DECISION.equals(k)||IMPORTANT.equals(k))?CONFIRMED:OPEN;

        if(id>0){
            ContentValues u=new ContentValues();u.put("title",friendlyTitle(k,title,body));u.put("body",n(body));u.put("source_key",n(source));
            u.put("confidence",clamp(confidence));u.put("importance",Math.max(0,Math.min(100,importance)));u.put("semantic_key",semanticKey);
            if(threadId>0)u.put("thread_id",threadId);if(signalId>0)u.put("anchor_signal_id",signalId);if(memoryId>0)u.put("anchor_memory_id",memoryId);
            u.put("last_seen_at",seen);u.put("metadata_json",n(metadataJson));
            s.update("truth_objects",u,"id=?",new String[]{String.valueOf(id)});
        }else{
            ContentValues v=new ContentValues();v.put("kind",k);v.put("state",initial);v.put("title",friendlyTitle(k,title,body));v.put("body",n(body));
            v.put("source_key",n(source));v.put("confidence",clamp(confidence));v.put("importance",Math.max(0,Math.min(100,importance)));v.put("semantic_key",semanticKey);
            v.put("thread_id",Math.max(0,threadId));v.put("anchor_event_id",eventId);v.put("anchor_signal_id",Math.max(0,signalId));v.put("anchor_memory_id",Math.max(0,memoryId));
            v.put("first_seen_at",seen);v.put("last_seen_at",seen);v.put("resolved_at",0);v.put("metadata_json",n(metadataJson));
            id=s.insertWithOnConflict("truth_objects",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            if(id<=0)id=findActiveBySemantic(s,k,semanticKey);
        }
        if(id>0)linkEvidence(s,id,eventId,confidence);
        return id;
    }

    /** Remove a stale interpretation only when this event is its sole evidence authority. */
    public static void reconcileEventKinds(VaultDb db,long eventId,Set<String> supportedKinds){
        if(db==null||eventId<=0)return;TruthSchema.ensure(db);SQLiteDatabase s=db.getWritableDatabase();
        HashSet<String> supported=new HashSet<>();if(supportedKinds!=null)for(String x:supportedKinds)supported.add(n(x).toUpperCase(Locale.ROOT));
        Cursor c=s.rawQuery("SELECT id,kind,state FROM truth_objects WHERE anchor_event_id=? AND state IN ('OPEN','CONFIRMED')",new String[]{String.valueOf(eventId)});
        ArrayList<Long> stale=new ArrayList<>();
        while(c.moveToNext()){long id=c.getLong(0);String kind=n(c.getString(1)).toUpperCase(Locale.ROOT);if(supported.contains(kind))continue;
            Cursor e=s.rawQuery("SELECT COUNT(DISTINCT event_id) FROM truth_evidence WHERE truth_id=?",new String[]{String.valueOf(id)});
            int evidence=e.moveToFirst()?e.getInt(0):0;e.close();if(evidence<=1)stale.add(id);
        }c.close();
        for(Long id:stale)transition(db,id,DISMISSED,"Evidence no longer supports this interpretation",eventId);
    }

    public static ArrayList<Item> active(VaultDb db,String kind,int limit){
        TruthSchema.ensure(db);ArrayList<Item> out=new ArrayList<>();String where="state IN ('OPEN','CONFIRMED')";
        ArrayList<String> args=new ArrayList<>();if(!empty(kind)){where+=" AND kind=?";args.add(n(kind).toUpperCase(Locale.ROOT));}
        Cursor c=db.getReadableDatabase().query("truth_objects",columns(),where,args.toArray(new String[0]),null,null,"importance DESC,last_seen_at DESC",String.valueOf(Math.max(1,limit)));
        while(c.moveToNext())out.add(from(c));c.close();return out;
    }

    public static Item get(VaultDb db,long id){
        if(db==null||id<=0)return null;TruthSchema.ensure(db);Cursor c=db.getReadableDatabase().query("truth_objects",columns(),"id=?",new String[]{String.valueOf(id)},null,null,null,"1");
        Item x=c.moveToFirst()?from(c):null;c.close();return x;
    }

    public static boolean resolve(VaultDb db,long id,String reason){return transition(db,id,RESOLVED,empty(reason)?"Resolved by user":reason,0);}
    public static boolean dismiss(VaultDb db,long id,String reason){return transition(db,id,DISMISSED,empty(reason)?"Dismissed":reason,0);}

    private static boolean transition(VaultDb db,long id,String to,String reason,long evidenceEventId){
        if(db==null||id<=0)return false;TruthSchema.ensure(db);SQLiteDatabase s=db.getWritableDatabase();
        Cursor c=s.query("truth_objects",new String[]{"state"},"id=?",new String[]{String.valueOf(id)},null,null,null,"1");String from=c.moveToFirst()?n(c.getString(0)):"";c.close();
        if(from.isEmpty()||to.equals(from))return !from.isEmpty();
        long now=System.currentTimeMillis();ContentValues u=new ContentValues();u.put("state",to);u.put("last_seen_at",now);if(RESOLVED.equals(to)||DISMISSED.equals(to))u.put("resolved_at",now);
        if(s.update("truth_objects",u,"id=?",new String[]{String.valueOf(id)})<=0)return false;
        ContentValues t=new ContentValues();t.put("truth_id",id);t.put("from_state",from);t.put("to_state",to);t.put("reason",n(reason));t.put("evidence_event_id",Math.max(0,evidenceEventId));t.put("created_at",now);
        s.insert("truth_transitions",null,t);return true;
    }

    private static void linkEvidence(SQLiteDatabase s,long truthId,long eventId,double confidence){
        ContentValues e=new ContentValues();e.put("truth_id",truthId);e.put("event_id",eventId);e.put("relation","supports");e.put("confidence",clamp(confidence));e.put("created_at",System.currentTimeMillis());
        s.insertWithOnConflict("truth_evidence",null,e,SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static long findActiveBySemantic(SQLiteDatabase s,String kind,String semanticKey){
        Cursor c=s.rawQuery("SELECT id FROM truth_objects WHERE kind=? AND semantic_key=? AND state IN ('OPEN','CONFIRMED') ORDER BY last_seen_at DESC LIMIT 1",new String[]{kind,semanticKey});
        long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;
    }
    private static long findActiveByEvent(SQLiteDatabase s,String kind,long eventId){
        Cursor c=s.rawQuery("SELECT id FROM truth_objects WHERE kind=? AND anchor_event_id=? AND state IN ('OPEN','CONFIRMED') ORDER BY last_seen_at DESC LIMIT 1",new String[]{kind,String.valueOf(eventId)});
        long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;
    }

    private static String[] columns(){return new String[]{"id","kind","state","title","body","source_key","confidence","importance","semantic_key","thread_id","anchor_event_id","anchor_signal_id","anchor_memory_id","first_seen_at","last_seen_at","resolved_at","metadata_json"};}
    private static Item from(Cursor c){return new Item(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getDouble(6),c.getInt(7),c.getString(8),c.getLong(9),c.getLong(10),c.getLong(11),c.getLong(12),c.getLong(13),c.getLong(14),c.getLong(15),c.getString(16));}
    private static String friendlyTitle(String kind,String title,String body){String t=n(title);if(!t.isEmpty())return t;String b=n(body).replaceAll("\\s+"," ");if(!b.isEmpty())return b.length()<=100?b:b.substring(0,100)+"…";return Character.toUpperCase(kind.charAt(0))+kind.substring(1).toLowerCase(Locale.ROOT);}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String n(String s){return s==null?"":s.trim();}
}
