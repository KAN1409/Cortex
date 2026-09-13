package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/** Adds functional intelligence plus final expected-vs-actual acceptance to every full-app audit. */
public final class CortexFunctionalAuditWorker extends Worker {
    public CortexFunctionalAuditWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        long runId=getInputData().getLong("run_id",0);if(runId<=0)return Result.failure();
        long startedWall=System.currentTimeMillis(),started=SystemClock.elapsedRealtime();
        CortexFunctionalSelfTest.Report report=CortexFunctionalSelfTest.run(getApplicationContext());
        CortexFinalAcceptanceAssessment.Report acceptance=CortexFinalAcceptanceAssessment.run(getApplicationContext());
        long duration=SystemClock.elapsedRealtime()-started;VaultDb db=null;
        try{
            db=new VaultDb(getApplicationContext());CortexAuditStore.ensure(db);
            ContentValues v=new ContentValues();v.put("run_id",runId);v.put("test_key","intelligence_functional");v.put("feature","Intelligence");v.put("title","Functional intelligence self-test");v.put("mode","auto");v.put("status",report.ok()?"pass":"fail");v.put("passed",report.ok()?1:0);v.put("severity",report.ok()?(report.warn>0?"warning":"ok"):"error");v.put("description","Exercises real Cortex read/compose/classification/provider/wiring paths without fabricating personal data or executing external mutations.");v.put("expected","No functional failures. Provider-dependent capabilities are explicit warnings when not configured.");v.put("started_at",startedWall);v.put("finished_at",System.currentTimeMillis());v.put("duration_ms",duration);v.put("detail",report.text());v.put("evidence_json",report.metrics.toString());db.getWritableDatabase().insertWithOnConflict("cortex_audit_tests",null,v,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
            ContentValues a=new ContentValues();a.put("run_id",runId);a.put("test_key","final_acceptance_expected_actual");a.put("feature","Final acceptance");a.put("title","Whole-app expected vs actual assessment");a.put("mode","auto");a.put("status",acceptance.shipReady()?"pass":"fail");a.put("passed",acceptance.shipReady()?1:0);a.put("severity",acceptance.shipReady()?(acceptance.warn>0?"warning":"ok"):"error");a.put("description","Compares Cortex product goals with the real device/app state across capture, voice, photo, evidence hygiene, reliability, Brain, judgment, Work, privacy, actions and diagnostics.");a.put("expected","Every critical acceptance case passes. Optional/provider/device-dependent gaps are warnings, never fake passes.");a.put("started_at",startedWall);a.put("finished_at",System.currentTimeMillis());a.put("duration_ms",duration);a.put("detail",acceptance.text());a.put("evidence_json",acceptance.json().toString());db.getWritableDatabase().insertWithOnConflict("cortex_audit_tests",null,a,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
            CortexAuditStore.log(db,runId,report.ok()?"info":"error","Intelligence","intelligence_functional",report.text(),new JSONObject().put("pass",report.pass).put("warn",report.warn).put("fail",report.fail).put("duration_ms",duration));
            CortexAuditStore.log(db,runId,acceptance.shipReady()?"info":"error","Final acceptance","expected_actual",acceptance.text(),acceptance.json());
            return Result.success();
        }catch(Throwable e){if(db!=null)try{CortexAuditStore.log(db,runId,"error","Intelligence","functional_and_acceptance_worker",e.toString(),null);}catch(Throwable ignored){}return Result.success();}
        finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }
}
