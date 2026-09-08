package com.kareem.cortex;

import android.content.Context;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

/** Runtime-only Gemini Nano/AICore capability probe. No cloud API key and no paid inference. */
public final class GeminiNanoProbe {
    public interface Callback { void done(Result result); }
    public static final class Result {
        public final boolean supported;
        public final int status;
        public final String label;
        public final String detail;
        Result(boolean supported,int status,String label,String detail){this.supported=supported;this.status=status;this.label=label;this.detail=detail;}
    }
    private GeminiNanoProbe(){}

    public static void run(Context context, Callback callback){
        new Thread(()->{
            try{
                GenerativeModelFutures model=GenerativeModelFutures.from(Generation.INSTANCE.getClient());
                int status=model.checkStatus().get();
                String label;
                boolean supported=true;
                if(status==FeatureStatus.AVAILABLE)label="AVAILABLE";
                else if(status==FeatureStatus.DOWNLOADABLE)label="DOWNLOADABLE";
                else if(status==FeatureStatus.DOWNLOADING)label="DOWNLOADING";
                else if(status==FeatureStatus.UNAVAILABLE){label="UNAVAILABLE";supported=false;}
                else label="STATUS_"+status;
                callback.done(new Result(supported,status,label,
                        "ML Kit Prompt API / AICore status checked locally. No remote Cortex API was called."));
            }catch(Throwable t){
                callback.done(new Result(false,-1,"PROBE_FAILED",t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage())));
            }
        },"cortex-nano-probe").start();
    }
}
