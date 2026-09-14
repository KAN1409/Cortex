package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Feed contract: no raw statistics; every item explains value, evidence and uncertainty. */
public final class DiscoveryFeedAssembler {
    private DiscoveryFeedAssembler(){}

    public static ArrayList<Item> build(VaultDb vault,int limit){
        ArrayList<Item> out=new ArrayList<>();if(vault==null||limit<=0)return out;
        SQLiteDatabase db=vault.getReadableDatabase();DiscoverySchema.ensure(db);
        Cursor c=db.rawQuery("SELECT c.id,c.situation_id,c.family,c.title,c.body,c.why_matters,c.why_now,c.confidence,c.score,c.evidence_count,c.critic_reason,c.updated_at,"+
                "COALESCE(ds.domain,'GENERAL') FROM discovery_candidates c LEFT JOIN discovery_domain_state ds ON ds.situation_id=c.situation_id "+
                "WHERE c.state='publishable' ORDER BY c.score DESC,c.updated_at DESC LIMIT ?",new String[]{String.valueOf(limit)});
        while(c.moveToNext()){
            long id=c.getLong(0),sid=c.getLong(1);
            ArrayList<Long> evidence=new ArrayList<>();
            Cursor e=db.rawQuery("SELECT item_id FROM discovery_candidate_evidence WHERE candidate_id=? ORDER BY item_id",new String[]{String.valueOf(id)});
            while(e.moveToNext())evidence.add(e.getLong(0));e.close();
            String history=DiscoveryHistoryWriter.latest(db,sid);
            String research=latestResearch(db,sid);
            out.add(new Item(id,sid,s(c,2),s(c,3),s(c,4),s(c,5),s(c,6),c.getDouble(7),c.getDouble(8),c.getInt(9),s(c,10),c.getLong(11),s(c,12),history,research,evidence));
        }c.close();return out;
    }

    private static String latestResearch(SQLiteDatabase db,long sid){
        Cursor c=db.rawQuery("SELECT result_text FROM discovery_research WHERE situation_id=? AND state='complete' ORDER BY updated_at DESC LIMIT 1",new String[]{String.valueOf(sid)});
        String x=c.moveToFirst()?s(c,0):"";c.close();return x;
    }
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}

    public static final class Item{
        public final long id,situationId,updatedAt;public final String family,title,whatFound,whyMatters,whyNow,criticReason,domain,history,research;
        public final double confidence,score;public final int evidenceCount;public final List<Long> evidenceIds;
        Item(long i,long s,String f,String t,String w,String wm,String wn,double cf,double sc,int ec,String cr,long u,String d,String h,String r,List<Long> ev){
            id=i;situationId=s;family=f;title=t;whatFound=w;whyMatters=wm;whyNow=wn;confidence=cf;score=sc;evidenceCount=ec;criticReason=cr;updatedAt=u;domain=d;history=h;research=r;evidenceIds=Collections.unmodifiableList(new ArrayList<>(ev));
        }
    }
}
