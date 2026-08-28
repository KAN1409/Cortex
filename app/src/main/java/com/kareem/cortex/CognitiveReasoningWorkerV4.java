package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Durable execution boundary for autonomous Deep Brain reasoning. */
public final class CognitiveReasoningWorkerV4 extends Worker {
    static final String KEY_TRIGGER="trigger";
    static final String KEY_BASELINE="baseline";
    public CognitiveReasoningWorkerV4(@NonNull Context appContext,@NonNull WorkerParameters params){super(appContext,params);}

    @NonNull @Override public Result doWork(){
        String trigger=getInputData().getString(KEY_TRIGGER);boolean baseline=getInputData().getBoolean(KEY_BASELINE,false);
        // Policy/budget/backoff are owned by the orchestrator. A provider failure is recorded there
        // and should not create an uncontrolled WorkManager retry loop.
        CognitiveReasoningOrchestratorV4.run(getApplicationContext(),trigger==null?"work":trigger,baseline);
        return Result.success();
    }
}
