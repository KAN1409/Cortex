package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Small local-only timing ledger so debug exports can explain where a tap or AI request was slow. */
public final class InteractionTelemetry {
    private InteractionTelemetry(){}

    public static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS interaction_telemetry(id INTEGER PRIMARY KEY AUTOINCREMENT,created_at INTEGER NOT NULL,surface TEXT,action TEXT,stage TEXT,item_id INTEGER DEFAULT 0,latency_ms INTEGER DEFAULT 0,status TEXT,detail TEXT,metrics_json TEXT)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_interaction_action ON interaction_telemetry(action,created_at)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_interaction_surface ON interaction_telemetry(surface,created_at)");
    }

    public static void log(VaultDb db,String surface,String action,String stage,long itemId,long latencyMs,String status,String detail,JSONObject metrics){
        try{
            ensure(db);ContentValues v=new ContentValues();v.put("created_at",System.currentTimeMillis());v.put("surface",nz(surface));v.put("action",nz(action));v.put("stage",nz(stage));v.put("item_id",itemId);v.put("latency_ms",Math.max(0,latencyMs));v.put("status",nz(status));v.put("detail",nz(detail));v.put("metrics_json",metrics==null?"{}":metrics.toString());db.getWritableDatabase().insert("interaction_telemetry",null,v);
        }catch(Exception ignored){}
    }

    public static JSONObject latest(VaultDb db,String action,String stage){
        JSONObject o=new JSONObject();try{ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT created_at,surface,action,stage,item_id,latency_ms,status,detail,metrics_json FROM interaction_telemetry WHERE action=? AND stage=? ORDER BY id DESC LIMIT 1",new String[]{nz(action),nz(stage)});if(c.moveToFirst()){o.put("created_at",c.getLong(0));o.put("surface",c.getString(1));o.put("action",c.getString(2));o.put("stage",c.getString(3));o.put("item_id",c.getLong(4));o.put("latency_ms",c.getLong(5));o.put("status",c.getString(6));o.put("detail",c.getString(7));try{o.put("metrics",new JSONObject(c.getString(8)==null?"{}":c.getString(8)));}catch(Exception e){o.put("metrics_raw",c.getString(8));}}c.close();}catch(Exception ignored){}return o;
    }
    private static String nz(String s){return s==null?"":s;}
}
