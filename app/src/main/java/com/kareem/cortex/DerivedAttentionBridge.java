package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Bridges existing grounded derived intelligence into the unified feed without reclassifying it as an open loop. */
public final class DerivedAttentionBridge {
    private DerivedAttentionBridge(){}

    public static void refresh(VaultDb db){if(db==null)return;CortexAttentionSchema.ensure(db);SQLiteDatabase sql=db.getWritableDatabase();sql.delete("attention_feed","entity_type='derived'",null);Cursor c=sql.rawQuery("SELECT id,kind,title,body,source_key,confidence,importance,thread_id,updated_at FROM derived_items WHERE state='open' AND kind IN ('INSIGHT','OPPORTUNITY','PROJECT_CANDIDATE','GOAL_SIGNAL') ORDER BY importance DESC,updated_at DESC LIMIT 60",null);long now=System.currentTimeMillis();while(c.moveToNext()){long id=c.getLong(0),threadId=c.getLong(7),updated=c.getLong(8);String kind=n(c.getString(1)),title=n(c.getString(2)),body=n(c.getString(3)),source=n(c.getString(4));double confidence=c.getDouble(5);int importance=c.getInt(6);boolean insight="INSIGHT".equals(kind)||"OPPORTUNITY".equals(kind);String section=insight?"INSIGHTS":"PROJECTS",variant=insight?"INSIGHT":"PROJECT";double recency=recency(updated,now),rank=Math.max(0,Math.min(120,importance+recency*8));String basis=!body.isEmpty()?body:title;ContentValues v=new ContentValues();v.put("entity_type","derived");v.put("entity_id",id);v.put("semantic_group",DerivedSemanticIdentity.key(kind,basis));v.put("person_key","");v.put("project_key",insight?"":title);v.put("thread_id",threadId);v.put("section",section);v.put("title",title.isEmpty()?friendly(kind):title);v.put("subtitle",friendly(kind)+" • "+sourceLabel(source));v.put("rank",rank);v.put("confidence",confidence);v.put("variant",variant);v.put("status_dot","CONNECTED");v.put("primary_action",insight?"Review insight":"Open project context");v.put("explanation",body);v.put("source_count",sourceCount(db,id));v.put("created_at",updated>0?updated:now);v.put("updated_at",updated>0?updated:now);sql.insertWithOnConflict("attention_feed",null,v,SQLiteDatabase.CONFLICT_REPLACE);}c.close();}
    private static int sourceCount(VaultDb db,long id){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM source_links WHERE to_type='derived' AND to_id=?",new String[]{String.valueOf(id)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return Math.max(1,n);}
    private static double recency(long updated,long now){long age=Math.max(0,now-updated);if(age<=60*60_000L)return 1;if(age<=24*60*60_000L)return .8;if(age<=3*24*60*60_000L)return .55;if(age<=7*24*60*60_000L)return .3;return .1;}
    private static String friendly(String kind){if("PROJECT_CANDIDATE".equals(kind))return"Project";if("GOAL_SIGNAL".equals(kind))return"Goal";if("OPPORTUNITY".equals(kind))return"Opportunity";return"Cortex insight";}
    private static String sourceLabel(String s){s=n(s);if(s.isEmpty())return"Cortex";int p=s.lastIndexOf('.');if(p>=0&&p<s.length()-1)s=s.substring(p+1);return s.replace('_',' ');}
    private static String n(String s){return s==null?"":s.trim();}
}
