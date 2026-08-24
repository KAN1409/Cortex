package com.kareem.cortex;

import org.json.JSONObject;
import java.util.*;

/** Bridges analyzed intentional captures into the unified derived graph without turning passive evidence into tasks. */
public final class IntentionalCognitiveBridge {
    public static final String POLICY="intentional_bridge_001";
    private IntentionalCognitiveBridge(){}

    public static void afterAnalysis(VaultDb db,KnowledgeItem item,AnalysisResult r){
        if(db==null||item==null||r==null)return;CognitiveStore.ensure(db);
        if(passive(item))return;
        String text=bestText(item,r),norm=LocalSemanticEmbedder.norm(text);boolean intentional=intentional(item);
        try{
            // Existing action parser is only promoted for explicit user-intent captures. Weak parses go through Review.
            if(intentional){for(AnalysisResult.Action a:r.actions){String action=n(a.text);if(action.isEmpty())continue;if(strongAction(norm,LocalSemanticEmbedder.norm(action)))add(db,item,CognitiveTypes.DerivedKind.ACTION,action,action,"open",.92,76);else review(db,item,CognitiveTypes.DerivedKind.ACTION,action,action,.62,56,"intentional capture contains an action-like clause but explicit commitment is uncertain");}}

            if(intentional&&hasAny(norm,"waiting for","waiting on","awaiting","مستني","مستنى","منتظر","في انتظار","لما يرد","لما ترد"))add(db,item,CognitiveTypes.DerivedKind.WAITING,titleFor(r,"Waiting"),clip(text,700),"open",.90,70);
            if(intentional&&hasAny(norm,"i decided","we decided","decided to","agreed to","approved","قررت","قررنا","اتفقنا","اعتمدنا","تمت الموافقه","تم الموافقة"))add(db,item,CognitiveTypes.DerivedKind.DECISION,titleFor(r,"Decision"),clip(text,700),"open",.91,72);
            if(intentional&&hasAny(norm,"goal:","my goal","goal is","هدفي","الهدف:","هدف:"))add(db,item,CognitiveTypes.DerivedKind.GOAL_SIGNAL,titleFor(r,"Goal"),clip(text,700),"open",.94,74);
            if(intentional&&hasAny(norm,"idea:","idea is","فكره:","فكرة:","عندي فكره","عندي فكرة"))add(db,item,CognitiveTypes.DerivedKind.IDEA,titleFor(r,"Idea"),clip(text,700),"open",.94,58);
            if(intentional&&hasAny(norm,"opportunity:","فرصه:","فرصة:","دي فرصه","دي فرصة"))add(db,item,CognitiveTypes.DerivedKind.OPPORTUNITY,titleFor(r,"Opportunity"),clip(text,700),"open",.94,66);
            if(intentional&&hasAny(norm,"hypothesis:","my hypothesis","فرضيه:","فرضية:","ممكن يكون السبب","i suspect that"))add(db,item,CognitiveTypes.DerivedKind.HYPOTHESIS,titleFor(r,"Hypothesis"),clip(text,700),"open",.82,54);

            // Project detection is safe only as a candidate; ProjectCandidateStore requires explicit confirmation.
            for(AnalysisResult.Entity e:r.entities){if(!"PROJECT".equalsIgnoreCase(n(e.kind))||e.confidence<.72)continue;String name=n(e.value);if(name.length()<3)continue;add(db,item,CognitiveTypes.DerivedKind.PROJECT_CANDIDATE,name,clip(text,700),"pending",Math.max(.72,e.confidence),60);}
        }catch(Throwable e){DiagnosticsLog.error(db,"IntentionalCognitiveBridge","after_analysis",e,"INTENTIONAL_BRIDGE",item.id,0,0,0,0,null);}
    }

    private static long add(VaultDb db,KnowledgeItem item,String kind,String title,String body,String state,double confidence,int importance){
        try{JSONObject meta=new JSONObject().put("memory_id",item.id).put("source",n(item.source)).put("policy",POLICY).put("intentional",intentional(item));String fp=Fingerprint.text("intentional|"+kind+"|"+item.id+"|"+LocalSemanticEmbedder.norm(body));long id=CognitiveStore.addDerived(db,kind,title,body,state,confidence,importance,fp,meta.toString());if(id>0){CognitiveStore.setDerivedRouting(db,id,n(item.source),0,0,kind);CognitiveStore.link(db,CognitiveTypes.ObjectType.MEMORY,item.id,CognitiveTypes.ObjectType.DERIVED,id,CognitiveTypes.Relation.SUPPORTS,1.0,meta.toString());CognitiveStore.link(db,CognitiveTypes.ObjectType.DERIVED,id,CognitiveTypes.ObjectType.MEMORY,item.id,CognitiveTypes.Relation.GROUNDED_BY,1.0,"");}return id;}catch(Exception e){return 0;}
    }
    private static void review(VaultDb db,KnowledgeItem item,String candidate,String title,String body,double confidence,int importance,String reason){long id=ReviewQueueStore.enqueue(db,candidate,title,body,confidence,importance,0,0,reason,n(item.source));if(id>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.MEMORY,item.id,CognitiveTypes.ObjectType.DERIVED,id,CognitiveTypes.Relation.SUPPORTS,1.0,"{\"policy\":\""+POLICY+"\"}");}

    private static boolean strongAction(String full,String action){String x=full+" "+action;return hasAny(x,"remind me","remind","need to","must ","todo","to do","follow up","فكرني","لازم","محتاج اعمل","محتاج أعمل","عايز اعمل","عاوز اعمل","عايز أعمل","عاوز أعمل","هتابع","أتابع","اتابع");}
    private static boolean intentional(KnowledgeItem k){String s=n(k.source);return"manual".equals(s)||"manual_recording".equals(s)||"quick_capture".equals(s);}
    private static boolean passive(KnowledgeItem k){String t=n(k.type),s=n(k.source);return"CONTACT".equals(t)||"NOTIFICATION".equals(t)||"contacts_sync".equals(s)||"calendar_sync".equals(s)||"screenshot-folder".equals(s)||"SCREENSHOT".equals(t)||"IMAGE".equals(t);}
    private static String bestText(KnowledgeItem k,AnalysisResult r){String x=n(r.extractedText);if(x.isEmpty())x=n(k.rawText);if(x.isEmpty())x=n(r.summary);return x;}
    private static String titleFor(AnalysisResult r,String fallback){String x=n(r.title);return x.isEmpty()?fallback:x;}
    private static boolean hasAny(String s,String...xs){for(String x:xs)if(s.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n)+"…";}private static String n(String s){return s==null?"":s.trim();}
}
