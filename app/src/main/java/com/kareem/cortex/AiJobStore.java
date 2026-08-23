package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import org.json.JSONObject;

/** Persistent execution ledger for Ask, deep answers and later multi-agent work. */
public final class AiJobStore {
    private AiJobStore(){}

    public static long create(VaultDb db,String kind,String sourceMode,String inputJson,int priority){
        CognitiveStore.ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();
        v.put("job_key","job-"+now+"-"+Long.toHexString(Double.doubleToLongBits(Math.random())));
        v.put("kind",n(kind));v.put("state","queued");v.put("priority",priority);v.put("source_mode",empty(sourceMode)?"auto":sourceMode);v.put("input_json",n(inputJson));v.put("progress_json",stageJson("Queued","queued",0,"").toString());v.put("output_json","");v.put("error","");v.put("created_at",now);v.put("updated_at",now);v.put("started_at",0);v.put("completed_at",0);
        return db.getWritableDatabase().insert("ai_jobs",null,v);
    }

    public static void start(VaultDb db,long jobId,String stage,String detail){update(db,jobId,"running",stage,"running",5,detail,"",false);}
    public static void progress(VaultDb db,long jobId,String stage,String code,int percent,String detail){update(db,jobId,"running",stage,code,percent,detail,"",false);}
    public static void complete(VaultDb db,long jobId,String outputJson,String stage,String detail){
        CognitiveStore.ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("state","complete");v.put("progress_json",stageJson(empty(stage)?"Complete":stage,"complete",100,detail).toString());v.put("output_json",n(outputJson));v.put("error","");v.put("updated_at",now);v.put("completed_at",now);db.getWritableDatabase().update("ai_jobs",v,"id=?",new String[]{String.valueOf(jobId)});
    }
    public static void fail(VaultDb db,long jobId,String error,String detail){update(db,jobId,"failed","Failed","failed",100,detail,error,true);}

    public static long modelRun(VaultDb db,long jobId,int passIndex,String role,String provider,String model,String route,String state,String inputHash,long latencyMs,int tokensIn,int tokensOut,double confidence,String outputJson,String error){
        CognitiveStore.ensure(db);ContentValues v=new ContentValues();v.put("job_id",jobId);v.put("pass_index",passIndex);v.put("role",n(role));v.put("provider",n(provider));v.put("model",n(model));v.put("route",n(route));v.put("state",n(state));v.put("input_hash",n(inputHash));v.put("latency_ms",latencyMs);v.put("tokens_in",tokensIn);v.put("tokens_out",tokensOut);v.put("confidence",confidence);v.put("output_json",n(outputJson));v.put("error",n(error));v.put("created_at",System.currentTimeMillis());return db.getWritableDatabase().insert("model_runs",null,v);
    }

    public static void linkSources(VaultDb db,long jobId,GroundedAnswer grounded){
        if(jobId<=0||grounded==null)return;int rank=0;for(SemanticHit hit:grounded.sources){if(hit==null||hit.item==null||hit.item.id<=0)continue;rank++;String meta="{\"rank\":"+rank+",\"score\":"+hit.score+"}";CognitiveStore.link(db,"ai_job",jobId,"memory",hit.item.id,"retrieved",Math.max(0,Math.min(1,hit.score)),meta);}
    }

    public static Snapshot latest(VaultDb db,long jobId){
        CognitiveStore.ensure(db);Cursor c=db.getReadableDatabase().query("ai_jobs",new String[]{"state","progress_json","output_json","error","created_at","updated_at","started_at","completed_at"},"id=?",new String[]{String.valueOf(jobId)},null,null,null,"1");Snapshot s=null;if(c.moveToFirst())s=new Snapshot(jobId,c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getLong(6),c.getLong(7));c.close();return s;
    }

    private static void update(VaultDb db,long jobId,String state,String stage,String code,int percent,String detail,String error,boolean done){
        if(jobId<=0)return;CognitiveStore.ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("state",state);v.put("progress_json",stageJson(stage,code,percent,detail).toString());v.put("error",n(error));v.put("updated_at",now);Cursor c=db.getReadableDatabase().query("ai_jobs",new String[]{"started_at"},"id=?",new String[]{String.valueOf(jobId)},null,null,null,"1");long started=c.moveToFirst()?c.getLong(0):0;c.close();if(started<=0&&"running".equals(state))v.put("started_at",now);if(done)v.put("completed_at",now);db.getWritableDatabase().update("ai_jobs",v,"id=?",new String[]{String.valueOf(jobId)});
    }

    private static JSONObject stageJson(String stage,String code,int percent,String detail){
        JSONObject o=new JSONObject();try{o.put("stage",n(stage));o.put("code",n(code));o.put("percent",Math.max(0,Math.min(100,percent)));o.put("detail",n(detail));o.put("at",System.currentTimeMillis());}catch(Exception ignored){}return o;
    }
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}private static String n(String s){return s==null?"":s;}

    public static final class Snapshot{
        public final long id,createdAt,updatedAt,startedAt,completedAt;public final String state,progressJson,outputJson,error;
        Snapshot(long i,String st,String p,String o,String e,long c,long u,long s,long d){id=i;state=n(st);progressJson=n(p);outputJson=n(o);error=n(e);createdAt=c;updatedAt=u;startedAt=s;completedAt=d;}
    }
}
