package com.kareem.cortex;

import android.content.Context;

/** Local llama.cpp-backed Qwen provider. One process-wide model handle is serialized by LocalLlmBridge. */
public final class LocalQwenBrain implements CortexBrain {
    private final Context app;
    public LocalQwenBrain(Context context){this.app=context==null?null:context.getApplicationContext();}

    @Override public BrainCompletion classify(BrainRequest input)throws BrainException{return run(input);}
    @Override public BrainCompletion synthesizePulse(BrainRequest input)throws BrainException{return run(input);}
    @Override public BrainCompletion answer(BrainRequest input)throws BrainException{return run(input);}

    private BrainCompletion run(BrainRequest input)throws BrainException{
        if(app==null)throw new BrainException("LOCAL_CONTEXT_MISSING","Android context unavailable");
        if(!LocalModelManager.installed(app))throw new BrainException("LOCAL_MODEL_MISSING","Verified local Qwen model/runtime is unavailable");
        if(!LocalBrainRuntimePolicy.thermalAllowsInference(app))throw new BrainException("THERMAL_PAUSED","Device thermal state is too high for local inference");
        try{
            long started=System.currentTimeMillis();
            LocalLlmBridge.CompletionResult r=LocalLlmBridge.completeCached(LocalModelManager.modelFile(app).getAbsolutePath(),input.userPrompt,input.systemPrompt,input.maxTokens);
            long latency=Math.max(r.getDurationMs(),System.currentTimeMillis()-started);
            return new BrainCompletion(r.getText(),provider(),model(),latency,r.getTokensGenerated(),r.getTokensPerSecond(),r.getCacheHit());
        }catch(Throwable t){throw new BrainException("LOCAL_INFERENCE_FAILED",t.getClass().getSimpleName()+": "+(t.getMessage()==null?"":t.getMessage()),t);}
    }

    @Override public boolean isAvailable(){return app!=null&&LocalModelManager.installed(app)&&LocalBrainRuntimePolicy.thermalAllowsInference(app);}
    @Override public String provider(){return"LOCAL";}
    @Override public String model(){return LocalModelManager.MODEL_NAME;}
}
