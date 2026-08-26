package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;
import java.net.SocketTimeoutException;

/** Rollback-only verification of bounded Visual recovery semantics. */
public final class VisualRecoveryFixture {
    private VisualRecoveryFixture(){}

    public static String verify(VaultDb db)throws Exception{
        if(db==null)throw new IllegalArgumentException("db required");VisualInsightStore.ensure(db);VisualRecoveryStore.ensure(db);SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        String token="visual-recovery-fixture-"+System.nanoTime();try{
            long id=db.insert("IMAGE","diagnostic_rollback","Synthetic visual recovery","","Diagnostics","visual,synthetic,recovery","",Fingerprint.text(token),"{\"synthetic\":true,\"rollback\":true}");if(id<=0)throw new AssertionError("fixture image insert failed");VisualInsightStore.saveState(db,id,"failed","safe","Synthetic timeout fixture");
            SocketTimeoutException timeout=new SocketTimeoutException("synthetic visual timeout");VisualFailurePolicy.Decision first=VisualFailurePolicy.classify(timeout,0);VisualRecoveryStore.State s1=VisualRecoveryStore.record(db,id,first,timeout);if(s1==null||!s1.recoverable||s1.attempts!=1)throw new AssertionError("first bounded attempt mismatch");
            int retried=VisualRecoveryStore.retryRecoverableNow(db,id);VisualRecoveryStore.State afterRetry=VisualRecoveryStore.get(db,id);if(retried!=1||afterRetry==null||afterRetry.attempts!=1||!afterRetry.recoverable)throw new AssertionError("retry-now reset attempt history");
            VisualFailurePolicy.Decision second=VisualFailurePolicy.classify(timeout,afterRetry.attempts);VisualRecoveryStore.State s2=VisualRecoveryStore.record(db,id,second,timeout);if(s2==null||!s2.recoverable||s2.attempts!=2)throw new AssertionError("second bounded attempt mismatch");
            VisualFailurePolicy.Decision third=VisualFailurePolicy.classify(timeout,s2.attempts);VisualRecoveryStore.State s3=VisualRecoveryStore.record(db,id,third,timeout);if(s3==null||s3.recoverable||s3.attempts!=3)throw new AssertionError("terminal exhaustion mismatch");VisualInsightStore.saveState(db,id,"failed","safe","network_timeout_exhausted · synthetic fixture");
            int reset=VisualRecoveryStore.resetTerminalBudget(db,id);if(reset!=1||VisualRecoveryStore.get(db,id)!=null)throw new AssertionError("terminal reset did not start a fresh budget");VisualInsightStore.Insight v=VisualInsightStore.get(db,id);if(v==null||!"failed".equals(v.status))throw new AssertionError("terminal reset lost retry-eligible visual state");
            return"exact item only · attempts 1→retry-now stays 1→2→terminal 3 · explicit terminal reset clears ledger · rollback";
        }finally{sql.endTransaction();}
    }
}
