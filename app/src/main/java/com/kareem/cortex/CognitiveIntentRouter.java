package com.kareem.cortex;

import android.database.Cursor;
import java.util.*;

/** High-precision routing before generic semantic retrieval. */
public final class CognitiveIntentRouter {
    private CognitiveIntentRouter(){}

    public static GroundedAnswer tryAnswer(VaultDb db,String question){
        String q=n(question);String z=LocalSemanticEmbedder.norm(q);
        if(isWorkProjectQuery(z))return workProjects(db,q);
        if(isNoiseQuery(z))return noiseAudit(db,q);
        if(isLifecycleQuery(z))return lifecycle(db,q);
        return null;
    }

    private static GroundedAnswer workProjects(VaultDb db,String q){
        ArrayList<String> lines=new ArrayList<>();ArrayList<SemanticHit> src=new ArrayList<>();
        Cursor c=db.getReadableDatabase().rawQuery("SELECT title,body,kind,importance,updated_at FROM derived_items WHERE state IN ('open','pending') AND (kind='PROJECT_CANDIDATE' OR lower(title) LIKE '%project%' OR lower(body) LIKE '%project%' OR title LIKE '%مشروع%' OR body LIKE '%مشروع%' OR lower(title) LIKE '%work%' OR lower(body) LIKE '%work%') ORDER BY importance DESC,updated_at DESC LIMIT 12",null);
        try{while(c.moveToNext()){String t=n(c.getString(0)),b=n(c.getString(1));String line=t+(b.isEmpty()?"":" — "+clip(b,180));if(!line.trim().isEmpty())lines.add(line);}}finally{c.close();}
        ArrayList<SemanticHit> hits=SemanticIndex.searchForAsk(db,"work project deliverable budget drawings site client مشروع شغل ميزانية رسومات موقع",12);for(SemanticHit h:hits){if(!workLike(h.item))continue;boolean exists=false;for(SemanticHit old:src)if(old.item.id==h.item.id){exists=true;break;}if(!exists)src.add(h);if(lines.size()<8){String x=n(h.item.title);String b=!n(h.item.summary).isEmpty()?h.item.summary:h.snippet;lines.add(x+(n(b).isEmpty()?"":" — "+clip(b,180)));}}
        if(lines.isEmpty())return new GroundedAnswer(q,"I don't have a grounded open work/project thread right now.",.72,src,new ArrayList<String>(),new ArrayList<String>());
        return new GroundedAnswer(q,bullet("Open work/project threads I can ground",dedupe(lines,6)),.88,src,new ArrayList<String>(),new ArrayList<String>());
    }

    private static GroundedAnswer noiseAudit(VaultDb db,String q){
        ArrayList<SemanticHit> hits=SemanticIndex.searchForAskRaw(db,q,16);ArrayList<SemanticHit> keep=new ArrayList<>();ArrayList<String> noise=new ArrayList<>();ArrayList<String> context=new ArrayList<>();
        for(SemanticHit h:hits){KnowledgeItem k=h.item;if(k==null)continue;String label=n(k.title);if(isNoise(k)){noise.add(label.isEmpty()?k.type:label);keep.add(h);}else if(isContextOnly(k)){context.add(label.isEmpty()?k.type:label);keep.add(h);}}
        if(noise.isEmpty()&&context.isEmpty())return new GroundedAnswer(q,"I don't see enough grounded low-quality/context-only evidence to classify reliably.",.66,keep,new ArrayList<String>(),new ArrayList<String>());
        StringBuilder a=new StringBuilder();if(!noise.isEmpty()){a.append("Probably noise / low-value evidence:\n");for(String x:dedupe(noise,6))a.append("• ").append(clip(x,120)).append('\n');}if(!context.isEmpty()){if(a.length()>0)a.append('\n');a.append("Context, not an action:\n");for(String x:dedupe(context,6))a.append("• ").append(clip(x,120)).append('\n');}a.append("\nThese should not become tasks or attention items unless newer evidence gives them a concrete obligation.");
        return new GroundedAnswer(q,a.toString().trim(),.90,keep,new ArrayList<String>(),new ArrayList<String>());
    }

    private static GroundedAnswer lifecycle(VaultDb db,String q){
        ArrayList<String> live=new ArrayList<>(),closed=new ArrayList<>();
        Cursor a=db.getReadableDatabase().rawQuery("SELECT title,body,kind FROM derived_items WHERE state IN ('open','pending') ORDER BY importance DESC,updated_at DESC LIMIT 8",null);try{while(a.moveToNext())live.add(n(a.getString(0))+(n(a.getString(1)).isEmpty()?"":" — "+clip(a.getString(1),150)));}finally{a.close();}
        Cursor b=db.getReadableDatabase().rawQuery("SELECT title,body,state FROM derived_items WHERE state IN ('dismissed','resolved','done','closed') ORDER BY updated_at DESC LIMIT 8",null);try{while(b.moveToNext())closed.add(n(b.getString(0))+" — "+n(b.getString(2))+(n(b.getString(1)).isEmpty()?"":" · "+clip(b.getString(1),130)));}finally{b.close();}
        StringBuilder out=new StringBuilder();out.append("Still live:\n");if(live.isEmpty())out.append("• None I can ground.\n");else for(String x:dedupe(live,6))out.append("• ").append(x).append('\n');out.append("\nDo not resurface as live:\n");if(closed.isEmpty())out.append("• No recently closed obligations found.\n");else for(String x:dedupe(closed,6))out.append("• ").append(x).append('\n');
        return new GroundedAnswer(q,out.toString().trim(),.92,new ArrayList<SemanticHit>(),new ArrayList<String>(),new ArrayList<String>());
    }

    private static boolean workLike(KnowledgeItem k){String x=LocalSemanticEmbedder.norm(n(k.title)+" "+n(k.summary)+" "+n(k.extractedText)+" "+n(k.rawText)+" "+n(k.category)+" "+n(k.tags));return has(x,"project","work","site","client","budget","drawing","drawings","مشروع","شغل","موقع","ميزانية","رسومات","عميل");}
    private static boolean isNoise(KnowledgeItem k){String x=LocalSemanticEmbedder.norm(n(k.title)+" "+n(k.summary)+" "+n(k.extractedText)+" "+n(k.rawText));if(AskSourcePolicy.isSelfUiScreenshot(k))return true;if(("SCREENSHOT".equals(k.type)||"IMAGE".equals(k.type))&&OcrGarbageGate.looksGarbage(x))return true;return has(x,"screenshot saved","package installer","system ui","systemui","play store download","response ready");}
    private static boolean isContextOnly(KnowledgeItem k){String x=LocalSemanticEmbedder.norm(n(k.category)+" "+n(k.tags)+" "+n(k.summary));return has(x,"reference","research","context","contact","links & research")&&!has(x,"todo","action","follow up","reminder","decision","deadline","appointment","لازم","متابعة","موعد");}
    private static boolean isWorkProjectQuery(String z){return has(z,"work","project","projects","project threads","work threads","شغل","مشروع","مشاريع")&&has(z,"open","still","ongoing","thread","threads","مفتوح","مستمرة","مستمر");}
    private static boolean isNoiseQuery(String z){return has(z,"noise","context rather than actions","not actions","probably noise","ضوضاء","مش مهمة","مش actions");}
    private static boolean isLifecycleQuery(String z){return has(z,"lifecycle","genuinely still live","must not be resurfaced","dismissed","resolved","done","closed","مقفول","اتحل","خلص");}
    private static boolean has(String z,String...xs){for(String x:xs)if(z.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static ArrayList<String> dedupe(List<String> xs,int max){LinkedHashMap<String,String> m=new LinkedHashMap<>();for(String x:xs){String k=LocalSemanticEmbedder.norm(x);if(k.length()>100)k=k.substring(0,100);if(!k.isEmpty()&&!m.containsKey(k))m.put(k,x);if(m.size()>=max)break;}return new ArrayList<>(m.values());}
    private static String bullet(String head,List<String> xs){StringBuilder s=new StringBuilder(head).append(":\n");for(String x:xs)s.append("• ").append(x).append('\n');return s.toString().trim();}
    private static String clip(String s,int n){String x=n(s).replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String n(String s){return s==null?"":s.trim();}
}
