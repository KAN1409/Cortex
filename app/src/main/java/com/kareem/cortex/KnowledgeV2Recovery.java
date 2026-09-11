package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/** Idempotent startup repair for the rebuildable Knowledge V2 processing ledger. */
public final class KnowledgeV2Recovery {
    private static final long STALE_RUNNING_MS=15L*60L*1000L;
    private KnowledgeV2Recovery(){}

    public static void recover(Context context){
        if(context==null||StartupSafetyGate.active())return;
        VaultDb db=new VaultDb(context.getApplicationContext());
        try{
            SQLiteDatabase s=db.getWritableDatabase();KnowledgeV2Schema.ensure(s);long now=System.currentTimeMillis();
            // Every eligible evidence item must have a current extraction ledger row. This also
            // repairs older installs where evidence was registered while the emergency gate was on.
            s.execSQL(
                    "INSERT OR IGNORE INTO kv2_processing(evidence_id,stage,pipeline_version,state,attempt_count,last_error,updated_at) "+
                    "SELECT id,?,?,CASE WHEN knowledge_eligible=1 AND self_reference_score<0.72 THEN 'PENDING' ELSE 'BLOCKED' END,0,"+
                    "CASE WHEN knowledge_eligible=1 AND self_reference_score<0.72 THEN NULL ELSE 'Blocked by provenance/self-reference policy' END,? FROM kv2_evidence",
                    new Object[]{KnowledgeV2Store.STAGE_EXTRACTION,KnowledgeV2Schema.PIPELINE_VERSION,now});

            long cutoff=now-STALE_RUNNING_MS;
            ContentValues reset=new ContentValues();reset.put("state","PENDING");reset.put("last_error","Recovered stale RUNNING state after process interruption");reset.put("updated_at",now);
            s.update("kv2_processing",reset,"state='RUNNING' AND updated_at<? AND stage IN (?,?) AND pipeline_version=?",
                    new String[]{String.valueOf(cutoff),KnowledgeV2Store.STAGE_EXTRACTION,KnowledgeV2EnrichmentWorker.STAGE,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
        }finally{try{db.close();}catch(Throwable ignored){}}
    }
}
