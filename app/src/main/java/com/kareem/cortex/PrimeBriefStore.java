package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Product read model for Now.
 *
 * Evidence and derived decisions are historical/provenance layers. Now reads the replaceable
 * BrainSituationStore projection so a changing conversation/topic appears as one current state,
 * never as an append-only notification ledger.
 */
public final class PrimeBriefStore {
    private PrimeBriefStore(){}

    public static final class Item {
        /** current derived id; retained so existing feedback/actions remain grounded. */
        public final long id;
        public final long situationId,threadId,signalId,updatedAt,lastChangedAt;
        public final int evidenceCount;
        public final String kind,attentionKind,title,body,source,state;
        public final double confidence;
        public final int importance,attentionScore;
        public final AttentionEngine.Band attentionBand;
        public final String whyNow;

        Item(long id,String kind,String title,String body,String source,String state,double confidence,
             int importance,long threadId,long signalId,long updatedAt){
            this(id,0,kind,title,body,source,state,confidence,importance,threadId,signalId,updatedAt,updatedAt,1,null);
        }

        Item(long id,String kind,String title,String body,String source,String state,double confidence,
             int importance,long threadId,long signalId,long updatedAt,AttentionEngine.Decision a){
            this(id,0,kind,title,body,source,state,confidence,importance,threadId,signalId,updatedAt,updatedAt,1,a);
        }

        Item(long id,long situationId,String kind,String title,String body,String source,String state,double confidence,
             int importance,long threadId,long signalId,long updatedAt,long lastChangedAt,int evidenceCount,
             AttentionEngine.Decision a){
            this.id=id;this.situationId=situationId;this.kind=n(kind);this.title=n(title);this.body=n(body);
            this.source=n(source);this.state=n(state);this.confidence=confidence;this.importance=importance;
            this.threadId=threadId;this.signalId=signalId;this.updatedAt=updatedAt;this.lastChangedAt=lastChangedAt;
            this.evidenceCount=Math.max(1,evidenceCount);this.attentionKind=CandidateConsolidator.effectiveKind(this);
            AttentionEngine.Decision d=a==null?AttentionEngine.evaluate(this,System.currentTimeMillis()):a;
            this.attentionScore=d.score;this.attentionBand=d.band;this.whyNow=d.whyNow;
        }
    }

    public static final class Snapshot {
        public final ArrayList<KnowledgeItem> recent;
        public final ArrayList<Item> actions,waiting,decisions,changes,worthKnowing;
        public final ArrayList<ReviewQueueStore.Item> reviews;
        public final int currentSituationCount;
        public final long lastSituationChangeAt;

        Snapshot(ArrayList<KnowledgeItem> recent,ArrayList<Item>a,ArrayList<Item>w,ArrayList<Item>d,
                 ArrayList<Item>c,ArrayList<Item>k,ArrayList<ReviewQueueStore.Item>r,int count,long changedAt){
            this.recent=recent;actions=a;waiting=w;decisions=d;changes=c;worthKnowing=k;reviews=r;
            currentSituationCount=count;lastSituationChangeAt=changedAt;
        }
        public boolean attentionEmpty(){return actions.isEmpty()&&waiting.isEmpty()&&decisions.isEmpty()&&reviews.isEmpty()&&changes.isEmpty()&&worthKnowing.isEmpty();}
        public boolean empty(){return recent.isEmpty()&&attentionEmpty();}
        public int needsYouCount(){return actions.size()+decisions.size();}
    }

    public static Snapshot load(VaultDb db){
        CognitiveStore.ensure(db);AttentionAdjudicationStore.ensure(db);
        try{BrainSituationStore.reconcile(db);}catch(Throwable e){DiagnosticsLog.error(db,"PrimeBriefStore","situation_projection",e,"SITUATION_PROJECTION",0,0,0,0,0,null);}

        ArrayList<BrainSituationStore.Item> projected=safeProjection(db);
        ArrayList<Item> all=new ArrayList<>();
        for(BrainSituationStore.Item s:projected){
            try{
                Item x=fromSituation(db,s);
                if(x==null||hardSurfaceNoise(x))continue;
                all.add(x);
            }catch(Throwable e){DiagnosticsLog.error(db,"PrimeBriefStore","projection_row",e,"PRIME_ROW",0,s.threadId,s.signalId,0,s.modelRunId,null);}
        }

        ArrayList<Item> actions=pickAttention(all,12,"ACTION","REMINDER");
        ArrayList<Item> waiting=pickAttention(all,12,"WAITING");
        ArrayList<Item> decisions=pickAttention(all,8,"DECISION");
        ArrayList<Item> changes=pickRecent(all,10,"ALERT","CHANGE","EVENT");
        ArrayList<Item> worth=pickWorth(all,10);

        ArrayList<Item> seen=new ArrayList<>();
        actions=uniqueAgainst(actions,seen,12);waiting=uniqueAgainst(waiting,seen,12);
        decisions=uniqueAgainst(decisions,seen,8);changes=uniqueAgainst(changes,seen,10);
        worth=uniqueAgainst(worth,seen,10);

        ArrayList<KnowledgeItem> recent=safeRecent(db);
        ArrayList<ReviewQueueStore.Item> reviews=safeReviews(db);
        return new Snapshot(recent,actions,waiting,decisions,changes,worth,reviews,
                projected.size(),BrainSituationStore.lastChangedAt(db));
    }

    private static Item fromSituation(VaultDb db,BrainSituationStore.Item s){
        if(s==null||s.currentDerivedId<=0)return null;
        long freshness=s.lastChangedAt>0?s.lastChangedAt:s.updatedAt;
        Item raw=new Item(s.currentDerivedId,s.id,s.kind,s.title,s.body,s.sourceKey,"open",s.confidence,
                s.importance,s.threadId,s.signalId,freshness,freshness,s.evidenceCount,null);
        String k=n(s.kind).toUpperCase();
        if(!("ACTION".equals(k)||"WAITING".equals(k)||"DECISION".equals(k)||"REMINDER".equals(k)))return raw;
        AttentionEngine.Decision baseline=AttentionEngine.evaluate(raw,System.currentTimeMillis());
        AttentionEngine.Decision merged=AttentionAdjudicationStore.applyFresh(db,raw,baseline);
        AttentionEngine.Decision learned=AttentionLearning.apply(db,raw,merged);
        return new Item(s.currentDerivedId,s.id,s.kind,s.title,s.body,s.sourceKey,"open",s.confidence,
                s.importance,s.threadId,s.signalId,freshness,freshness,s.evidenceCount,learned);
    }

    private static ArrayList<Item> pickAttention(ArrayList<Item> all,int limit,String... kinds){
        Set<String>want=new HashSet<>(Arrays.asList(kinds));ArrayList<Item> out=new ArrayList<>();
        for(Item x:all)if(want.contains(x.attentionKind)&&x.attentionBand!=AttentionEngine.Band.QUIET)out.add(x);
        out.sort(PrimeBriefStore::compareAttention);return bounded(out,limit);
    }

    private static ArrayList<Item> pickRecent(ArrayList<Item> all,int limit,String... kinds){
        Set<String>want=new HashSet<>(Arrays.asList(kinds));ArrayList<Item> out=new ArrayList<>();long cutoff=System.currentTimeMillis()-48L*60L*60L*1000L;
        for(Item x:all)if(want.contains(x.attentionKind)&&x.lastChangedAt>=cutoff)out.add(x);
        out.sort((a,b)->Long.compare(b.lastChangedAt,a.lastChangedAt));return bounded(out,limit);
    }

    private static ArrayList<Item> pickWorth(ArrayList<Item> all,int limit){
        ArrayList<Item> out=new ArrayList<>();long cutoff=System.currentTimeMillis()-72L*60L*60L*1000L;
        for(Item x:all){
            String k=n(x.attentionKind).toUpperCase();
            boolean insight="IDEA".equals(k)||"OPPORTUNITY".equals(k)||"INSIGHT".equals(k);
            boolean exceptionalContent="CONTENT".equals(k)&&x.importance>=70;
            if((insight||exceptionalContent)&&x.lastChangedAt>=cutoff)out.add(x);
        }
        out.sort((a,b)->{int z=Integer.compare(b.importance,a.importance);return z!=0?z:Long.compare(b.lastChangedAt,a.lastChangedAt);});
        return bounded(out,limit);
    }

    private static ArrayList<KnowledgeItem> recentCaptures(VaultDb db,int limit){
        ArrayList<KnowledgeItem> out=new ArrayList<>();for(KnowledgeItem k:db.lexicalSearch("",100)){if(!intentionalCapture(k))continue;out.add(k);if(out.size()>=limit)break;}return out;
    }
    private static boolean intentionalCapture(KnowledgeItem k){if(k==null)return false;String s=n(k.source);if("CONTACT".equals(k.type)||"NOTIFICATION".equals(k.type))return false;return"manual".equals(s)||"manual_recording".equals(s)||"android_share".equals(s)||"audio_import".equals(s)||"quick_capture".equals(s)||"screen_understanding".equals(s)||"screen_understand".equals(s);}

    static boolean hardSurfaceNoise(Item x){
        if(x==null)return true;String t=plain(x.title+" "+x.body);if(mechanicalProgress(t))return true;
        if(!"ACTION".equalsIgnoreCase(n(x.kind)))return false;
        boolean info=t.contains("important info about")||t.contains("important information about")||t.contains("google account")||t.contains("account notice")||t.contains("account update")||t.contains("weekly digest")||t.contains("newsletter")||t.contains("privacy policy")||t.contains("terms of service");
        boolean obligation=t.contains("action required")||t.contains("please confirm")||t.contains("please review")||t.contains("please send")||t.contains("can you confirm")||t.contains("can you review")||t.contains("can you send")||t.contains("verify your")||t.contains("complete your")||t.contains("submit your")||t.contains("required to")||t.contains("you must")||t.contains("مطلوب منك")||t.contains("محتاج منك")||t.contains("لازم تعمل")||t.contains("ممكن تبعت")||t.contains("ابعتلي");
        return info&&!obligation;
    }
    private static boolean mechanicalProgress(String t){return(t.contains("deleting item")||t.contains("deleting ")||t.contains("uploading ")||t.contains("downloading ")||t.contains("syncing ")||t.contains("processing "))&&(t.matches(".*\\b\\d+\\s+of\\s+\\d+\\b.*")||t.contains("%")||t.contains("progress"));}

    static boolean sameSurfaceEvent(Item a,Item b){
        if(a==null||b==null)return false;if(a.situationId>0&&a.situationId==b.situationId)return true;
        if(a.threadId>0&&a.threadId==b.threadId&&actionableSurface(a.attentionKind)&&actionableSurface(b.attentionKind))return true;
        if(CandidateConsolidator.sameEvent(a,b))return true;
        String ab=surfaceBody(a),bb=surfaceBody(b);return !ab.isEmpty()&&ab.equals(bb);
    }
    private static boolean actionableSurface(String k){return"ACTION".equals(k)||"WAITING".equals(k)||"DECISION".equals(k)||"REMINDER".equals(k);}
    private static String surfaceBody(Item x){return plain(n(x.body).replaceAll("\\[(?:\\d+|[^\\]]{0,12})\\]"," "));}
    private static String plain(String s){return MasterRelevanceFilter.ruleNorm(n(s)).replaceAll("\\s+"," ").trim();}

    private static ArrayList<Item> uniqueAgainst(ArrayList<Item> xs,ArrayList<Item> seen,int limit){ArrayList<Item> out=new ArrayList<>();outer:for(Item x:xs){for(Item old:seen)if(sameSurfaceEvent(x,old))continue outer;seen.add(x);out.add(x);if(out.size()>=limit)break;}return out;}
    private static int compareAttention(Item a,Item b){int z=Integer.compare(b.attentionScore,a.attentionScore);if(z!=0)return z;z=Integer.compare(b.importance,a.importance);return z!=0?z:Long.compare(b.lastChangedAt,a.lastChangedAt);}
    private static ArrayList<BrainSituationStore.Item> safeProjection(VaultDb db){try{return BrainSituationStore.current(db,160);}catch(Throwable e){DiagnosticsLog.error(db,"PrimeBriefStore","projection_read",e,"PRIME_SECTION",0,0,0,0,0,null);return new ArrayList<>();}}
    private static ArrayList<KnowledgeItem> safeRecent(VaultDb db){try{return recentCaptures(db,10);}catch(Throwable e){DiagnosticsLog.error(db,"PrimeBriefStore","recent_captures",e,"PRIME_SECTION",0,0,0,0,0,null);return new ArrayList<>();}}
    private static ArrayList<ReviewQueueStore.Item> safeReviews(VaultDb db){try{return ReviewQueueHygiene.pending(db,6);}catch(Throwable e){DiagnosticsLog.error(db,"PrimeBriefStore","review_queue",e,"PRIME_SECTION",0,0,0,0,0,null);return new ArrayList<>();}}
    private static<T> ArrayList<T> bounded(ArrayList<T> xs,int limit){if(xs==null||xs.isEmpty())return new ArrayList<>();return new ArrayList<>(xs.subList(0,Math.min(Math.max(0,limit),xs.size())));}
    private static String n(String s){return s==null?"":s.trim();}
}
