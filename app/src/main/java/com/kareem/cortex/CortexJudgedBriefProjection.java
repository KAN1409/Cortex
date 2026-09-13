package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;

/**
 * User-facing read projection. FINAL JUDGMENT remains the only attention owner.
 *
 * The stateful worker already materializes CortexAttentionJudge outcomes into the canonical
 * attention ledger. UI reads must never re-run judgment or write traces. A tiny process-local
 * cache collapses the immediate onCreate/onResume double-read without making the surface stale.
 */
public final class CortexJudgedBriefProjection {
    public static final String VERSION="cortex_judged_brief_projection_004";
    private static final long UI_COALESCE_MS=300L;
    private static final Object LOCK=new Object();
    private static volatile PrimeBriefStore.Snapshot cached;
    private static volatile long cachedAtElapsed;

    private CortexJudgedBriefProjection(){}

    public static PrimeBriefStore.Snapshot load(Context context,VaultDb db){
        if(db==null)return empty();
        long now=SystemClock.elapsedRealtime();
        PrimeBriefStore.Snapshot hit=cached;
        if(hit!=null&&now-cachedAtElapsed<=UI_COALESCE_MS)return hit;
        synchronized(LOCK){
            now=SystemClock.elapsedRealtime();
            hit=cached;
            if(hit!=null&&now-cachedAtElapsed<=UI_COALESCE_MS)return hit;
            PrimeBriefStore.Snapshot fresh=PrimeBriefStore.load(db);
            cached=fresh;
            cachedAtElapsed=SystemClock.elapsedRealtime();
            return fresh;
        }
    }

    static void invalidateForTests(){synchronized(LOCK){cached=null;cachedAtElapsed=0;}}

    private static PrimeBriefStore.Snapshot empty(){
        return new PrimeBriefStore.Snapshot(
                new java.util.ArrayList<>(),new java.util.ArrayList<>(),new java.util.ArrayList<>(),
                new java.util.ArrayList<>(),new java.util.ArrayList<>(),new java.util.ArrayList<>(),
                new java.util.ArrayList<>());
    }
}
