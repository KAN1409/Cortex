package com.kareem.cortex.rebuild;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Second-pass structural guard for live Situations.
 * One capture may contain several independent commitments; each becomes its own Situation.
 */
public final class SituationDecomposer {
    private static final String MODEL="openai/gpt-oss-120b";
    private static final String ENDPOINT="https://api.groq.com/openai/v1/chat/completions";
    private SituationDecomposer(){}

    public static List<Spec> decompose(Context context,CortexDb.AttachmentEvidence evidence,String transcript,String contextJson) throws Exception{
        String key=GroqKeyStore.get(context);if(key==null||key.trim().isEmpty())return new ArrayList<>();
        String prompt="You are Cortex Situation Decomposer. Convert one grounded capture into the minimum set of independent unresolved live situations. " +
                "Never combine unrelated commitments into one card. If the user says A, then B, then C, produce separate items unless they are genuinely one atomic action. " +
                "Preserve explicit people, times and verbs exactly enough to distinguish actions. Do not invent deadlines or people. Reuse a canonical_key from CURRENT_STATE only when it is clearly the same live issue. " +
                "Return one item if the capture is truly one situation. Return zero if it is not a live situation.\n\n"+
                "LOCAL_TIME: "+ZonedDateTime.now()+"\nTIME_ZONE: "+ZoneId.systemDefault().getId()+"\nEVIDENCE_ID: "+evidence.id+"\nCAPTURE:\n"+transcript+"\n\nCURRENT_STATE:\n"+(contextJson==null?"{}":contextJson);
        JSONObject item=objectSchema(props(
                "canonical_key",type("string"),
                "title",type("string"),
                "summary",type("string"),
                "attention",enumString("quiet","watching","needs_attention")
        ),"canonical_key","title","summary","attention");
        JSONObject schema=objectSchema(props("situations",new JSONObject().put("type","array").put("items",item)),"situations");
        JSONObject req=new JSONObject();req.put("model",MODEL);req.put("reasoning_effort","medium");req.put("reasoning_format","hidden");req.put("max_completion_tokens",1400);
        req.put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",prompt)));
        req.put("response_format",new JSONObject().put("type","json_schema").put("json_schema",new JSONObject().put("name","cortex_situation_decomposition").put("strict",true).put("schema",schema)));
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(12000);c.setReadTimeout(40000);c.setRequestProperty("Authorization","Bearer "+key.trim());c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
        try(OutputStream out=c.getOutputStream()){out.write(req.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();String response=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());c.disconnect();if(code<200||code>=300)throw new IOException("Situation decomposition HTTP "+code+": "+clip(response,300));
        JSONObject root=new JSONObject(response);JSONArray choices=root.optJSONArray("choices");JSONObject choice=choices==null||choices.length()==0?null:choices.optJSONObject(0);JSONObject message=choice==null?null:choice.optJSONObject("message");String content=message==null?"":message.optString("content","");if(content.trim().isEmpty())return new ArrayList<>();
        JSONArray arr=new JSONObject(content).optJSONArray("situations");ArrayList<Spec> out=new ArrayList<>();if(arr==null)return out;for(int i=0;i<arr.length();i++){JSONObject j=arr.optJSONObject(i);if(j==null)continue;String title=clean(j.optString("title")),summary=clean(j.optString("summary"));if(title.isEmpty()||summary.isEmpty())continue;String a=clean(j.optString("attention","quiet")).toLowerCase(Locale.ROOT);if(!a.equals("needs_attention")&&!a.equals("watching"))a="quiet";out.add(new Spec(clean(j.optString("canonical_key")),title,summary,a));if(out.size()>=8)break;}return out;
    }

    private static JSONObject type(String t)throws Exception{return new JSONObject().put("type",t);}private static JSONObject enumString(String...v)throws Exception{JSONArray a=new JSONArray();for(String x:v)a.put(x);return new JSONObject().put("type","string").put("enum",a);}private static JSONObject props(Object...p)throws Exception{JSONObject o=new JSONObject();for(int i=0;i+1<p.length;i+=2)o.put((String)p[i],p[i+1]);return o;}private static JSONObject objectSchema(JSONObject p,String...required)throws Exception{JSONArray r=new JSONArray();for(String x:required)r.put(x);return new JSONObject().put("type","object").put("properties",p).put("required",r).put("additionalProperties",false);}private static String read(InputStream in)throws Exception{if(in==null)return"";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[]buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}private static String clean(String s){return s==null?"":s.trim();}private static String clip(String s,int n){String x=clean(s).replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}
    public static final class Spec{public final String canonicalKey,title,summary,attention;public Spec(String canonicalKey,String title,String summary,String attention){this.canonicalKey=clean(canonicalKey);this.title=clean(title);this.summary=clean(summary);this.attention=clean(attention);}}
}
