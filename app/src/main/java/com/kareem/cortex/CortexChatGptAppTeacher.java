package com.kareem.cortex;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CortexChatGptAppTeacher {
    public static final String VERSION="cortex_chatgpt_app_teacher_001";
    private static final String CHATGPT_PACKAGE="com.openai.chatgpt";
    private static final String PREF="cortex_chatgpt_app_teacher";
    private static final String KEY_PENDING="pending";
    private static final String KEY_PROMPT="prompt";
    private static final String KEY_LAUNCHED_AT="launched_at";

    private CortexChatGptAppTeacher(){}

    public static final class ImportResult {
        public final boolean ok;
        public final String version,error;
        ImportResult(boolean ok,String version,String error){
            this.ok=ok;
            this.version=version==null?"":version;
            this.error=error==null?"":error;
        }
    }

    public static String buildPrompt(Context context)throws Exception{
        Context app=context.getApplicationContext();
        VaultDb db=new VaultDb(app);
        try{
            JSONObject pack=CortexTeacherContextNormalizer.apply(CortexTeacherContext.build(app,db));
            String contextText=pack.toString();
            if(contextText.length()>70000)contextText=contextText.substring(0,70000);

            return "You are ChatGPT acting as the bounded JUDGMENT teacher for Cortex.\n\n"
                    +"IMPORTANT CONTRACT\n"
                    +"Cortex alone owns evidence, canonical knowledge, world state, actions, and execution.\n"
                    +"Do not invent or strengthen evidence.\n"
                    +"Do not create canonical facts.\n"
                    +"Do not classify raw notifications.\n"
                    +"Do not directly surface, suppress, or execute any item.\n"
                    +"Your only job is to teach a temporary, conservative Policy Pack for CortexAttentionJudge.\n"
                    +"Prefer silence when attention value does not justify interruption.\n"
                    +"Keep changes modest unless recent outcomes strongly justify them.\n\n"
                    +"RETURN FORMAT\n"
                    +"Return ONLY one raw JSON object. No markdown and no explanation before or after it.\n"
                    +"The object MUST contain exactly these top-level fields:\n"
                    +"{\n"
                    +"  \"version\": \"chatgpt-teacher-<short-name>\",\n"
                    +"  \"ttlMs\": 604800000,\n"
                    +"  \"attentionThreshold\": 0.72,\n"
                    +"  \"maxNowItems\": 7,\n"
                    +"  \"interruptionPenaltyScale\": 0.24,\n"
                    +"  \"featureWeights\": {},\n"
                    +"  \"boosts\": [],\n"
                    +"  \"teacherNotes\": \"brief reason for the policy\"\n"
                    +"}\n\n"
                    +"BOUNDS\n"
                    +"ttlMs: 60000..2592000000\n"
                    +"attentionThreshold: 0..1\n"
                    +"maxNowItems: 1..12\n"
                    +"interruptionPenaltyScale: 0..0.55\n"
                    +"featureWeights values: 0..0.45\n"
                    +"boost weight: -1..1\n"
                    +"boosts max 40\n\n"
                    +"NORMALIZED CORTEX CONTEXT PACK\n"
                    +contextText;
        }finally{
            try{db.close();}catch(Throwable ignored){}
        }
    }

    public static boolean launch(Activity activity)throws Exception{
        String prompt=buildPrompt(activity);
        ClipboardManager cm=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Cortex Teacher Prompt",prompt));

        activity.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_PENDING,true)
                .putString(KEY_PROMPT,prompt)
                .putLong(KEY_LAUNCHED_AT,System.currentTimeMillis())
                .apply();

        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,prompt);
        send.setPackage(CHATGPT_PACKAGE);

        PackageManager pm=activity.getPackageManager();
        if(send.resolveActivity(pm)!=null){
            activity.startActivity(send);
            return true;
        }

        Intent launch=pm.getLaunchIntentForPackage(CHATGPT_PACKAGE);
        if(launch!=null){
            activity.startActivity(launch);
            return true;
        }

        Intent generic=new Intent(Intent.ACTION_SEND);
        generic.setType("text/plain");
        generic.putExtra(Intent.EXTRA_TEXT,prompt);
        activity.startActivity(Intent.createChooser(generic,"Open with ChatGPT"));
        return false;
    }

    public static boolean pending(Context context){
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(KEY_PENDING,false);
    }

    public static long launchedAt(Context context){
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_LAUNCHED_AT,0);
    }

    public static ImportResult importClipboardIfReady(Context context){
        if(!pending(context))return new ImportResult(false,"","No pending ChatGPT teacher request");
        ClipboardManager cm=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm==null||!cm.hasPrimaryClip())return new ImportResult(false,"","Clipboard is empty");
        ClipData clip=cm.getPrimaryClip();
        if(clip==null||clip.getItemCount()==0)return new ImportResult(false,"","Clipboard is empty");
        CharSequence cs=clip.getItemAt(0).coerceToText(context);
        String text=cs==null?"":cs.toString().trim();
        String prompt=context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_PROMPT,"");
        if(text.isEmpty()||text.equals(prompt))return new ImportResult(false,"","Waiting for ChatGPT policy");
        return importText(context,text);
    }

    public static ImportResult importText(Context context,String raw){
        try{
            JSONObject policy=extractPolicy(raw);
            validate(policy);
            if(policy.optLong("generatedAt",0)<=0)policy.put("generatedAt",System.currentTimeMillis());
            CortexPersonalPolicy.save(context.getApplicationContext(),policy);
            context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_PENDING,false)
                    .remove(KEY_PROMPT)
                    .apply();
            return new ImportResult(true,policy.optString("version","chatgpt-teacher"),"");
        }catch(Throwable e){
            return new ImportResult(false,"",e.getClass().getSimpleName()+": "+safe(e.getMessage()));
        }
    }

    static JSONObject extractPolicy(String raw)throws Exception{
        String x=safe(raw).trim();
        int first=x.indexOf('{');
        int last=x.lastIndexOf('}');
        if(first<0||last<=first)throw new IllegalArgumentException("No JSON Policy Pack found");
        JSONObject root=new JSONObject(x.substring(first,last+1));
        JSONObject nested=root.optJSONObject("policy");
        return nested!=null?nested:root;
    }

    static void validate(JSONObject p)throws Exception{
        if(p==null)throw new IllegalArgumentException("Missing policy");
        String version=p.optString("version","").trim();
        if(version.isEmpty())throw new IllegalArgumentException("Missing version");

        double threshold=p.optDouble("attentionThreshold",Double.NaN);
        double interruption=p.optDouble("interruptionPenaltyScale",Double.NaN);
        int max=p.optInt("maxNowItems",-1);
        long ttl=p.optLong("ttlMs",-1);

        if(Double.isNaN(threshold)||threshold<0||threshold>1)throw new IllegalArgumentException("attentionThreshold outside bounds");
        if(Double.isNaN(interruption)||interruption<0||interruption>.55)throw new IllegalArgumentException("interruptionPenaltyScale outside bounds");
        if(max<1||max>12)throw new IllegalArgumentException("maxNowItems outside bounds");
        if(ttl<60000||ttl>30L*24L*60L*60L*1000L)throw new IllegalArgumentException("ttlMs outside bounds");

        JSONObject weights=p.optJSONObject("featureWeights");
        if(weights==null)throw new IllegalArgumentException("Missing featureWeights");
        java.util.Iterator<String> keys=weights.keys();
        while(keys.hasNext()){
            String k=keys.next();
            double v=weights.optDouble(k,Double.NaN);
            if(Double.isNaN(v)||v<0||v>.45)throw new IllegalArgumentException("feature weight outside bounds: "+k);
        }

        JSONArray boosts=p.optJSONArray("boosts");
        if(boosts==null)throw new IllegalArgumentException("Missing boosts");
        if(boosts.length()>40)throw new IllegalArgumentException("Too many boosts");
        for(int i=0;i<boosts.length();i++){
            JSONObject b=boosts.optJSONObject(i);
            if(b==null)throw new IllegalArgumentException("Invalid boost");
            double w=b.optDouble("weight",Double.NaN);
            if(Double.isNaN(w)||w<-1||w>1)throw new IllegalArgumentException("Boost weight outside bounds");
        }
    }

    private static String safe(String s){return s==null?"":s;}
}
