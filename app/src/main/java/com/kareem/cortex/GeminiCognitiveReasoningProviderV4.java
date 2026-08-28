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
import java.nio.charset.StandardCharsets;

/** Gemini implementation of Cortex autonomous Deep Brain. */
public final class GeminiCognitiveReasoningProviderV4 implements CognitiveReasoningProviderV4 {
    @Override public boolean configured(Context context){return context!=null&&GeminiKeyStore.has(context);}
    @Override public String id(){return "gemini";}
    @Override public String model(Context context){return context==null?GeminiModelConfig.DEFAULT_GENERATION_MODEL:GeminiModelConfig.generationModel(context);}

    @Override public Result reason(Context context,CognitiveDeepBrainPacketBuilderV4.Packet packet)throws Exception{
        if(context==null||packet==null)throw new IllegalArgumentException("context and packet required");String key=GeminiKeyStore.get(context);if(key.isEmpty())throw new IllegalStateException("Gemini API key not configured");String model=model(context);
        String prompt=prompt(packet);JSONArray parts=new JSONArray().put(new JSONObject().put("text",prompt));JSONArray contents=new JSONArray().put(new JSONObject().put("role","user").put("parts",parts));JSONObject req=new JSONObject().put("contents",contents).put("generationConfig",generationConfig());
        long started=SystemClock.elapsedRealtime();HttpURLConnection c=(HttpURLConnection)new URL(endpoint(model)).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(45000);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");c.setRequestProperty("x-goog-api-key",key);write(c,req.toString());int code=c.getResponseCode();String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());c.disconnect();long duration=SystemClock.elapsedRealtime()-started;if(code<200||code>=300)throw new IllegalStateException("Gemini cognitive reasoning HTTP "+code+": "+clip(body,500));String text=extractText(new JSONObject(body));String json=CognitiveDeepBrainProtocolV4.firstJsonObject(stripFence(text));if(json.isEmpty())throw new IllegalStateException("Gemini returned no response JSON");String raw=CognitiveDeepBrainProtocolV4.RESPONSE_MARKER+"\n"+json;CognitiveDeepBrainProtocolV4.ParsedResponse parsed=CognitiveDeepBrainProtocolV4.parseResponse(raw);if(!packet.requestId.equals(parsed.requestId))throw new IllegalStateException("Gemini response request_id mismatch");validateShape(parsed.json);return new Result(raw,id(),model,duration);
    }

    /** Autonomous runs must fail closed instead of marking a partial/malformed pass as fresh truth. */
    static void validateShape(JSONObject json){
        if(json==null)throw new IllegalArgumentException("Gemini response JSON required");
        if(!json.has("answer"))throw new IllegalArgumentException("Gemini response missing answer");
        requireArray(json,"priority_items");requireArray(json,"priority_updates");requireArray(json,"suggested_actions");requireArray(json,"reasoning_blocks");
        JSONArray ranked=json.optJSONArray("priority_items"),updates=json.optJSONArray("priority_updates"),actions=json.optJSONArray("suggested_actions"),reasoning=json.optJSONArray("reasoning_blocks");
        if(ranked.length()>20||updates.length()>20||actions.length()>20||reasoning.length()>20)throw new IllegalArgumentException("Gemini response exceeds Cortex reasoning limits");
    }
    private static void requireArray(JSONObject json,String key){if(json.optJSONArray(key)==null)throw new IllegalArgumentException("Gemini response missing "+key);}

    /** Current Gemini structured-output transport. Cortex validation remains authoritative. */
    static JSONObject generationConfig(){
        JSONObject text=new JSONObject().put("mimeType","application/json").put("schema",responseSchema());
        return new JSONObject().put("maxOutputTokens",4096).put("responseFormat",new JSONObject().put("text",text));
    }

    /** JSON Schema constrains transport shape; Cortex still performs grounding/state/action validation. */
    static JSONObject responseSchema(){
        JSONObject idArray=arrayOf(type("string"),12);
        JSONObject priorityItem=object(
                new JSONObject().put("rank",type("integer")).put("title",type("string")).put("reason",type("string")).put("attention_score",number01())
                        .put("situation_id",type("string")).put("memory_ids",idArray).put("world_ids",arrayOf(type("string"),12)),
                "rank","title","reason");
        JSONObject priorityUpdate=object(
                new JSONObject().put("situation_id",type("string")).put("attention_score",number01()).put("interruption_score",number01())
                        .put("state",enumString("DETECTED","RELEVANT","SURFACED","DEFERRED","WAITING")).put("reason",type("string")),
                "situation_id","reason");
        JSONObject payload=new JSONObject().put("type","object").put("additionalProperties",true);
        JSONObject action=object(
                new JSONObject().put("situation_id",type("string")).put("world_id",type("string"))
                        .put("type",enumString("OPEN","REPLY","CALL","DRAFT","SEND","REMIND","SCHEDULE","COMPLETE","DECIDE","REVIEW","CUSTOM"))
                        .put("label",type("string")).put("risk",enumString("SAFE","CONFIRMATION_REQUIRED","SENSITIVE","BLOCKED")).put("reason",type("string")).put("payload",payload),
                "type","label");
        action.put("anyOf",new JSONArray()
                .put(new JSONObject().put("required",new JSONArray().put("situation_id")))
                .put(new JSONObject().put("required",new JSONArray().put("world_id"))));
        JSONObject reasoning=object(
                new JSONObject().put("type",type("string")).put("grounding",type("string")).put("text",type("string"))
                        .put("evidence_ids",arrayOf(type("string"),12)).put("memory_ids",arrayOf(type("string"),12)).put("fact_ids",arrayOf(type("string"),12)).put("world_ids",arrayOf(type("string"),12)),
                "text");
        return object(new JSONObject()
                        .put("request_id",type("string")).put("answer",type("string"))
                        .put("priority_items",arrayOf(priorityItem,20)).put("priority_updates",arrayOf(priorityUpdate,20))
                        .put("suggested_actions",arrayOf(action,20)).put("reasoning_blocks",arrayOf(reasoning,20)),
                "request_id","answer","priority_items","priority_updates","suggested_actions","reasoning_blocks");
    }
    private static JSONObject object(JSONObject properties,String...required){JSONObject o=new JSONObject().put("type","object").put("properties",properties).put("additionalProperties",false);JSONArray a=new JSONArray();for(String x:required)a.put(x);return o.put("required",a);}
    private static JSONObject arrayOf(JSONObject item,int max){return new JSONObject().put("type","array").put("items",item).put("maxItems",max);}
    private static JSONObject type(String type){return new JSONObject().put("type",type);}
    private static JSONObject number01(){return new JSONObject().put("type","number").put("minimum",0).put("maximum",1);}
    private static JSONObject enumString(String...values){JSONArray a=new JSONArray();for(String x:values)a.put(x);return new JSONObject().put("type","string").put("enum",a);}

    private static String prompt(CognitiveDeepBrainPacketBuilderV4.Packet p){
        return "You are the autonomous Deep Brain inside Cortex. Use only the grounded Cortex JSON below for claims about the user's history. Think deeply about what needs attention now, why, and what should happen next. Never invent events, IDs, facts, completion, or resolution. Treat attention_score as live current attention and canonical_attention_score as the durable baseline. Reconsider anything with new_since_deep_brain=true. connector_enriched=true means trusted Second Brain context is available but does not by itself prove urgency.\n\n"+
                "Return ONLY one JSON object, with no markdown and no prose outside JSON. The object must contain request_id exactly as supplied, answer, priority_items, priority_updates, suggested_actions, reasoning_blocks. Keep each array at 20 items or fewer. Every priority must cite at least one supplied situation_id, memory_id, or world_id. Prefer an existing situation_id when it already represents the issue. Every suggested action must reference a supplied situation_id or world_id; never return a free-floating action. Allowed situation states are DETECTED, RELEVANT, SURFACED, DEFERRED, WAITING. Never return RESOLVED, CANCELLED, or DISMISSED. suggested_actions are proposals only and must never claim execution. reasoning_blocks must be concise conclusions, not hidden chain-of-thought. If nothing deserves model priority now, return priority_items: [].\n\n"+
                "REQUEST_ID: "+p.requestId+"\nQUESTION: "+p.question+"\nCONTEXT_JSON:\n"+p.compactContextJson;
    }

    private static String endpoint(String model){return "https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent";}
    private static void write(HttpURLConnection c,String s)throws Exception{byte[] b=s.getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(b);}}
    private static String read(InputStream in)throws Exception{if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line);}return b.toString();}
    private static String extractText(JSONObject root){JSONArray candidates=root.optJSONArray("candidates");if(candidates==null||candidates.length()==0)return"";JSONObject candidate=candidates.optJSONObject(0);if(candidate==null)return"";JSONObject content=candidate.optJSONObject("content");JSONArray parts=content==null?null:content.optJSONArray("parts");if(parts==null)return"";StringBuilder b=new StringBuilder();for(int i=0;i<parts.length();i++){JSONObject p=parts.optJSONObject(i);if(p==null)continue;String t=p.optString("text","");if(!t.isEmpty()){if(b.length()>0)b.append('\n');b.append(t);}}return b.toString();}
    private static String stripFence(String s){String x=s==null?"":s.trim();if(x.startsWith("```")){int first=x.indexOf('\n');int last=x.lastIndexOf("```");if(first>=0&&last>first)x=x.substring(first+1,last).trim();}return x;}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n);}
}
