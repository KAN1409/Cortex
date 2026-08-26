package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** Read-only Context diagnostics/replay. It never invokes the resolver and never mutates Context state. */
public final class ContextDiagnostics {
    private ContextDiagnostics(){}

    public static final class Report {
        public final long generatedAt,primaryContextId;
        public final String text,json;
        public final int stackCount,episodeCount,snapshotCount,feedbackCount,evidenceCount;
        Report(long at,long primary,String text,String json,int stack,int episodes,int snaps,int feedback,int evidence){generatedAt=at;primaryContextId=primary;this.text=text;this.json=json;stackCount=stack;episodeCount=episodes;snapshotCount=snaps;feedbackCount=feedback;evidenceCount=evidence;}
    }

    public static Report build(VaultDb db,long windowMs,int limit){
        if(db==null)return new Report(System.currentTimeMillis(),0,"Context diagnostics unavailable.","{}",0,0,0,0,0);ContextSchema.ensure(db);CognitiveStore.ensure(db);
        long now=System.currentTimeMillis(),since=now-Math.max(5L*60L*1000L,Math.min(24L*60L*60L*1000L,windowMs));int cap=Math.max(5,Math.min(80,limit));
        ContextStateStore.ContextState primary=ContextStateStore.primary(db);ArrayList<ContextStateStore.ContextState> stack=ContextStateStore.stack(db,12);
        JSONArray stackJson=new JSONArray(),timeline=new JSONArray(),feedbackJson=new JSONArray(),evidenceJson=new JSONArray();StringBuilder out=new StringBuilder();
        out.append("CONTEXT DIAGNOSTICS · READ ONLY\nGenerated: ").append(time(now)).append("\nWindow: ").append(duration(now-since)).append("\n\n");
        if(primary==null)out.append("PRIMARY\nNone\n\n");else{
            ContextOpenLoopResolver.State resume=ContextOpenLoopResolver.resolve(db,primary.id);
            out.append("PRIMARY\n").append(primary.title).append(" · ").append(Math.round(primary.stackConfidence*100)).append("%\nKey: ").append(primary.stableKey).append("\nLifecycle: ").append(primary.lifecycle).append(" · role ").append(primary.role).append("\nTransition: ").append(primary.transitionReason).append('\n');
            if(!resume.openLoop.isEmpty())out.append("Open loop: ").append(resume.openLoop).append("\nGrounding: ").append(resume.provenance()).append('\n');
            if(!resume.nextStep.isEmpty())out.append("Next: ").append(resume.nextStep).append('\n');
            out.append('\n');
        }
        out.append("STACK\n");for(ContextStateStore.ContextState x:stack){out.append(x.primary()?"▶ ":"· ").append(x.title).append(" · ").append(x.role).append(" · ").append(Math.round(x.stackConfidence*100)).append("% · evidence ").append(age(now,x.lastEvidenceAt)).append('\n');try{stackJson.put(new JSONObject().put("context_id",x.id).put("stable_key",x.stableKey).put("title",x.title).put("role",x.role).put("lifecycle",x.lifecycle).put("confidence",x.stackConfidence).put("last_evidence_at",x.lastEvidenceAt).put("transition_reason",x.transitionReason));}catch(Exception ignored){}}out.append('\n');

        int episodes=appendEpisodes(db,since,cap,timeline,out);int snaps=appendSnapshots(db,since,cap,timeline,out);sortTimeline(timeline);appendReplay(timeline,out);
        int feedback=appendFeedback(db,since,cap,feedbackJson,out);int evidence=primary==null?0:appendEvidence(db,primary.id,cap,evidenceJson,out);
        try{
            JSONObject root=new JSONObject().put("schema","cortex_context_replay_v1").put("read_only",true).put("generated_at",now).put("since",since).put("primary_context_id",primary==null?0:primary.id).put("stack",stackJson).put("timeline",timeline).put("feedback",feedbackJson).put("primary_evidence",evidenceJson);
            return new Report(now,primary==null?0:primary.id,out.toString().trim(),root.toString(),stack.size(),episodes,snaps,feedback,evidence);
        }catch(Exception e){return new Report(now,primary==null?0:primary.id,out.toString().trim(),"{}",stack.size(),episodes,snaps,feedback,evidence);}
    }

    private static int appendEpisodes(VaultDb db,long since,int limit,JSONArray timeline,StringBuilder ignoredOut){Cursor c=null;int n=0;try{c=db.getReadableDatabase().rawQuery("SELECT e.id,e.context_id,c.title,e.transition,e.reason,e.confidence,e.anchor_signal_id,e.started_at,e.ended_at,e.state FROM context_episodes e JOIN contexts c ON c.id=e.context_id WHERE e.started_at>=? OR e.ended_at>=? ORDER BY e.started_at DESC LIMIT ?",new String[]{String.valueOf(since),String.valueOf(since),String.valueOf(limit)});while(c.moveToNext()){JSONObject x=new JSONObject().put("event","EPISODE").put("event_at",c.getLong(7)).put("episode_id",c.getLong(0)).put("context_id",c.getLong(1)).put("context_title",n(c.getString(2))).put("transition",n(c.getString(3))).put("reason",n(c.getString(4))).put("confidence",c.getDouble(5)).put("anchor_signal_id",c.getLong(6)).put("started_at",c.getLong(7)).put("ended_at",c.getLong(8)).put("state",n(c.getString(9)));timeline.put(x);n++;}}catch(Throwable ignored){}finally{if(c!=null)c.close();}return n;}
    private static int appendSnapshots(VaultDb db,long since,int limit,JSONArray timeline,StringBuilder ignoredOut){Cursor c=null;int n=0;try{c=db.getReadableDatabase().rawQuery("SELECT s.id,s.context_id,c.title,s.current_activity,s.open_loop,s.next_step,s.evidence_summary,s.created_at FROM context_snapshots s JOIN contexts c ON c.id=s.context_id WHERE s.created_at>=? ORDER BY s.created_at DESC LIMIT ?",new String[]{String.valueOf(since),String.valueOf(limit)});while(c.moveToNext()){timeline.put(new JSONObject().put("event","SNAPSHOT").put("event_at",c.getLong(7)).put("snapshot_id",c.getLong(0)).put("context_id",c.getLong(1)).put("context_title",n(c.getString(2))).put("current_activity",n(c.getString(3))).put("open_loop",n(c.getString(4))).put("next_step",n(c.getString(5))).put("evidence_summary",n(c.getString(6))));n++;}}catch(Throwable ignored){}finally{if(c!=null)c.close();}return n;}
    private static void sortTimeline(JSONArray a){ArrayList<JSONObject> xs=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)xs.add(x);}xs.sort(Comparator.comparingLong(x->x.optLong("event_at",0)));while(a.length()>0)a.remove(a.length()-1);for(JSONObject x:xs)a.put(x);}
    private static void appendReplay(JSONArray timeline,StringBuilder out){out.append("REPLAY\n");if(timeline.length()==0){out.append("No Context transitions/snapshots in this window.\n\n");return;}for(int i=0;i<timeline.length();i++){JSONObject x=timeline.optJSONObject(i);if(x==null)continue;out.append(time(x.optLong("event_at",0))).append(" · ").append(x.optString("event",""));String title=x.optString("context_title","");if(!title.isEmpty())out.append(" · ").append(title);if("EPISODE".equals(x.optString("event"))){String tr=x.optString("transition","");if(!tr.isEmpty())out.append(" · ").append(tr);String reason=x.optString("reason","");if(!reason.isEmpty())out.append("\n  why: ").append(clip(reason,220));}else{String act=x.optString("current_activity","");if(!act.isEmpty())out.append("\n  now: ").append(clip(act,180));String loop=x.optString("open_loop","");if(!loop.isEmpty())out.append("\n  loop: ").append(clip(loop,180));String next=x.optString("next_step","");if(!next.isEmpty())out.append("\n  next: ").append(clip(next,180));}out.append('\n');}out.append('\n');}
    private static int appendFeedback(VaultDb db,long since,int limit,JSONArray json,StringBuilder out){Cursor c=null;int n=0;out.append("USER CORRECTIONS\n");try{c=db.getReadableDatabase().rawQuery("SELECT f.id,f.context_id,COALESCE(x.title,''),f.other_context_id,f.event_type,f.value_json,f.created_at FROM context_feedback f LEFT JOIN contexts x ON x.id=f.context_id WHERE f.created_at>=? ORDER BY f.created_at DESC LIMIT ?",new String[]{String.valueOf(since),String.valueOf(limit)});while(c.moveToNext()){JSONObject j=new JSONObject().put("feedback_id",c.getLong(0)).put("context_id",c.getLong(1)).put("context_title",n(c.getString(2))).put("other_context_id",c.getLong(3)).put("event_type",n(c.getString(4))).put("value_json",n(c.getString(5))).put("created_at",c.getLong(6));json.put(j);out.append(time(c.getLong(6))).append(" · ").append(n(c.getString(4))).append(" · ").append(n(c.getString(2))).append('\n');n++;}}catch(Throwable ignored){}finally{if(c!=null)c.close();}if(n==0)out.append("None in this window.\n");out.append('\n');return n;}
    private static int appendEvidence(VaultDb db,long contextId,int limit,JSONArray json,StringBuilder out){Cursor c=null;int n=0;out.append("PRIMARY EVIDENCE\n");try{c=db.getReadableDatabase().rawQuery("SELECT from_type,from_id,relation,confidence,metadata_json,created_at FROM source_links WHERE to_type='context' AND to_id=? ORDER BY created_at DESC LIMIT ?",new String[]{String.valueOf(contextId),String.valueOf(limit)});while(c.moveToNext()){json.put(new JSONObject().put("from_type",n(c.getString(0))).put("from_id",c.getLong(1)).put("relation",n(c.getString(2))).put("confidence",c.getDouble(3)).put("metadata_json",n(c.getString(4))).put("created_at",c.getLong(5)));out.append(time(c.getLong(5))).append(" · ").append(n(c.getString(0))).append(" #").append(c.getLong(1)).append(" · ").append(n(c.getString(2))).append(" · ").append(Math.round(c.getDouble(3)*100)).append("%\n");n++;}}catch(Throwable ignored){}finally{if(c!=null)c.close();}if(n==0)out.append("No linked evidence.\n");return n;}

    private static String time(long at){if(at<=0)return"unknown";try{return new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(at));}catch(Throwable ignored){return String.valueOf(at);}}
    private static String age(long now,long at){if(at<=0)return"unknown";long d=Math.max(0,now-at);if(d<60_000)return d/1000+"s ago";if(d<60L*60L*1000L)return d/60_000+"m ago";return d/(60L*60L*1000L)+"h ago";}
    private static String duration(long ms){long m=Math.max(1,ms/60_000);return m<60?m+" min":String.format(Locale.US,"%.1f h",m/60f);}
    private static String clip(String s,int max){String x=n(s).replaceAll("\\s+"," ");return x.length()<=max?x:x.substring(0,max)+"…";}
    private static String n(String s){return s==null?"":s.trim();}
}
