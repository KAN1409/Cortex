package com.kareem.cortex;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/** Repairs runs left active when Android stopped the process or native inference. */
public final class CognitiveCouncilRunRecovery {
    private CognitiveCouncilRunRecovery(){}
    public static int recoverStale(android.content.Context context,SQLiteDatabase db,long now){
        try(CouncilExecutionLease lease=CouncilExecutionLease.acquire(context)){
            return lease==null?0:recoverWhileOwned(db,now);
        }catch(java.io.IOException unavailable){return 0;}
    }
    static int recoverWhileOwned(SQLiteDatabase db,long now){
        DiscoveryV3Schema.ensure(db);
        ContentValues v=new ContentValues();v.put("state","interrupted");
        v.put("error","Previous execution ended without a result; persisted state identifies the last completed model pass; no active execution owner remains");
        v.put("completed_at",now);v.put("updated_at",now);
        return db.update("discovery_v3_council_runs",v,"state LIKE 'running%'",null);
    }
}
