package com.kareem.cortex;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Persistent structured understanding for screenshots. Raw image remains in Vault; this is derived/versioned intelligence. */
public final class VisualInsightStore {
    public static final int PIPELINE_VERSION=47;
    private static final String PREF="cortex_visual_v47";
    private VisualInsightStore(){}

    public static final class Insight {
        public long itemId,updatedAt;
        public String status="",provider="",contentType="",description="",visibleText="",usefulnessReason="",privacyLevel="",recreationPrompt="",searchQuery="",intentsJson="[]",rawJson="",error="";
        public int usefulnessScore,userValue;
        public boolean ready(){return "done".equals(status);}
        public ArrayList<String> actionLabels(){ArrayList<String> out=new ArrayList<>();try{JSONArray a=new JSONArray(intentsJson==null?"[]":intentsJson);for(int i=0;i<a.length()&&i<6;i++){JSONObject o=a.optJSONObject(i);if(o!=null){String x=o.optString("label","").trim();if(!x.isEmpty())out.add(x);}}}catch(Exception ignored){}return out;}
    }

    public static void ensure(VaultDb db){SQLiteDatabase s=db.getWritableDatabase();s.execSQL("CREATE TABLE IF NOT EXISTS visual_insights(item_id INTEGER PRIMARY KEY,pipeline_version INTEGER NOT NULL,status TEXT NOT NULL,provider TEXT,content_type TEXT,description TEXT,visible_text TEXT,usefulness_score INTEGER DEFAULT 0,usefulness_reason TEXT,privacy_level TEXT,recreation_prompt TEXT,search_query TEXT,intents_json TEXT,raw_json TEXT,error TEXT,user_value INTEGER DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");s.execSQL("CREATE INDEX IF NOT EXISTS idx_visual_status ON visual_insights(status)");s.execSQL("CREATE INDEX IF NOT EXISTS idx_visual_type ON visual_insights(content_type)");}

    public static long backgroundStart(Context c){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);long x=p.getLong("background_start",0);if(x==0){x=System.currentTimeMillis();p.edit().putLong("background_start",x).apply();}return x;}
    public static long existingBackgroundStart(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong("background_start",0);}

    public static Insight get(VaultDb db,long itemId){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT * FROM visual_insights WHERE item_id=?",new String[]{String.valueOf(itemId)});Insight x=c.moveToFirst()?from(c):null;c.close();return x;}
    public static int countDone(VaultDb db){ensure(db);return count(db,"SELECT COUNT(*) FROM visual_insights WHERE status='done'");}
    public static int countSkipped(VaultDb db){ensure(db);return count(db,"SELECT COUNT(*) FROM visual_insights WHERE status IN ('skipped','local_only')");}
    public static int countFailed(VaultDb db){ensure(db);return count(db,"SELECT COUNT(*) FROM visual_insights WHERE status='failed'");}
    public static int countPendingSince(VaultDb db,long start){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM knowledge_items k WHERE k.source='screenshot-folder' AND k.type IN ('SCREENSHOT','IMAGE') AND k.status='analyzed' AND k.created_at>=? AND NOT EXISTS(SELECT 1 FROM visual_insights v WHERE v.item_id=k.id)",new String[]{String.valueOf(start)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}

    public static void saveModel(VaultDb db,long itemId,String provider,JSONObject root){ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("item_id",itemId);v.put("pipeline_version",PIPELINE_VERSION);v.put("status","done");v.put("provider",provider);v.put("content_type",root.optString("content_type","unknown"));v.put("description",root.optString("description",""));v.put("visible_text",root.optString("visible_text",""));JSONObject u=root.optJSONObject("usefulness");v.put("usefulness_score",u==null?root.optInt("usefulness_score",0):u.optInt("score",0));v.put("usefulness_reason",u==null?root.optString("usefulness_reason",""):u.optString("why",""));JSONObject p=root.optJSONObject("privacy");v.put("privacy_level",p==null?"model_checked":p.optString("level","model_checked"));v.put("recreation_prompt",root.optString("recreation_prompt",""));v.put("search_query",root.optString("search_query",""));JSONArray intents=root.optJSONArray("suggested_actions");v.put("intents_json",intents==null?"[]":intents.toString());v.put("raw_json",root.toString());v.put("error","");v.put("updated_at",now);v.put("created_at",now);db.getWritableDatabase().insertWithOnConflict("visual_insights",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public static void saveState(VaultDb db,long itemId,String status,String privacy,String error){ensure(db);Insight old=get(db,itemId);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("item_id",itemId);v.put("pipeline_version",PIPELINE_VERSION);v.put("status",status);v.put("provider",old==null?"local_triage":old.provider);v.put("content_type",old==null?"":old.contentType);v.put("description",old==null?"":old.description);v.put("visible_text",old==null?"":old.visibleText);v.put("usefulness_score",old==null?0:old.usefulnessScore);v.put("usefulness_reason",old==null?"":old.usefulnessReason);v.put("privacy_level",privacy==null?"":privacy);v.put("recreation_prompt",old==null?"":old.recreationPrompt);v.put("search_query",old==null?"":old.searchQuery);v.put("intents_json",old==null?"[]":old.intentsJson);v.put("raw_json",old==null?"":old.rawJson);v.put("error",error==null?"":error);v.put("user_value",old==null?0:old.userValue);v.put("created_at",old==null?now:old.updatedAt);v.put("updated_at",now);db.getWritableDatabase().insertWithOnConflict("visual_insights",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public static void setUserValue(VaultDb db,long itemId,int value){ensure(db);ContentValues v=new ContentValues();v.put("user_value",Math.max(-1,Math.min(1,value)));v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("visual_insights",v,"item_id=?",new String[]{String.valueOf(itemId)});}

    public static KnowledgeItem nextBackground(VaultDb db,long start){ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT k.* FROM knowledge_items k WHERE k.source='screenshot-folder' AND k.type IN ('SCREENSHOT','IMAGE') AND k.status='analyzed' AND k.created_at>=? AND NOT EXISTS(SELECT 1 FROM visual_insights v WHERE v.item_id=k.id) ORDER BY k.created_at ASC LIMIT 1",new String[]{String.valueOf(start)});KnowledgeItem k=c.moveToFirst()?item(c):null;c.close();return k;}

    public static void setWorker(Context c,String state,long itemId,String stage,String detail){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("worker_state",state).putLong("worker_item",itemId).putString("worker_stage",stage==null?"":stage).putString("worker_detail",detail==null?"":detail).putLong("worker_at",System.currentTimeMillis()).apply();}
    public static String workerState(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("worker_state","idle");}
    public static long workerItem(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong("worker_item",0);}
    public static String workerStage(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("worker_stage","");}
    public static String workerDetail(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("worker_detail","");}

    private static Insight from(Cursor c){Insight x=new Insight();x.itemId=g(c,"item_id");x.status=s(c,"status");x.provider=s(c,"provider");x.contentType=s(c,"content_type");x.description=s(c,"description");x.visibleText=s(c,"visible_text");x.usefulnessScore=(int)g(c,"usefulness_score");x.usefulnessReason=s(c,"usefulness_reason");x.privacyLevel=s(c,"privacy_level");x.recreationPrompt=s(c,"recreation_prompt");x.searchQuery=s(c,"search_query");x.intentsJson=s(c,"intents_json");x.rawJson=s(c,"raw_json");x.error=s(c,"error");x.userValue=(int)g(c,"user_value");x.updatedAt=g(c,"updated_at");return x;}
    private static KnowledgeItem item(Cursor c){return new KnowledgeItem(g(c,"id"),s(c,"type"),s(c,"source"),s(c,"title"),s(c,"raw_text"),s(c,"extracted_text"),s(c,"summary"),s(c,"category"),s(c,"tags"),s(c,"attachment_path"),s(c,"status"),s(c,"fingerprint"),s(c,"analysis_error"),s(c,"metadata_json"),g(c,"created_at"),g(c,"updated_at"));}
    private static int count(VaultDb db,String q){Cursor c=db.getReadableDatabase().rawQuery(q,null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    private static String s(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?"":c.getString(i);}private static long g(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?0:c.getLong(i);}
}
