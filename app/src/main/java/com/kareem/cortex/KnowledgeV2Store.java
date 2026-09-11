package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.kareem.cortex.visualmemory.data.db.MediaItemEntity;
import java.util.LinkedHashMap;
import java.util.Map;

/** Safe additive bridge into Knowledge V2. Does not create facts yet. */
public final class KnowledgeV2Store {
    public static final String STAGE_EXTRACTION="KNOWLEDGE_EXTRACTION";
    private KnowledgeV2Store(){}

    public static long registerVisualEvidence(Context context,MediaItemEntity item,String ocrText){
        VaultDb db=new VaultDb(context.getApplicationContext());
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            KnowledgeV2Schema.ensure(sql);
            long now=System.currentTimeMillis();
            String sourceKey="picbrain:"+item.getMediaId()+":"+item.getDateModifiedSeconds();
            String raw=ocrText==null?"":ocrText.trim();
            String contentHash=Fingerprint.text("kv2-evidence|"+sourceKey+"|"+raw);
            ContentValues v=new ContentValues();
            v.put("source_type","PICBRAIN_SCREENSHOT");
            v.put("source_key",sourceKey);
            v.put("source_uri",item.getContentUri());
            v.put("source_media_id",item.getMediaId());
            v.put("raw_text",raw);
            v.put("content_hash",contentHash);
            v.put("origin",item.getOrigin());
            v.put("self_reference_score",item.getSelfReferenceScore());
            v.put("derivation_depth",item.getDerivationDepth());
            v.put("knowledge_eligible",item.getKnowledgeEligible()?1:0);
            v.put("provenance_reason",item.getProvenanceReason());
            long captured=item.getDateTakenMillis()!=null?item.getDateTakenMillis():item.getDateAddedSeconds()*1000L;
            v.put("captured_at",captured);
            v.put("observed_at",now);
            v.put("created_at",now);
            v.put("updated_at",now);
            sql.insertWithOnConflict("kv2_evidence",null,v,SQLiteDatabase.CONFLICT_IGNORE);

            Cursor c=sql.query("kv2_evidence",new String[]{"id"},"source_key=?",new String[]{sourceKey},null,null,null,"1");
            long evidenceId=c.moveToFirst()?c.getLong(0):0;c.close();
            if(evidenceId<=0)return 0;

            ContentValues p=new ContentValues();
            p.put("evidence_id",evidenceId);
            p.put("stage",STAGE_EXTRACTION);
            p.put("pipeline_version",KnowledgeV2Schema.PIPELINE_VERSION);
            p.put("state",item.getKnowledgeEligible()&&item.getSelfReferenceScore()<0.72f?"PENDING":"BLOCKED");
            p.put("attempt_count",0);
            p.put("last_error",item.getKnowledgeEligible()?"":"Blocked by provenance/self-reference policy");
            p.put("updated_at",now);
            sql.insertWithOnConflict("kv2_processing",null,p,SQLiteDatabase.CONFLICT_IGNORE);
            return evidenceId;
        }finally{db.close();}
    }

    public static Map<Long,String> visualProcessingStates(Context context){
        LinkedHashMap<Long,String> out=new LinkedHashMap<>();
        VaultDb db=new VaultDb(context.getApplicationContext());
        try{
            SQLiteDatabase sql=db.getReadableDatabase();KnowledgeV2Schema.ensure(sql);
            Cursor c=sql.rawQuery(
                    "SELECT e.source_media_id,p.state FROM kv2_evidence e JOIN kv2_processing p ON p.evidence_id=e.id "+
                    "WHERE e.source_type='PICBRAIN_SCREENSHOT' AND p.stage=? AND p.pipeline_version=?",
                    new String[]{STAGE_EXTRACTION,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
            while(c.moveToNext())out.put(c.getLong(0),c.getString(1)==null?"":c.getString(1));
            c.close();
            return out;
        }finally{db.close();}
    }

    public static int[] visualProcessingCounts(Context context){
        VaultDb db=new VaultDb(context.getApplicationContext());
        try{
            SQLiteDatabase sql=db.getReadableDatabase();KnowledgeV2Schema.ensure(sql);
            int pending=0,running=0,done=0,blocked=0,failed=0;
            Cursor c=sql.rawQuery(
                    "SELECT p.state,COUNT(*) FROM kv2_evidence e JOIN kv2_processing p ON p.evidence_id=e.id "+
                    "WHERE e.source_type='PICBRAIN_SCREENSHOT' AND p.stage=? AND p.pipeline_version=? GROUP BY p.state",
                    new String[]{STAGE_EXTRACTION,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
            while(c.moveToNext()){
                String s=c.getString(0)==null?"":c.getString(0);int n=c.getInt(1);
                if("PENDING".equals(s))pending=n;else if("RUNNING".equals(s))running=n;else if("DONE".equals(s))done=n;else if("BLOCKED".equals(s))blocked=n;else if("FAILED".equals(s))failed=n;
            }
            c.close();
            return new int[]{pending,running,done,blocked,failed};
        }finally{db.close();}
    }

    public static String processingState(Context context,long mediaId){
        VaultDb db=new VaultDb(context.getApplicationContext());
        try{
            SQLiteDatabase sql=db.getReadableDatabase();KnowledgeV2Schema.ensure(sql);
            Cursor c=sql.rawQuery("SELECT p.state FROM kv2_evidence e JOIN kv2_processing p ON p.evidence_id=e.id WHERE e.source_media_id=? AND p.stage=? AND p.pipeline_version=? ORDER BY p.updated_at DESC LIMIT 1",
                    new String[]{String.valueOf(mediaId),STAGE_EXTRACTION,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
            String state=c.moveToFirst()?c.getString(0):"";c.close();return state==null?"":state;
        }finally{db.close();}
    }
}
