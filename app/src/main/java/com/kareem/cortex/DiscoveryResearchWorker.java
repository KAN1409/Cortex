package com.kareem.cortex;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public final class DiscoveryResearchWorker extends Worker {
    public DiscoveryResearchWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){
        long sid=getInputData().getLong("situation_id",0);if(sid<=0)return Result.success();
        VaultDb vault=new VaultDb(getApplicationContext());SQLiteDatabase db=vault.getWritableDatabase();
        try{
            DiscoveryAdvancedSchema.ensure(db);
            if(!ExternalBrainProvider.webResearchConfigured(getApplicationContext()))return Result.success();
            Cursor c=db.rawQuery("SELECT id,question FROM discovery_hypotheses WHERE situation_id=? AND needs_research=1 AND status IN ('open','investigating','deferred') ORDER BY confidence DESC,updated_at DESC LIMIT 3",new String[]{String.valueOf(sid)});
            ArrayList<Long> ids=new ArrayList<>();ArrayList<String> qs=new ArrayList<>();while(c.moveToNext()){ids.add(c.getLong(0));qs.add(c.getString(1));}c.close();
            String history=DiscoveryHistoryWriter.latest(db,sid);
            String domain=domain(db,sid);
            for(int i=0;i<ids.size();i++)researchOne(db,sid,ids.get(i),domain,qs.get(i),history);
            return Result.success();
        }catch(Throwable t){return getRunAttemptCount()<2?Result.retry():Result.failure();}
        finally{try{vault.close();}catch(Throwable ignored){}}
    }

    private void researchOne(SQLiteDatabase db,long sid,long hid,String domain,String q,String history)throws Exception{
        String prompt="Cortex is investigating a private situation. Use web research only for general/current external knowledge; never invent private facts. "+
                "Domain: "+domain+"\nQuestion: "+q+"\nPrivate history (treat as evidence, not public fact):\n"+clip(history,7000)+
                "\nReturn a concise evidence-grounded assessment. Clearly separate external facts from what the private evidence shows, state uncertainty, and include source citations.";
        long row=start(db,sid,hid,q);
        try{
            ExternalBrainProvider.WebResearchResult r=ExternalBrainProvider.webResearch(getApplicationContext(),prompt);
            ContentValues v=new ContentValues();v.put("provider",r.provider);v.put("model",r.model);v.put("result_text",r.text);v.put("citations_json",r.citationsJson);
            v.put("state","complete");v.put("latency_ms",r.durationMs);v.put("updated_at",System.currentTimeMillis());db.update("discovery_research",v,"id=?",new String[]{String.valueOf(row)});
            ContentValues h=new ContentValues();h.put("status","researched");h.put("updated_at",System.currentTimeMillis());db.update("discovery_hypotheses",h,"id=?",new String[]{String.valueOf(hid)});
        }catch(Throwable e){ContentValues v=new ContentValues();v.put("state","failed");v.put("error",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));v.put("updated_at",System.currentTimeMillis());db.update("discovery_research",v,"id=?",new String[]{String.valueOf(row)});throw e;}
    }
    private long start(SQLiteDatabase db,long sid,long hid,String q){ContentValues v=new ContentValues();long now=System.currentTimeMillis();v.put("situation_id",sid);v.put("hypothesis_id",hid);v.put("query_text",q);v.put("state","running");v.put("created_at",now);v.put("updated_at",now);return db.insertOrThrow("discovery_research",null,v);}
    private String domain(SQLiteDatabase db,long sid){Cursor c=db.rawQuery("SELECT domain FROM discovery_domain_state WHERE situation_id=?",new String[]{String.valueOf(sid)});String x=c.moveToFirst()?c.getString(0):"GENERAL";c.close();return x;}
    private static String clip(String s,int n){String x=s==null?"":s;return x.length()<=n?x:x.substring(0,n)+"…";}
}
