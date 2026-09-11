package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/**
 * Stateful notification event pipeline.
 *
 * Raw observations are immutable evidence. Streams hold current state. Semantic events are
 * meaningful transitions only. Android categories are treated as platform hints, never as
 * Cortex semantic truth.
 */
public final class NotificationEventEngine {
    private static final long CROSS_STREAM_DEDUP_MS=3_000L;
    public static final class Result {
        public final long rawId,streamId,semanticEventId;
        public final String transition,technicalType,platformHint;
        public final boolean shouldUnderstand;
        Result(long raw,long stream,long semantic,String tr,String tech,String hint,boolean understand){
            rawId=raw;streamId=stream;semanticEventId=semantic;transition=safe(tr);technicalType=safe(tech);platformHint=safe(hint);shouldUnderstand=understand;
        }
    }

    private NotificationEventEngine(){}

    public static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS notification_raw_observations(id INTEGER PRIMARY KEY AUTOINCREMENT,stream_key TEXT NOT NULL,package_name TEXT NOT NULL,app_label TEXT,event_type TEXT NOT NULL,title TEXT,body TEXT,content_hash TEXT,platform_hint TEXT,technical_type TEXT,metadata_json TEXT,occurred_at INTEGER NOT NULL,created_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_raw_time ON notification_raw_observations(occurred_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_raw_stream ON notification_raw_observations(stream_key,occurred_at ASC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_raw_pkg ON notification_raw_observations(package_name,occurred_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_raw_hash ON notification_raw_observations(package_name,content_hash,occurred_at DESC)");

        s.execSQL("CREATE TABLE IF NOT EXISTS notification_streams(id INTEGER PRIMARY KEY AUTOINCREMENT,stream_key TEXT UNIQUE NOT NULL,package_name TEXT NOT NULL,app_label TEXT,title TEXT,body TEXT,content_hash TEXT,platform_hint TEXT,technical_type TEXT,state TEXT NOT NULL DEFAULT 'active',progress INTEGER DEFAULT 0,progress_max INTEGER DEFAULT 0,observation_count INTEGER DEFAULT 0,meaningful_count INTEGER DEFAULT 0,first_seen_at INTEGER NOT NULL,last_seen_at INTEGER NOT NULL,last_meaningful_at INTEGER DEFAULT 0,metadata_json TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_stream_recent ON notification_streams(last_seen_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_stream_pkg ON notification_streams(package_name,last_seen_at DESC)");

        s.execSQL("CREATE TABLE IF NOT EXISTS notification_semantic_events(id INTEGER PRIMARY KEY AUTOINCREMENT,stream_id INTEGER NOT NULL,raw_observation_id INTEGER NOT NULL,transition TEXT NOT NULL,technical_type TEXT,platform_hint TEXT,semantic_state TEXT NOT NULL DEFAULT 'pending',semantic_type TEXT DEFAULT '',confidence REAL DEFAULT 0,reason TEXT DEFAULT '',occurred_at INTEGER NOT NULL,created_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_semantic_recent ON notification_semantic_events(occurred_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_semantic_state ON notification_semantic_events(semantic_state,occurred_at ASC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_semantic_stream ON notification_semantic_events(stream_id,occurred_at ASC)");
    }

    public static Result ingest(VaultDb db,String pkg,String appLabel,String eventType,String title,String body,long occurredAt,JSONObject meta){
        ensure(db);
        long now=System.currentTimeMillis(),when=occurredAt>0?occurredAt:now;
        String p=safe(pkg),app=safe(appLabel),ev=safe(eventType),t=safe(title),b=safe(body);
        JSONObject m=meta==null?new JSONObject():meta;
        String hint=platformHint(m),tech=technicalType(p,m,hint),streamKey=streamKey(p,m),hash=Fingerprint.text(t+"\n"+b+"\n"+semanticPayload(m));
        SQLiteDatabase s=db.getWritableDatabase();

        long streamId=0;String oldHash="",oldState="",oldTech="";int oldProgress=0,oldProgressMax=0,oldMeaningful=0;
        Cursor c=s.rawQuery("SELECT id,content_hash,state,technical_type,progress,progress_max,meaningful_count FROM notification_streams WHERE stream_key=? LIMIT 1",new String[]{streamKey});
        if(c.moveToFirst()){streamId=c.getLong(0);oldHash=safe(c.getString(1));oldState=safe(c.getString(2));oldTech=safe(c.getString(3));oldProgress=c.getInt(4);oldProgressMax=c.getInt(5);oldMeaningful=c.getInt(6);}c.close();

        int progress=m.optInt("progress",0),progressMax=m.optInt("progress_max",0);
        String transition=transition(streamId,ev,hash,oldHash,tech,oldTech,progress,progressMax,oldProgress,oldProgressMax,oldState);
        boolean meaningful=isMeaningful(transition);
        boolean semanticDuplicate=meaningful&&dedupEligible(tech)&&recentEquivalentMeaning(s,p,hash,streamKey,when);

        ContentValues raw=new ContentValues();raw.put("stream_key",streamKey);raw.put("package_name",p);raw.put("app_label",app);raw.put("event_type",ev);raw.put("title",t);raw.put("body",b);raw.put("content_hash",hash);raw.put("platform_hint",hint);raw.put("technical_type",tech);raw.put("metadata_json",m.toString());raw.put("occurred_at",when);raw.put("created_at",now);
        long rawId=s.insertOrThrow("notification_raw_observations",null,raw);

        if(streamId<=0){
            ContentValues v=new ContentValues();v.put("stream_key",streamKey);v.put("package_name",p);v.put("app_label",app);v.put("title",t);v.put("body",b);v.put("content_hash",hash);v.put("platform_hint",hint);v.put("technical_type",tech);v.put("state","removed".equals(ev)?"removed":"active");v.put("progress",progress);v.put("progress_max",progressMax);v.put("observation_count",1);v.put("meaningful_count",meaningful&&!semanticDuplicate?1:0);v.put("first_seen_at",when);v.put("last_seen_at",when);v.put("last_meaningful_at",meaningful&&!semanticDuplicate?when:0);v.put("metadata_json",m.toString());v.put("created_at",now);v.put("updated_at",now);streamId=s.insertOrThrow("notification_streams",null,v);
        }else{
            ContentValues v=new ContentValues();v.put("package_name",p);v.put("app_label",app);v.put("title",t);v.put("body",b);v.put("content_hash",hash);v.put("platform_hint",hint);v.put("technical_type",tech);v.put("state","removed".equals(ev)?"removed":"active");v.put("progress",progress);v.put("progress_max",progressMax);v.put("last_seen_at",when);v.put("metadata_json",m.toString());v.put("updated_at",now);v.put("observation_count",rawCountForStream(s,streamKey));if(meaningful&&!semanticDuplicate){v.put("meaningful_count",oldMeaningful+1);v.put("last_meaningful_at",when);}s.update("notification_streams",v,"id=?",new String[]{String.valueOf(streamId)});
        }

        long semanticId=0;
        if(meaningful&&!semanticDuplicate){ContentValues v=new ContentValues();v.put("stream_id",streamId);v.put("raw_observation_id",rawId);v.put("transition",transition);v.put("technical_type",tech);v.put("platform_hint",hint);v.put("semantic_state","pending");v.put("occurred_at",when);v.put("created_at",now);semanticId=s.insert("notification_semantic_events",null,v);}
        String resultTransition=semanticDuplicate?"DUPLICATE_EVIDENCE":transition;
        return new Result(rawId,streamId,semanticId,resultTransition,tech,hint,!semanticDuplicate&&meaningful&&hasSemanticContent(b,tech));
    }

    public static long rawCountSince(VaultDb db,long since){ensure(db);return scalar(db,"SELECT COUNT(*) FROM notification_raw_observations WHERE occurred_at>=?",new String[]{String.valueOf(since)});}
    public static long streamCountSince(VaultDb db,long since){ensure(db);return scalar(db,"SELECT COUNT(*) FROM notification_streams WHERE last_seen_at>=?",new String[]{String.valueOf(since)});}
    public static long meaningfulCountSince(VaultDb db,long since){ensure(db);return scalar(db,"SELECT COUNT(*) FROM notification_semantic_events WHERE occurred_at>=?",new String[]{String.valueOf(since)});}
    public static long appCountSince(VaultDb db,long since){ensure(db);return scalar(db,"SELECT COUNT(DISTINCT package_name) FROM notification_streams WHERE last_seen_at>=?",new String[]{String.valueOf(since)});}

    private static boolean recentEquivalentMeaning(SQLiteDatabase s,String pkg,String hash,String streamKey,long when){
        long lo=Math.max(0,when-CROSS_STREAM_DEDUP_MS),hi=when+CROSS_STREAM_DEDUP_MS;
        Cursor c=s.rawQuery("SELECT 1 FROM notification_semantic_events se JOIN notification_raw_observations ro ON ro.id=se.raw_observation_id WHERE ro.package_name=? AND ro.content_hash=? AND ro.stream_key<>? AND se.occurred_at BETWEEN ? AND ? LIMIT 1",
                new String[]{pkg,hash,streamKey,String.valueOf(lo),String.valueOf(hi)});
        boolean found=c.moveToFirst();c.close();return found;
    }
    private static boolean dedupEligible(String tech){return !"conversation_notification".equals(tech)&&!"call_hint".equals(tech);}

    private static String transition(long streamId,String event,String hash,String oldHash,String tech,String oldTech,int progress,int max,int oldProgress,int oldMax,String oldState){
        if("removed".equals(event))return "removed".equals(oldState)?"NOISE_UPDATE":"REMOVED";
        if(streamId<=0)return "NEW";
        if(hash.equals(oldHash))return "NOISE_UPDATE";
        if("progress_state".equals(tech)){
            boolean completed=max>0&&progress>=max&&(oldMax<=0||oldProgress<oldMax);
            if(completed)return "MEANINGFUL_TRANSITION";
            if(!tech.equals(oldTech))return "MEANINGFUL_TRANSITION";
            return "NOISE_UPDATE";
        }
        if("service_state".equals(tech)&&"service_state".equals(oldTech))return "NOISE_UPDATE";
        return "MEANINGFUL_UPDATE";
    }

    private static boolean isMeaningful(String t){return "NEW".equals(t)||"MEANINGFUL_UPDATE".equals(t)||"MEANINGFUL_TRANSITION".equals(t)||"REMOVED".equals(t);}
    private static boolean hasSemanticContent(String body,String tech){if(body!=null&&!body.trim().isEmpty())return true;return "call_hint".equals(tech)||"alarm".equals(tech)||"reminder".equals(tech)||"event".equals(tech);}

    private static String platformHint(JSONObject m){String x=safe(m.optString("platform_hint",""));if(!x.isEmpty())return x;x=safe(m.optString("notification_kind",""));if(!x.isEmpty())return x;return safe(m.optString("category",""));}
    private static String technicalType(String pkg,JSONObject m,String hint){
        int max=m.optInt("progress_max",0);boolean ind=m.optBoolean("progress_indeterminate",false);String h=safe(hint).toLowerCase();String template=safe(m.optString("template","")).toLowerCase();
        if(max>0||ind||"progress".equals(h))return"progress_state";
        if(template.contains("messagingstyle")||"message".equals(h))return"conversation_notification";
        if("call".equals(h))return"call_hint";
        if("email".equals(h))return"email_hint";
        if("alarm".equals(h))return"alarm";
        if("reminder".equals(h))return"reminder";
        if("event".equals(h))return"event";
        if("service".equals(h)||m.optBoolean("ongoing",false))return"service_state";
        if(pkg.contains("download")||pkg.contains("downloads"))return"download_state";
        return"generic_notification";
    }

    private static String streamKey(String pkg,JSONObject m){
        String key=safe(m.optString("notification_key",""));if(!key.isEmpty())return Fingerprint.text(pkg+"|key|"+key);
        String shortcut=safe(m.optString("shortcut_id",""));String tag=safe(m.optString("tag",""));int id=m.optInt("notification_id",0);
        if(!shortcut.isEmpty())return Fingerprint.text(pkg+"|shortcut|"+shortcut);
        return Fingerprint.text(pkg+"|id|"+id+"|tag|"+tag);
    }

    private static String semanticPayload(JSONObject m){return safe(m.optString("messages",""))+"|"+safe(m.optString("conversation_title",""))+"|"+m.optInt("progress",0)+"/"+m.optInt("progress_max",0)+"|"+safe(m.optString("category",""));}
    private static int rawCountForStream(SQLiteDatabase s,String key){Cursor c=s.rawQuery("SELECT COUNT(*) FROM notification_raw_observations WHERE stream_key=?",new String[]{key});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    private static long scalar(VaultDb db,String sql,String[] args){Cursor c=db.getReadableDatabase().rawQuery(sql,args);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    private static String safe(String s){return s==null?"":s.trim();}
}
