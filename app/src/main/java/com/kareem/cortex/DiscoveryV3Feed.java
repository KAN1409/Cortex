package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

public final class DiscoveryV3Feed {
    private DiscoveryV3Feed(){}

    public static ArrayList<Item> top(VaultDb vault,int limit){
        ArrayList<Item> out=new ArrayList<>();if(vault==null||limit<=0)return out;
        SQLiteDatabase db=vault.getReadableDatabase();DiscoveryV3Schema.ensure(db);
        Cursor c=db.rawQuery("SELECT id,situation_id,family,domain,title,what_found,why_matters,why_now,suggested_action,confidence,score,evidence_count,last_evidence_at "+
                "FROM discovery_v3_insights WHERE state='published' ORDER BY score DESC,last_evidence_at DESC LIMIT 40",null);
        HashSet<String> situationFamily=new HashSet<>();HashMap<String,Integer> domainCounts=new HashMap<>();
        while(c.moveToNext()&&out.size()<limit){
            long id=c.getLong(0),sid=c.getLong(1);String family=s(c,2),domain=s(c,3);
            String sf=sid+"|"+family;if(!situationFamily.add(sf))continue;
            int dc=domainCounts.containsKey(domain)?domainCounts.get(domain):0;if(dc>=2&&out.size()<limit-1)continue;
            domainCounts.put(domain,dc+1);
            ArrayList<Long> ev=new ArrayList<>();Cursor e=db.rawQuery("SELECT item_id FROM discovery_v3_insight_evidence WHERE insight_id=? ORDER BY item_id",new String[]{String.valueOf(id)});
            while(e.moveToNext())ev.add(e.getLong(0));e.close();
            out.add(new Item(id,sid,family,domain,s(c,4),s(c,5),s(c,6),s(c,7),s(c,8),c.getDouble(9),c.getDouble(10),c.getInt(11),c.getLong(12),DiscoveryV3History.latest(db,sid),ev));
        }c.close();return out;
    }

    public static void feedback(VaultDb vault,Item x,String event){
        if(vault==null||x==null)return;double w="useful".equals(event)?1:"acted".equals(event)?1.4:"not_useful".equals(event)?-1:"wrong".equals(event)?-1.5:0;
        ContentValues v=new ContentValues();v.put("insight_id",x.id);v.put("family",x.family);v.put("event",event);v.put("weight",w);v.put("created_at",System.currentTimeMillis());
        vault.getWritableDatabase().insert("discovery_v3_feedback",null,v);
    }

    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    public static final class Item{
        public final long id,situationId,lastEvidenceAt;public final String family,domain,title,whatFound,whyMatters,whyNow,suggestedAction,history;public final double confidence,score;public final int evidenceCount;public final List<Long> evidenceIds;
        Item(long i,long sid,String f,String d,String t,String wf,String wm,String wn,String a,double c,double sc,int ec,long le,String h,List<Long> ev){id=i;situationId=sid;family=f;domain=d;title=t;whatFound=wf;whyMatters=wm;whyNow=wn;suggestedAction=a;confidence=c;score=sc;evidenceCount=ec;lastEvidenceAt=le;history=h;evidenceIds=Collections.unmodifiableList(new ArrayList<>(ev));}
    }
}
