package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.*;

/** Evidence-first health persistence. Metrics and imported artifacts retain source provenance. */
public final class HealthStore {
    private HealthStore(){}

    public static final class SourceState {
        public final String key,status,metadata;public final long lastSyncAt;
        SourceState(String key,String status,long lastSyncAt,String metadata){this.key=n(key);this.status=n(status);this.lastSyncAt=lastSyncAt;this.metadata=n(metadata);}
    }

    public static void ensure(VaultDb db){HealthSchema.ensure(db.getWritableDatabase());}

    public static String sourceKeyForOrigin(String packageName){
        String p=packageName==null?"":packageName.trim().toLowerCase(Locale.US);
        if(p.contains("shealth")||p.contains("samsung"))return "samsung_health";
        if(p.contains("huawei")||p.contains("healthkit"))return "huawei_health";
        if(p.isEmpty())return "health_connect";
        return "health_connect:"+p;
    }

    public static long addMetric(VaultDb db,String sourceKey,String metric,double value,String unit,long startAt,long endAt,String externalId,String metadata){
        ensure(db);String sk=blank(sourceKey)?"health_connect":sourceKey;String ext=externalId==null?"":externalId;String fp=Fingerprint.text("health_metric|"+sk+"|"+metric+"|"+startAt+"|"+endAt+"|"+ext+"|"+value+"|"+unit);
        ContentValues v=new ContentValues();v.put("source_key",sk);v.put("metric_type",metric);v.put("value_real",value);v.put("unit",unit==null?"":unit);v.put("start_at",startAt);v.put("end_at",endAt);v.put("external_id",ext);v.put("metadata_json",metadata==null?"{}":metadata);v.put("fingerprint",fp);v.put("created_at",System.currentTimeMillis());
        return db.getWritableDatabase().insertWithOnConflict("health_metrics",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    public static long linkKnowledgeEvidence(VaultDb db,long knowledgeItemId,String evidenceKind,String sourceKey){
        if(knowledgeItemId<=0)return 0;ensure(db);KnowledgeItem k=db.getById(knowledgeItemId);if(k==null)return 0;String kind=blank(evidenceKind)?"import":evidenceKind;String sk=blank(sourceKey)?"health_import":sourceKey;String fp=Fingerprint.text("health_evidence|memory|"+knowledgeItemId+"|"+kind);
        ContentValues v=new ContentValues();v.put("source_key",sk);v.put("evidence_kind",kind);v.put("knowledge_item_id",knowledgeItemId);v.put("external_id","");v.put("title",k.title==null?"":k.title);String body=!blank(k.summary)?k.summary:!blank(k.extractedText)?k.extractedText:k.rawText;v.put("body",body==null?"":body);v.put("occurred_at",k.createdAt>0?k.createdAt:System.currentTimeMillis());v.put("metadata_json","{\"source_type\":\""+safe(k.type)+"\"}");v.put("fingerprint",fp);v.put("created_at",System.currentTimeMillis());return db.getWritableDatabase().insertWithOnConflict("health_evidence",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    public static long addEvidence(VaultDb db,String sourceKey,String kind,String externalId,String title,String body,long occurredAt,String metadata){
        ensure(db);String sk=blank(sourceKey)?"health_import":sourceKey;String ext=externalId==null?"":externalId;String fp=Fingerprint.text("health_evidence|"+sk+"|"+kind+"|"+ext+"|"+occurredAt+"|"+title);
        ContentValues v=new ContentValues();v.put("source_key",sk);v.put("evidence_kind",blank(kind)?"event":kind);v.put("knowledge_item_id",0);v.put("external_id",ext);v.put("title",title==null?"":title);v.put("body",body==null?"":body);v.put("occurred_at",occurredAt>0?occurredAt:System.currentTimeMillis());v.put("metadata_json",metadata==null?"{}":metadata);v.put("fingerprint",fp);v.put("created_at",System.currentTimeMillis());return db.getWritableDatabase().insertWithOnConflict("health_evidence",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    public static void markSource(VaultDb db,String sourceKey,String status,long lastSyncAt,String metadata){
        ensure(db);ensureSourceRow(db,sourceKey);ContentValues v=new ContentValues();v.put("status",blank(status)?"unknown":status);if(lastSyncAt>0)v.put("last_sync_at",lastSyncAt);if(metadata!=null)v.put("metadata_json",metadata);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("health_sources",v,"source_key=?",new String[]{sourceKey});
    }

    public static SourceState sourceState(VaultDb db,String sourceKey){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT source_key,status,last_sync_at,COALESCE(metadata_json,'{}') FROM health_sources WHERE source_key=? LIMIT 1",new String[]{sourceKey});SourceState s=c.moveToFirst()?new SourceState(c.getString(0),c.getString(1),c.getLong(2),c.getString(3)):null;c.close();return s;}

    public static long beginSync(VaultDb db,String sourceKey){
        ensure(db);markSource(db,sourceKey,"syncing",0,null);ContentValues v=new ContentValues();v.put("source_key",sourceKey);v.put("state","running");v.put("started_at",System.currentTimeMillis());v.put("metadata_json","{}");return db.getWritableDatabase().insert("health_sync_runs",null,v);
    }

    /** Legacy caller retained. New Health Connect path uses finishSyncDetailed. */
    public static void finishSync(VaultDb db,long runId,String sourceKey,int seen,int added,String error){
        HealthSyncResult r=blank(error)?HealthSyncResult.ok(seen,added,null,null):HealthSyncResult.fail(seen,added,HealthSyncResult.ERROR,"legacy_error",error,"Review the source and retry explicitly.",null,null);finishSyncDetailed(db,runId,sourceKey,r);
    }

    public static void finishSyncDetailed(VaultDb db,long runId,String gatewaySource,HealthSyncResult result){
        ensure(db);if(result==null)return;long now=System.currentTimeMillis();String state=result.success()?"success":"failed";
        JSONObject meta=new JSONObject();try{meta.put("result_state",result.state);meta.put("failure_kind",result.failureKind);meta.put("next_action",result.nextAction);JSONObject seen=new JSONObject(),added=new JSONObject();for(Map.Entry<String,Integer> e:result.sourceSeen.entrySet())seen.put(e.getKey(),e.getValue());for(Map.Entry<String,Integer> e:result.sourceAdded.entrySet())added.put(e.getKey(),e.getValue());meta.put("source_seen",seen);meta.put("source_added",added);}catch(Throwable ignored){}
        ContentValues v=new ContentValues();v.put("state",state);v.put("records_seen",result.seen);v.put("records_added",result.added);v.put("error",result.error);v.put("finished_at",now);v.put("metadata_json",meta.toString());if(runId>0)db.getWritableDatabase().update("health_sync_runs",v,"id=?",new String[]{String.valueOf(runId)});

        String gatewayStatus=result.success()?"active":sourceStatus(result.state);markSource(db,gatewaySource,gatewayStatus,result.success()?now:0,meta.toString());
        if(result.success())for(Map.Entry<String,Integer> e:result.sourceSeen.entrySet()){
            String key=e.getKey();if(blank(key)||gatewaySource.equals(key)||e.getValue()==null||e.getValue()<=0)continue;
            JSONObject sm=new JSONObject();try{sm.put("route","health_connect");sm.put("records_seen",e.getValue());sm.put("records_added",result.sourceAdded.containsKey(key)?result.sourceAdded.get(key):0);sm.put("observed_via","health_connect");}catch(Throwable ignored){}
            markSource(db,key,"active_via_health_connect",now,sm.toString());
        }
    }

    public static Summary summary(VaultDb db){
        ensure(db);Summary s=new Summary();Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(MAX(end_at),0) FROM health_metrics",null);if(c.moveToFirst()){s.metricCount=c.getLong(0);s.lastMetricAt=c.getLong(1);}c.close();c=db.getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(MAX(occurred_at),0) FROM health_evidence",null);if(c.moveToFirst()){s.evidenceCount=c.getLong(0);s.lastEvidenceAt=c.getLong(1);}c.close();c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM health_followups WHERE state='open'",null);if(c.moveToFirst())s.openFollowups=c.getLong(0);c.close();return s;
    }

    public static String recentTimeline(VaultDb db,int limit){
        ensure(db);StringBuilder out=new StringBuilder();Cursor c=db.getReadableDatabase().rawQuery("SELECT metric_type,value_real,unit,end_at,source_key FROM health_metrics ORDER BY end_at DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});while(c.moveToNext()){if(out.length()>0)out.append('\n');out.append(c.getString(0)).append(" · ").append(trimNumber(c.getDouble(1))).append(' ').append(c.getString(2)==null?"":c.getString(2)).append(" · ").append(c.getString(4));}c.close();return out.toString();
    }

    private static void ensureSourceRow(VaultDb db,String sourceKey){if(blank(sourceKey))return;Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM health_sources WHERE source_key=? LIMIT 1",new String[]{sourceKey});boolean exists=c.moveToFirst();c.close();if(exists)return;long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("source_key",sourceKey);v.put("kind","ORIGIN");v.put("display_name",sourceKey.startsWith("health_connect:")?sourceKey.substring("health_connect:".length()):sourceKey);v.put("package_name",sourceKey.startsWith("health_connect:")?sourceKey.substring("health_connect:".length()):"");v.put("status","observed");v.put("last_sync_at",0);v.put("metadata_json","{\"route\":\"health_connect\"}");v.put("updated_at",now);db.getWritableDatabase().insertWithOnConflict("health_sources",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    private static String sourceStatus(String resultState){if(HealthSyncResult.NEEDS_ACCESS.equals(resultState))return"needs_access";if(HealthSyncResult.UPDATE_REQUIRED.equals(resultState))return"update_required";if(HealthSyncResult.UNAVAILABLE.equals(resultState))return"unavailable";return"error";}
    public static final class Summary{public long metricCount,evidenceCount,openFollowups,lastMetricAt,lastEvidenceAt;}
    private static boolean blank(String s){return s==null||s.trim().isEmpty();}
    private static String safe(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ");}
    private static String n(String s){return s==null?"":s.trim();}
    private static String trimNumber(double x){if(Math.rint(x)==x)return String.valueOf((long)x);return String.format(Locale.US,"%.2f",x);}
}
