package com.kareem.cortex;

import android.content.Context;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

/** Runtime capability probe only. No Cortex cloud API key and no paid inference. */
public final class GeminiNanoProbe {
    public interface Callback{void done(Result r);} public static final class Result{public final boolean supported;public final int status;public final String label,detail;Result(boolean s,int st,String l,String d){supported=s;status=st;label=l;detail=d;}}
    private GeminiNanoProbe(){}
    public static void run(Context c,Callback cb){new Thread(()->{try{GenerativeModelFutures m=GenerativeModelFutures.from(Generation.INSTANCE.getClient());int s=m.checkStatus().get();String l;boolean ok=true;if(s==FeatureStatus.AVAILABLE)l="AVAILABLE";else if(s==FeatureStatus.DOWNLOADABLE)l="DOWNLOADABLE";else if(s==FeatureStatus.DOWNLOADING)l="DOWNLOADING";else if(s==FeatureStatus.UNAVAILABLE){l="UNAVAILABLE";ok=false;}else l="STATUS_"+s;cb.done(new Result(ok,s,l,"ML Kit Prompt API / AICore checked locally."));}catch(Throwable t){cb.done(new Result(false,-1,"PROBE_FAILED",t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage())));}},"cortex-nano-probe").start();}
}
