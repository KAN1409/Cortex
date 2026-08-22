package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public final class AudioStore {
    private AudioStore(){}
    private static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS transcript_segments(id INTEGER PRIMARY KEY AUTOINCREMENT,item_id INTEGER NOT NULL,start_ms INTEGER NOT NULL,end_ms INTEGER NOT NULL,text TEXT NOT NULL,confidence REAL DEFAULT 0)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_transcript_item ON transcript_segments(item_id)");
        s.execSQL("CREATE TABLE IF NOT EXISTS audio_info(item_id INTEGER PRIMARY KEY,language TEXT,duration_ms INTEGER,engine TEXT)");
        addColumn(s,"audio_info","processed_duration_ms","INTEGER DEFAULT 0");
        addColumn(s,"audio_info","coverage","REAL DEFAULT 0");
        addColumn(s,"audio_info","raw_transcript","TEXT DEFAULT ''");
        addColumn(s,"audio_info","provider_merged_transcript","TEXT DEFAULT ''");
        addColumn(s,"audio_info","raw_provider_response","TEXT DEFAULT ''");
    }
    private static void addColumn(SQLiteDatabase s,String table,String column,String type){try{s.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+type);}catch(Exception ignored){}}

    public static void save(VaultDb db,long itemId,AnalysisResult r){
        ensure(db);SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();try{
            s.delete("transcript_segments","item_id=?",new String[]{String.valueOf(itemId)});
            for(AnalysisResult.TranscriptSegment x:r.transcriptSegments){ContentValues v=new ContentValues();v.put("item_id",itemId);v.put("start_ms",x.startMs);v.put("end_ms",x.endMs);v.put("text",x.text);v.put("confidence",x.confidence);s.insert("transcript_segments",null,v);}
            ContentValues a=new ContentValues();a.put("item_id",itemId);a.put("language",r.audioLanguage);a.put("duration_ms",r.audioDurationMs);a.put("engine",r.engine);a.put("processed_duration_ms",r.audioProcessedDurationMs);a.put("coverage",r.audioCoverage);a.put("raw_transcript",r.audioRawTranscript);a.put("provider_merged_transcript",r.audioProviderMergedTranscript);a.put("raw_provider_response",r.audioRawProviderResponse);s.insertWithOnConflict("audio_info",null,a,SQLiteDatabase.CONFLICT_REPLACE);s.setTransactionSuccessful();
        }finally{s.endTransaction();}
    }

    public static ArrayList<String> segments(VaultDb db,long itemId){ensure(db);ArrayList<String> out=new ArrayList<>();Cursor c=db.getReadableDatabase().query("transcript_segments",new String[]{"start_ms","text"},"item_id=?",new String[]{String.valueOf(itemId)},null,null,"start_ms ASC");while(c.moveToNext())out.add(fmt(c.getLong(0))+"  "+c.getString(1));c.close();return out;}

    public static String info(VaultDb db,long itemId){
        ensure(db);Cursor c=db.getReadableDatabase().query("audio_info",new String[]{"language","duration_ms","engine","processed_duration_ms","coverage","raw_provider_response"},"item_id=?",new String[]{String.valueOf(itemId)},null,null,null,"1");String x="";
        if(c.moveToFirst()){
            String lang=c.getString(0);long ms=c.getLong(1);String engine=c.getString(2);long processed=c.getLong(3);double coverage=c.getDouble(4);String response=c.getString(5);
            x="Duration: "+fmt(ms)+(lang==null||lang.isEmpty()?"":"\nLanguage: "+lang)+(engine==null||engine.isEmpty()?"":"\nEngine: "+engine)+(processed>0?"\nProcessed: "+fmt(processed):"")+(coverage>0?"\nCoverage: "+Math.round(coverage*100)+"%":"");
            String bench=benchmark(response);if(!bench.isEmpty())x+="\n\nASR BENCHMARK\n"+bench;
        }
        c.close();return x;
    }

    private static String benchmark(String response){
        if(response==null||response.trim().isEmpty())return "";
        try{
            JSONObject root=new JSONObject(response);JSONArray arr=root.optJSONArray("candidates");if(arr==null||arr.length()==0)return "";
            String selected=root.optString("selected","");StringBuilder b=new StringBuilder();
            for(int i=0;i<arr.length();i++){
                JSONObject j=arr.optJSONObject(i);if(j==null)continue;String label=j.optString("label","candidate");boolean win=label.equals(selected);
                if(b.length()>0)b.append("\n\n");b.append(win?"★ SELECTED — ":"• ").append(label);
                b.append("\nScore: ").append(j.optDouble("score",0));
                b.append("  | Coverage: ").append(Math.round(j.optDouble("coverage",0)*100)).append('%');
                b.append("\nArabic: ").append(Math.round(j.optDouble("arabic_ratio",0)*100)).append('%');
                b.append("  | Latin: ").append(Math.round(j.optDouble("latin_ratio",0)*100)).append('%');
                if(j.has("avg_logprob"))b.append("\navg_logprob: ").append(j.optDouble("avg_logprob",0));
                if(j.has("avg_compression_ratio"))b.append("  | compression: ").append(j.optDouble("avg_compression_ratio",0));
                if(j.has("avg_no_speech_prob"))b.append("\nno_speech: ").append(j.optDouble("avg_no_speech_prob",0));
                String warning=j.optString("warning","");if(!warning.isEmpty())b.append("\nWarning: ").append(warning);
                String text=j.optString("text","").trim();if(!text.isEmpty())b.append("\nText: ").append(text);
            }
            return b.toString();
        }catch(Exception ignored){return "";}
    }

    public static String diagnostics(VaultDb db,long itemId){
        ensure(db);Cursor c=db.getReadableDatabase().query("audio_info",new String[]{"raw_transcript","provider_merged_transcript","raw_provider_response"},"item_id=?",new String[]{String.valueOf(itemId)},null,null,null,"1");String x="";
        if(c.moveToFirst()){
            String raw=c.getString(0),merged=c.getString(1),response=c.getString(2);
            StringBuilder b=new StringBuilder();
            String bench=benchmark(response);if(!bench.isEmpty())b.append("ASR BENCHMARK\n").append(bench);
            if(raw!=null&&!raw.trim().isEmpty()){if(b.length()>0)b.append("\n\n");b.append("RAW ENGINE OUTPUT\n").append(raw.trim());}
            if(merged!=null&&!merged.trim().isEmpty()&&!merged.trim().equals(raw==null?"":raw.trim())){if(b.length()>0)b.append("\n\n");b.append("MERGED SEGMENTS\n").append(merged.trim());}
            if(response!=null&&!response.trim().isEmpty()){if(b.length()>0)b.append("\n\n");String compact=response.trim();if(compact.length()>2500)compact=compact.substring(0,2500)+"…";b.append("RAW PROVIDER JSON\n").append(compact);}
            x=b.toString();
        }
        c.close();return x;
    }

    private static String fmt(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.US,"%02d:%02d",sec/60,sec%60);}
}
