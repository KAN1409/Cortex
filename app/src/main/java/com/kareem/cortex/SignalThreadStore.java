package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.Locale;

/** Groups raw signals into evolving conversations/events before higher-cost relevance analysis. */
public final class SignalThreadStore {
    private SignalThreadStore(){}

    public static long attach(VaultDb db,long signalId,MasterRelevanceFilter.Signal signal){
        if(signalId<=0||signal==null)return 0;CognitiveStore.ensure(db);long now=System.currentTimeMillis();long occurred=signal.occurredAt>0?signal.occurredAt:now;
        Key k=key(signal);String title=empty(signal.title)?friendlySource(signal.source):signal.title.trim();String summary=clip(signal.body,420);
        ContentValues seed=new ContentValues();seed.put("kind",threadKind(signal));seed.put("source",signal.source);seed.put("external_key",k.external);seed.put("title",title);seed.put("participant_key",k.participant);seed.put("state","open");seed.put("summary",summary);seed.put("metadata_json",threadMeta(signal,k));seed.put("started_at",occurred);seed.put("last_event_at",occurred);seed.put("created_at",now);seed.put("updated_at",now);
        db.getWritableDatabase().insertWithOnConflict("signal_threads",null,seed,SQLiteDatabase.CONFLICT_IGNORE);
        long threadId=find(db,threadKind(signal),signal.source,k.external);if(threadId<=0)return 0;
        ContentValues u=new ContentValues();u.put("title",title);u.put("summary",summary);u.put("last_event_at",occurred);u.put("updated_at",now);if(!empty(k.participant))u.put("participant_key",k.participant);db.getWritableDatabase().update("signal_threads",u,"id=?",new String[]{String.valueOf(threadId)});
        ContentValues rs=new ContentValues();rs.put("thread_id",threadId);rs.put("updated_at",now);db.getWritableDatabase().update("raw_signals",rs,"id=?",new String[]{String.valueOf(signalId)});
        CognitiveStore.link(db,"raw_signal",signalId,"thread",threadId,"member_of",1.0,"");return threadId;
    }

    public static int signalCount(VaultDb db,long threadId){if(threadId<=0)return 0;Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM raw_signals WHERE thread_id=?",new String[]{String.valueOf(threadId)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}

    private static long find(VaultDb db,String kind,String source,String external){Cursor c=db.getReadableDatabase().query("signal_threads",new String[]{"id"},"kind=? AND source=? AND external_key=?",new String[]{kind,n(source),external},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}

    private static Key key(MasterRelevanceFilter.Signal s){
        String src=low(s.source),title=LocalSemanticEmbedder.norm(s.title),group="",notification="";try{JSONObject o=new JSONObject(s.metadataJson);group=o.optString("group_key","");notification=o.optString("notification_key","");}catch(Exception ignored){}
        if(conversationSource(src)&&!empty(title))return new Key("participant:"+title,title);
        if(!empty(group))return new Key("group:"+stable(group),title);
        if(!empty(title))return new Key("title:"+title,title);
        if(!empty(notification))return new Key("notification:"+stable(notification),"");
        String basis=s.kind+"|"+s.source+"|"+clip(s.body,160);return new Key("fallback:"+Fingerprint.text(basis),"");
    }

    private static String threadKind(MasterRelevanceFilter.Signal s){String src=low(s.source);if(conversationSource(src))return"communication";if(src.contains("mail")||src.contains("gmail")||src.contains("outlook"))return"email";return n(s.kind).isEmpty()?"signal":s.kind.toLowerCase(Locale.ROOT);}
    private static boolean conversationSource(String s){return s.contains("whatsapp")||s.contains("telegram")||s.contains("messenger")||s.contains("signal")||s.contains("messages")||s.contains("sms");}
    private static String threadMeta(MasterRelevanceFilter.Signal s,Key k){JSONObject o=new JSONObject();try{o.put("source",s.source);o.put("thread_key_strategy",k.external.startsWith("participant:")?"participant":(k.external.startsWith("group:")?"group":"fallback"));o.put("last_signal_kind",s.kind);}catch(Exception ignored){}return o.toString();}
    private static String stable(String x){return Fingerprint.text(x);}
    private static String friendlySource(String s){if(empty(s))return"Context";int i=s.lastIndexOf('.');String x=i>=0&&i<s.length()-1?s.substring(i+1):s;return x.isEmpty()?"Context":Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private static String clip(String s,int n){String x=n(s).trim();return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String low(String s){return n(s).toLowerCase(Locale.ROOT);}private static boolean empty(String s){return s==null||s.trim().isEmpty();}private static String n(String s){return s==null?"":s;}
    private static final class Key{final String external,participant;Key(String e,String p){external=e;participant=p;}}
}
