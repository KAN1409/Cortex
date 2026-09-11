package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/**
 * Final projection hygiene pass. Evidence/facts stay intact; only bad user-facing action projections
 * are retired so screenshot text cannot flood Now.
 */
public final class KnowledgeV2QualityWorker extends Worker {
    public KnowledgeV2QualityWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}

    @NonNull @Override public Result doWork(){
        if(StartupSafetyGate.active())return Result.retry();
        VaultDb db=new VaultDb(getApplicationContext());
        try{
            SQLiteDatabase s=db.getWritableDatabase();
            CognitiveStore.ensure(db);
            KnowledgeV2Schema.ensure(s);
            Cursor c=s.rawQuery(
                    "SELECT id,kind,title,body,source_key,metadata_json FROM derived_items "+
                    "WHERE state IN ('open','pending') AND (source_key='picbrain' OR metadata_json LIKE '%\"canonical\":\"knowledge_v2\"%')",
                    null);
            long now=System.currentTimeMillis();
            while(c.moveToNext()){
                long id=c.getLong(0);
                String kind=n(c.getString(1)),title=n(c.getString(2)),body=n(c.getString(3)),source=n(c.getString(4)),meta=n(c.getString(5));
                if(!NowQualityPolicy.suppress(kind,source,title,body))continue;
                ContentValues v=new ContentValues();v.put("state","filtered");v.put("updated_at",now);
                s.update("derived_items",v,"id=?",new String[]{String.valueOf(id)});
                try{
                    long eventId=new JSONObject(meta).optLong("event_id",0);
                    if(eventId>0){ContentValues ev=new ContentValues();ev.put("status","dismissed");ev.put("updated_at",now);s.update("kv2_events",ev,"id=?",new String[]{String.valueOf(eventId)});}
                }catch(Exception ignored){}
            }
            c.close();
            return Result.success();
        }catch(Throwable t){return Result.retry();}
        finally{try{db.close();}catch(Throwable ignored){}}
    }

    private static String n(String s){return s==null?"":s.trim();}
}
