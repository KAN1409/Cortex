package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Gemini implementation of Cortex autonomous Deep Brain. */
public final class GeminiCognitiveReasoningProviderV4 implements CognitiveReasoningProviderV4 {
    @Override public boolean configured(Context context){return context!=null&&GeminiKeyStore.has(context);}
    @Override public String id(){return "gemini";}
    @Override public String model(Context context){return context==null?GeminiModelConfig.DEFAULT_GENERATION_MODEL:GeminiModelConfig.generationModel(context);}

    @Override public Result reason(Context context,CognitiveDeepBrainPacketBuilderV4.Packet packet)throws Exception{
        if(context==null||packet==null)throw new IllegalArgumentException("context and packet required");String key=GeminiKeyStore.get(context);if(key.isEmpty())throw new IllegalStateException("Gemini API key not configured");String model=model(context);
        String prompt=prompt(packet);JSONArray parts=new JSONArray().put(new JSONObject().put("text",prompt));JSONArray contents=new JSONArray().put(new JSONObject().put("role","user").put("parts",parts));JSONObject cfg=new JSONObject().put("temperature",0.12).put("maxOutputTokens",1800).put("responseMimeType","application/json");JSONObject req=new JSONObject().put("contents",contents).put("generationConfig",cfg);
        long started=SystemClock.elapsedRealtime();HttpURLConnection c=(HttpURLConnection)new URL(endpoint(model,key)).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(45000);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");write(c,req.toString());int code=c.getResponseCode();String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());c.disconnect();long duration=SystemClock.elapsedRealtime()-started;if(code<200||code>=300)throw new IllegalStateException("Gemini cognitive reasoning HTTP "+code+": "+clip(body,500));String text=extractText(new JSONObject(body));String json=CognitiveDeepBrainProtocolV4.firstJsonObject(stripFence(text));if(json.isEmpty())throw new IllegalStateException("Gemini returned no response JSON");String raw=CognitiveDeepBrainProtocolV4.RESPONSE_MARKER+"\n"+json;CognitiveDeepBrainProtocolV4.ParsedResponse parsed=CognitiveDeepBrainProtocolV4.parseResponse(raw);if(!packet.requestId.equals(parsed.requestId))throw new IllegalStateException("Gemini response request_id mismatch");validateShape(parsed.json);return new Result(raw,id(),model,duration);
    }

    /** Autonomous runs must fail closed instead of marking a partial/malformed pass as fresh truth. */
    static void validateShape(JSONObject json){
        if(json==null)throw new IllegalArgumentException("Gemini response JSON required");
        requireArray(json,"priority_items");requireArray(json,"priority_updates");requireArray(json,"suggested_actions");requireArray(json,"reasoning_blocks");
        JSONArray ranked=json.optJSONArray("priority_items"),updates=json.optJSONArray("priority_updates"),actions=json.optJSONArray("suggested_actions");
        if(ranked.length()>20||updates.length()>20||actions.length()>20)throw new IllegalArgumentException("Gemini response exceeds Cortex reasoning limits");
    }
    private static void requireArray(JSONObject json,String key){if(json.optJSONArray(key)==null)throw new IllegalArgumentException("Gemini response missing "+key);}

    private static String prompt(CognitiveDeepBrainPacketBuilderV4.Packet p){
        return "You are the autonomous Deep Brain inside Cortex. Use only the grounded Cortex JSON below for claims about the user's history. Think deeply about what needs attention now, why, and what should happen next. Never invent events, IDs, facts, completion, or resolution. Treat attention_score as live current attention and canonical_attention_score as the durable baseline. Reconsider anything with new_since_deep_brain=true. connector_enriched=true means trusted Second Brain context is available but does not by itself prove urgency.\n\n"+
                "Return ONLY one JSON object, with no markdown and no prose outside JSON. The object must contain request_id exactly as supplied, answer, priority_items, priority_updates, suggested_actions, reasoning_blocks. Keep each array at 20 items or fewer. Every priority must cite at least one supplied situation_id, memory_id, or world_id. Prefer an existing situation_id when it already represents the issue. Allowed situation states are DETECTED, RELEVANT, SURFACED, DEFERRED, WAITING. Never return RESOLVED, CANCELLED, or DISMISSED. suggested_actions are proposals only and must never claim execution. reasoning_blocks must be concise conclusions, not hidden chain-of-thought. If nothing deserves model priority now, return priority_items: [].\n\n"+
                "REQUEST_ID: "+p.requestId+"\nQUESTION: "+p.question+"\nCONTEXT_JSON:\n"+p.compactContextJson;
    }

    private static String endpoint(String model,String key)throws Exception{return "https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent?key="+URLEncoder.encode(key,"UTF-8");}
    private static void write(HttpURLConnection c,String s)throws Exception{byte[] b=s.getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(b);}}
    private static String read(InputStream in)throws Exception{if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line);}return b.toString();}
    private static String extractText(JSONObject root){JSONArray candidates=root.optJSONArray("candidates");if(candidates==null||candidates.length()==0)return"";JSONObject candidate=candidates.optJSONObject(0);if(candidate==null)return"";JSONObject content=candidate.optJSONObject("content");JSONArray parts=content==null?null:content.optJSONArray("parts");if(parts==null)return"";StringBuilder b=new StringBuilder();for(int i=0;i<parts.length();i++){JSONObject p=parts.optJSONObject(i);if(p==null)continue;String t=p.optString("text","");if(!t.isEmpty()){if(b.length()>0)b.append('\n');b.append(t);}}return b.toString();}
    private static String stripFence(String s){String x=s==null?"":s.trim();if(x.startsWith("```")){int first=x.indexOf('\n');int last=x.lastIndexOf("```");if(first>=0&&last>first)x=x.substring(first+1,last).trim();}return x;}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n);}
}
