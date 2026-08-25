package com.kareem.cortex;

import java.util.*;

/** Immutable result of one user-triggered health sync. Partial counts are preserved on failure. */
public final class HealthSyncResult {
    public static final String SUCCESS="SUCCESS",NEEDS_ACCESS="NEEDS_ACCESS",UNAVAILABLE="UNAVAILABLE",UPDATE_REQUIRED="UPDATE_REQUIRED",ERROR="ERROR";
    public final int seen,added;
    public final String state,failureKind,error,nextAction;
    public final Map<String,Integer> sourceSeen,sourceAdded;

    public HealthSyncResult(int seen,int added,String state,String failureKind,String error,String nextAction,Map<String,Integer> sourceSeen,Map<String,Integer> sourceAdded){
        this.seen=Math.max(0,seen);this.added=Math.max(0,added);this.state=n(state);this.failureKind=n(failureKind);this.error=n(error);this.nextAction=n(nextAction);
        this.sourceSeen=copy(sourceSeen);this.sourceAdded=copy(sourceAdded);
    }
    public boolean success(){return SUCCESS.equals(state);}
    public boolean needsAccess(){return NEEDS_ACCESS.equals(state);}
    public boolean unavailable(){return UNAVAILABLE.equals(state)||UPDATE_REQUIRED.equals(state);}

    public static HealthSyncResult ok(int seen,int added,Map<String,Integer> sourceSeen,Map<String,Integer> sourceAdded){return new HealthSyncResult(seen,added,SUCCESS,"","","",sourceSeen,sourceAdded);}
    public static HealthSyncResult fail(int seen,int added,String state,String kind,String error,String nextAction,Map<String,Integer> sourceSeen,Map<String,Integer> sourceAdded){return new HealthSyncResult(seen,added,state,kind,error,nextAction,sourceSeen,sourceAdded);}

    private static Map<String,Integer> copy(Map<String,Integer> in){LinkedHashMap<String,Integer> out=new LinkedHashMap<>();if(in!=null)for(Map.Entry<String,Integer> e:in.entrySet())if(e.getKey()!=null&&!e.getKey().trim().isEmpty())out.put(e.getKey(),Math.max(0,e.getValue()==null?0:e.getValue()));return Collections.unmodifiableMap(out);}
    private static String n(String s){return s==null?"":s.trim();}
}
