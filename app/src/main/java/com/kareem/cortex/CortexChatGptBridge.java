package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Bidirectional transport between Cortex and the private ChatGPT MCP relay.
 * No OpenAI model API is called here. Cortex uploads a compact context pack and pulls a policy pack.
 */
public final class CortexChatGptBridge {
    private static final int CONNECT_TIMEOUT_MS=8_000,READ_TIMEOUT_MS=12_000;
    private CortexChatGptBridge(){}

    public static boolean sync(Context context)throws Exception{
        Context app=context.getApplicationContext();if(!CortexChatGptBridgeConfig.enabled(app))return false;
        String endpoint=CortexChatGptBridgeConfig.endpoint(app);String token=CortexChatGptBridgeConfig.token(app);
        VaultDb db=new VaultDb(app);try{
            JSONObject contextPack=buildContextPack(app,db);
            request("POST",endpoint+"/device/context",token,contextPack.toString());
            String response=request("GET",endpoint+"/device/policy",token,null);
            JSONObject root=new JSONObject(response);JSONObject policy=root.optJSONObject("policy");if(policy!=null)CortexPersonalPolicy.save(app,policy);
            CortexChatGptBridgeConfig.record(app,true,"");return true;
        }catch(Exception e){CortexChatGptBridgeConfig.record(app,false,e.getClass().getSimpleName()+": "+safe(e.getMessage()));throw e;}
        finally{try{db.close();}catch(Throwable ignored){}}
    }

    static JSONObject buildContextPack(Context app,VaultDb db)throws Exception{
        SQLiteDatabase s=db.getReadableDatabase();JSONObject root=new JSONObject();root.put("schemaVersion",1);root.put("deviceId",CortexChatGptBridgeConfig.deviceId(app));root.put("generatedAt",System.currentTimeMillis());
        root.put("priorityCandidates",priorityCandidates(s));root.put("interests",interests(s));root.put("situations",situations(s));root.put("feedback",feedback(s));root.put("system",system(s));return root;
    }

    private static JSONArray priorityCandidates(SQLiteDatabase s){
        JSONArray a=new JSONArray();Cursor c=null;try{if(!table(s,"derived_items"))return a;c=s.rawQuery("SELECT id,COALESCE(kind,''),COALESCE(title,''),COALESCE(body,''),COALESCE(state,''),COALESCE(confidence,0),COALESCE(importance,0),COALESCE(source,''),COALESCE(updated_at,0) FROM derived_items WHERE state IN ('open','pending') ORDER BY importance DESC,updated_at DESC LIMIT 80",null);while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getLong(0));o.put("kind",c.getString(1));o.put("title",clip(c.getString(2),220));o.put("body",clip(c.getString(3),700));o.put("state",c.getString(4));o.put("confidence",c.getDouble(5));o.put("importance",c.getInt(6));o.put("source",c.getString(7));o.put("updatedAt",c.getLong(8));a.put(o);}}catch(Throwable ignored){}finally{if(c!=null)c.close();}return a;
    }
    private static JSONArray interests(SQLiteDatabase s){
        JSONArray a=new JSONArray();Cursor c=null;try{if(!table(s,"nx_interests"))return a;c=s.rawQuery("SELECT id,label,affinity,momentum,confidence,evidence_count,updated_at FROM nx_interests ORDER BY affinity DESC,momentum DESC LIMIT 20",null);while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getString(0));o.put("label",c.getString(1));o.put("affinity",c.getDouble(2));o.put("momentum",c.getDouble(3));o.put("confidence",c.getDouble(4));o.put("evidenceCount",c.getInt(5));o.put("updatedAt",c.getLong(6));a.put(o);}}catch(Throwable ignored){}finally{if(c!=null)c.close();}return a;
    }
    private static JSONArray situations(SQLiteDatabase s){
        JSONArray a=new JSONArray();Cursor c=null;try{if(!table(s,"derived_items"))return a;c=s.rawQuery("SELECT id,kind,title,body,confidence,importance,source,updated_at FROM derived_items WHERE state='open' AND kind IN ('ACTION','WAITING','DECISION','INSIGHT','OPPORTUNITY') ORDER BY updated_at DESC LIMIT 40",null);while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id","derived:"+c.getLong(0));o.put("type",c.getString(1));o.put("title",clip(c.getString(2),220));o.put("summary",clip(c.getString(3),700));o.put("confidence",c.getDouble(4));o.put("importance",c.getInt(5));o.put("source",c.getString(6));o.put("updatedAt",c.getLong(7));a.put(o);}}catch(Throwable ignored){}finally{if(c!=null)c.close();}return a;
    }
    private static JSONObject feedback(SQLiteDatabase s){
        JSONObject o=new JSONObject();Cursor c=null;try{if(!table(s,"feedback_events"))return o;c=s.rawQuery("SELECT COALESCE(event_type,''),COUNT(*) FROM feedback_events GROUP BY event_type ORDER BY COUNT(*) DESC LIMIT 30",null);while(c.moveToNext())o.put(c.getString(0),c.getInt(1));}catch(Throwable ignored){}finally{if(c!=null)c.close();}return o;
    }
    private static JSONObject system(SQLiteDatabase s){
        JSONObject o=new JSONObject();try{o.put("openDerived",count(s,"SELECT COUNT(*) FROM derived_items WHERE state IN ('open','pending')"));o.put("knowledge",count(s,"SELECT COUNT(*) FROM knowledge_items"));o.put("observed",count(s,"SELECT COUNT(*) FROM ue_raw_observations"));o.put("understood",count(s,"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='complete' AND superseded_by=0"));o.put("policyVersion","");}catch(Throwable ignored){}return o;
    }

    private static String request(String method,String url,String token,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();try{c.setRequestMethod(method);c.setConnectTimeout(CONNECT_TIMEOUT_MS);c.setReadTimeout(READ_TIMEOUT_MS);c.setRequestProperty("Accept","application/json");if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();String text=read(in);if(code<200||code>=300)throw new IOException("Bridge HTTP "+code+(text.isEmpty()?"":" · "+clip(text,240)));return text;
        }finally{c.disconnect();}
    }
    private static String read(InputStream in)throws IOException{if(in==null)return"";ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n,total=0;while((n=in.read(x))>0&&total<1_000_000){b.write(x,0,n);total+=n;}return b.toString(StandardCharsets.UTF_8.name());}
    private static boolean table(SQLiteDatabase s,String name){Cursor c=null;try{c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{name});return c.moveToFirst();}catch(Throwable ignored){return false;}finally{if(c!=null)c.close();}}
    private static long count(SQLiteDatabase s,String sql){Cursor c=null;try{c=s.rawQuery(sql,null);return c.moveToFirst()?c.getLong(0):0;}catch(Throwable ignored){return 0;}finally{if(c!=null)c.close();}}
    private static String clip(String s,int n){String x=safe(s).replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String safe(String s){return s==null?"":s;}
}
