package com.kareem.cortex;

import java.util.*;

/** Authoritative read model and structured Ask router for the clean-slate Truth layer. */
public final class TruthNowEngine {
    public static final class Snapshot {
        public final ArrayList<TruthObjectStore.Item> actions,waiting,decisions,important;
        Snapshot(ArrayList<TruthObjectStore.Item>a,ArrayList<TruthObjectStore.Item>w,ArrayList<TruthObjectStore.Item>d,ArrayList<TruthObjectStore.Item>i){actions=a;waiting=w;decisions=d;important=i;}
        public boolean empty(){return actions.isEmpty()&&waiting.isEmpty()&&decisions.isEmpty()&&important.isEmpty();}
    }

    private TruthNowEngine(){}

    public static Snapshot snapshot(VaultDb db){
        EventEngine.backfillRecent(db,180);
        return new Snapshot(
            TruthObjectStore.active(db,TruthObjectStore.ACTION,20),
            TruthObjectStore.active(db,TruthObjectStore.WAITING,20),
            recent(TruthObjectStore.active(db,TruthObjectStore.DECISION,40),30L*24L*60L*60L*1000L,16),
            recent(TruthObjectStore.active(db,TruthObjectStore.IMPORTANT,40),14L*24L*60L*60L*1000L,20));
    }

    private static ArrayList<TruthObjectStore.Item> recent(List<TruthObjectStore.Item> xs,long ageMs,int limit){
        ArrayList<TruthObjectStore.Item> out=new ArrayList<>();long cutoff=System.currentTimeMillis()-ageMs;for(TruthObjectStore.Item x:xs){if(x.lastSeenAt<cutoff)continue;out.add(x);if(out.size()>=limit)break;}return out;
    }

    public static GroundedAnswer tryAnswer(VaultDb db,String question){
        if(db==null||empty(question))return null;String q=LocalSemanticEmbedder.norm(question);
        boolean attention=isAttention(q),waiting=isWaiting(q),decisions=isDecisions(q),important=isImportant(q),overview=isOverview(q);
        if(!attention&&!waiting&&!decisions&&!important&&!overview)return null;
        Snapshot s=snapshot(db);ArrayList<SemanticHit> sources=new ArrayList<>(),loops=new ArrayList<>(),decisionLines=new ArrayList<>();
        StringBuilder out=new StringBuilder();

        if(attention||overview){
            append(out,"Needs your attention",s.actions,6);
            append(out,"Waiting for",s.waiting,6);
            if(overview){append(out,"Decisions you made",s.decisions,5);append(out,"Important",s.important,6);}
        }else if(waiting)append(out,"You’re waiting for",s.waiting,10);
        else if(decisions)append(out,"Recent decisions you made",s.decisions,10);
        else if(important)append(out,"Important",s.important,10);

        ArrayList<TruthObjectStore.Item> used=new ArrayList<>();
        if(attention){used.addAll(s.actions);used.addAll(s.waiting);used.addAll(s.important);}
        else if(overview){used.addAll(s.actions);used.addAll(s.waiting);used.addAll(s.decisions);used.addAll(s.important);}
        else if(waiting)used.addAll(s.waiting);else if(decisions)used.addAll(s.decisions);else used.addAll(s.important);
        addSources(db,used,sources);
        for(TruthObjectStore.Item x:s.actions)loops.add(x.text());
        for(TruthObjectStore.Item x:s.waiting)loops.add(x.text());
        for(TruthObjectStore.Item x:s.decisions)decisionLines.add(x.text());

        String answer=out.toString().trim();
        if(answer.isEmpty()){
            if(waiting)answer="Cortex doesn’t currently have any grounded Waiting items.";
            else if(decisions)answer="Cortex doesn’t currently have any confirmed decisions you made.";
            else if(important)answer="Cortex doesn’t currently have any grounded important events to surface.";
            else answer="Nothing grounded currently needs your attention in Cortex.";
        }
        return new GroundedAnswer(question,answer,.99,sources,loops,decisionLines);
    }

    private static void append(StringBuilder out,String heading,List<TruthObjectStore.Item> xs,int limit){
        if(xs==null||xs.isEmpty())return;if(out.length()>0)out.append("\n\n");out.append(heading).append(":\n");
        int i=0;for(TruthObjectStore.Item x:xs){String t=clip(x.text(),220);if(t.isEmpty())continue;out.append("• ").append(t).append('\n');if(++i>=limit)break;}
    }

    private static void addSources(VaultDb db,List<TruthObjectStore.Item> xs,ArrayList<SemanticHit> out){
        HashSet<Long> seen=new HashSet<>();for(TruthObjectStore.Item x:xs){
            long id=x.memoryId;if(id<=0&&x.signalId>0)try{id=RawSignalStore.promotedItemId(db,x.signalId);}catch(Throwable ignored){}
            if(id<=0||!seen.add(id))continue;try{KnowledgeItem k=db.getById(id);if(k!=null)out.add(new SemanticHit(k,.99,clip(x.text(),260)));}catch(Throwable ignored){}
        }
    }

    private static boolean isAttention(String q){return has(q,"what needs my attention","what still needs my attention","what needs me","what do i need to do","what should i do now","needs attention","محتاج انتباهي","محتاج مني","محتاج اعمل ايه","محتاج أعمل ايه","ايه اللي محتاجني","إيه اللي محتاجني","ايه اللي محتاج اهتمامي","إيه اللي محتاج اهتمامي");}
    private static boolean isWaiting(String q){return has(q,"what am i waiting for","what am i waiting on","what are we waiting for","waiting on","مستني ايه","مستنى ايه","منتظر ايه","في انتظار ايه");}
    private static boolean isDecisions(String q){return has(q,"what did i decide recently","recent decisions","what have i decided","decisions i made","قررت ايه مؤخرا","قررت ايه قريب","ايه القرارات الاخيره","إيه القرارات الأخيرة","القرارات اللي خدتها");}
    private static boolean isImportant(String q){return has(q,"what is important","what matters right now","important events","anything important","ايه المهم","إيه المهم","في حاجة مهمة","في حاجه مهمه","ايه اللي مهم دلوقتي","إيه اللي مهم دلوقتي");}
    private static boolean isOverview(String q){return has(q,"what actually needs my attention now","separate confirmed actions","what should i know or do now","truth now","now overview","ملخص دلوقتي","ايه اللي لازم اعرفه او اعمله دلوقتي","إيه اللي لازم أعرفه أو أعمله دلوقتي");}
    private static boolean has(String t,String...xs){for(String x:xs)if(t.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static String clip(String s,int max){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=max?x:x.substring(0,max)+"…";}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
}
