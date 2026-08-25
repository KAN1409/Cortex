package com.kareem.cortex;

import android.database.Cursor;
import java.util.*;

/** Compact local context passport for Brain/Brief. Field-level cloud export is deliberately separate. */
public final class ContextPacketBuilder {
    private ContextPacketBuilder(){}
    public static final class Packet {
        public final long contextId;public final String title,goal,currentActivity,openLoops,nextStep,localText,cloudText;public final double confidence;public final ArrayList<String> background;
        Packet(long id,String title,String goal,String activity,String loops,String next,String local,String cloud,double confidence,ArrayList<String> background){contextId=id;this.title=n(title);this.goal=n(goal);currentActivity=n(activity);openLoops=n(loops);nextStep=n(next);localText=n(local);cloudText=n(cloud);this.confidence=confidence;this.background=background;}
        public boolean available(){return contextId>0&&!title.isEmpty();}
    }

    public static Packet buildLocal(VaultDb db,int tokenBudget){
        if(db==null)return empty();ContextStateStore.ContextState primary=ContextResolver.refresh(db);if(primary==null)return empty();int chars=Math.max(500,Math.min(6400,tokenBudget*4));ArrayList<ContextStateStore.ContextState> stack=ContextStateStore.stack(db,5);ArrayList<String> bg=new ArrayList<>();for(ContextStateStore.ContextState x:stack)if(x.id!=primary.id&&!ContextStateStore.ROLE_AMBIENT.equals(x.role))bg.add(x.title);
        SnapshotFields snap=latestSnapshot(db,primary.id);String activity=!snap.activity.isEmpty()?snap.activity:primary.summary;String loops=!snap.openLoop.isEmpty()?snap.openLoop:globalOpenLoops(db,3);String next=!snap.nextStep.isEmpty()?snap.nextStep:globalNextStep(db);StringBuilder b=new StringBuilder();b.append("CURRENT CORTEX CONTEXT\n");b.append("Context: ").append(primary.title).append("\n");b.append("Confidence: ").append(Math.round(primary.stackConfidence*100)).append("%\n");if(!primary.goal.isEmpty())b.append("Goal: ").append(primary.goal).append("\n");if(!activity.isEmpty())b.append("Current activity: ").append(activity).append("\n");if(!loops.isEmpty())b.append("Open loop: ").append(loops).append("\n");if(!next.isEmpty())b.append("Likely next: ").append(next).append("\n");if(!bg.isEmpty())b.append("Suspended/background: ").append(join(bg,3)).append("\n");b.append("Privacy: local context passport; do not treat it as cloud-shareable unless each source is separately approved.");String local=clip(b.toString(),chars);
        // v1 intentionally exports no derived context summary to cloud. This prevents local-only phone,
        // thread or memory evidence from being laundered into a cloud-safe summary. Cloud packets will
        // be enabled later only from source-linked evidence that independently passes policy.
        return new Packet(primary.id,primary.title,primary.goal,activity,loops,next,local,"",primary.stackConfidence,bg);
    }

    public static String conciseForUi(VaultDb db){Packet p=buildLocal(db,220);if(!p.available())return"";StringBuilder b=new StringBuilder(p.title);if(!p.currentActivity.isEmpty()&&!norm(p.currentActivity).equals(norm(p.title)))b.append(" · ").append(clip(p.currentActivity,120));if(!p.nextStep.isEmpty())b.append("\nNext: ").append(clip(p.nextStep,120));return b.toString();}

    private static final class SnapshotFields {String activity="",openLoop="",nextStep="";}
    private static SnapshotFields latestSnapshot(VaultDb db,long contextId){SnapshotFields x=new SnapshotFields();ContextSchema.ensure(db);Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT current_activity,open_loop,next_step FROM context_snapshots WHERE context_id=? ORDER BY created_at DESC LIMIT 1",new String[]{String.valueOf(contextId)});if(c.moveToFirst()){x.activity=n(c.getString(0));x.openLoop=n(c.getString(1));x.nextStep=n(c.getString(2));}}catch(Throwable ignored){}finally{if(c!=null)c.close();}return x;}
    private static String globalOpenLoops(VaultDb db,int limit){Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT kind,title,body FROM derived_items WHERE state='open' AND kind IN ('ACTION','WAITING','DECISION') ORDER BY CASE kind WHEN 'ACTION' THEN 0 WHEN 'WAITING' THEN 1 ELSE 2 END,importance DESC,updated_at DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,Math.min(6,limit)))});StringBuilder b=new StringBuilder();while(c.moveToNext()){if(b.length()>0)b.append(" | ");String kind=n(c.getString(0)),title=n(c.getString(1)),body=n(c.getString(2));b.append(kind).append(": ").append(!body.isEmpty()?clip(body,120):clip(title,120));}return b.toString();}catch(Throwable ignored){return"";}finally{if(c!=null)c.close();}}
    private static String globalNextStep(VaultDb db){Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT title,body FROM derived_items WHERE state='open' AND kind='ACTION' ORDER BY importance DESC,updated_at DESC LIMIT 1",null);if(!c.moveToFirst())return"";String body=n(c.getString(1));return !body.isEmpty()?clip(body,180):clip(c.getString(0),180);}catch(Throwable ignored){return"";}finally{if(c!=null)c.close();}}
    private static Packet empty(){return new Packet(0,"","","","","","","",0,new ArrayList<>());}private static String join(List<String> xs,int limit){StringBuilder b=new StringBuilder();for(int i=0;i<xs.size()&&i<limit;i++){if(b.length()>0)b.append(" · ");b.append(xs.get(i));}return b.toString();}private static String clip(String s,int n){String x=n(s).replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}private static String norm(String s){return LocalSemanticEmbedder.norm(safe(s));}private static String safe(String s){return s==null?"":s;}private static String n(String s){return safe(s).trim();}
}
