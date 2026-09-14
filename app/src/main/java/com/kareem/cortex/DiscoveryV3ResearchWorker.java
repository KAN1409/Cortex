package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class DiscoveryV3ResearchWorker extends Worker {
    public static final String UNIQUE="cortex-discovery-v3-research";
    public DiscoveryV3ResearchWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){
        if(!DiscoveryV3ResearchProvider.configured(getApplicationContext()))return Result.success();
        VaultDb vault=null;try{
            vault=new VaultDb(getApplicationContext());SQLiteDatabase db=vault.getWritableDatabase();DiscoveryV3Schema.ensure(db);
            for(int i=0;i<3&&!isStopped();i++){
                Cursor c=db.rawQuery("SELECT id,insight_id,query_text FROM discovery_v3_research WHERE state='queued' ORDER BY created_at ASC LIMIT 1",null);
                if(!c.moveToFirst()){c.close();break;}long id=c.getLong(0),insightId=c.getLong(1);String q=c.getString(2);c.close();
                ContentValues running=new ContentValues();running.put("state","running");running.put("updated_at",System.currentTimeMillis());db.update("discovery_v3_research",running,"id=?",new String[]{String.valueOf(id)});
                try{
                    DiscoveryV3ResearchProvider.Result r=DiscoveryV3ResearchProvider.search(getApplicationContext(),q);
                    ContentValues v=new ContentValues();v.put("state","complete");v.put("result_text",r.text);v.put("citations_json",r.citationsJson);v.put("provider","openrouter-web:"+r.model);v.put("updated_at",System.currentTimeMillis());db.update("discovery_v3_research",v,"id=?",new String[]{String.valueOf(id)});
                    attachResearch(db,insightId,r.text);
                }catch(Throwable e){
                    ContentValues v=new ContentValues();v.put("state","failed");v.put("error",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));v.put("updated_at",System.currentTimeMillis());db.update("discovery_v3_research",v,"id=?",new String[]{String.valueOf(id)});
                }
            }
            return Result.success();
        }catch(Throwable t){return getRunAttemptCount()<2?Result.retry():Result.failure();}
        finally{if(vault!=null)try{vault.close();}catch(Throwable ignored){}}
    }

    private static void attachResearch(SQLiteDatabase db,long insightId,String text){
        if(text==null||text.trim().isEmpty())return;
        Cursor c=db.rawQuery("SELECT why_matters FROM discovery_v3_insights WHERE id=?",new String[]{String.valueOf(insightId)});
        String old=c.moveToFirst()?c.getString(0):"";c.close();
        String add=" External research context: "+clip(text,650);
        ContentValues v=new ContentValues();v.put("why_matters",(old==null?"":old)+add);v.put("updated_at",System.currentTimeMillis());db.update("discovery_v3_insights",v,"id=?",new String[]{String.valueOf(insightId)});
    }

    public static void enqueue(Context c){
        Constraints x=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build();
        OneTimeWorkRequest r=new OneTimeWorkRequest.Builder(DiscoveryV3ResearchWorker.class).setConstraints(x).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,r);
    }
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
