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
        Context ctx=getApplicationContext();
        if(StartupSafetyGate.active())return Result.retry();
        if(!LocalCouncilModelRegistry.fullCouncilReady(ctx))return Result.success();

        VaultDb vault=null;
        try{
            vault=new VaultDb(ctx);SQLiteDatabase db=vault.getWritableDatabase();DiscoveryV3Schema.ensure(db);
            ArrayList<Long> situations=dueSituations(db,2);
            int published=0;
            for(long sid:situations){
                if(isStopped())break;
                CognitiveCouncilOrchestrator.Result r=CognitiveCouncilOrchestrator.run(ctx,vault,sid);
                markState(db,sid,r);
                if(r.ok&&r.publish&&publish(db,sid,r)>0)published++;
            }
            if(published>0)DiscoveryV3ResearchWorker.enqueue(ctx);
            return Result.success(new Data.Builder().putInt("situations",situations.size()).putInt("published",published).build());
        }catch(Throwable t){
            return getRunAttemptCount()<2?Result.retry():Result.failure();
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
        String domain="GENERAL";long lastEvidence=System.currentTimeMillis();
        Cursor s=db.rawQuery("SELECT domain,last_seen FROM discovery_v3_situations WHERE id=?",new String[]{String.valueOf(sid)});
        if(s.moveToFirst()){domain=s.getString(0);lastEvidence=s.getLong(1);}s.close();

        String issue="council|"+sid+"|"+Fingerprint.text(DiscoveryV3Policy.norm(r.title+" "+r.whatFound));
        long now=System.currentTimeMillis(),id=0;
        Cursor old=db.rawQuery("SELECT id FROM discovery_v3_insights WHERE issue_key=? LIMIT 1",new String[]{issue});
        if(old.moveToFirst())id=old.getLong(0);old.close();

        double score=Math.max(.72,Math.min(.98,.62+r.confidence*.34));
        ContentValues v=new ContentValues();v.put("situation_id",sid);v.put("issue_key",issue);v.put("family","COUNCIL_DISCOVERY");v.put("domain",domain);
        v.put("title",r.title);v.put("what_found",r.whatFound);v.put("why_matters",r.whyMatters);v.put("why_now",r.whyNow);v.put("suggested_action",r.suggestedAction);
        v.put("confidence",r.confidence);v.put("score",score);v.put("state","published");v.put("quality_reason","Survived multi-model investigator, independent analyst, adversarial critic and final judge");
        v.put("evidence_count",r.evidenceIds.size());v.put("last_evidence_at",lastEvidence);v.put("updated_at",now);
        if(id>0)db.update("discovery_v3_insights",v,"id=?",new String[]{String.valueOf(id)});
        else{v.put("created_at",now);id=db.insertOrThrow("discovery_v3_insights",null,v);}

        for(long item:r.evidenceIds){
            ContentValues e=new ContentValues();e.put("insight_id",id);e.put("item_id",item);e.put("role","council_evidence");
            db.insertWithOnConflict("discovery_v3_insight_evidence",null,e,SQLiteDatabase.CONFLICT_IGNORE);
        }
        if("HEALTH".equals(domain)||"PURCHASE".equals(domain))DiscoveryV3Research.enqueueIfNeeded(db,id,domain,r.title,r.whatFound,r.whyMatters);
        return id;
    }

    private static void markState(SQLiteDatabase db,long sid,CognitiveCouncilOrchestrator.Result r){
        long evidenceAt=0;Cursor c=db.rawQuery("SELECT last_seen FROM discovery_v3_situations WHERE id=?",new String[]{String.valueOf(sid)});if(c.moveToFirst())evidenceAt=c.getLong(0);c.close();
        ContentValues v=new ContentValues();v.put("scope_key","situation:"+sid);v.put("last_run_at",System.currentTimeMillis());v.put("last_evidence_at",evidenceAt);
        v.put("last_model",r.modelsUsed);v.put("last_error",r.error);v.put("updated_at",System.currentTimeMillis());
        db.insertWithOnConflict("discovery_v3_deep_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
}
