package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;

/**
 * User-facing read projection. FINAL JUDGMENT remains the only attention owner.
 *
 * The stateful worker already materializes CortexAttentionJudge outcomes into the canonical
 * attention ledger. UI reads must never re-run judgment or write traces. A tiny process-local
 * cache collapses the immediate onCreate/onResume double-read while a cheap canonical attention
 * revision prevents user actions such as Mark resolved from being hidden by that cache.
 */
public final class CortexJudgedBriefProjection {
    public static final String VERSION="cortex_judged_brief_projection_005";
    private static final long UI_COALESCE_MS=300L;
    private static final Object LOCK=new Object();
    private static volatile PrimeBriefStore.Snapshot cached;
    private static volatile long cachedAtElapsed;
    private static volatile long cachedAttentionRevision=Long.MIN_VALUE;

    private CortexJudgedBriefProjection(){}

    public static PrimeBriefStore.Snapshot load(Context context,VaultDb db){
        if(db==null)return empty();
        SQLiteDatabase writable=db.getWritableDatabase();
        CanonicalResolutionTriggers.ensure(writable);
        long revision=attentionRevision(writable);
        long now=SystemClock.elapsedRealtime();
        PrimeBriefStore.Snapshot hit=cached;
        if(hit!=null&&revision==cachedAttentionRevision&&now-cachedAtElapsed<=UI_COALESCE_MS)return hit;
        synchronized(LOCK){
            revision=attentionRevision(writable);
            now=SystemClock.elapsedRealtime();
            hit=cached;
            if(hit!=null&&revision==cachedAttentionRevision&&now-cachedAtElapsed<=UI_COALESCE_MS)return hit;
            PrimeBriefStore.Snapshot fresh=PrimeBriefStore.load(db);
            cached=fresh;
            cachedAttentionRevision=attentionRevision(writable);
            cachedAtElapsed=SystemClock.elapsedRealtime();
            return fresh;
        }
    }

    static void invalidateForTests(){synchronized(LOCK){cached=null;cachedAtElapsed=0;cachedAttentionRevision=Long.MIN_VALUE;}}

    private static long attentionRevision(SQLiteDatabase db){
        Cursor c=null;
        try{
            c=db.rawQuery("SELECT COUNT(*),COALESCE(MAX(updated_at),0) FROM ue_attention_items WHERE state='open'",null);
            if(!c.moveToFirst())return 0L;
            long count=c.getLong(0),latest=c.getLong(1);
            return (count*1000003L)^latest;
        }catch(Throwable ignored){return Long.MIN_VALUE;}
        finally{if(c!=null)c.close();}
    }

    private static PrimeBriefStore.Snapshot empty(){
        return new PrimeBriefStore.Snapshot(
                new java.util.ArrayList<>(),new java.util.ArrayList<>(),new java.util.ArrayList<>(),
                new java.util.ArrayList<>(),new java.util.ArrayList<>(),new java.util.ArrayList<>(),
                new java.util.ArrayList<>());
    }
}
