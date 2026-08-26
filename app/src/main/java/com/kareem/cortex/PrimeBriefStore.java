package com.kareem.cortex;

import android.database.Cursor;
import java.util.*;

/**
 * Read model for Today. Derived relevance remains the source of candidates, while
 * AttentionEngine decides the current ordering. Relevance != attention.
 * Cached AI refinements may adjust the baseline; this class never performs network work.
 */
public final class PrimeBriefStore {
    private PrimeBriefStore(){}
    public static final class Item {
        public final long id,threadId,signalId,updatedAt;
        public final String kind,title,body,source,state;
        public final double confidence;
        public final int importance,attentionScore;
        public final AttentionEngine.Band attentionBand;
        public final String whyNow;
        Item(long id,String kind,String title,String body,String source,String state,double confidence,int importance,long threadId,long signalId,long updatedAt){
            this(id,kind,title,body,source,state,confidence,importance,threadId,signalId,updatedAt,null);
        }
        Item(long id,String kind,String title,String body,String source,String state,double confidence,int importance,long threadId,long signalId,long updatedAt,AttentionEngine.Decision a){
            this.id=id;this.kind=n(kind);this.title=n(title);this.body=n(body);this.source=n(source);this.state=n(state);this.confidence=confidence;this.importance=importance;this.threadId=threadId;this.signalId=signalId;this.updatedAt=updatedAt;
            AttentionEngine.Decision d=a==null?AttentionEngine.evaluate(this,System.currentTimeMillis()):a;
            this.attentionScore=d.score;this.attentionBand=d.band;this.whyNow=d.whyNow;
        }
    }
    public static final class Snapshot {public final ArrayList<KnowledgeItem> recent;public final ArrayList<Item> actions,waiting,decisions,changes,worthKnowing;public final ArrayList<ReviewQueueStore.Item> reviews;Snapshot(ArrayList<KnowledgeItem> recent,ArrayList<Item>a,ArrayList<Item>w,ArrayList<Item>d,ArrayList<Item>c,ArrayList<Item>k,ArrayList<ReviewQueueStore.Item>r){this.recent=recent;actions=a;waiting=w;decisions=d;changes=c;worthKnowing=k;reviews=r;}public boolean empty(){return recent.isEmpty()&&actions.isEmpty()&&waiting.isEmpty()&&decisions.isEmpty()&&reviews.isEmpty()&&changes.isEmpty()&&worthKnowing.isEmpty();}}
    public static Snapshot load(VaultDb db){CognitiveStore.ensure(db);AttentionAdjudicationStore.ensure(db);return new Snapshot(recentCaptures(db,10),query(db,"ACTION",12),query(db,"WAITING",12),query(db,"DECISION",8),recentChanges(db,10),worthKnowing(db,10),ReviewQueueStore.pending(db,12));}
    private static ArrayList<KnowledgeItem> recentCaptures(VaultDb db,int limit){ArrayList<KnowledgeItem> out=new ArrayList<>();for(KnowledgeItem k:db.lexicalSearch("",100)){if(!intentionalCapture(k))continue;out.add(k);if(out.size()>=limit)break;}return out;}
    private static boolean intentionalCapture(KnowledgeItem k){if(k==null)return false;String s=n(k.source);if("CONTACT".equals(k.type)||"NOTIFICATION".equals(k.type))return false;return"manual".equals(s)||"manual_recording".equals(s)||"android_share".equals(s)||"audio_import".equals(s)||"quick_capture".equals(s)||"screen_understanding".equals(s)||"screen_understand".equals(s);}
    private static ArrayList<Item> query(VaultDb db,String kind,int limit){ArrayList<Item> out=new ArrayList<>();Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"id","kind","title","body","source_key","state","confidence","importance","thread_id","anchor_signal_id","updated_at"},"kind=? AND state='open'",new String[]{kind},null,null,"updated_at DESC",String.valueOf(limit*5));while(c.moveToNext())out.add(from(c,db));c.close();return dedupeAndRank(out,limit,true);}
    private static ArrayList<Item> recentChanges(VaultDb db,int limit){ArrayList<Item> out=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,source_key,state,confidence,importance,thread_id,anchor_signal_id,updated_at FROM derived_items WHERE state IN ('open','pending') AND kind IN ('DECISION','PROJECT_CANDIDATE','GOAL_SIGNAL') ORDER BY updated_at DESC LIMIT ?",new String[]{String.valueOf(limit*4)});while(c.moveToNext())out.add(from(c,db));c.close();return dedupeAndRank(out,limit,false);}
    private static ArrayList<Item> worthKnowing(VaultDb db,int limit){ArrayList<Item> out=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,source_key,state,confidence,importance,thread_id,anchor_signal_id,updated_at FROM derived_items WHERE state='open' AND kind IN ('IDEA','OPPORTUNITY','INSIGHT','HYPOTHESIS') ORDER BY updated_at DESC LIMIT ?",new String[]{String.valueOf(limit*4)});while(c.moveToNext())out.add(from(c,db));c.close();return dedupeAndRank(out,limit,false);}
    private static ArrayList<Item> dedupeAndRank(ArrayList<Item> xs,int limit,boolean attentionSensitive){
        LinkedHashMap<String,Item> map=new LinkedHashMap<>();for(Item x:xs){if(attentionSensitive&&x.attentionBand==AttentionEngine.Band.QUIET)continue;String basis=!x.body.isEmpty()?x.body:x.title;String key=x.kind+"|"+LocalSemanticEmbedder.norm(basis);if(key.length()>180)key=key.substring(0,180);Item old=map.get(key);if(old==null||x.attentionScore>old.attentionScore||x.updatedAt>old.updatedAt)map.put(key,x);}
        ArrayList<Item> out=new ArrayList<>(map.values());
        out.sort((a,b)->{
            int z;
            if(attentionSensitive){z=Integer.compare(b.attentionScore,a.attentionScore);if(z!=0)return z;}
            z=Integer.compare(b.importance,a.importance);return z!=0?z:Long.compare(b.updatedAt,a.updatedAt);
        });
        if(out.size()>limit)return new ArrayList<>(out.subList(0,limit));return out;
    }
    private static Item from(Cursor c,VaultDb db){
        long id=c.getLong(0),thread=c.getLong(8),signal=c.getLong(9),updated=c.getLong(10);String kind=c.getString(1),title=c.getString(2),body=c.getString(3),source=c.getString(4),state=c.getString(5);double confidence=c.getDouble(6);int importance=c.getInt(7);
        Item raw=new Item(id,kind,title,body,source,state,confidence,importance,thread,signal,updated);String k=n(kind).toUpperCase(Locale.ROOT);if(!("ACTION".equals(k)||"WAITING".equals(k)||"DECISION".equals(k)))return raw;
        AttentionEngine.Decision baseline=AttentionEngine.evaluate(raw,System.currentTimeMillis());AttentionEngine.Decision merged=AttentionAdjudicationStore.applyFresh(db,raw,baseline);AttentionEngine.Decision learned=AttentionLearning.apply(db,raw,merged);return new Item(id,kind,title,body,source,state,confidence,importance,thread,signal,updated,learned);
    }
    private static String n(String s){return s==null?"":s.trim();}
}
