package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class DiscoveryV3ResearchProvider {
    private DiscoveryV3ResearchProvider(){}
    public static boolean configured(Context c){return OpenRouterKeyStore.has(c);}

    public static Result search(Context c,String query)throws Exception{
        String key=OpenRouterKeyStore.get(c);if(key==null||key.trim().isEmpty())throw new IllegalStateException("OpenRouter key missing");
        String model=OpenRouterModelConfig.generationModel(c);
        JSONObject req=new JSONObject();
        req.put("model",model);
        JSONArray messages=new JSONArray();
        messages.put(new JSONObject().put("role","system").put("content",
                "You are Cortex Research. Use current web sources only for external context. Never invent private user facts. Distinguish external facts from the private observation supplied in the query. Prefer primary/authoritative sources. Return concise findings with uncertainty."));
        messages.put(new JSONObject().put("role","user").put("content",query));
        req.put("messages",messages);
        req.put("plugins",new JSONArray().put(new JSONObject().put("id","web").put("max_results",5)));
        req.put("max_tokens",1600);

        long started=SystemClock.elapsedRealtime();
        HttpURLConnection conn=(HttpURLConnection)new URL("https://openrouter.ai/api/v1/chat/completions").openConnection();
        conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setConnectTimeout(20000);conn.setReadTimeout(90000);
        conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Authorization","Bearer "+key);conn.setRequestProperty("X-Title","Cortex Discovery Research");
        try(OutputStream out=conn.getOutputStream()){out.write(req.toString().getBytes(StandardCharsets.UTF_8));}
        int code=conn.getResponseCode();String body=read(code>=200&&code<300?conn.getInputStream():conn.getErrorStream());conn.disconnect();
        long ms=SystemClock.elapsedRealtime()-started;if(code<200||code>=300)throw new IOException("OpenRouter web HTTP "+code+": "+clip(body,320));
        JSONObject root=new JSONObject(body);JSONArray choices=root.optJSONArray("choices");if(choices==null||choices.length()==0)throw new IOException("No research answer");
        JSONObject msg=choices.optJSONObject(0).optJSONObject("message");String text=msg==null?"":msg.optString("content","");
        JSONArray citations=new JSONArray();JSONArray anns=msg==null?null:msg.optJSONArray("annotations");
        if(anns!=null)for(int i=0;i<anns.length();i++){JSONObject a=anns.optJSONObject(i);JSONObject u=a==null?null:a.optJSONObject("url_citation");if(u==null)continue;citations.put(new JSONObject().put("url",u.optString("url","")).put("title",u.optString("title","")));}
        return new Result(text.trim(),citations.toString(),model,ms);
    }

    private static String read(InputStream in)throws Exception{if(in==null)return"";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
    public static final class Result{public final String text,citationsJson,model;public final long latencyMs;Result(String t,String c,String m,long l){text=t;citationsJson=c;model=m;latencyMs=l;}}
}
