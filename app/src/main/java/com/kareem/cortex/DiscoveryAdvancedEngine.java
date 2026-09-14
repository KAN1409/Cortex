package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.work.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class DiscoveryAdvancedEngine {
    public static final String VERSION="discovery_advanced_001";
    private DiscoveryAdvancedEngine(){}

    public static void process(Context context,VaultDb vault,long situationId){
        if(vault==null||situationId<=0)return;
        SQLiteDatabase db=vault.getWritableDatabase();
        DiscoveryAdvancedSchema.ensure(db);
        String history=DiscoveryHistoryWriter.latest(db,situationId);
        if(history.isEmpty())return;
        String title=situationTitle(db,situationId);
        String space=situationSpace(db,situationId);
        String domain=DiscoveryDomainReasoner.domain(history,title,space);
        persistDomain(db,situationId,domain,history);
        DiscoveryGraphEngine.rebuildForSituation(db,situationId);
        List<String> questions=DiscoveryDomainReasoner.questions(domain,title);
        for(String q:questions){
            long hId=upsertHypothesis(db,situationId,domain,q,DiscoveryDomainReasoner.researchUseful(domain,q));
            evaluateHypothesis(db,situationId,hId,q);
        }
        if(context!=null)enqueueResearch(context,situationId);
    }

    private static void persistDomain(SQLiteDatabase db,long sid,String domain,String history){
        ContentValues v=new ContentValues();v.put("situation_id",sid);v.put("domain",domain);v.put("reasoner_version",VERSION);
        v.put("quality",Math.min(1.0,0.55+Math.min(0.4,history.length()/8000.0)));v.put("last_assessed_at",System.currentTimeMillis());
        v.put("assessment_json","{\"history_chars\":"+history.length()+"}");
        db.insertWithOnConflict("discovery_domain_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static long upsertHypothesis(SQLiteDatabase db,long sid,String family,String q,boolean research){
        String fp=Fingerprint.text(sid+"|"+family+"|"+DiscoveryPolicy.norm(q)); long now=System.currentTimeMillis(),id=0;
        Cursor c=db.rawQuery("SELECT id FROM discovery_hypotheses WHERE fingerprint=? LIMIT 1",new String[]{fp});
        if(c.moveToFirst())id=c.getLong(0);c.close();
        ContentValues v=new ContentValues();v.put("situation_id",sid);v.put("family",family);v.put("question",q);v.put("needs_research",research?1:0);v.put("updated_at",now);v.put("fingerprint",fp);
        if(id>0)db.update("discovery_hypotheses",v,"id=?",new String[]{String.valueOf(id)});
        else{v.put("created_at",now);id=db.insertOrThrow("discovery_hypotheses",null,v);}
        return id;
    }

    private static void evaluateHypothesis(SQLiteDatabase db,long sid,long hid,String q){
        int evidence=count(db,"SELECT COUNT(*) FROM discovery_situation_evidence WHERE situation_id=?",sid);
        int contradictions=count(db,"SELECT COUNT(*) FROM discovery_candidates WHERE situation_id=? AND family='CONTRADICTION' AND state='publishable'",sid);
        int open=count(db,"SELECT COUNT(*) FROM discovery_candidates WHERE situation_id=? AND family IN ('OPEN_LOOP','MISSING_LINK') AND state='publishable'",sid);
        int counter=0;
        String nq=DiscoveryPolicy.norm(q);
        if(nq.contains("disagree")||nq.contains("contrad"))counter=contradictions==0?1:0;
        if(nq.contains("missing")||nq.contains("unresolved"))counter=open==0?1:0;
        double conf=Math.min(.92,.45+Math.min(.32,evidence*.045)+(contradictions+open>0?.12:0)-(counter>0?.10:0));
        ContentValues v=new ContentValues();v.put("support_count",Math.max(0,evidence-counter));v.put("counter_count",counter);v.put("confidence",conf);
        v.put("status",counter>0&&evidence<3?"deferred":"investigating");v.put("updated_at",System.currentTimeMillis());
        db.update("discovery_hypotheses",v,"id=?",new String[]{String.valueOf(hid)});
    }

    public static void enqueueResearch(Context context,long sid){
        if(context==null)return;
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        Data data=new Data.Builder().putLong("situation_id",sid).build();
        OneTimeWorkRequest req=new OneTimeWorkRequest.Builder(DiscoveryResearchWorker.class).setInputData(data).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build();
        WorkManager.getInstance(context).enqueueUniqueWork("cortex-discovery-research-"+sid,ExistingWorkPolicy.KEEP,req);
    }

    public static double personalizedMultiplier(SQLiteDatabase db,String family){
        Cursor c=db.rawQuery("SELECT COALESCE(SUM(weight),0),COUNT(*) FROM discovery_user_signals WHERE family=? AND created_at>?",
                new String[]{family,String.valueOf(System.currentTimeMillis()-120L*86400000L)});
        double sum=0;int n=0;if(c.moveToFirst()){sum=c.getDouble(0);n=c.getInt(1);}c.close();
        if(n==0)return 1.0;return Math.max(.55,Math.min(1.45,1.0+sum/Math.max(5.0,n*3.0)));
    }

    public static double novelty(SQLiteDatabase db,String fingerprint){
        Cursor c=db.rawQuery("SELECT COUNT(*),MAX(created_at) FROM discovery_insight_history WHERE fingerprint=?",new String[]{fingerprint});
        int n=0;long last=0;if(c.moveToFirst()){n=c.getInt(0);last=c.isNull(1)?0:c.getLong(1);}c.close();
        if(n==0)return 1.0;
        double days=(System.currentTimeMillis()-last)/86400000.0;
        return Math.max(.12,Math.min(.85,days/30.0))/(1.0+Math.min(4,n)*.12);
    }

    public static void signal(VaultDb vault,long candidateId,long sid,String family,String event){
        SQLiteDatabase db=vault.getWritableDatabase();DiscoveryAdvancedSchema.ensure(db);
        double w="acted".equals(event)?1.0:"opened".equals(event)?.35:"saved".equals(event)?.65:"dismissed".equals(event)?-.85:"irrelevant".equals(event)?-1.0:"wrong".equals(event)?-1.4:0;
        ContentValues v=new ContentValues();v.put("candidate_id",candidateId);v.put("situation_id",sid);v.put("family",family);v.put("event",event);v.put("weight",w);v.put("created_at",System.currentTimeMillis());
        db.insert("discovery_user_signals",null,v);
    }

    static String situationTitle(SQLiteDatabase db,long sid){Cursor c=db.rawQuery("SELECT title FROM ue_situations WHERE id=?",new String[]{String.valueOf(sid)});String s=c.moveToFirst()?c.getString(0):"Situation";c.close();return s==null||s.trim().isEmpty()?"Situation":s;}
    static String situationSpace(SQLiteDatabase db,long sid){Cursor c=db.rawQuery("SELECT d.space FROM discovery_annotations d JOIN discovery_situation_evidence se ON se.item_id=d.item_id WHERE se.situation_id=? LIMIT 1",new String[]{String.valueOf(sid)});String s=c.moveToFirst()?c.getString(0):"UNKNOWN";c.close();return s==null?"UNKNOWN":s;}
    private static int count(SQLiteDatabase db,String q,long sid){Cursor c=db.rawQuery(q,new String[]{String.valueOf(sid)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
}
