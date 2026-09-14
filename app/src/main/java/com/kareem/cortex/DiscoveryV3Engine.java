package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

public final class DiscoveryV3Engine {
    public static final String VERSION="discovery_v3_engine_001";
    private DiscoveryV3Engine(){}

    public static long processAnalyzedItem(VaultDb vault,long itemId){
        if(vault==null||itemId<=0)return 0;
        KnowledgeItem item=vault.getById(itemId);
        if(item==null||!"analyzed".equals(item.status))return 0;
        SQLiteDatabase db=vault.getWritableDatabase();DiscoveryV3Schema.ensure(db);

        String all=n(item.title)+" "+n(item.summary)+" "+n(item.extractedText)+" "+n(item.rawText)+" "+n(item.tags);
        String space=DiscoveryV3Policy.space(item);
        String domain=DiscoveryV3Policy.domain(item,all);
        Anchor anchor=strongAnchor(db,item,space,all);
        long sid=upsertSituation(db,item,space,domain,anchor);
        linkEvidence(db,item,sid,space);
        writeClaims(db,item,sid,anchor,all);
        DiscoveryV3History.rebuild(db,sid);

        detectOpenLoops(db,item,sid,domain);
        detectContradiction(db,sid,domain);
        detectCrossSourceConnection(db,sid,domain,anchor);
        refreshSituation(db,sid);

        return sid;
    }

    public static int backfill(VaultDb vault,int limit){
        if(vault==null||limit<=0)return 0;SQLiteDatabase db=vault.getReadableDatabase();DiscoveryV3Schema.ensure(db);
        Cursor c=db.rawQuery("SELECT k.id FROM knowledge_items k LEFT JOIN discovery_v3_evidence e ON e.item_id=k.id "+
                "WHERE k.status='analyzed' AND e.item_id IS NULL ORDER BY k.updated_at DESC,k.id DESC LIMIT ?",
                new String[]{String.valueOf(Math.min(64,limit))});
        ArrayList<Long> ids=new ArrayList<>();while(c.moveToNext())ids.add(c.getLong(0));c.close();
        int done=0;for(long id:ids){try{if(processAnalyzedItem(vault,id)>0)done++;}catch(Throwable ignored){}}
        return done;
    }

    private static Anchor strongAnchor(SQLiteDatabase db,KnowledgeItem item,String space,String all){
        String ref=DiscoveryV3Policy.exactRef(all);
        if(!ref.isEmpty())return new Anchor("ref|"+ref.toLowerCase(Locale.ROOT),ref,.98,true);

        Cursor c=db.rawQuery("SELECT kind,value,confidence FROM entities WHERE item_id=? ORDER BY confidence DESC,id ASC",
                new String[]{String.valueOf(item.id)});
        Anchor best=null;
        while(c.moveToNext()){
            String kind=s(c,0).toLowerCase(Locale.ROOT),value=s(c,1).trim();double conf=c.getDouble(2);
            if(value.length()<3||conf<.78)continue;
            String norm=DiscoveryV3Policy.norm(value);
            boolean strong=kind.contains("project")||kind.contains("organization")||kind.contains("company")||kind.contains("vendor")||kind.contains("supplier");
            if(strong){best=new Anchor("entity|"+kind+"|"+Fingerprint.text(norm),value,Math.min(.95,conf),true);break;}
            if((kind.contains("phone")||kind.contains("email")||kind.contains("contact_id"))&&conf>=.85){best=new Anchor("contact|"+Fingerprint.text(norm),value,conf,true);break;}
        }c.close();
        if(best!=null)return best;

        String health=healthMarker(all);
        if("LIFE".equals(space)&&!health.isEmpty())return new Anchor("health|"+health.toLowerCase(Locale.ROOT),health,.86,true);

        return new Anchor("item|"+item.id,item.title==null||item.title.trim().isEmpty()?"Evidence":item.title,.60,false);
    }

    private static String healthMarker(String text){
        String x=DiscoveryV3Policy.norm(text);
        for(String marker:new String[]{"ldl","hdl","cholesterol","triglycerides","hba1c","vitamin d","vitamin b12","tsh","crp","esr"}){
            if(x.contains(marker))return marker.toUpperCase(Locale.ROOT);
        }
        return "";
    }

    private static long upsertSituation(SQLiteDatabase db,KnowledgeItem item,String space,String domain,Anchor a){
        String key=space+"|"+a.key;long now=System.currentTimeMillis(),sid=0;
        Cursor c=db.rawQuery("SELECT id FROM discovery_v3_situations WHERE situation_key=? LIMIT 1",new String[]{key});
        if(c.moveToFirst())sid=c.getLong(0);c.close();
        if(sid>0){
            ContentValues v=new ContentValues();v.put("last_seen",item.createdAt>0?item.createdAt:now);v.put("updated_at",now);
            if(a.strong)v.put("label",a.label);db.update("discovery_v3_situations",v,"id=?",new String[]{String.valueOf(sid)});return sid;
        }
        ContentValues v=new ContentValues();v.put("situation_key",key);v.put("space",space);v.put("domain",domain);v.put("label",a.label);
        v.put("state","active");v.put("evidence_count",0);v.put("first_seen",item.createdAt>0?item.createdAt:now);v.put("last_seen",item.createdAt>0?item.createdAt:now);
        v.put("created_at",now);v.put("updated_at",now);return db.insertOrThrow("discovery_v3_situations",null,v);
    }

    private static void linkEvidence(SQLiteDatabase db,KnowledgeItem item,long sid,String space){
        ContentValues v=new ContentValues();v.put("item_id",item.id);v.put("situation_id",sid);v.put("space",space);
        v.put("source_type",n(item.type));v.put("source_key",n(item.source));v.put("quality",evidenceQuality(db,item.id));v.put("processed_at",System.currentTimeMillis());
        db.insertWithOnConflict("discovery_v3_evidence",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static double evidenceQuality(SQLiteDatabase db,long itemId){
        Cursor c=db.rawQuery("SELECT COALESCE(MAX(confidence),0) FROM entities WHERE item_id=?",new String[]{String.valueOf(itemId)});
        double q=c.moveToFirst()?c.getDouble(0):0;c.close();return Math.max(.62,Math.min(.96,q>0?q:.72));
    }

    private static void writeClaims(SQLiteDatabase db,KnowledgeItem item,long sid,Anchor a,String all){
        if(!a.strong)return;
        String state=DiscoveryV3Policy.stateClaim(all);if(state.isEmpty())return;
        ContentValues v=new ContentValues();v.put("situation_id",sid);v.put("item_id",item.id);v.put("subject_key",a.key);v.put("subject_label",a.label);
        v.put("predicate","status");v.put("value",state);v.put("value_norm",state);v.put("confidence",a.confidence);
        v.put("observed_at",item.createdAt);v.put("created_at",System.currentTimeMillis());
        db.insertWithOnConflict("discovery_v3_claims",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void detectOpenLoops(SQLiteDatabase db,KnowledgeItem item,long sid,String domain){
        Cursor c=db.rawQuery("SELECT action_text,due_text FROM actions WHERE item_id=? AND status='open' ORDER BY id ASC",
                new String[]{String.valueOf(item.id)});
        while(c.moveToNext()){
            String action=s(c,0).replaceAll("\\s+"," ").trim(),due=s(c,1).trim();
            if(action.length()<8||isGenericAction(action))continue;
            String issue="open|"+sid+"|"+Fingerprint.text(DiscoveryV3Policy.norm(action));
            String found="An unresolved commitment or action is still present: "+clip(action,220)+(due.isEmpty()?"":" · "+due);
            String why="Open commitments are easy to lose once the original notification, message or file leaves view.";
            String now=due.isEmpty()?"Cortex found no completion state for this specific action.":"This action carries a date or due reference and still has no completion state.";
            upsertInsight(db,sid,issue,"OPEN_LOOP",domain,"This may still need you",found,why,now,"Open the evidence and confirm whether this is completed or still pending.",
                    .80,.78,.82,.72,.14,Collections.singletonList(item.id));
        }c.close();
    }

    private static boolean isGenericAction(String s){
        String x=DiscoveryV3Policy.norm(s);
        return x.equals("follow up")||x.equals("review")||x.equals("check")||x.equals("reply")||x.length()<8;
    }

    private static void detectContradiction(SQLiteDatabase db,long sid,String domain){
        Cursor c=db.rawQuery("SELECT id,item_id,subject_key,subject_label,predicate,value_norm,confidence,observed_at "+
                "FROM discovery_v3_claims WHERE situation_id=? ORDER BY observed_at DESC,id DESC LIMIT 24",
                new String[]{String.valueOf(sid)});
        ArrayList<Claim> xs=new ArrayList<>();while(c.moveToNext())xs.add(new Claim(c.getLong(0),c.getLong(1),s(c,2),s(c,3),s(c,4),s(c,5),c.getDouble(6),c.getLong(7)));c.close();
        for(int i=0;i<xs.size();i++)for(int j=i+1;j<xs.size();j++){
            Claim a=xs.get(i),b=xs.get(j);
            if(a.itemId==b.itemId||!a.subject.equals(b.subject)||!a.predicate.equals(b.predicate))continue;
            if(!DiscoveryV3Policy.incompatible(a.value,b.value))continue;
            String issue="contradiction|"+sid+"|"+a.subject+"|"+a.predicate;
            String found=a.label+" has conflicting status evidence: "+a.value+" versus "+b.value+".";
            String why="A status conflict can lead to the wrong next action if Cortex assumes either source is final.";
            String now="The conflict is backed by two distinct evidence items about the same identified subject.";
            upsertInsight(db,sid,issue,"CONTRADICTION",domain,"Status conflict in "+a.label,found,why,now,
                    "Open both evidence items and resolve which status is current.",Math.min(a.confidence,b.confidence),.90,.88,.82,.08,Arrays.asList(a.itemId,b.itemId));
            return;
        }
    }

    private static void detectCrossSourceConnection(SQLiteDatabase db,long sid,String domain,Anchor anchor){
        if(!anchor.strong)return;
        Cursor c=db.rawQuery("SELECT COUNT(DISTINCT item_id),COUNT(DISTINCT COALESCE(NULLIF(source_key,''),source_type)) "+
                "FROM discovery_v3_evidence WHERE situation_id=?",new String[]{String.valueOf(sid)});
        int items=0,sources=0;if(c.moveToFirst()){items=c.getInt(0);sources=c.getInt(1);}c.close();
        if(items<2||sources<2)return;
        ArrayList<Long> ids=new ArrayList<>();Cursor e=db.rawQuery("SELECT item_id FROM discovery_v3_evidence WHERE situation_id=? ORDER BY processed_at DESC LIMIT 8",new String[]{String.valueOf(sid)});
        while(e.moveToNext())ids.add(e.getLong(0));e.close();
        String issue="connection|"+sid+"|"+anchor.key;
        String found=anchor.label+" appears in "+sources+" different sources that Cortex can now treat as one grounded situation.";
        upsertInsight(db,sid,issue,"CROSS_SOURCE_CONNECTION",domain,"Cortex connected evidence that was split across apps",found,
                "Cross-source context can reveal what no single file, message or notification shows on its own.",
                "A second independent source was linked to the same strong identifier.",
                "Open the history to review the combined story.",.82,.78,.66,.70,.10,ids);
    }

    private static void upsertInsight(SQLiteDatabase db,long sid,String issue,String family,String domain,String title,String found,String why,String whyNow,String action,
                                      double confidence,double novelty,double consequence,double timeliness,double uncertainty,List<Long> evidenceIds){
        LinkedHashSet<Long> unique=new LinkedHashSet<>(evidenceIds);int n=unique.size();
        double evidence=Math.min(1,.52+.16*Math.max(0,n-1));
        double score=DiscoveryV3Policy.score(consequence,novelty,evidence,timeliness,confidence,uncertainty);
        boolean publish=DiscoveryV3Policy.publishable(family,found,action,n,confidence,score);
        long now=System.currentTimeMillis(),id=0;Cursor c=db.rawQuery("SELECT id FROM discovery_v3_insights WHERE issue_key=? LIMIT 1",new String[]{issue});
        if(c.moveToFirst())id=c.getLong(0);c.close();
        ContentValues v=new ContentValues();v.put("situation_id",sid);v.put("issue_key",issue);v.put("family",family);v.put("domain",domain);v.put("title",title);
        v.put("what_found",found);v.put("why_matters",why);v.put("why_now",whyNow);v.put("suggested_action",action);v.put("confidence",confidence);v.put("score",score);
        v.put("state",publish?"published":"suppressed");v.put("quality_reason",publish?"passed strict evidence and value gates":"failed strict publication gate");
        v.put("evidence_count",n);v.put("last_evidence_at",now);v.put("updated_at",now);
        if(id>0)db.update("discovery_v3_insights",v,"id=?",new String[]{String.valueOf(id)});
        else{v.put("created_at",now);id=db.insertOrThrow("discovery_v3_insights",null,v);}
        for(long itemId:unique){ContentValues ev=new ContentValues();ev.put("insight_id",id);ev.put("item_id",itemId);ev.put("role","supports");db.insertWithOnConflict("discovery_v3_insight_evidence",null,ev,SQLiteDatabase.CONFLICT_IGNORE);}
        if(publish&&score>=.80&&("HEALTH".equals(domain)||"PURCHASE".equals(domain)))DiscoveryV3Research.enqueueIfNeeded(db,id,domain,title,found,why);
    }

    private static void refreshSituation(SQLiteDatabase db,long sid){
        Cursor c=db.rawQuery("SELECT COUNT(*),MIN(k.created_at),MAX(k.created_at) FROM discovery_v3_evidence e JOIN knowledge_items k ON k.id=e.item_id WHERE e.situation_id=?",
                new String[]{String.valueOf(sid)});
        if(c.moveToFirst()){ContentValues v=new ContentValues();v.put("evidence_count",c.getInt(0));if(!c.isNull(1))v.put("first_seen",c.getLong(1));if(!c.isNull(2))v.put("last_seen",c.getLong(2));v.put("updated_at",System.currentTimeMillis());db.update("discovery_v3_situations",v,"id=?",new String[]{String.valueOf(sid)});}c.close();
    }

    private static String clip(String s,int n){String x=n(s);return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String n(String s){return s==null?"":s.trim();}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static final class Anchor{final String key,label;final double confidence;final boolean strong;Anchor(String k,String l,double c,boolean s){key=k;label=l;confidence=c;strong=s;}}
    private static final class Claim{final long id,itemId,when;final String subject,label,predicate,value;final double confidence;Claim(long i,long item,String s,String l,String p,String v,double c,long w){id=i;itemId=item;subject=s;label=l;predicate=p;value=v;confidence=c;when=w;}}
}
