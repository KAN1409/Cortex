package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Explicit cloud route for Brain. Never invoked from Your Data mode. */
public final class ExternalBrainProvider {
    private ExternalBrainProvider(){}

    public static final class Result {
        public final String text,rawResponse;
        public final long durationMs;
        Result(String t,String raw,long ms){text=t;rawResponse=raw;durationMs=ms;}
    }

    public static Result ask(Context context,String question,GroundedAnswer grounded,boolean combined)throws Exception{
        String key=GeminiKeyStore.get(context);if(key.isEmpty())throw new IllegalStateException("Gemini API key not configured");
        String model=GeminiModelConfig.generationModel(context);
        String prompt=combined?combinedPrompt(question,grounded):externalPrompt(question);
        JSONArray parts=new JSONArray().put(new JSONObject().put("text",prompt));
        JSONArray contents=new JSONArray().put(new JSONObject().put("role","user").put("parts",parts));
        JSONObject cfg=new JSONObject().put("temperature",0.25).put("maxOutputTokens",1200);
        JSONObject req=new JSONObject().put("contents",contents).put("generationConfig",cfg);
        String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent?key="+java.net.URLEncoder.encode(key,"UTF-8");
        long started=SystemClock.elapsedRealtime();HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(90000);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
        try(OutputStream out=c.getOutputStream()){out.write(req.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());c.disconnect();long ms=SystemClock.elapsedRealtime()-started;
        if(code<200||code>=300)throw new IOException("Gemini Brain HTTP "+code+" ["+model+"]: "+compact(body));
        String text=extractText(new JSONObject(body)).trim();if(text.isEmpty())throw new IOException("Gemini returned an empty answer");
        text=text.replaceAll("(?s)<think>.*?</think>","").replaceAll("^```(?:text|markdown)?\\s*","").replaceAll("```$","").trim();
        return new Result(text,body,ms);
    }

    private static String externalPrompt(String q){return "You are Brain, the general AI surface inside Cortex. Answer the user's question using general knowledge only. No private Cortex memory has been supplied in this route. If the answer depends on current live information you cannot verify, say that clearly rather than pretending it is current. Preserve Egyptian Arabic and English code-switching naturally. Be concise, useful, and do not reveal chain-of-thought.\n\nUSER QUESTION:\n"+q;}

    /** Dynamic total evidence budget: richer context with few sources, bounded payload with many sources. */
    private static String combinedPrompt(String q,GroundedAnswer g){
        StringBuilder s=new StringBuilder();
        s.append("You are Brain, the AI surface inside Cortex. The user explicitly selected Combined mode, so the following selected private Cortex evidence may be used together with general knowledge. Distinguish what comes from Cortex evidence from general knowledge. Never invent missing private facts. If current live information is required but unavailable, say so. Preserve Egyptian Arabic and English code-switching naturally. Do not reveal chain-of-thought.\n\nUSER QUESTION:\n").append(q).append("\n\nSELECTED CORTEX EVIDENCE:\n");
        int n=g==null?0:Math.min(6,g.sources.size());
        int perSource=n<=0?0:Math.min(1200,Math.max(480,4800/n));
        for(int i=0;i<n;i++){
            KnowledgeItem k=g.sources.get(i).item;
            String body=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);if(body==null)body="";body=body.replace('\u0000',' ').trim();
            if(body.length()>perSource)body=body.substring(0,perSource)+"…";
            s.append("[M").append(i+1).append("] ").append(k.title==null?"Memory":k.title).append("\n").append(body).append("\n\n");
        }
        if(n==0)s.append("(No relevant private evidence was found.)\n\n");
        s.append("Answer the question. When you use private evidence, cite [M1], [M2], etc. Make it clear when a statement is only general knowledge.");return s.toString();
    }

    private static String extractText(JSONObject root){JSONArray cs=root.optJSONArray("candidates");if(cs==null||cs.length()==0)return"";JSONObject c=cs.optJSONObject(0);if(c==null)return"";JSONObject content=c.optJSONObject("content");if(content==null)return"";JSONArray parts=content.optJSONArray("parts");if(parts==null)return"";StringBuilder b=new StringBuilder();for(int i=0;i<parts.length();i++){JSONObject p=parts.optJSONObject(i);if(p==null)continue;String t=p.optString("text","");if(!t.isEmpty()){if(b.length()>0)b.append('\n');b.append(t);}}return b.toString();}
    private static String read(InputStream in)throws Exception{if(in==null)return"";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}
    private static String compact(String s){if(s==null)return"";String x=s.replaceAll("\\s+"," ").trim();return x.length()>500?x.substring(0,500)+"…":x;}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
}
