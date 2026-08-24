package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/** Adds one real functional-intelligence result to every full-app audit without mutating user data. */
public final class CortexFunctionalAuditWorker extends Worker {
    public CortexFunctionalAuditWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){long runId=getInputData().getLong("run_id",0);if(runId<=0)return Result.failure();long startedWall=System.currentTimeMillis(),started=SystemClock.elapsedRealtime();CortexFunctionalSelfTest.Report report=CortexFunctionalSelfTest.run(getApplicationContext());long duration=SystemClock.elapsedRealtime()-started;VaultDb db=null;try{db=new VaultDb(getApplicationContext());CortexAuditStore.ensure(db);ContentValues v=new ContentValues();v.put("run_id",runId);v.put("test_key","intelligence_functional");v.put("feature","Intelligence");v.put("title","Functional intelligence self-test");v.put("mode","auto");v.put("status",report.ok()?"pass":"fail");v.put("passed",report.ok()?1:0);v.put("severity",report.ok()?(report.warn>0?"warning":"ok"):"error");v.put("description","Exercises real Cortex read/compose/classification/provider/wiring paths without fabricating personal data or executing external mutations.");v.put("expected","No functional failures. Provider-dependent capabilities are explicit warnings when not configured.");v.put("started_at",startedWall);v.put("finished_at",System.currentTimeMillis());v.put("duration_ms",duration);v.put("detail",report.text());v.put("evidence_json",report.metrics.toString());db.getWritableDatabase().insertWithOnConflict("cortex_audit_tests",null,v,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);CortexAuditStore.log(db,runId,report.ok()?"info":"error","Intelligence","intelligence_functional",report.text(),new JSONObject().put("pass",report.pass).put("warn",report.warn).put("fail",report.fail).put("duration_ms",duration));return Result.success();}catch(Throwable e){if(db!=null)try{CortexAuditStore.log(db,runId,"error","Intelligence","intelligence_functional_worker",e.toString(),null);}catch(Throwable ignored){}return Result.success();}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}
}
