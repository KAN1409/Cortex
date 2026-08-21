package com.kareem.cortex;

public final class ProactiveSignal {
    public final String kind,title,body,reason;
    public final long itemId;
    public final double priority;
    public ProactiveSignal(String kind,String title,String body,String reason,long itemId,double priority){
        this.kind=kind;this.title=title;this.body=body;this.reason=reason;this.itemId=itemId;this.priority=priority;
    }
}
