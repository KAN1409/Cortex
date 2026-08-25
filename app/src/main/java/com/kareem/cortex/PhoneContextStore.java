package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Local-only phone context timeline. This is context, not durable personal memory.
 * It records app/window transitions and bounded UI hints so Cortex can understand
 * what was happening on the phone without turning every interaction into a memory.
 */
public final class PhoneContextStore {
    private static final long RETENTION_MS=14L*24L*60L*60L*1000L;
    private static final int MAX_ROWS=25000;
    private PhoneContextStore(){}

    public static final class Event {
        public long id,occurredAt;
        public String kind="",source="",packageName="",appLabel="",className="",eventType="",text="",metadataJson="";
        public String human(){
            String app=!appLabel.isEmpty()?appLabel:(!packageName.isEmpty()?packageName:"Phone");
            StringBuilder b=new StringBuilder(app);
            if(!eventType.isEmpty())b.append(" · ").append(eventType.replace('_',' '));
            if(!text.isEmpty())b.append(" · ").append(clip(text,180));
            return b.toString();
        }
    }

    public static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS phone_context_events(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,source TEXT NOT NULL,package_name TEXT,app_label TEXT,class_name TEXT,event_type TEXT,text_preview TEXT,metadata_json TEXT,fingerprint TEXT UNIQUE,occurred_at INTEGER NOT NULL,created_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_phone_context_recent ON phone_context_events(occurred_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_phone_context_pkg ON phone_context_events(package_name,occurred_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_phone_context_kind ON phone_context_events(kind,occurred_at DESC)");
    }

    public static long record(VaultDb db,String kind,String source,String pkg,String label,String cls,String eventType,String text,long occurredAt,JSONObject meta){
        if(db==null)return 0;ensure(db);long now=System.currentTimeMillis();long when=occurredAt>0?occurredAt:now;
        String k=n(kind),src=n(source),p=n(pkg),a=n(label),c=n(cls),e=n(eventType),t=sanitizeText(text);
        String fp=Fingerprint.text("phone_context|"+k+"|"+src+"|"+p+"|"+e+"|"+t+"|"+(when/3000));
        ContentValues v=new ContentValues();v.put("kind",k.isEmpty()?"app_context":k);v.put("source",src.isEmpty()?"phone":src);v.put("package_name",p);v.put("app_label",a);v.put("class_name",c);v.put("event_type",e);v.put("text_preview",t);v.put("metadata_json",meta==null?"{}":meta.toString());v.put("fingerprint",fp);v.put("occurred_at",when);v.put("created_at",now);
        long id=db.getWritableDatabase().insertWithOnConflict("phone_context_events",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        maybeCleanup(db);return id;
    }

    public static ArrayList<Event> recent(VaultDb db,long since,int limit){
        ensure(db);ArrayList<Event> out=new ArrayList<>();int lim=Math.max(1,Math.min(200,limit));
        Cursor c=db.getReadableDatabase().query("phone_context_events",null,"occurred_at>=?",new String[]{String.valueOf(Math.max(0,since))},null,null,"occurred_at DESC,id DESC",String.valueOf(lim));
        while(c.moveToNext())out.add(from(c));c.close();return out;
    }

    public static Event latest(VaultDb db){
        ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT * FROM phone_context_events ORDER BY occurred_at DESC,id DESC LIMIT 1",null);Event e=c.moveToFirst()?from(c):null;c.close();return e;
    }

    public static String recentSummary(VaultDb db,long windowMs,int limit){
        long since=System.currentTimeMillis()-Math.max(60_000L,windowMs);ArrayList<Event> xs=recent(db,since,Math.max(limit*3,limit));
        LinkedHashSet<String> seen=new LinkedHashSet<>();StringBuilder b=new StringBuilder();int n=0;
        for(Event e:xs){String key=e.packageName+"|"+e.eventType+"|"+LocalSemanticEmbedder.norm(e.text);if(!seen.add(key))continue;
            if(b.length()>0)b.append('\n');b.append("• ").append(time(e.occurredAt)).append(" · ").append(e.human());if(++n>=limit)break;}
        return b.toString();
    }

    public static long countSince(VaultDb db,long since){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM phone_context_events WHERE occurred_at>=?",new String[]{String.valueOf(since)});long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    public static long distinctAppsSince(VaultDb db,long since){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT package_name) FROM phone_context_events WHERE occurred_at>=? AND COALESCE(package_name,'')<>''",new String[]{String.valueOf(since)});long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}

    public static void cleanup(VaultDb db){
        ensure(db);SQLiteDatabase s=db.getWritableDatabase();long cutoff=System.currentTimeMillis()-RETENTION_MS;
        s.delete("phone_context_events","occurred_at<?",new String[]{String.valueOf(cutoff)});
        s.execSQL("DELETE FROM phone_context_events WHERE id NOT IN (SELECT id FROM phone_context_events ORDER BY occurred_at DESC,id DESC LIMIT "+MAX_ROWS+")");
    }
    private static void maybeCleanup(VaultDb db){try{if((System.currentTimeMillis()/60000)%30==0)cleanup(db);}catch(Throwable ignored){}}

    private static Event from(Cursor c){Event e=new Event();e.id=g(c,"id");e.kind=s(c,"kind");e.source=s(c,"source");e.packageName=s(c,"package_name");e.appLabel=s(c,"app_label");e.className=s(c,"class_name");e.eventType=s(c,"event_type");e.text=s(c,"text_preview");e.metadataJson=s(c,"metadata_json");e.occurredAt=g(c,"occurred_at");return e;}
    private static String s(Cursor c,String k){int i=c.getColumnIndex(k);return i<0||c.isNull(i)?"":c.getString(i);}private static long g(Cursor c,String k){int i=c.getColumnIndex(k);return i<0||c.isNull(i)?0:c.getLong(i);}
    private static String sanitizeText(String s){String x=n(s).replace('\u0000',' ').replaceAll("\\s+"," ");if(x.length()>800)x=x.substring(0,800)+"…";return x;}
    private static String time(long ms){return new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(ms));}
    private static String clip(String s,int n){String x=s==null?"":s;return x.length()<=n?x:x.substring(0,n)+"…";}private static String n(String s){return s==null?"":s.trim();}
}
