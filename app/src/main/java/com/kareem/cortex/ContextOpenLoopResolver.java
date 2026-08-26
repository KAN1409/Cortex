package com.kareem.cortex;

import android.database.Cursor;
import java.util.*;

/**
 * Resolves resumable work for one Context without borrowing unrelated global obligations.
 *
 * Authority order:
 * 1) the latest snapshot recorded for this exact context;
 * 2) an open ACTION/WAITING/DECISION explicitly linked to this context;
 * 3) an open derived item whose anchor raw signal is linked to this context.
 *
 * No title similarity and no global-open-loop fallback is allowed here. A context may explain why
 * an obligation matters, but it may not adopt an unrelated obligation merely because both are open.
 */
public final class ContextOpenLoopResolver {
    public static final String SNAPSHOT="SNAPSHOT",LINKED_DERIVED="LINKED_DERIVED",NONE="NONE";

    public static final class State {
        public final long contextId,snapshotId,derivedId;
        public final String currentActivity,openLoop,nextStep,kind,title,body,source;
        public final double confidence;
        State(long contextId,long snapshotId,long derivedId,String activity,String loop,String next,String kind,String title,String body,String source,double confidence){
            this.contextId=contextId;this.snapshotId=snapshotId;this.derivedId=derivedId;currentActivity=n(activity);openLoop=n(loop);nextStep=n(next);this.kind=n(kind);this.title=n(title);this.body=n(body);this.source=n(source);this.confidence=clamp(confidence);
        }
        public boolean available(){return contextId>0&&(!currentActivity.isEmpty()||!openLoop.isEmpty()||!nextStep.isEmpty()||derivedId>0);}
        public boolean hasObligation(){return !openLoop.isEmpty()||derivedId>0;}
        public boolean hasNext(){return !nextStep.isEmpty();}
        public String provenance(){if(SNAPSHOT.equals(source)&&derivedId>0)return"context snapshot + linked "+kind.toLowerCase(Locale.ROOT);if(SNAPSHOT.equals(source))return"context snapshot";if(LINKED_DERIVED.equals(source))return"linked "+kind.toLowerCase(Locale.ROOT);return"";}
    }

    private ContextOpenLoopResolver(){}

    public static State resolve(VaultDb db,long contextId){
        if(db==null||contextId<=0)return empty(contextId);ContextSchema.ensure(db);CognitiveStore.ensure(db);
        Snapshot snap=latestSnapshot(db,contextId);Derived linked=bestLinkedDerived(db,contextId);
        String activity=snap.activity,loop=snap.openLoop,next=snap.nextStep,source=snap.id>0?SNAPSHOT:NONE;
        if(loop.isEmpty()&&linked.id>0)loop=obligationText(linked);
        if(next.isEmpty()&&linked.id>0&&"ACTION".equals(linked.kind))next=obligationText(linked);
        if(source.equals(NONE)&&linked.id>0)source=LINKED_DERIVED;
        double conf=linked.id>0?linked.confidence:(snap.id>0?1.0:0.0);
        return new State(contextId,snap.id,linked.id,activity,loop,next,linked.kind,linked.title,linked.body,source,conf);
    }

    /** Count only obligations grounded to this context. Used when the user marks a context done. */
    public static int linkedOpenCount(VaultDb db,long contextId){
        if(db==null||contextId<=0)return 0;ContextSchema.ensure(db);CognitiveStore.ensure(db);Cursor c=null;try{
            c=db.getReadableDatabase().rawQuery(
                "SELECT COUNT(DISTINCT d.id) FROM derived_items d WHERE d.state='open' AND d.kind IN ('ACTION','WAITING','DECISION') AND ("+
                "EXISTS(SELECT 1 FROM source_links l WHERE l.from_type='derived' AND l.from_id=d.id AND l.to_type='context' AND l.to_id=?) OR "+
                "EXISTS(SELECT 1 FROM source_links l WHERE l.from_type='raw_signal' AND l.from_id=d.anchor_signal_id AND l.to_type='context' AND l.to_id=?)"+
                ")",new String[]{String.valueOf(contextId),String.valueOf(contextId)});
            return c.moveToFirst()?Math.max(0,c.getInt(0)):0;
        }catch(Throwable ignored){return 0;}finally{if(c!=null)c.close();}
    }

    public static String resumeLabel(State s){
        if(s==null)return"";if(!s.nextStep.isEmpty())return clip(s.nextStep,140);if(!s.openLoop.isEmpty())return clip(s.openLoop,140);return"";
    }

    private static Snapshot latestSnapshot(VaultDb db,long contextId){Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT id,current_activity,open_loop,next_step FROM context_snapshots WHERE context_id=? ORDER BY created_at DESC,id DESC LIMIT 1",new String[]{String.valueOf(contextId)});if(c.moveToFirst())return new Snapshot(c.getLong(0),c.getString(1),c.getString(2),c.getString(3));}catch(Throwable ignored){}finally{if(c!=null)c.close();}return new Snapshot(0,"","","");}

    private static Derived bestLinkedDerived(VaultDb db,long contextId){Cursor c=null;try{
        c=db.getReadableDatabase().rawQuery(
            "SELECT d.id,d.kind,d.title,d.body,d.confidence,d.importance FROM derived_items d WHERE d.state='open' AND d.kind IN ('ACTION','WAITING','DECISION') AND ("+
            "EXISTS(SELECT 1 FROM source_links l WHERE l.from_type='derived' AND l.from_id=d.id AND l.to_type='context' AND l.to_id=?) OR "+
            "EXISTS(SELECT 1 FROM source_links l WHERE l.from_type='raw_signal' AND l.from_id=d.anchor_signal_id AND l.to_type='context' AND l.to_id=?)"+
            ") ORDER BY CASE d.kind WHEN 'ACTION' THEN 0 WHEN 'WAITING' THEN 1 ELSE 2 END,d.importance DESC,d.updated_at DESC LIMIT 1",
            new String[]{String.valueOf(contextId),String.valueOf(contextId)});
        if(c.moveToFirst())return new Derived(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getDouble(4),c.getInt(5));
        }catch(Throwable ignored){}finally{if(c!=null)c.close();}return new Derived(0,"","","",0,0);
    }

    private static String obligationText(Derived d){String text=!d.body.isEmpty()?d.body:d.title;if(text.isEmpty())return"";return (d.kind.isEmpty()?"":d.kind+": ")+clip(text,220);}
    private static State empty(long id){return new State(Math.max(0,id),0,0,"","","","","","",NONE,0);}
    private static String clip(String s,int n){String x=n(s).replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static String n(String s){return s==null?"":s.trim();}
    private static final class Snapshot {final long id;final String activity,openLoop,nextStep;Snapshot(long id,String a,String o,String n){this.id=id;activity=ContextOpenLoopResolver.n(a);openLoop=ContextOpenLoopResolver.n(o);nextStep=ContextOpenLoopResolver.n(n);}}
    private static final class Derived {final long id;final String kind,title,body;final double confidence;final int importance;Derived(long id,String k,String t,String b,double c,int i){this.id=id;kind=n(k).toUpperCase(Locale.ROOT);title=n(t);body=n(b);confidence=clamp(c);importance=i;}}
}
