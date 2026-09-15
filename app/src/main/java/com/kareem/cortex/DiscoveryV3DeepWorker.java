package com.kareem.cortex;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.*;

/** Runs the full local cognitive council against evolving situations. */
public final class DiscoveryV3DeepWorker extends Worker {
    public DiscoveryV3DeepWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        if(!getInputData().getBoolean("user_requested",false)){
            DiscoveryV3DeepScheduler.enable(getApplicationContext());
            return Result.success();
        }
        Context ctx=getApplicationContext();
        if(StartupSafetyGate.active())return Result.failure(new Data.Builder().putString("error","Startup recovery is active; try again when ready").build());
        if(!LocalCouncilModelRegistry.fullCouncilReady(ctx)){
            LocalLlmRuntime.State runtime=LocalLlmRuntime.state(ctx);
            String detail="Required local inference is not proven ready";
            if(runtime!=null&&runtime.error!=null&&!runtime.error.isEmpty())detail+=": "+runtime.error;
            return Result.failure(new Data.Builder().putString("error",detail).build());
        }

        VaultDb vault=null;
        try{
            vault=new VaultDb(ctx);SQLiteDatabase db=vault.getWritableDatabase();DiscoveryV3Schema.ensure(db);CognitiveCouncilRunRecovery.recoverStale(ctx,db,System.currentTimeMillis());
            ArrayList<Long> situations=dueSituations(db,1);
            if(situations.isEmpty())return Result.failure(new Data.Builder().putString("error","No eligible situation with new evidence is available").build());
            int published=0;
            for(long sid:situations){
                if(isStopped())break;
                CognitiveCouncilOrchestrator.Result r=CognitiveCouncilOrchestrator.run(ctx,vault,sid);
                markState(db,sid,r);
                if(!r.ok)return Result.failure(new Data.Builder().putString("error",r.error).build());
                if(r.ok&&r.publish){
                    long id=publish(db,sid,r);
                    try(Cursor c=db.rawQuery("SELECT state FROM discovery_v3_insights WHERE id=?",new String[]{String.valueOf(id)})){
                        if(c.moveToFirst()&&"published".equals(c.getString(0)))published++;
                    }
                }
            }
            if(published>0)DiscoveryV3ResearchWorker.enqueue(ctx);
            return Result.success(new Data.Builder().putInt("situations",situations.size()).putInt("published",published).build());
        }catch(Throwable t){
            try{CapabilitySupervisor.recordFailure(ctx,CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE,t);}catch(Throwable ignored){}
            return Result.failure(new Data.Builder().putString("error","Analysis stopped: "+t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage())).build());
        }finally{if(vault!=null)try{vault.close();}catch(Throwable ignored){}}
    }

    private static ArrayList<Long> dueSituations(SQLiteDatabase db,int limit){
        ArrayList<Long> out=new ArrayList<>();
        String sql="SELECT s.id FROM discovery_v3_situations s LEFT JOIN discovery_v3_deep_state d ON d.scope_key='situation:'||s.id "+
                "WHERE s.state='active' AND s.evidence_count>=2 AND (d.last_run_at IS NULL OR s.last_seen>d.last_evidence_at OR d.last_run_at<?) "+
                "ORDER BY CASE WHEN d.last_run_at IS NULL THEN 0 ELSE 1 END,s.last_seen DESC LIMIT ?";
        long stale=System.currentTimeMillis()-6L*60L*60L*1000L;
        Cursor c=db.rawQuery(sql,new String[]{String.valueOf(stale),String.valueOf(limit)});
        while(c.moveToNext())out.add(c.getLong(0));c.close();return out;
    }

    private static long publish(SQLiteDatabase db,long sid,CognitiveCouncilOrchestrator.Result r){
        String domain="GENERAL";
        try(Cursor c=db.rawQuery("SELECT domain FROM discovery_v3_situations WHERE id=?",new String[]{String.valueOf(sid)})){if(c.moveToFirst())domain=c.getString(0);}
        String issue="council|"+sid+"|"+Fingerprint.text(DiscoveryV3Policy.norm(r.title+" "+r.whatFound));
        return CortexInsightPublisher.submit(db,sid,issue,"COUNCIL_DISCOVERY",domain,r.title,r.whatFound,r.whyMatters,r.whyNow,r.suggestedAction,r.confidence,Math.max(.72,Math.min(.98,.62+r.confidence*.34)),r.evidenceIds,System.currentTimeMillis());
    }

    private static void markState(SQLiteDatabase db,long sid,CognitiveCouncilOrchestrator.Result r){
        long evidenceAt=0;Cursor c=db.rawQuery("SELECT last_seen FROM discovery_v3_situations WHERE id=?",new String[]{String.valueOf(sid)});if(c.moveToFirst())evidenceAt=c.getLong(0);c.close();
        ContentValues v=new ContentValues();v.put("scope_key","situation:"+sid);v.put("last_run_at",System.currentTimeMillis());v.put("last_evidence_at",evidenceAt);
        v.put("last_model",r.modelsUsed);v.put("last_error",r.error);v.put("updated_at",System.currentTimeMillis());
        db.insertWithOnConflict("discovery_v3_deep_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
}
