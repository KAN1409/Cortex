package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class CortexOpenAiTeacher {
    public static final String VERSION="cortex_free_teacher_002";
    public static final String MODEL="openrouter/free";
    private static final String ENDPOINT="https://openrouter.ai/api/v1/chat/completions";
    private CortexOpenAiTeacher(){}

    public static final class Result {
        public final boolean ok;
        public final String policyVersion,error;
        public final long durationMs;
        Result(boolean ok,String policyVersion,String error,long durationMs){
            this.ok=ok;this.policyVersion=policyVersion==null?"":policyVersion;
            this.error=error==null?"":error;this.durationMs=durationMs;
        }
    }

    public static boolean configured(Context context){
        return context!=null&&OpenRouterKeyStore.has(context);
    }

    public static Result sync(Context context)throws Exception{
        Context app=context.getApplicationContext();
        if(!OpenRouterKeyStore.has(app))throw new IllegalStateException("OpenRouter key is not configured");
        String key=OpenRouterKeyStore.get(app);
        if(key==null||key.trim().isEmpty())throw new IllegalStateException("OpenRouter key is empty");
        long started=SystemClock.elapsedRealtime();
        VaultDb db=new VaultDb(app);
        try{
            JSONObject pack=CortexTeacherContextNormalizer.apply(CortexTeacherContext.build(app,db));
            String body=post(key,request(pack));
            JSONObject policy=parsePolicy(body);
            CortexPersonalPolicy.save(app,policy);
            return new Result(true,policy.optString("version","teacher"),"",SystemClock.elapsedRealtime()-started);
        }catch(Exception e){
            return new Result(false,"",e.getClass().getSimpleName()+": "+safe(e.getMessage()),SystemClock.elapsedRealtime()-started);
        }finally{
            try{db.close();}catch(Throwable ignored){}
        }
    }

    static JSONObject request(JSONObject contextPack)throws Exception{
        JSONObject schema=new JSONObject()
                .put("name","cortex_policy_pack")
                .put("strict",true)
                .put("schema",new JSONObject()
                        .put("type","object")
                        .put("additionalProperties",false)
                        .put("required",new JSONArray()
                                .put("version").put("ttlMs").put("attentionThreshold")
                                .put("maxNowItems").put("interruptionPenaltyScale")
                                .put("featureWeights").put("boosts").put("teacherNotes"))
                        .put("properties",new JSONObject()
                                .put("version",new JSONObject().put("type","string"))
                                .put("ttlMs",num(60000,2592000000L))
                                .put("attentionThreshold",num(0,1))
                                .put("maxNowItems",num(1,12).put("type","integer"))
                                .put("interruptionPenaltyScale",num(0,.55))
                                .put("featureWeights",new JSONObject()
                                        .put("type","object")
                                        .put("additionalProperties",num(0,.45)))
                                .put("boosts",new JSONObject()
                                        .put("type","array")
                                        .put("maxItems",40)
                                        .put("items",new JSONObject()
                                                .put("type","object")
                                                .put("additionalProperties",false)
                                                .put("required",new JSONArray().put("match").put("weight"))
                                                .put("properties",new JSONObject()
                                                        .put("match",new JSONObject().put("type","string"))
                                                        .put("weight",num(-1,1)))))
                                .put("teacherNotes",new JSONObject().put("type","string"))));

        String system="You are the bounded JUDGMENT teacher for Cortex. "
                +"Cortex alone owns evidence, canonical knowledge, world state, actions and execution. "
                +"Never invent or strengthen evidence. Never create canonical facts. "
                +"Never classify raw notifications. Never directly surface, suppress, or execute an item. "
                +"Return only a temporary conservative policy for CortexAttentionJudge. "
                +"Prefer silence when attention value does not justify interruption. "
                +"Keep changes modest unless recent user outcomes strongly justify them. "
                +"Policy must expire and be reversible.";

        JSONArray messages=new JSONArray()
                .put(new JSONObject().put("role","system").put("content",system))
                .put(new JSONObject().put("role","user").put("content",
                        "Teach Cortex FINAL JUDGMENT policy from this normalized Context Pack:\n"+contextPack.toString()));

        return new JSONObject()
                .put("model",MODEL)
                .put("messages",messages)
                .put("max_tokens",1800)
                .put("temperature",0.15)
                .put("response_format",new JSONObject().put("type","json_schema").put("json_schema",schema));
    }

    private static JSONObject num(double min,double max)throws Exception{
        return new JSONObject().put("type","number").put("minimum",min).put("maximum",max);
    }

    static JSONObject parsePolicy(String response)throws Exception{
        JSONObject root=new JSONObject(response);
        JSONArray choices=root.optJSONArray("choices");
        if(choices==null||choices.length()==0)throw new IllegalStateException("Teacher returned no choices");
        JSONObject message=choices.getJSONObject(0).optJSONObject("message");
        if(message==null)throw new IllegalStateException("Teacher returned no message");
        Object content=message.opt("content");
        String text="";
        if(content instanceof String)text=(String)content;
        else if(content instanceof JSONArray){
            JSONArray a=(JSONArray)content;
            for(int i=0;i<a.length();i++){
                JSONObject p=a.optJSONObject(i);
                if(p!=null&&!p.optString("text","").isEmpty()){text=p.optString("text");break;}
            }
        }
        text=text==null?"":text.trim();
        if(text.startsWith("```")){
            text=text.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
        }
        JSONObject policy=new JSONObject(text);
        validate(policy);
        if(policy.optLong("generatedAt",0)<=0)policy.put("generatedAt",System.currentTimeMillis());
        return policy;
    }

    static void validate(JSONObject p)throws Exception{
        if(p==null)throw new IllegalArgumentException("Missing policy");
        String version=p.optString("version","").trim();
        if(version.isEmpty())throw new IllegalArgumentException("Missing policy version");
        double threshold=p.optDouble("attentionThreshold",Double.NaN);
        double interruption=p.optDouble("interruptionPenaltyScale",Double.NaN);
        int max=p.optInt("maxNowItems",-1);
        long ttl=p.optLong("ttlMs",-1);
        if(Double.isNaN(threshold)||threshold<0||threshold>1)throw new IllegalArgumentException("Invalid attention threshold");
        if(Double.isNaN(interruption)||interruption<0||interruption>.55)throw new IllegalArgumentException("Invalid interruption penalty");
        if(max<1||max>12)throw new IllegalArgumentException("Invalid maxNowItems");
        if(ttl<60000||ttl>30L*24L*60L*60L*1000L)throw new IllegalArgumentException("Invalid policy ttl");
    }

    private static String post(String key,JSONObject req)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        try{
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            c.setRequestProperty("Content-Type","application/json");
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("Authorization","Bearer "+key);
            c.setRequestProperty("X-Title","Cortex Free Teacher");
            try(OutputStream out=c.getOutputStream()){
                out.write(req.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code=c.getResponseCode();
            String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());
            if(code<200||code>=300)throw new IllegalStateException("Teacher HTTP "+code+": "+clip(body,500));
            return body;
        }finally{
            try{c.disconnect();}catch(Throwable ignored){}
        }
    }

    private static String read(InputStream in)throws Exception{
        if(in==null)return "";
        try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];
            for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);
            return b.toString("UTF-8");
        }
    }

    private static String clip(String s,int n){String x=safe(s);return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String safe(String s){return s==null?"":s;}
}
