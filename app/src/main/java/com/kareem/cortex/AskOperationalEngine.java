package com.kareem.cortex;

import android.database.Cursor;
import java.util.*;

/** Answers state questions from live Cortex state instead of semantic similarity. */
public final class AskOperationalEngine {
    private AskOperationalEngine(){}

    public static GroundedAnswer tryAnswer(VaultDb db,String question){
        String q=n(question),norm=LocalSemanticEmbedder.norm(q);
        if(q.isEmpty())return null;
        if(isAttention(norm))return attention(db,q);
        if(isWaiting(norm))return waiting(db,q);
        if(isRecentDecisions(norm))return decisions(db,q);
        if(isContextlessProject(norm))return new GroundedAnswer(q,"Tell me which project you mean, and I’ll ground the answer in that project’s Cortex context.",1.0,new ArrayList<SemanticHit>(),new ArrayList<String>(),new ArrayList<String>());
        return null;
    }

    private static GroundedAnswer attention(VaultDb db,String q){
        ContactSafetyMaintenance.run(db);CognitiveStore.ensure(db);ReviewQueueStore.expireStale(db);
        LinkedHashSet<String> lines=new LinkedHashSet<>();ArrayList<SemanticHit> sources=new ArrayList<>();ArrayList<String> loops=new ArrayList<>();
        Cursor c=db.getReadableDatabase().rawQuery("SELECT a.item_id,a.action_text,a.due_text FROM actions a JOIN knowledge_items k ON k.id=a.item_id LEFT JOIN smart_inbox si ON si.item_id=k.id WHERE a.status='open' AND NOT (k.type='CONTACT' AND k.source='contacts_sync') AND (k.type NOT IN ('SCREENSHOT','IMAGE') OR COALESCE(si.manual_bucket,0)=1) ORDER BY CASE WHEN a.due_text IS NULL OR TRIM(a.due_text)='' THEN 1 ELSE 0 END,a.created_at DESC LIMIT 12",null);
        while(c.moveToNext()){long itemId=c.getLong(0);String action=n(c.getString(1)),due=n(c.getString(2));if(action.isEmpty())continue;String line=action+(due.isEmpty()?"":" — due: "+TemporalResolver.displayStored(due));if(lines.add(line)){loops.add(line);addSource(db,sources,itemId,.98,action);}}c.close();
        Cursor d=db.getReadableDatabase().rawQuery("SELECT kind,title,body,confidence FROM derived_items WHERE state='open' AND kind IN ('ACTION','WAITING') ORDER BY importance DESC,updated_at DESC LIMIT 12",null);while(d.moveToNext()){String kind=n(d.getString(0)),title=n(d.getString(1)),body=n(d.getString(2));String payload=!body.isEmpty()?body:title;if(payload.isEmpty())continue;String line=("WAITING".equals(kind)?"Waiting: ":"Action: ")+clip(payload,180);if(lines.add(line))loops.add(line);}d.close();
        for(ReviewQueueStore.Item r:ReviewQueueStore.pending(db,5)){String payload=!r.body.isEmpty()?r.body:r.title;String line="Review "+friendly(r.candidateKind)+": "+clip(payload,160);lines.add(line);}
        Cursor f=db.getReadableDatabase().rawQuery("SELECT id,title FROM knowledge_items WHERE status IN ('analysis_failed','failed_retryable') AND NOT (type='CONTACT' AND source='contacts_sync') ORDER BY updated_at DESC LIMIT 5",null);while(f.moveToNext()){long id=f.getLong(0);String line="Needs retry: "+n(f.getString(1));if(lines.add(line))addSource(db,sources,id,.90,line);}f.close();
        String answer;if(lines.isEmpty())answer="Nothing currently needs your attention in Cortex.";else{StringBuilder s=new StringBuilder("Here’s what currently needs your attention:\n");int i=0;for(String x:lines){s.append("• ").append(x).append('\n');if(++i>=10)break;}answer=s.toString().trim();}
        return new GroundedAnswer(q,answer,.97,sources,loops,new ArrayList<String>());
    }

    private static GroundedAnswer waiting(VaultDb db,String q){
        CognitiveStore.ensure(db);LinkedHashSet<String> xs=new LinkedHashSet<>();ArrayList<String> loops=new ArrayList<>();
        Cursor c=db.getReadableDatabase().rawQuery("SELECT title,body FROM derived_items WHERE kind='WAITING' AND state='open' ORDER BY importance DESC,updated_at DESC LIMIT 12",null);while(c.moveToNext()){String x=n(c.getString(1));if(x.isEmpty())x=n(c.getString(0));if(!x.isEmpty())xs.add(clip(x,180));}c.close();for(String x:xs)loops.add(x);String answer;if(xs.isEmpty())answer="Cortex doesn’t currently have any confirmed Waiting items.";else{StringBuilder s=new StringBuilder("You’re currently waiting on:\n");for(String x:xs)s.append("• ").append(x).append('\n');answer=s.toString().trim();}return new GroundedAnswer(q,answer,.97,new ArrayList<SemanticHit>(),loops,new ArrayList<String>());
    }

    private static GroundedAnswer decisions(VaultDb db,String q){
        CognitiveStore.ensure(db);ArrayList<String> xs=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT title,body FROM derived_items WHERE kind='DECISION' AND state IN ('open','confirmed') ORDER BY updated_at DESC LIMIT 10",null);while(c.moveToNext()){String x=n(c.getString(1));if(x.isEmpty())x=n(c.getString(0));if(!x.isEmpty())xs.add(clip(x,220));}c.close();String answer;if(xs.isEmpty())answer="I don’t have any confirmed recent decisions in the Cortex decision ledger yet.";else{StringBuilder s=new StringBuilder("Recent confirmed decisions:\n");for(String x:xs)s.append("• ").append(x).append('\n');answer=s.toString().trim();}return new GroundedAnswer(q,answer,.96,new ArrayList<SemanticHit>(),new ArrayList<String>(),xs);
    }

    private static boolean isAttention(String q){return has(q,"what still needs my attention","what needs my attention","what needs me","what do i need to do","what should i do","needs attention","محتاج انتباهي","محتاج مني","محتاج اعمل ايه","محتاج أعمل ايه","ايه اللي محتاجني","إيه اللي محتاجني","ايه اللي محتاج اهتمامي","إيه اللي محتاج اهتمامي");}
    private static boolean isWaiting(String q){return has(q,"what am i waiting for","what am i waiting on","what are we waiting for","waiting on","waiting for","مستني ايه","مستنى ايه","منتظر ايه","في انتظار ايه");}
    private static boolean isRecentDecisions(String q){return has(q,"what did i decide recently","recent decisions","what have i decided","قررت ايه مؤخرا","قررت ايه قريب","ايه القرارات الاخيره","إيه القرارات الأخيرة");}
    private static boolean isContextlessProject(String q){return has(q,"this project","المشروع ده","المشروع دا","البروجكت ده","البروجكت دا");}
    private static boolean has(String t,String... xs){for(String x:xs)if(t.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static void addSource(VaultDb db,ArrayList<SemanticHit> out,long id,double score,String snippet){for(SemanticHit h:out)if(h.item.id==id)return;KnowledgeItem k=db.getById(id);if(k!=null)out.add(new SemanticHit(k,score,snippet));}
    private static String friendly(String x){String k=n(x).toLowerCase(Locale.ROOT).replace('_',' ');return k.isEmpty()?"item":k;}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String n(String s){return s==null?"":s.trim();}
}
