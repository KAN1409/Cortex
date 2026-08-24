package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/** Groups raw signals into evolving conversations/events before higher-cost relevance analysis. */
public final class SignalThreadStore {
    public static final long THREAD_EXPIRY_MS=48L*60L*60L*1000L;
    private SignalThreadStore(){}

    public static long attach(VaultDb db,long signalId,MasterRelevanceFilter.Signal signal){
        if(signalId<=0||signal==null)return 0;CognitiveStore.ensure(db);long now=System.currentTimeMillis();long occurred=signal.occurredAt>0?signal.occurredAt:now;
        Key k=key(signal);String kind=threadKind(signal),source=n(signal.source);String title=empty(signal.title)?friendlySource(source):signal.title.trim();String summary=clip(signal.body,420);long threadId=0;
        SQLiteDatabase w=db.getWritableDatabase();w.beginTransaction();
        try{
            threadId=findActiveThread(db,kind,source,k.external,occurred);
            if(threadId<=0){
                archiveStaleBaseThread(db,kind,source,k.external,occurred,now);
                ContentValues seed=new ContentValues();seed.put("kind",kind);seed.put("source",source);seed.put("external_key",k.external);seed.put("title",title);seed.put("participant_key",k.participant);seed.put("state","open");seed.put("summary",summary);seed.put("metadata_json",threadMeta(signal,k));seed.put("started_at",occurred);seed.put("last_event_at",occurred);seed.put("created_at",now);seed.put("updated_at",now);
                w.insertWithOnConflict("signal_threads",null,seed,SQLiteDatabase.CONFLICT_IGNORE);
                threadId=findActiveThread(db,kind,source,k.external,occurred);
            }
            if(threadId>0){
                long previousLast=lastEventAt(db,threadId);ContentValues u=new ContentValues();u.put("title",title);u.put("summary",summary);u.put("last_event_at",Math.max(previousLast,occurred));u.put("updated_at",now);if(!empty(k.participant))u.put("participant_key",k.participant);w.update("signal_threads",u,"id=?",new String[]{String.valueOf(threadId)});
                ContentValues rs=new ContentValues();rs.put("thread_id",threadId);rs.put("updated_at",now);w.update("raw_signals",rs,"id=?",new String[]{String.valueOf(signalId)});
            }
            w.setTransactionSuccessful();
        }finally{w.endTransaction();}
        if(threadId>0)CognitiveStore.link(db,"raw_signal",signalId,"thread",threadId,"member_of",1.0,"");return threadId;
    }

    /** Only the currently open episode with activity within 48h is eligible. */
    private static long findActiveThread(VaultDb db,String kind,String source,String external,long occurredAt){
        long cutoff=occurredAt-THREAD_EXPIRY_MS;Cursor c=db.getReadableDatabase().query("signal_threads",new String[]{"id"},"kind=? AND source=? AND external_key=? AND last_event_at>=? AND state='open'",new String[]{kind,n(source),external,String.valueOf(cutoff)},null,null,"last_event_at DESC,id DESC","1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;
    }

    /**
     * The schema intentionally keeps one exact base key unique. When an episode expires,
     * rename the historical key before inserting the next episode; thread ids and provenance
     * remain stable, while the new active episode can reuse the canonical conversation key.
     */
    private static void archiveStaleBaseThread(VaultDb db,String kind,String source,String external,long occurredAt,long now){
        Cursor c=db.getReadableDatabase().query("signal_threads",new String[]{"id","last_event_at","state"},"kind=? AND source=? AND external_key=?",new String[]{kind,n(source),external},null,null,"last_event_at DESC,id DESC","1");
        if(!c.moveToFirst()){c.close();return;}long id=c.getLong(0),last=c.getLong(1);String state=n(c.getString(2));c.close();
        long cutoff=occurredAt-THREAD_EXPIRY_MS;if("open".equals(state)&&last>=cutoff)return;
        ContentValues u=new ContentValues();u.put("external_key",external+"|closed:"+id);u.put("state","closed");u.put("updated_at",now);db.getWritableDatabase().update("signal_threads",u,"id=?",new String[]{String.valueOf(id)});
    }

    /** Chronological context from the newest N signals in this thread episode. */
    public static String recentContext(VaultDb db,long threadId,int maxSignals){
        if(threadId<=0)return"";int lim=Math.max(1,Math.min(20,maxSignals));Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"title","body"},"thread_id=?",new String[]{String.valueOf(threadId)},null,null,"occurred_at DESC,id DESC",String.valueOf(lim));ArrayList<String> parts=new ArrayList<>();
        while(c.moveToNext()){String title=n(c.getString(0)),body=n(c.getString(1));String text=!body.isEmpty()?body:title;if(text.isEmpty())continue;parts.add(clip(text,700));}c.close();Collections.reverse(parts);
        StringBuilder out=new StringBuilder();for(String part:parts){if(out.length()>0)out.append("\n---\n");out.append(part);if(out.length()>6000){out.setLength(6000);out.append("…");break;}}return out.toString();
    }

    public static int signalCount(VaultDb db,long threadId){if(threadId<=0)return 0;Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM raw_signals WHERE thread_id=?",new String[]{String.valueOf(threadId)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    private static long lastEventAt(VaultDb db,long threadId){Cursor c=db.getReadableDatabase().query("signal_threads",new String[]{"last_event_at"},"id=?",new String[]{String.valueOf(threadId)},null,null,null,"1");long t=c.moveToFirst()?c.getLong(0):0;c.close();return t;}

    private static Key key(MasterRelevanceFilter.Signal s){
        String src=low(s.source),title=LocalSemanticEmbedder.norm(s.title),group="",notification="";try{JSONObject o=new JSONObject(s.metadataJson);group=o.optString("group_key","");notification=o.optString("notification_key","");}catch(Exception ignored){}
        if(conversationSignal(s)&&!empty(title))return new Key("participant:"+title,title);
        if(!empty(group))return new Key("group:"+stable(group),title);
        if(!empty(title))return new Key("title:"+title,title);
        if(!empty(notification))return new Key("notification:"+stable(notification),"");
        String basis=s.kind+"|"+s.source+"|"+clip(s.body,160);return new Key("fallback:"+Fingerprint.text(basis),"");
    }

    private static String threadKind(MasterRelevanceFilter.Signal s){String src=low(s.source),notificationKind=notificationKind(s);if(conversationSource(src)||"message".equals(notificationKind))return"communication";if(src.contains("mail")||src.contains("gmail")||src.contains("outlook")||"email".equals(notificationKind))return"email";return n(s.kind).isEmpty()?"signal":s.kind.toLowerCase(Locale.ROOT);}
    private static boolean conversationSignal(MasterRelevanceFilter.Signal s){return conversationSource(low(s.source))||"message".equals(notificationKind(s));}
    private static boolean conversationSource(String s){return s.contains("whatsapp")||s.contains("telegram")||s.contains("messenger")||s.contains("messaging")||s.contains("messages")||s.contains("signal")||s.contains("sms");}
    private static String notificationKind(MasterRelevanceFilter.Signal s){try{return low(new JSONObject(n(s.metadataJson)).optString("notification_kind",""));}catch(Exception ignored){return"";}}
    private static String threadMeta(MasterRelevanceFilter.Signal s,Key k){JSONObject o=new JSONObject();try{o.put("source",s.source);o.put("thread_key_strategy",k.external.startsWith("participant:")?"participant":(k.external.startsWith("group:")?"group":"fallback"));o.put("last_signal_kind",s.kind);o.put("episode_expiry_ms",THREAD_EXPIRY_MS);}catch(Exception ignored){}return o.toString();}
    private static String stable(String x){return Fingerprint.text(x);}
    private static String friendlySource(String s){if(empty(s))return"Context";int i=s.lastIndexOf('.');String x=i>=0&&i<s.length()-1?s.substring(i+1):s;return x.isEmpty()?"Context":Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private static String clip(String s,int max){String x=n(s).trim();return x.length()<=max?x:x.substring(0,max)+"…";}
    private static String low(String s){return n(s).toLowerCase(Locale.ROOT);}private static boolean empty(String s){return s==null||s.trim().isEmpty();}private static String n(String s){return s==null?"":s;}
    private static final class Key{final String external,participant;Key(String e,String p){external=e;participant=p;}}
}
