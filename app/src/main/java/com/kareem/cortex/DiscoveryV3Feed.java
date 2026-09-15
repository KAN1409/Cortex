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
                "FROM discovery_v3_insights WHERE state='published' ORDER BY score DESC,last_evidence_at DESC LIMIT 80",null);
        HashSet<String> situationFamily=new HashSet<>();HashSet<Long> councilSituations=new HashSet<>();HashSet<String> semantic=new HashSet<>();HashMap<String,Integer> domainCounts=new HashMap<>();
        while(c.moveToNext()&&out.size()<limit){
            long id=c.getLong(0),sid=c.getLong(1);String family=s(c,2),domain=s(c,3),title=s(c,4),found=s(c,5),action=s(c,8);
            if(!userWorthy(family,title,found,action,c.getInt(11),c.getDouble(9),c.getDouble(10)))continue;
            // Once the multi-model council has judged a situation, heuristic observations from that same situation are implementation noise.
            if("COUNCIL_DISCOVERY".equals(family))councilSituations.add(sid);else if(councilSituations.contains(sid))continue;
            String sf=sid+"|"+family;if(!situationFamily.add(sf))continue;
            String meaning=semanticKey(title,found);if(!semantic.add(meaning))continue;
            int dc=domainCounts.containsKey(domain)?domainCounts.get(domain):0;if(dc>=2&&out.size()<limit-1)continue;
            domainCounts.put(domain,dc+1);
            ArrayList<Long> ev=new ArrayList<>();Cursor e=db.rawQuery("SELECT item_id FROM discovery_v3_insight_evidence WHERE insight_id=? ORDER BY item_id",new String[]{String.valueOf(id)});
            while(e.moveToNext())ev.add(e.getLong(0));e.close();
            out.add(new Item(id,sid,family,domain,title,found,s(c,6),s(c,7),action,c.getDouble(9),c.getDouble(10),c.getInt(11),c.getLong(12),DiscoveryV3History.latest(db,sid),ev));
        }c.close();return out;
    }

    /** Product boundary: Now is for discoveries, never raw pipeline facts dressed as insights. */
    static boolean userWorthy(String family,String title,String found,String action,int evidenceCount,double confidence,double score){
        String x=DiscoveryV3Policy.norm(title+" "+found);
        if(title==null||title.trim().length()<8||found==null||found.trim().length()<18||action==null||action.trim().length()<8)return false;
        // Never surface legacy malformed procurement identifiers already persisted by older builds.
        if(hasMalformedProcurementRef(title)||hasMalformedProcurementRef(found))return false;
        if(confidence<.72||score<.68)return false;
        if(("CONTRADICTION".equals(family)||"CROSS_SOURCE_CONNECTION".equals(family)||"COUNCIL_DISCOVERY".equals(family))&&evidenceCount<2)return false;
        if("CROSS_SOURCE_CONNECTION".equals(family)){
            // Connectivity is graph infrastructure, never a user-facing discovery by itself.
            return false;
        }
        if("COUNCIL_DISCOVERY".equals(family)&&!hasConcreteConsequence(found,action))return false;
        if(x.matches(".*\\b\\d+\\s+(records?|items?|observations?|notifications?|sources?|screenshots?|projects?)\\b.*"))return false;
        return !x.contains("cortex can now treat")&&!x.contains("open the history to review");
    }

    static boolean hasMalformedProcurementRef(String text){
        if(text==null)return false;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(P[RO])[-_/]([A-Z0-9]+(?:[-_/][A-Z0-9]+)*)(?![\\p{L}\\p{N}])").matcher(text);
        while(m.find()){
            String body=m.group(2);
            // Persisted corruption is alphabetic prose accidentally split after PR/PO.
            // Real references must carry numeric identity somewhere in the body.
            if(!body.matches(".*\\d.*"))return true;
        }
        return false;
    }

    static boolean hasConcreteConsequence(String found,String action){
        String x=DiscoveryV3Policy.norm(found+" "+action);
        if(x.length()<32)return false;
        String[] signals={"pending","overdue","missing","changed","increase","decrease","conflict","revision","rejected","approved","completed","price","cost","deadline","due","risk","delay","compare","verify","confirm","follow up","معلق","متأخر","ناقص","تغير","زيادة","انخفاض","تعارض","تعديل","مرفوض","معتمد","سعر","تكلفة","موعد","خطر","تأخير","راجع","تأكد","تابع"};
        for(String s:signals)if(x.contains(DiscoveryV3Policy.norm(s)))return true;
        return false;
    }

    static String semanticKey(String title,String found){
        String x=DiscoveryV3Policy.norm(title+" "+found)
                .replaceAll("\\b(cortex|evidence|status|found|needs|attention|different|source|sources)\\b"," ")
                .replaceAll("\\s+"," ").trim();
        return x.length()<=120?x:x.substring(0,120);
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
