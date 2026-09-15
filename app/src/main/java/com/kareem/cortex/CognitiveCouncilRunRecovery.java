package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/**
 * Council runs are durable workflows. A missing process owner is not a terminal
 * interruption: persisted passes are the resume point for the next executor.
 */
public final class CognitiveCouncilRunRecovery {
    private CognitiveCouncilRunRecovery(){}
    public static int recoverStale(android.content.Context context,SQLiteDatabase db,long now){
        DiscoveryV3Schema.ensure(db);
        // File-lock ownership is process scoped. If another executor owns it, leave state alone.
        try(CouncilExecutionLease lease=CouncilExecutionLease.acquire(context)){
            return lease==null?0:recoverWhileOwned(db,now);
        }catch(java.io.IOException unavailable){return 0;}
    }
    static int recoverWhileOwned(SQLiteDatabase db,long now){
        DiscoveryV3Schema.ensure(db);
        // Intentionally do not convert resumable running_* checkpoints into terminal failures.
        // The orchestrator reuses the run and skips every pass already committed to SQLite.
        return 0;
    }
}
