package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Materialized feed: Home reads this directly; reasoning updates it incrementally. */
public final class AttentionFeedStore {
    private AttentionFeedStore(){}

    public static final class Item {
        public final long id,entityId,threadId,createdAt,updatedAt;public final String entityType,semanticGroup,personKey,projectKey,section,title,subtitle,variant,statusDot,primaryAction,explanation;public final double rank,confidence;public final int sourceCount;
        Item(Cursor c){id=c.getLong(0);entityType=c.getString(1);entityId=c.getLong(2);semanticGroup=c.getString(3);personKey=c.getString(4);projectKey=c.getString(5);threadId=c.getLong(6);section=c.getString(7);title=c.getString(8);subtitle=c.getString(9);rank=c.getDouble(10);confidence=c.getDouble(11);variant=c.getString(12);statusDot=c.getString(13);primaryAction=c.getString(14);explanation=c.getString(15);sourceCount=c.getInt(16);createdAt=c.getLong(17);updatedAt=c.getLong(18);}
    }

    public static void upsertLoop(VaultDb db,OpenLoopStore.Loop loop,AttentionModels.Decision d){
        CortexAttentionSchema.ensure(db);if(loop==null||d==null)return;SQLiteDatabase sql=db.getWritableDatabase();if(!d.surfaceNow){sql.delete("attention_feed","entity_type='open_loop' AND entity_id=?",new String[]{String.valueOf(loop.id)});return;}
        long now=System.currentTimeMillis();String section=section(loop,d),variant=d.assessment.level==AttentionModels.Level.CRITICAL?"ACTIVE":"ACTIVE",dot=d.assessment.level==AttentionModels.Level.NONE?"":"NEEDS_ATTENTION",group=!loop.threadIdEqualsZero()?"thread:"+loop.threadId:"loop:"+loop.id;
        ContentValues v=new ContentValues();v.put("entity_type","open_loop");v.put("entity_id",loop.id);v.put("semantic_group",group);v.put("person_key",loop.personKey);v.put("project_key",loop.projectKey);v.put("thread_id",loop.threadId);v.put("section",section);v.put("title",friendlyTitle(loop));v.put("subtitle",subtitle(loop,now));v.put("rank",rankWithHistory(db,loop.id,d.rank));v.put("confidence",d.assessment.confidence);v.put("variant",variant);v.put("status_dot",dot);v.put("primary_action",d.assessment.suggestedAction);v.put("explanation",d.assessment.primaryReason);v.put("source_count",sourceCount(db,loop.id));v.put("updated_at",now);
        Cursor c=sql.query("attention_feed",new String[]{"id","created_at"},"entity_type='open_loop' AND entity_id=?",new String[]{String.valueOf(loop.id)},null,null,null,"1");long id=0,created=now;if(c.moveToFirst()){id=c.getLong(0);created=c.getLong(1);}c.close();v.put("created_at",created);if(id>0)sql.update("attention_feed",v,"id=?",new String[]{String.valueOf(id)});else sql.insert("attention_feed",null,v);recordSurface(db,loop.id,now);
    }

    public static void removeLoop(VaultDb db,long loopId){CortexAttentionSchema.ensure(db);db.getWritableDatabase().delete("attention_feed","entity_type='open_loop' AND entity_id=?",new String[]{String.valueOf(loopId)});}
    public static List<Item> loadNow(VaultDb db,int limit){CortexAttentionSchema.ensure(db);ArrayList<Item> out=new ArrayList<>();Cursor c=db.getReadableDatabase().query("attention_feed",cols(),"section='NOW'",null,null,null,"rank DESC,updated_at DESC",String.valueOf(Math.max(1,limit)));while(c.moveToNext())out.add(new Item(c));c.close();return out;}

    private static String section(OpenLoopStore.Loop l,AttentionModels.Decision d){if(OpenLoopStore.WAITING.equals(l.state))return"COMING_UP";return d.assessment.timeSensitive||d.assessment.actionability!=AttentionModels.Actionability.NONE?"NOW":"COMING_UP";}
    private static String friendlyTitle(OpenLoopStore.Loop l){if(!l.personKey.isEmpty())return l.personKey+" · "+l.subject;return l.subject;}
    private static String subtitle(OpenLoopStore.Loop l,long now){long m=Math.max(0,(now-l.updatedAt)/60_000L);String age=m<1?"just now":m<60?m+" min ago":(m/60)+" h ago";return l.userCommitted?"You committed • "+age:"Unresolved request • "+age;}
    private static int sourceCount(VaultDb db,long loopId){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT signal_id) FROM open_loop_evidence WHERE loop_id=?",new String[]{String.valueOf(loopId)});int n=c.moveToFirst()?c.getInt(0):1;c.close();return Math.max(1,n);}
    private static double rankWithHistory(VaultDb db,long loopId,double rank){Cursor c=db.getReadableDatabase().query("attention_history",new String[]{"surface_count","dismissed_count","snoozed_until"},"entity_type='open_loop' AND entity_id=?",new String[]{String.valueOf(loopId)},null,null,null,"1");if(!c.moveToFirst()){c.close();return rank;}int surfaces=c.getInt(0),dismissed=c.getInt(1);long snooze=c.getLong(2);c.close();if(snooze>System.currentTimeMillis())return -1000;double fatigue=surfaces>=8?25:surfaces>=5?15:surfaces>=3?7:0;return rank-fatigue-(dismissed*5);}
    private static void recordSurface(VaultDb db,long loopId,long now){SQLiteDatabase sql=db.getWritableDatabase();Cursor c=sql.query("attention_history",new String[]{"first_surfaced_at","surface_count"},"entity_type='open_loop' AND entity_id=?",new String[]{String.valueOf(loopId)},null,null,null,"1");long first=now;int count=0;if(c.moveToFirst()){first=c.getLong(0);count=c.getInt(1);}c.close();ContentValues v=new ContentValues();v.put("entity_type","open_loop");v.put("entity_id",loopId);v.put("first_surfaced_at",first>0?first:now);v.put("last_surfaced_at",now);v.put("surface_count",count+1);sql.insertWithOnConflict("attention_history",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private static String[] cols(){return new String[]{"id","entity_type","entity_id","semantic_group","person_key","project_key","thread_id","section","title","subtitle","rank","confidence","variant","status_dot","primary_action","explanation","source_count","created_at","updated_at"};}
}
