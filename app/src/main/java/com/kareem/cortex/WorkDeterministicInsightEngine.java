package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.*;

/**
 * Tier-0 Work intelligence. It publishes only deterministic comparisons backed by exact Work Vault sources.
 * No model is required and no missing business fact is invented.
 */
public final class WorkDeterministicInsightEngine {
    public static final String VERSION="work_deterministic_insights_001";
    private static final double MIN_ABS_CHANGE_PERCENT=10.0;
    private static final double MAX_ABS_CHANGE_PERCENT=100.0;
    private static final double MIN_SOURCE_CONFIDENCE=.80;
    private WorkDeterministicInsightEngine(){}

    public static Result refresh(VaultDb vault,int limit){
        Result out=new Result();if(vault==null)return out;
        SQLiteDatabase db=vault.getWritableDatabase();DiscoveryV3Schema.ensure(db);
        ArrayList<WorkPriceComparisonEngine.Comparison> xs=WorkPriceComparisonEngine.recent(vault,Math.max(8,limit*4));
        for(WorkPriceComparisonEngine.Comparison x:xs){
            if(out.considered>=Math.max(1,limit))break;
            out.considered++;
            long id=publishComparison(db,x,System.currentTimeMillis());
            if(id<=0){out.skipped++;continue;}
            try(Cursor c=db.rawQuery("SELECT state FROM discovery_v3_insights WHERE id=?",new String[]{String.valueOf(id)})){
                if(c.moveToFirst()&&"published".equals(c.getString(0)))out.published++;else out.suppressed++;
            }
        }
        return out;
    }

    static long publishComparison(SQLiteDatabase db,WorkPriceComparisonEngine.Comparison x,long now){
        if(db==null||x==null||!x.comparable||x.current==null||x.previous==null)return 0;
        double abs=Math.abs(x.percent);if(abs<MIN_ABS_CHANGE_PERCENT||abs>MAX_ABS_CHANGE_PERCENT)return 0;
        double confidence=Math.min(x.current.confidence,x.previous.confidence);
        if(!Double.isFinite(confidence)||confidence<MIN_SOURCE_CONFIDENCE)return 0;
        String group=WorkPriceComparisonEngine.comparisonKey(x.current);if(group.isEmpty())return 0;

        long currentEvidence=ensureEvidence(db,x.current,now),previousEvidence=ensureEvidence(db,x.previous,now);
        if(currentEvidence<=0||previousEvidence<=0||currentEvidence==previousEvidence)return 0;
        long sid=ensureSituation(db,group,x,now);if(sid<=0)return 0;
        attachEvidence(db,sid,currentEvidence,x.current,now);attachEvidence(db,sid,previousEvidence,x.previous,now);refreshSituationCoverage(db,sid,now);

        String item=clean(x.current.item),unit=WorkPriceComparisonEngine.unit(x.current.unit),currency=WorkPriceComparisonEngine.currency(x.current.currency);
        String direction=x.percent>0?"increased":"decreased";
        String title=clip(item+" price "+direction+" "+pct(abs),120);
        String found="Comparable "+item+" unit price "+direction+" from "+money(x.previous.unitPrice,currency,unit)+" to "+money(x.current.unitPrice,currency,unit)+" ("+(x.percent>0?"+":"")+pct(x.percent)+")"+scopeText(x.current)+".";
        String why="The change is based on two separate indexed Work Vault sources with the same item, unit, currency and procurement scope; it can affect the next commercial decision.";
        String whyNow="The newer grounded source is "+sourceName(x.current)+"; the previous comparable source is "+sourceName(x.previous)+".";
        String action="Open both sources and verify the revised unit rate before the next approval or PO step.";
        double consequence=Math.min(1,.68+Math.min(abs,50)/100d),novelty=.88,evidence=.94,timeliness=.84,uncertainty=.05;
        double score=DiscoveryV3Policy.score(consequence,novelty,evidence,timeliness,confidence,uncertainty);
        String issue="work_price|"+group;
        return CortexInsightPublisher.submit(db,sid,issue,"PRICE_CHANGE","WORK_PROCUREMENT",title,found,why,whyNow,action,confidence,score,Arrays.asList(previousEvidence,currentEvidence),now);
    }

    private static long ensureEvidence(SQLiteDatabase db,WorkPriceComparisonEngine.Price p,long now){
        String fp="work_price_source:"+p.versionId+":"+p.id;
        try(Cursor c=db.rawQuery("SELECT id FROM knowledge_items WHERE fingerprint=? LIMIT 1",new String[]{fp})){
            if(c.moveToFirst())return c.getLong(0);
        }
        long created=p.sourceTime>0?p.sourceTime:now;
        String unit=WorkPriceComparisonEngine.unit(p.unit),currency=WorkPriceComparisonEngine.currency(p.currency);
        String raw=clean(p.item)+" · "+money(p.unitPrice,currency,unit)+scopeText(p)+" · file "+sourceName(p)+locationText(p);
        JSONObject meta=new JSONObject();
        try{
            meta.put("space","WORK");meta.put("kind","work_price_source");meta.put("work_price_id",p.id);meta.put("work_file_id",p.fileId);meta.put("work_version_id",p.versionId);meta.put("project_id",p.projectId);meta.put("project",clean(p.project));meta.put("reference_type",clean(p.referenceType));meta.put("reference_value",clean(p.referenceValue));meta.put("sheet",clean(p.sheet));meta.put("page",p.page);meta.put("row",p.row);meta.put("source_confidence",p.confidence);meta.put("source_uri",clean(p.documentUri));
        }catch(Exception ignored){}
        ContentValues v=new ContentValues();v.put("type","WORK_SOURCE_EVIDENCE");v.put("source","work_vault");v.put("title",sourceName(p));v.put("raw_text",raw);v.put("extracted_text",raw);v.put("summary",raw);v.put("category","WORK_PROCUREMENT");v.put("tags","price source evidence");v.put("attachment_path",clean(p.documentUri));v.put("status","analyzed");v.put("fingerprint",fp);v.put("analysis_error","");v.put("metadata_json",meta.toString());v.put("created_at",created);v.put("updated_at",now);
        return db.insertOrThrow("knowledge_items",null,v);
    }

    private static long ensureSituation(SQLiteDatabase db,String group,WorkPriceComparisonEngine.Comparison x,long now){
        String key="work-price|"+group;long id=0;
        try(Cursor c=db.rawQuery("SELECT id FROM discovery_v3_situations WHERE situation_key=? LIMIT 1",new String[]{key})){if(c.moveToFirst())id=c.getLong(0);}
        long first=Math.min(sourceTime(x.current,now),sourceTime(x.previous,now)),last=Math.max(sourceTime(x.current,now),sourceTime(x.previous,now));
        ContentValues v=new ContentValues();v.put("space","WORK");v.put("domain","WORK_PROCUREMENT");v.put("label",clean(x.current.item)+" price");v.put("state","active");v.put("first_seen",first);v.put("last_seen",last);v.put("updated_at",now);
        if(id>0){db.update("discovery_v3_situations",v,"id=?",new String[]{String.valueOf(id)});return id;}
        v.put("situation_key",key);v.put("evidence_count",0);v.put("created_at",now);return db.insertOrThrow("discovery_v3_situations",null,v);
    }

    private static void attachEvidence(SQLiteDatabase db,long sid,long itemId,WorkPriceComparisonEngine.Price p,long now){
        ContentValues e=new ContentValues();e.put("item_id",itemId);e.put("situation_id",sid);e.put("space","WORK");e.put("source_type","WORK_FILE");e.put("source_key","work_file:"+p.fileId);e.put("quality",p.confidence);e.put("processed_at",now);db.insertWithOnConflict("discovery_v3_evidence",null,e,SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static void refreshSituationCoverage(SQLiteDatabase db,long sid,long now){
        try(Cursor c=db.rawQuery("SELECT COUNT(*),MIN(k.created_at),MAX(k.created_at) FROM discovery_v3_evidence e JOIN knowledge_items k ON k.id=e.item_id WHERE e.situation_id=?",new String[]{String.valueOf(sid)})){
            if(!c.moveToFirst())return;ContentValues v=new ContentValues();v.put("evidence_count",c.getInt(0));if(!c.isNull(1))v.put("first_seen",c.getLong(1));if(!c.isNull(2))v.put("last_seen",c.getLong(2));v.put("updated_at",now);db.update("discovery_v3_situations",v,"id=?",new String[]{String.valueOf(sid)});
        }
    }

    private static long sourceTime(WorkPriceComparisonEngine.Price p,long fallback){return p.sourceTime>0?p.sourceTime:fallback;}
    private static String scopeText(WorkPriceComparisonEngine.Price p){
        if(p==null)return "";if(!clean(p.project).isEmpty())return " in "+clean(p.project);
        String t=clean(p.referenceType),v=clean(p.referenceValue);if(!t.isEmpty()&&!v.isEmpty())return " for "+t+"-"+v;return "";
    }
    private static String locationText(WorkPriceComparisonEngine.Price p){StringBuilder b=new StringBuilder();if(!clean(p.sheet).isEmpty())b.append(" · sheet ").append(clean(p.sheet));if(p.page>0)b.append(" · page ").append(p.page);if(p.row>0)b.append(" · row ").append(p.row);return b.toString();}
    private static String sourceName(WorkPriceComparisonEngine.Price p){String x=clean(p.fileName);return x.isEmpty()?"Work Vault source #"+p.fileId:x;}
    private static String money(double v,String currency,String unit){return number(v)+" "+currency+(unit.isEmpty()?"":"/"+unit);}
    private static String pct(double v){return number(v)+"%";}
    private static String number(double v){if(Math.abs(v-Math.rint(v))<.0001)return String.format(Locale.US,"%.0f",v);return String.format(Locale.US,"%.1f",v);}
    private static String clean(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
    private static String clip(String s,int n){String x=clean(s);return x.length()<=n?x:x.substring(0,n-1)+"…";}

    public static final class Result{public int considered,published,suppressed,skipped;}
}
