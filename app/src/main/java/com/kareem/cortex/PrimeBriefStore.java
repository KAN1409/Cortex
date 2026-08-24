package com.kareem.cortex;

import android.database.Cursor;
import java.util.*;

/** Read model for PRIME Brief. Uses unified derived intelligence, never legacy task guesses. */
public final class PrimeBriefStore {
    private PrimeBriefStore(){}

    public static final class Item {
        public final long id,threadId,signalId,updatedAt;
        public final String kind,title,body,source,state;
        public final double confidence;
        public final int importance;
        Item(long id,String kind,String title,String body,String source,String state,double confidence,int importance,long threadId,long signalId,long updatedAt){
            this.id=id;this.kind=n(kind);this.title=n(title);this.body=n(body);this.source=n(source);this.state=n(state);this.confidence=confidence;this.importance=importance;this.threadId=threadId;this.signalId=signalId;this.updatedAt=updatedAt;
        }
    }

    public static final class Snapshot {
        public final ArrayList<Item> actions,waiting,decisions,changes;
        public final ArrayList<ReviewQueueStore.Item> reviews;
        Snapshot(ArrayList<Item>a,ArrayList<Item>w,ArrayList<Item>d,ArrayList<Item>c,ArrayList<ReviewQueueStore.Item>r){actions=a;waiting=w;decisions=d;changes=c;reviews=r;}
        public boolean empty(){return actions.isEmpty()&&waiting.isEmpty()&&reviews.isEmpty()&&changes.isEmpty();}
    }

    public static Snapshot load(VaultDb db){
        CognitiveStore.ensure(db);
        ArrayList<Item> actions=query(db,"ACTION",12);
        ArrayList<Item> waiting=query(db,"WAITING",12);
        ArrayList<Item> decisions=query(db,"DECISION",8);
        ArrayList<Item> changes=recentChanges(db,10);
        ArrayList<ReviewQueueStore.Item> reviews=ReviewQueueStore.pending(db,12);
        return new Snapshot(actions,waiting,decisions,changes,reviews);
    }

    private static ArrayList<Item> query(VaultDb db,String kind,int limit){
        ArrayList<Item> out=new ArrayList<>();
        Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"id","kind","title","body","source_key","state","confidence","importance","thread_id","anchor_signal_id","updated_at"},"kind=? AND state='open'",new String[]{kind},null,null,"importance DESC, updated_at DESC",String.valueOf(limit*3));
        while(c.moveToNext())out.add(from(c));c.close();return dedupe(out,limit);
    }

    private static ArrayList<Item> recentChanges(VaultDb db,int limit){
        ArrayList<Item> out=new ArrayList<>();
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,source_key,state,confidence,importance,thread_id,anchor_signal_id,updated_at FROM derived_items WHERE state IN ('open','pending') AND kind IN ('DECISION','PROJECT_CANDIDATE','GOAL_SIGNAL') ORDER BY updated_at DESC,importance DESC LIMIT ?",new String[]{String.valueOf(limit*3)});
        while(c.moveToNext())out.add(from(c));c.close();return dedupe(out,limit);
    }

    private static ArrayList<Item> dedupe(ArrayList<Item> xs,int limit){
        LinkedHashMap<String,Item> map=new LinkedHashMap<>();
        for(Item x:xs){String basis=!x.body.isEmpty()?x.body:x.title;String key=x.kind+"|"+LocalSemanticEmbedder.norm(basis);if(key.length()>180)key=key.substring(0,180);Item old=map.get(key);if(old==null||x.importance>old.importance||x.updatedAt>old.updatedAt)map.put(key,x);}
        ArrayList<Item> out=new ArrayList<>(map.values());out.sort((a,b)->{int z=Integer.compare(b.importance,a.importance);return z!=0?z:Long.compare(b.updatedAt,a.updatedAt);});if(out.size()>limit)return new ArrayList<>(out.subList(0,limit));return out;
    }

    private static Item from(Cursor c){return new Item(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getDouble(6),c.getInt(7),c.getLong(8),c.getLong(9),c.getLong(10));}
    private static String n(String s){return s==null?"":s.trim();}
}
