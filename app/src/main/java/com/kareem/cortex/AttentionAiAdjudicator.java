package com.kareem.cortex;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Optional model refinement over an explainable deterministic baseline.
 * The model may refine attention, never become the sole authority.
 */
public final class AttentionAiAdjudicator {
    private static final int MIN_CONFIDENCE_PCT=72;
    private AttentionAiAdjudicator(){}

    public static final class Result {
        public final AttentionEngine.Decision merged;public final int modelScore;public final double confidence;public final String provider,evidence;
        Result(AttentionEngine.Decision d,int s,double c,String p,String e){merged=d;modelScore=s;confidence=c;provider=p;evidence=e;}
    }

    public static Result adjudicate(Context context,PrimeBriefStore.Item item,AttentionEngine.Decision baseline,AttentionContextBuilder.Pack pack)throws Exception{
        if(context==null||item==null||baseline==null||pack==null||!pack.usable())return null;
        if(!ExternalBrainProvider.configured(context))return null;
        String prompt=prompt(item,baseline,pack.text);String provider=ExternalBrainProvider.activeProviderId(context);String text="openrouter".equals(provider)?openRouter(context,prompt):gemini(context,prompt);JSONObject j=parse(text);
        double confidence=clamp(j.optDouble("confidence",0));if(Math.round(confidence*100)<MIN_CONFIDENCE_PCT)return null;
        int modelScore=Math.max(0,Math.min(100,j.optInt("score",baseline.score)));String reason=clean(j.optString("why_now",""));String evidence=clean(j.optString("evidence",""));
        boolean temporal=j.optBoolean("temporal_evidence",false),ownership=j.optBoolean("responsibility_evidence",false),resolved=j.optBoolean("resolved",false);
        AttentionEngine.Decision merged=merge(baseline,modelScore,reason,confidence,temporal,ownership,resolved,evidence);
        return merged==null?null:new Result(merged,modelScore,confidence,provider+" · "+ExternalBrainProvider.activeModel(context),evidence);
    }

    private static AttentionEngine.Decision merge(AttentionEngine.Decision b,int modelScore,String why,double confidence,boolean temporal,boolean ownership,boolean resolved,String evidence){
        if(resolved&&confidence>=0.88)return new AttentionEngine.Decision(Math.min(25,b.score),AttentionEngine.Band.QUIET,why.isEmpty()?"New grounded evidence indicates this loop is already resolved.":why,b.urgency,b.consequence,b.responsibility,b.temporalPressure,b.openLoopPressure,b.novelty,confidence);
        int maxDelta=confidence>=0.90&&(temporal||ownership)?20:15;int delta=Math.max(-maxDelta,Math.min(maxDelta,modelScore-b.score));int finalScore=Math.max(0,Math.min(100,b.score+delta));AttentionEngine.Band band=band(finalScore);
        // A weak candidate cannot jump directly into NOW on model opinion alone.
        if(b.band==AttentionEngine.Band.QUIET||b.band==AttentionEngine.Band.WATCHING){if(band==AttentionEngine.Band.NOW&&!(confidence>=0.88&&(temporal||ownership)&&!evidence.isEmpty())){finalScore=Math.min(finalScore,71);band=band(finalScore);}}
        // Evidence-free positive boosts are not trusted. Negative corrections are allowed because they reduce interruption.
        if(delta>0&&evidence.isEmpty()){finalScore=b.score;band=b.band;}
        String reason=why.isEmpty()?b.whyNow:why;
        return new AttentionEngine.Decision(finalScore,band,reason,b.urgency,b.consequence,b.responsibility,b.temporalPressure,b.openLoopPressure,b.novelty,confidence);
    }

    private static String prompt(PrimeBriefStore.Item item,AttentionEngine.Decision b,String context){return
        "You are Cortex Attention Adjudicator. Decide whether this already-relevant personal intelligence deserves the user's LIMITED attention now. Do not summarize everything. Do not invent dates, responsibility, completion, urgency, people, or consequences. Use only the grounded evidence supplied. The deterministic baseline is a safety prior, not a command. Return STRICT JSON ONLY with schema: {\"score\":0,\"why_now\":\"one evidence-backed sentence\",\"evidence\":\"short exact fact from supplied evidence supporting the change, or empty\",\"temporal_evidence\":false,\"responsibility_evidence\":false,\"resolved\":false,\"confidence\":0.0}. Score means current attention value, not long-term importance. 0-35 quiet, 36-53 watching, 54-71 later, 72-100 now. Be conservative: if evidence does not materially change the baseline, keep approximately the same score.\n\nBASELINE\nscore="+b.score+" band="+b.band+" why="+b.whyNow+"\n\n"+context;}

    private static String openRouter(Context c,String prompt)throws Exception{
        String key=OpenRouterKeyStore.get(c),model=OpenRouterModelConfig.generationModel(c);if(key.isEmpty())throw new IOException("OpenRouter key missing");JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content","Return strict JSON only. No chain-of-thought.")).put(new JSONObject().put("role","user").put("content",prompt));JSONObject req=new JSONObject().put("model",model).put("messages",messages).put("max_tokens",420).put("temperature",0.05);HttpURLConnection h=open("https://openrouter.ai/api/v1/chat/completions");h.setRequestProperty("Authorization","Bearer "+key);h.setRequestProperty("X-Title","Cortex");write(h,req);int code=h.getResponseCode();String body=read(code>=200&&code<300?h.getInputStream():h.getErrorStream());h.disconnect();if(code<200||code>=300)throw new IOException("Attention OpenRouter HTTP "+code);JSONObject root=new JSONObject(body);JSONArray choices=root.optJSONArray("choices");JSONObject msg=choices!=null&&choices.length()>0?choices.optJSONObject(0).optJSONObject("message"):null;return msg==null?"":msg.optString("content","");
    }

    private static String gemini(Context c,String prompt)throws Exception{
        String key=GeminiKeyStore.get(c),model=GeminiModelConfig.generationModel(c);if(key.isEmpty())throw new IOException("Gemini key missing");JSONArray parts=new JSONArray().put(new JSONObject().put("text",prompt));JSONArray contents=new JSONArray().put(new JSONObject().put("role","user").put("parts",parts));JSONObject cfg=new JSONObject().put("temperature",0.05).put("maxOutputTokens",420).put("responseMimeType","application/json");JSONObject req=new JSONObject().put("contents",contents).put("generationConfig",cfg);String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+URLEncoder.encode(model,"UTF-8")+":generateContent?key="+URLEncoder.encode(key,"UTF-8");HttpURLConnection h=open(endpoint);write(h,req);int code=h.getResponseCode();String body=read(code>=200&&code<300?h.getInputStream():h.getErrorStream());h.disconnect();if(code<200||code>=300)throw new IOException("Attention Gemini HTTP "+code);JSONObject root=new JSONObject(body);JSONArray cs=root.optJSONArray("candidates");JSONObject content=cs!=null&&cs.length()>0?cs.optJSONObject(0).optJSONObject("content"):null;JSONArray ps=content==null?null:content.optJSONArray("parts");return ps!=null&&ps.length()>0?ps.optJSONObject(0).optString("text",""):"";
    }

    private static HttpURLConnection open(String url)throws Exception{HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection();h.setRequestMethod("POST");h.setDoOutput(true);h.setConnectTimeout(15000);h.setReadTimeout(45000);h.setRequestProperty("Content-Type","application/json");h.setRequestProperty("Accept","application/json");return h;}
    private static void write(HttpURLConnection h,JSONObject j)throws Exception{try(OutputStream o=h.getOutputStream()){o.write(j.toString().getBytes(StandardCharsets.UTF_8));}}
    private static String read(InputStream in)throws Exception{if(in==null)return"";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}
    private static JSONObject parse(String s)throws Exception{String x=clean(s).replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","").trim();return new JSONObject(x);}
    private static AttentionEngine.Band band(int score){return score>=72?AttentionEngine.Band.NOW:score>=54?AttentionEngine.Band.LATER:score>=36?AttentionEngine.Band.WATCHING:AttentionEngine.Band.QUIET;}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static String clean(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
}
