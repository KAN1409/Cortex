package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

public final class AudioStore {
    private AudioStore(){}
    private static void ensure(VaultDb db){SQLiteDatabase s=db.getWritableDatabase();s.execSQL("CREATE TABLE IF NOT EXISTS transcript_segments(id INTEGER PRIMARY KEY AUTOINCREMENT,item_id INTEGER NOT NULL,start_ms INTEGER NOT NULL,end_ms INTEGER NOT NULL,text TEXT NOT NULL,confidence REAL DEFAULT 0)");s.execSQL("CREATE INDEX IF NOT EXISTS idx_transcript_item ON transcript_segments(item_id)");s.execSQL("CREATE TABLE IF NOT EXISTS audio_info(item_id INTEGER PRIMARY KEY,language TEXT,duration_ms INTEGER,engine TEXT)");}
    public static void save(VaultDb db,long itemId,AnalysisResult r){ensure(db);SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();try{s.delete("transcript_segments","item_id=?",new String[]{String.valueOf(itemId)});for(AnalysisResult.TranscriptSegment x:r.transcriptSegments){ContentValues v=new ContentValues();v.put("item_id",itemId);v.put("start_ms",x.startMs);v.put("end_ms",x.endMs);v.put("text",x.text);v.put("confidence",x.confidence);s.insert("transcript_segments",null,v);}ContentValues a=new ContentValues();a.put("item_id",itemId);a.put("language",r.audioLanguage);a.put("duration_ms",r.audioDurationMs);a.put("engine",r.engine);s.insertWithOnConflict("audio_info",null,a,SQLiteDatabase.CONFLICT_REPLACE);s.setTransactionSuccessful();}finally{s.endTransaction();}}
    public static ArrayList<String> segments(VaultDb db,long itemId){ensure(db);ArrayList<String> out=new ArrayList<>();Cursor c=db.getReadableDatabase().query("transcript_segments",new String[]{"start_ms","text"},"item_id=?",new String[]{String.valueOf(itemId)},null,null,"start_ms ASC");while(c.moveToNext())out.add(fmt(c.getLong(0))+"  "+c.getString(1));c.close();return out;}
    public static String info(VaultDb db,long itemId){ensure(db);Cursor c=db.getReadableDatabase().query("audio_info",new String[]{"language","duration_ms","engine"},"item_id=?",new String[]{String.valueOf(itemId)},null,null,null,"1");String x="";if(c.moveToFirst()){String lang=c.getString(0);long ms=c.getLong(1);String engine=c.getString(2);x="Duration: "+fmt(ms)+(lang==null||lang.isEmpty()?"":"\nLanguage: "+lang)+(engine==null||engine.isEmpty()?"":"\nEngine: "+engine);}c.close();return x;}
    private static String fmt(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.US,"%02d:%02d",sec/60,sec%60);}
}
