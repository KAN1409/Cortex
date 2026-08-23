package com.kareem.cortex;

import android.content.*;

/** Truthful runtime readiness: only READY after loading the verified GGUF and producing a real local inference. */
public final class LocalLlmRuntime {
    private static final String PREF="cortex_local_runtime";
    private static final String K_STATE="state",K_ERROR="error",K_TEXT="self_test_text",K_INFO="system_info",K_TPS="tokens_per_second",K_TOKENS="tokens_generated",K_DURATION="duration_ms",K_TESTED="tested_at",K_MODEL_SHA="model_sha",K_AUTO="auto_started_v43";
    private LocalLlmRuntime(){}

    public interface Callback{void done(State state);}

    public static State state(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);String st=p.getString(K_STATE,"not_tested");
        if("ready".equals(st)&&!LocalModelManager.SHA256.equalsIgnoreCase(p.getString(K_MODEL_SHA,"")))st="not_tested";
        return new State(st,p.getString(K_ERROR,""),p.getString(K_TEXT,""),p.getString(K_INFO,""),p.getFloat(K_TPS,0f),p.getInt(K_TOKENS,0),p.getLong(K_DURATION,0),p.getLong(K_TESTED,0));
    }
    public static boolean ready(Context c){return "ready".equals(state(c).state)&&LocalModelManager.verified(c);}
    public static boolean testing(Context c){return "testing".equals(state(c).state);}
    public static String runtimeVersion(){return LocalLlmBridge.RUNTIME_VERSION;}

    public static void maybeAutoSelfTest(Context c,Callback cb){
        if(!LocalModelManager.verified(c)||ready(c)||testing(c))return;SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);if(p.getBoolean(K_AUTO,false))return;p.edit().putBoolean(K_AUTO,true).apply();runSelfTest(c,cb);
    }

    public static void runSelfTest(Context c,Callback cb){
        Context app=c.getApplicationContext();if(!LocalModelManager.verified(app)){if(cb!=null)cb.done(state(app));return;}
        app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(K_STATE,"testing").putString(K_ERROR,"").apply();
        new Thread(()->{
            long at=System.currentTimeMillis();LocalLlmBridge.SelfTestResult r;
            try{r=LocalLlmBridge.selfTest(LocalModelManager.modelFile(app).getAbsolutePath());}
            catch(Throwable t){r=null;SharedPreferences.Editor e=app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(K_STATE,"failed").putString(K_ERROR,t.getClass().getSimpleName()+": "+safe(t.getMessage())).putLong(K_TESTED,at).putString(K_MODEL_SHA,LocalModelManager.SHA256);e.apply();if(cb!=null)cb.done(state(app));return;}
            SharedPreferences.Editor e=app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();e.putString(K_STATE,r.getOk()?"ready":"failed");e.putString(K_ERROR,safe(r.getError()));e.putString(K_TEXT,safeLong(r.getText(),1200));e.putString(K_INFO,safeLong(r.getSystemInfo(),2400));e.putFloat(K_TPS,r.getTokensPerSecond());e.putInt(K_TOKENS,r.getTokensGenerated());e.putLong(K_DURATION,r.getDurationMs());e.putLong(K_TESTED,System.currentTimeMillis());e.putString(K_MODEL_SHA,LocalModelManager.SHA256);e.apply();if(cb!=null)cb.done(state(app));
        },"cortex-local-self-test").start();
    }

    public static void invalidate(Context c){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().clear().apply();}
    private static String safe(String s){return s==null?"":(s.length()>240?s.substring(0,240):s);}
    private static String safeLong(String s,int n){return s==null?"":(s.length()>n?s.substring(0,n):s);}

    public static final class State{
        public final String state,error,selfTestText,systemInfo;public final float tokensPerSecond;public final int tokensGenerated;public final long durationMs,testedAt;
        State(String s,String e,String t,String i,float tps,int tok,long d,long at){state=s;error=e;selfTestText=t;systemInfo=i;tokensPerSecond=tps;tokensGenerated=tok;durationMs=d;testedAt=at;}
        public String label(){if("ready".equals(state))return"Installed • Verified • Local inference ready";if("testing".equals(state))return"Loading model + running local self-test";if("failed".equals(state))return"Runtime self-test failed";return"Runtime installed in APK • self-test pending";}
    }
}
