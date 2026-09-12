package com.kareem.cortex;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Process-local ref-counted index state shared by Work Vault service and UI. */
final class WorkVaultIndexServiceState {
    private static final ConcurrentHashMap<Long,AtomicInteger> ACTIVE=new ConcurrentHashMap<>();
    private WorkVaultIndexServiceState(){}

    static void reserve(long sourceId){
        if(sourceId<=0)return;
        ACTIVE.compute(sourceId,(id,count)->{if(count==null)count=new AtomicInteger();count.incrementAndGet();return count;});
    }

    static void release(long sourceId){
        if(sourceId<=0)return;
        ACTIVE.computeIfPresent(sourceId,(id,count)->count.decrementAndGet()<=0?null:count);
    }

    static boolean isActive(long sourceId){AtomicInteger count=ACTIVE.get(sourceId);return sourceId>0&&count!=null&&count.get()>0;}
    static int count(long sourceId){AtomicInteger count=ACTIVE.get(sourceId);return count==null?0:Math.max(0,count.get());}
    static void clear(){ACTIVE.clear();}
}
