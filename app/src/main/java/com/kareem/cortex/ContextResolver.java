package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;
import java.util.*;

/**
 * Conservative local context resolver.
 * It prefers explicit project/thread anchors and intentional captures; phone activity can challenge
 * only when repeated evidence exists. ContextStateStore supplies hysteresis so app switching alone
 * does not constantly replace the user's cognitive context.
 */
public final class ContextResolver {
    private static final long PROJECT_WINDOW=60L*60L*1000L,THREAD_WINDOW=45L*60L*1000L,CAPTURE_WINDOW=25L*60L*1000L,PHONE_WINDOW=15L*60L*1000L;
    private static final long ACTIVE_INTERRUPT_WINDOW=6L*60L*1000L,RESUME_BACKGROUND_WINDOW=2L*60L*60L*1000L;
    private ContextResolver(){}

    private static final class Candidate {
        String key="",title="",goal="",summary="",reason="",sourceType="",metadata="{}";long sourceId=0,evidenceAt=0,anchorSignalId=0;double confidence=0;int priority=50;
    }

    public static ContextStateStore.ContextState refresh(VaultDb db){
        if(db==null)return null;ContextSchema.ensure(db);long now=System.currentTimeMillis();Candidate best=best(db,now);
        if(best==null){ContextStateStore.suspendPrimaryIfStale(db,30L*60L*1000L,"No strong context evidence recently");return ContextStateStore.primary(db);}
        long id=ContextStateStore.upsert(db,best.key,best.title,"TASK",ContextStateStore.LIFE_ACTIVE,best.confidence,best.goal,best.summary,best.metadata,best.evidenceAt);
        if(id<=0)return ContextStateStore.primary(db);
        ContextStateStore.Offer offer=ContextStateStore.offerPrimary(db,id,best.confidence,best.priority,best.reason,best.evidenceAt,best.anchorSignalId);
        if(best.sourceId>0)ContextStateStore.linkEvidence(db,best.sourceType,best.sourceId,id,"supports_context",best.confidence,json("resolver",best.reason));
        ContextStateStore.ContextState current=ContextStateStore.primary(db);
        if(current!=null&&current.id==id&&shouldSnapshot(db,current.id,offer.transition,now)){
            String loop=openLoop(db,current),next=nextStep(db,current);ContextStateStore.recordSnapshot(db,current.id,best.summary,loop,next,evidenceLine(best),"{\"local_only\":true,\"cloud_summary_allowed\":false}");
        }
        return current;
    }

    private static Candidate best(VaultDb db,long now){
        Candidate project=projectCandidate(db,now),thread=threadCandidate(db,now),capture=captureCandidate(db,now),phone=phoneCandidate(db,now);ContextStateStore.ContextState current=ContextStateStore.primary(db);
        // A fresh communication thread that begins after a project/context anchor is treated as a
        // temporary interruption, not as permanent project replacement.
        long anchorAt=project!=null?project.evidenceAt:(current==null?0:current.lastEvidenceAt);
        if(thread!=null&&now-thread.evidenceAt<=ACTIVE_INTERRUPT_WINDOW&&thread.evidenceAt>anchorAt+5_000L&&(current==null||!current.stableKey.equals(thread.key))){thread.confidence=.96;thread.priority=98;thread.reason=ContextBoundaryDetector.interrupt("A newer active conversation interrupted the prior working context");return learned(db,thread);}

        // Once a short interruption goes quiet, prefer the explicit project again. If no fresh
        // project evidence exists, resume the strongest recent suspended/background context.
        if(current!=null&&current.stableKey.startsWith("thread:")&&now-Math.max(current.lastEvidenceAt,current.lastActiveAt)>ACTIVE_INTERRUPT_WINDOW){
            if(project!=null){project.confidence=.95;project.priority=97;project.reason=ContextBoundaryDetector.resume("Return to the recent project after the conversation interruption");return learned(db,project);}
            Candidate background=backgroundResumeCandidate(db,now,current.id);if(background!=null)return learned(db,background);
        }

        ArrayList<Candidate> xs=new ArrayList<>();if(project!=null)xs.add(project);if(thread!=null)xs.add(thread);if(capture!=null)xs.add(capture);if(phone!=null)xs.add(phone);if(xs.isEmpty())return null;for(Candidate x:xs)learned(db,x);xs.sort((a,b)->{int c=Double.compare(b.confidence,a.confidence);if(c!=0)return c;return Long.compare(b.evidenceAt,a.evidenceAt);});return xs.get(0);
    }

    private static Candidate learned(VaultDb db,Candidate x){if(x==null)return null;double boost=0;try{boost=ContextFingerprintLearner.boost(db,x.key);}catch(Throwable ignored){}if(Math.abs(boost)>=.005){x.confidence=Math.max(.55,Math.min(.99,x.confidence+boost));x.reason=x.reason+" · learned fingerprint "+(boost>0?"+":"")+Math.round(boost*100)+"%";}return x;}

    private static Candidate backgroundResumeCandidate(VaultDb db,long now,long excludeId){try{for(ContextStateStore.ContextState s:ContextStateStore.stack(db,8)){if(s.id==excludeId||!ContextStateStore.ROLE_BACKGROUND.equals(s.role))continue;long seen=Math.max(s.lastEvidenceAt,s.lastActiveAt);if(seen<=0||now-seen>RESUME_BACKGROUND_WINDOW)continue;Candidate x=new Candidate();x.key=s.stableKey;x.title=s.title;x.goal=s.goal;x.summary=s.summary;x.evidenceAt=now;x.confidence=Math.max(.86,Math.min(.95,s.stackConfidence));x.priority=Math.max(86,s.priority);x.reason=ContextBoundaryDetector.resume("Resume the most recent suspended working context after a short interruption");x.sourceType="context";x.metadata="{\"resolver\":\"background_resume\",\"local_only\":true}";return x;}}catch(Throwable ignored){}return null;}

    /** A confirmed Project entity linked to recent evidence is the strongest stable context identity. */
    private static Candidate projectCandidate(VaultDb db,long now){Cursor c=null;try{String sql="SELECT n.id,n.canonical_name,k.id,k.title,k.summary,k.extracted_text,k.raw_text,k.created_at FROM source_links sl JOIN entity_nodes n ON n.id=sl.to_id JOIN knowledge_items k ON k.id=sl.from_id WHERE sl.from_type='memory' AND sl.to_type='entity' AND n.kind='PROJECT' AND n.status='active' AND k.created_at>=? ORDER BY k.created_at DESC,sl.confidence DESC LIMIT 1";c=db.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(now-PROJECT_WINDOW)});if(!c.moveToFirst())return null;long entityId=c.getLong(0),memoryId=c.getLong(2);String project=n(c.getString(1)),title=n(c.getString(3)),summary=n(c.getString(4)),extracted=n(c.getString(5)),raw=n(c.getString(6)),body=!summary.isEmpty()?summary:!extracted.isEmpty()?extracted:raw;Candidate x=new Candidate();x.sourceId=memoryId;x.key="project:"+entityId;x.title=!project.isEmpty()?project:(!title.isEmpty()?title:"Current project");x.goal=clip(body,200);x.summary=(!title.isEmpty()?title+(body.isEmpty()?"":" · "):"")+clip(body,320);x.evidenceAt=c.getLong(7);x.confidence=.94;x.priority=96;x.reason="Recent evidence linked to a confirmed Project entity";x.sourceType="memory";x.metadata="{\"resolver\":\"project_entity\",\"project_entity_id\":"+entityId+",\"local_only\":true}";return x;}catch(Throwable ignored){return null;}finally{if(c!=null)c.close();}}

    private static Candidate threadCandidate(VaultDb db,long now){
        Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT id,title,summary,participant_key,last_event_at FROM signal_threads WHERE state='open' AND last_event_at>=? ORDER BY last_event_at DESC LIMIT 1",new String[]{String.valueOf(now-THREAD_WINDOW)});if(!c.moveToFirst())return null;Candidate x=new Candidate();x.sourceId=c.getLong(0);x.key="thread:"+x.sourceId;String title=n(c.getString(1)),summary=n(c.getString(2)),person=n(c.getString(3));x.title=!title.isEmpty()?title:(!person.isEmpty()?"Conversation with "+person:"Active conversation");x.goal=!summary.isEmpty()?clip(summary,180):x.title;x.summary=!summary.isEmpty()?summary:x.title;x.evidenceAt=c.getLong(4);long age=Math.max(0,now-x.evidenceAt);x.confidence=age<=10L*60L*1000L?.92:.84;x.priority=90;x.reason="Active Cortex signal thread";x.sourceType="thread";x.metadata="{\"resolver\":\"signal_thread\",\"local_only\":true}";return x;}catch(Throwable ignored){return null;}finally{if(c!=null)c.close();}}

    private static Candidate captureCandidate(VaultDb db,long now){
        Cursor c=null;try{String sql="SELECT id,title,summary,extracted_text,raw_text,category,created_at FROM knowledge_items WHERE created_at>=? AND source IN ('manual','manual_recording','android_share','audio_import','quick_capture','screen_understanding','screen_understand') ORDER BY created_at DESC LIMIT 1";c=db.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(now-CAPTURE_WINDOW)});if(!c.moveToFirst())return null;Candidate x=new Candidate();x.sourceId=c.getLong(0);String title=n(c.getString(1)),summary=n(c.getString(2)),extracted=n(c.getString(3)),raw=n(c.getString(4)),category=n(c.getString(5));String body=!summary.isEmpty()?summary:!extracted.isEmpty()?extracted:raw;String basis=!category.isEmpty()?category+"|"+title:title+"|"+clip(body,160);x.key="capture:"+shortHash(basis);x.title=!title.isEmpty()?title:(!category.isEmpty()?category:"Recent Cortex capture");x.goal=clip(body,180);x.summary=clip(body,360);x.evidenceAt=c.getLong(6);x.confidence=.84;x.priority=82;x.reason="Recent intentional Cortex capture";x.sourceType="memory";x.metadata="{\"resolver\":\"intentional_capture\",\"local_only\":true}";return x;}catch(Throwable ignored){return null;}finally{if(c!=null)c.close();}}

    private static Candidate phoneCandidate(VaultDb db,long now){
        try{PhoneContextStore.ensure(db);ArrayList<PhoneContextStore.Event> events=PhoneContextStore.recent(db,now-PHONE_WINDOW,40);if(events.isEmpty())return null;LinkedHashMap<String,ArrayList<PhoneContextStore.Event>> byPkg=new LinkedHashMap<>();for(PhoneContextStore.Event e:events){String pkg=n(e.packageName);if(pkg.isEmpty()||pkg.equals("com.kareem.cortex")||pkg.contains("launcher"))continue;byPkg.computeIfAbsent(pkg,k->new ArrayList<>()).add(e);}ArrayList<PhoneContextStore.Event> best=null;for(ArrayList<PhoneContextStore.Event> group:byPkg.values())if(group.size()>=3&&(best==null||group.get(0).occurredAt>best.get(0).occurredAt))best=group;if(best==null)return null;PhoneContextStore.Event latest=best.get(0);String app=!n(latest.appLabel).isEmpty()?latest.appLabel:latest.packageName,text=meaningful(best);Candidate x=new Candidate();x.sourceId=latest.id;x.key="phone:"+latest.packageName+(text.isEmpty()?"":"|"+shortHash(topic(text)));x.title=text.isEmpty()?"Working in "+app:clip(text,72);x.goal=text.isEmpty()?"Continue current activity in "+app:clip(text,180);x.summary=("Recent repeated activity in "+app)+(text.isEmpty()?"":" · "+clip(text,260));x.evidenceAt=latest.occurredAt;x.confidence=Math.min(.86,.76+Math.min(5,best.size())*.02);x.priority=70;x.reason="Repeated phone activity with stable app/topic evidence";x.sourceType="phone_context";x.metadata="{\"resolver\":\"phone_context\",\"package\":\""+escape(latest.packageName)+"\",\"local_only\":true}";return x;}catch(Throwable ignored){return null;}}

    private static String meaningful(ArrayList<PhoneContextStore.Event> xs){for(PhoneContextStore.Event e:xs){String t=n(e.text);if(t.isEmpty()||t.startsWith("<sensitive"))continue;if(t.length()>=5)return t;}return"";}
    private static String topic(String text){String n=LocalSemanticEmbedder.norm(text);StringBuilder b=new StringBuilder();int z=0;for(String w:n.split(" ")){if(w.length()<3)continue;if(b.length()>0)b.append(' ');b.append(w);if(++z>=6)break;}return b.toString();}

    private static String openLoop(VaultDb db,ContextStateStore.ContextState c){return derived(db,c,"WAITING","ACTION","DECISION");}
    private static String nextStep(VaultDb db,ContextStateStore.ContextState c){return derived(db,c,"ACTION","WAITING","DECISION");}
    private static String derived(VaultDb db,ContextStateStore.ContextState context,String...kinds){Cursor c=null;try{String thread="";if(context.stableKey.startsWith("thread:"))thread=context.stableKey.substring(7);StringBuilder in=new StringBuilder();for(int i=0;i<kinds.length;i++){if(i>0)in.append(',');in.append('\'').append(kinds[i]).append('\'');}String base="SELECT title,body FROM derived_items WHERE state='open' AND kind IN ("+in+")";boolean found=false;if(!thread.isEmpty()){c=db.getReadableDatabase().rawQuery(base+" AND thread_id=? ORDER BY importance DESC,updated_at DESC LIMIT 1",new String[]{thread});found=c.moveToFirst();if(!found){c.close();c=null;}}if(!found){c=db.getReadableDatabase().rawQuery(base+" ORDER BY importance DESC,updated_at DESC LIMIT 1",null);found=c.moveToFirst();}if(!found)return"";String title=n(c.getString(0)),body=n(c.getString(1));return !body.isEmpty()?clip(body,220):clip(title,220);}catch(Throwable ignored){return"";}finally{if(c!=null)c.close();}}

    private static boolean shouldSnapshot(VaultDb db,long contextId,String transition,long now){if(!"CONTINUE".equals(transition))return true;Cursor c=null;try{c=db.getReadableDatabase().rawQuery("SELECT created_at FROM context_snapshots WHERE context_id=? ORDER BY created_at DESC LIMIT 1",new String[]{String.valueOf(contextId)});long last=c.moveToFirst()?c.getLong(0):0;return last==0||now-last>=20L*60L*1000L;}catch(Throwable ignored){return true;}finally{if(c!=null)c.close();}}
    private static String evidenceLine(Candidate x){return x.reason+" · confidence "+Math.round(x.confidence*100)+"%";}
    private static JSONObject json(String k,String v){JSONObject o=new JSONObject();try{o.put(k,v);o.put("local_only",true);}catch(Exception ignored){}return o;}
    private static String shortHash(String s){String x=Fingerprint.text("context|"+safe(s));return x.length()<=16?x:x.substring(0,16);}
    private static String clip(String s,int n){String x=n(s).replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}private static String escape(String s){return safe(s).replace("\\","\\\\").replace("\"","\\\"");}private static String safe(String s){return s==null?"":s;}private static String n(String s){return safe(s).trim();}
}
