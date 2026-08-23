package com.kareem.cortex;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Structured, bounded, local-only backend diagnostics. Never store raw private message bodies. */
public final class DiagnosticsLog {
    public static final String VERSION="diagnostics_001";
    private static final long RETENTION_MS=14L*24L*60L*60L*1000L;
    private static final int MAX_ROWS=5000;
    private static final long REPEAT_WINDOW_MS=30_000L;
    private static final ConcurrentHashMap<String,Long> LAST=new ConcurrentHashMap<>();
    private static final AtomicInteger WRITES=new AtomicInteger();
    private DiagnosticsLog(){}

    public static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS diagnostics_log(id INTEGER PRIMARY KEY AUTOINCREMENT,created_at INTEGER NOT NULL,severity TEXT NOT NULL,component TEXT NOT NULL,event TEXT NOT NULL,status TEXT,error_class TEXT,error_code TEXT,item_id INTEGER DEFAULT 0,thread_id INTEGER DEFAULT 0,signal_id INTEGER DEFAULT 0,job_id INTEGER DEFAULT 0,model_run_id INTEGER DEFAULT 0,latency_ms INTEGER DEFAULT 0,metadata_json TEXT)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_diag_recent ON diagnostics_log(created_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_diag_component ON diagnostics_log(component,severity,created_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_diag_signal ON diagnostics_log(signal_id,created_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_diag_job ON diagnostics_log(job_id,created_at DESC)");
    }

    public static void info(VaultDb db,String component,String event,String status,long itemId,long threadId,long signalId,long jobId,long modelRunId,long latencyMs,JSONObject metadata){log(db,"INFO",component,event,status,"","",itemId,threadId,signalId,jobId,modelRunId,latencyMs,metadata);}
    public static void warn(VaultDb db,String component,String event,String status,String errorCode,long itemId,long threadId,long signalId,long jobId,long modelRunId,JSONObject metadata){log(db,"WARNING",component,event,status,"",errorCode,itemId,threadId,signalId,jobId,modelRunId,0,metadata);}
    public static void error(VaultDb db,String component,String event,Throwable error,String errorCode,long itemId,long threadId,long signalId,long jobId,long modelRunId,JSONObject metadata){String cls=error==null?"":error.getClass().getSimpleName();log(db,"ERROR",component,event,"failed",cls,errorCode,itemId,threadId,signalId,jobId,modelRunId,0,metadata);}

    public static void log(VaultDb db,String severity,String component,String event,String status,String errorClass,String errorCode,long itemId,long threadId,long signalId,long jobId,long modelRunId,long latencyMs,JSONObject metadata){
        if(db==null)return;try{
            ensure(db);String sev=normSeverity(severity),comp=clip(component,80),ev=clip(event,100),err=clip(errorClass,100),code=clip(errorCode,100);
            String repeatKey=sev+"|"+comp+"|"+ev+"|"+err+"|"+code;long now=System.currentTimeMillis();
            if(("ERROR".equals(sev)||"WARNING".equals(sev))&&repeated(repeatKey,now))return;
            ContentValues v=new ContentValues();v.put("created_at",now);v.put("severity",sev);v.put("component",comp);v.put("event",ev);v.put("status",clip(status,80));v.put("error_class",err);v.put("error_code",code);v.put("item_id",Math.max(0,itemId));v.put("thread_id",Math.max(0,threadId));v.put("signal_id",Math.max(0,signalId));v.put("job_id",Math.max(0,jobId));v.put("model_run_id",Math.max(0,modelRunId));v.put("latency_ms",Math.max(0,latencyMs));v.put("metadata_json",sanitize(metadata));db.getWritableDatabase().insert("diagnostics_log",null,v);
            if(WRITES.incrementAndGet()%100==0)cleanup(db);
        }catch(Throwable ignored){}
    }

    public static void cleanup(VaultDb db){try{ensure(db);SQLiteDatabase s=db.getWritableDatabase();long cutoff=System.currentTimeMillis()-RETENTION_MS;s.delete("diagnostics_log","created_at<?",new String[]{String.valueOf(cutoff)});s.execSQL("DELETE FROM diagnostics_log WHERE id NOT IN (SELECT id FROM diagnostics_log ORDER BY id DESC LIMIT "+MAX_ROWS+")");}catch(Throwable ignored){}}

    private static boolean repeated(String key,long now){Long old=LAST.put(key,now);return old!=null&&now-old<REPEAT_WINDOW_MS;}
    private static String sanitize(JSONObject input){
        if(input==null)return"{}";try{JSONObject out=new JSONObject();java.util.Iterator<String> it=input.keys();while(it.hasNext()){String k=it.next();String lk=k.toLowerCase(Locale.ROOT);if(lk.contains("body")||lk.contains("text")||lk.contains("message")||lk.contains("prompt")||lk.contains("password")||lk.contains("otp")||lk.contains("token")||lk.contains("secret")){out.put(k,"<redacted>");continue;}Object v=input.opt(k);String x=String.valueOf(v);out.put(k,x.length()>300?x.substring(0,300)+"…":v);}String s=out.toString();return s.length()>2000?s.substring(0,2000):s;}catch(Throwable e){return"{}";}
    }
    private static String normSeverity(String s){String x=s==null?"INFO":s.trim().toUpperCase(Locale.ROOT);return ("DEBUG".equals(x)||"INFO".equals(x)||"WARNING".equals(x)||"ERROR".equals(x)||"CRITICAL".equals(x))?x:"INFO";}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n);}
}
