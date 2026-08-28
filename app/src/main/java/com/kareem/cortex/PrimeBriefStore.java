package com.kareem.cortex;

import android.database.Cursor;
import java.util.*;

/**
 * Read model for Today. Derived relevance remains authoritative; candidates are consolidated
 * into canonical attention events before presentation so duplicate evidence cannot consume
 * multiple attention slots. Stored kind and presentation-time attentionKind are both retained.
 */
public final class PrimeBriefStore {
    private PrimeBriefStore(){}
    public static final class Item {
        public final long id,threadId,signalId,updatedAt;
        public final String kind,attentionKind,title,body,source,state;
        public final double confidence;
        public final int importance,attentionScore;
        public final AttentionEngine.Band attentionBand;
        public final String whyNow;
        Item(long id,String kind,String title,String body,String source,String state,double confidence,int importance,long threadId,long signalId,long updatedAt){this(id,kind,title,body,source,state,confidence,importance,threadId,signalId,updatedAt,null);}
        Item(long id,String kind,String title,String body,String source,String state,double confidence,int importance,long threadId,long signalId,long updatedAt,AttentionEngine.Decision a){
            this.id=id;this.kind=n(kind);this.title=n(title);this.body=n(body);this.source=n(source);this.state=n(state);this.confidence=confidence;this.importance=importance;this.threadId=threadId;this.signalId=signalId;this.updatedAt=updatedAt;this.attentionKind=CandidateConsolidator.effectiveKind(this);
            AttentionEngine.Decision d=a==null?AttentionEngine.evaluate(this,System.currentTimeMillis()):a;this.attentionScore=d.score;this.attentionBand=d.band;this.whyNow=d.whyNow;
        }
    }
    public static final class Snapshot {
        public final ArrayList<KnowledgeItem> recent;public final ArrayList<Item> actions,waiting,decisions,changes,worthKnowing;public final ArrayList<ReviewQueueStore.Item> reviews;
        Snapshot(ArrayList<KnowledgeItem> recent,ArrayList<Item>a,ArrayList<Item>w,ArrayList<Item>d,ArrayList<Item>c,ArrayList<Item>k,ArrayList<ReviewQueueStore.Item>r){this.recent=recent;actions=a;waiting=w;decisions=d;changes=c;worthKnowing=k;reviews=r;}
        /** Whether the attention surface itself is clear; recent passive context must not defeat this state. */
        public boolean attentionEmpty(){return actions.isEmpty()&&waiting.isEmpty()&&decisions.isEmpty()&&reviews.isEmpty()&&changes.isEmpty()&&worthKnowing.isEmpty();}
        public boolean empty(){return recent.isEmpty()&&attentionEmpty();}
    }
    public static Snapshot load(VaultDb db){
        CognitiveStore.ensure(db);AttentionAdjudicationStore.ensure(db);ArrayList<Item> canonical=attentionCandidates(db,80);
        ArrayList<Item> actions=pickAny(canonical,12,"ACTION","REMINDER"),waiting=pickAny(canonical,12,"WAITING"),decisions=pickAny(canonical,8,"DECISION"),attentionChanges=pickAny(canonical,12,"ALERT","CHANGE");
        ArrayList<Item> changes=recentChanges(db,12);changes.addAll(attentionChanges);changes=consolidate(changes,16,false);
        ArrayList<Item> worth=worthKnowing(db,12);

        // One global presentation budget: once a canonical event has appeared in a higher
        // attention section it cannot consume another card in a lower section.
        ArrayList<Item> seen=new ArrayList<>();
        actions=uniqueAgainst(actions,seen,12);
        waiting=uniqueAgainst(waiting,seen,12);
        decisions=uniqueAgainst(decisions,seen,8);
        changes=uniqueAgainst(changes,seen,10);
        worth=uniqueAgainst(worth,seen,10);

        return new Snapshot(recentCaptures(db,10),actions,waiting,decisions,changes,worth,ReviewQueueStore.pending(db,12));
    }
    private static ArrayList<KnowledgeItem> recentCaptures(VaultDb db,int limit){ArrayList<KnowledgeItem> out=new ArrayList<>();for(KnowledgeItem k:db.lexicalSearch("",100)){if(!intentionalCapture(k))continue;out.add(k);if(out.size()>=limit)break;}return out;}
    private static boolean intentionalCapture(KnowledgeItem k){if(k==null)return false;String s=n(k.source);if("CONTACT".equals(k.type)||"NOTIFICATION".equals(k.type))return false;return"manual".equals(s)||"manual_recording".equals(s)||"android_share".equals(s)||"audio_import".equals(s)||"quick_capture".equals(s)||"screen_understanding".equals(s)||"screen_understand".equals(s);}
    private static ArrayList<Item> attentionCandidates(VaultDb db,int limit){ArrayList<Item> all=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,source_key,state,confidence,importance,thread_id,anchor_signal_id,updated_at FROM derived_items WHERE state='open' AND kind IN ('ACTION','WAITING','DECISION','ALERT','CHANGE') ORDER BY updated_at DESC LIMIT 300",null);while(c.moveToNext()){Item x=from(c,db);if(x.attentionBand!=AttentionEngine.Band.QUIET)all.add(x);}c.close();all.sort(PrimeBriefStore::compareAttention);return consolidate(all,limit,true);}
    private static ArrayList<Item> pickAny(ArrayList<Item> xs,int limit,String... kinds){HashSet<String>want=new HashSet<>(Arrays.asList(kinds));ArrayList<Item> out=new ArrayList<>();for(Item x:xs)if(want.contains(x.attentionKind)){out.add(x);if(out.size()>=limit)break;}return out;}
    private static ArrayList<Item> recentChanges(VaultDb db,int limit){ArrayList<Item> out=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,source_key,state,confidence,importance,thread_id,anchor_signal_id,updated_at FROM derived_items WHERE state IN ('open','pending') AND kind IN ('DECISION','PROJECT_CANDIDATE','GOAL_SIGNAL','ALERT','CHANGE') ORDER BY updated_at DESC LIMIT ?",new String[]{String.valueOf(limit*6)});while(c.moveToNext()){Item x=from(c,db);if("DECISION".equals(x.kind)&&!"CHANGE".equals(x.attentionKind))continue;out.add(x);}c.close();return consolidate(out,limit,false);}
    private static ArrayList<Item> worthKnowing(VaultDb db,int limit){ArrayList<Item> out=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,source_key,state,confidence,importance,thread_id,anchor_signal_id,updated_at FROM derived_items WHERE state='open' AND kind IN ('IDEA','OPPORTUNITY','INSIGHT','HYPOTHESIS') ORDER BY updated_at DESC LIMIT ?",new String[]{String.valueOf(limit*5)});while(c.moveToNext())out.add(from(c,db));c.close();return consolidate(out,limit,false);}
    private static ArrayList<Item> consolidate(ArrayList<Item> xs,int limit,boolean attentionSensitive){ArrayList<Item> ranked=new ArrayList<>(xs);ranked.sort(attentionSensitive?PrimeBriefStore::compareAttention:(a,b)->{int z=Integer.compare(b.importance,a.importance);return z!=0?z:Long.compare(b.updatedAt,a.updatedAt);});ArrayList<Item> out=CandidateConsolidator.consolidate(ranked,new CandidateConsolidator.ItemAccessor<Item>(){public Item item(Item x){return x;}public int priority(Item x){return attentionSensitive?x.attentionScore:x.importance;}});out.sort(attentionSensitive?PrimeBriefStore::compareAttention:(a,b)->{int z=Integer.compare(b.importance,a.importance);return z!=0?z:Long.compare(b.updatedAt,a.updatedAt);});if(out.size()>limit)return new ArrayList<>(out.subList(0,limit));return out;}
    private static ArrayList<Item> uniqueAgainst(ArrayList<Item> xs,ArrayList<Item> seen,int limit){ArrayList<Item> out=new ArrayList<>();outer:for(Item x:xs){for(Item old:seen)if(CandidateConsolidator.sameEvent(x,old))continue outer;seen.add(x);out.add(x);if(out.size()>=limit)break;}return out;}
    private static int compareAttention(Item a,Item b){int z=Integer.compare(b.attentionScore,a.attentionScore);if(z!=0)return z;z=Integer.compare(b.importance,a.importance);return z!=0?z:Long.compare(b.updatedAt,a.updatedAt);}
    private static Item from(Cursor c,VaultDb db){long id=c.getLong(0),thread=c.getLong(8),signal=c.getLong(9),updated=c.getLong(10);String kind=c.getString(1),title=c.getString(2),body=c.getString(3),source=c.getString(4),state=c.getString(5);double confidence=c.getDouble(6);int importance=c.getInt(7);Item raw=new Item(id,kind,title,body,source,state,confidence,importance,thread,signal,updated);String k=n(kind).toUpperCase(Locale.ROOT);if(!("ACTION".equals(k)||"WAITING".equals(k)||"DECISION".equals(k)))return raw;AttentionEngine.Decision baseline=AttentionEngine.evaluate(raw,System.currentTimeMillis()),merged=AttentionAdjudicationStore.applyFresh(db,raw,baseline),learned=AttentionLearning.apply(db,raw,merged);return new Item(id,kind,title,body,source,state,confidence,importance,thread,signal,updated,learned);}
    private static String n(String s){return s==null?"":s.trim();}
}
