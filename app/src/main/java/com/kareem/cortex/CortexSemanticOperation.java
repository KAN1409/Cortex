package com.kareem.cortex;

import android.os.SystemClock;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process semantic completion ledger for user-triggered asynchronous work.
 *
 * A visual change is not functional success. Surfaces register one operation token when real work
 * starts and close that exact token only when the user-visible function has a terminal outcome.
 * The experimental Robot reads this ledger without scraping progress copy or guessing from timing.
 */
public final class CortexSemanticOperation {
    public static final String RUNNING="RUNNING",COMPLETED="COMPLETED",FAILED="FAILED",TIMEOUT="TIMEOUT",CANCELLED="CANCELLED";
    private static final AtomicLong SEQ=new AtomicLong();
    private static final ConcurrentSkipListMap<Long,Entry> OPS=new ConcurrentSkipListMap<>();
    private static final int MAX_HISTORY=160;

    public static final class Snapshot {
        public final long token,startedElapsedMs,finishedElapsedMs;
        public final String kind,label,state,stage,detail;
        public final int percent;
        Snapshot(Entry e){token=e.token;startedElapsedMs=e.started;finishedElapsedMs=e.finished;kind=e.kind;label=e.label;state=e.state;stage=e.stage;detail=e.detail;percent=e.percent;}
        public boolean terminal(){return !RUNNING.equals(state);}
        public boolean success(){return COMPLETED.equals(state);}
        public long durationMs(){long end=finishedElapsedMs>0?finishedElapsedMs:SystemClock.elapsedRealtime();return Math.max(0,end-startedElapsedMs);}
    }

    private static final class Entry {
        final long token,started;final String kind,label;
        volatile long finished;volatile String state=RUNNING,stage="Started",detail="";volatile int percent=0;
        Entry(long token,String kind,String label){this.token=token;this.kind=n(kind);this.label=n(label);this.started=SystemClock.elapsedRealtime();}
    }

    private CortexSemanticOperation(){}

    public static long cursor(){return SEQ.get();}

    public static long begin(String kind,String label){
        long token=SEQ.incrementAndGet();Entry e=new Entry(token,kind,label);OPS.put(token,e);prune();return token;
    }

    public static void progress(long token,String stage,int percent,String detail){
        Entry e=OPS.get(token);if(e==null||!RUNNING.equals(e.state))return;e.stage=n(stage);e.percent=Math.max(0,Math.min(99,percent));e.detail=n(detail);
    }

    public static void complete(long token,String detail){finish(token,COMPLETED,"Complete",100,detail);}
    public static void fail(long token,String detail){finish(token,FAILED,"Failed",100,detail);}
    public static void timeout(long token,String detail){finish(token,TIMEOUT,"Timed out",100,detail);}
    public static void cancel(long token,String detail){finish(token,CANCELLED,"Cancelled",100,detail);}

    public static Snapshot get(long token){Entry e=OPS.get(token);return e==null?null:new Snapshot(e);}

    /** Returns the earliest operation that began after the caller's pre-click cursor. */
    public static Snapshot firstAfter(long cursor){Map.Entry<Long,Entry> x=OPS.higherEntry(cursor);return x==null?null:new Snapshot(x.getValue());}

    public static long defaultTimeoutMs(String kind){String k=n(kind).toUpperCase();if(k.contains("CAPTURE")||k.contains("ASR")||k.contains("VISUAL"))return 75_000L;if(k.contains("PROPOSAL"))return 35_000L;if(k.contains("BRAIN"))return 30_000L;if(k.contains("HEALTH"))return 45_000L;return 20_000L;}

    private static void finish(long token,String state,String stage,int percent,String detail){Entry e=OPS.get(token);if(e==null||!RUNNING.equals(e.state))return;e.detail=n(detail);e.stage=stage;e.percent=percent;e.state=state;e.finished=SystemClock.elapsedRealtime();}
    private static void prune(){while(OPS.size()>MAX_HISTORY){Map.Entry<Long,Entry> first=OPS.firstEntry();if(first==null)break;if(RUNNING.equals(first.getValue().state))break;OPS.remove(first.getKey(),first.getValue());}}
    private static String n(String s){return s==null?"":s.trim();}
}
