package com.kareem.cortex;

import android.content.Context;

/** Local-first provider router. Semantic confidence is validated outside providers before escalation. */
public final class CortexBrainRouter {
    private final LocalQwenBrain local;
    private final DeepQwenBrain deep;

    public CortexBrainRouter(Context context){local=new LocalQwenBrain(context);deep=new DeepQwenBrain(context);}

    public BrainCompletion classifyLocal(BrainRequest request)throws BrainException{return local.classify(request);}

    /**
     * Returns the local result when it is reliable. Mid-confidence results may escalate to the
     * optional Deep Qwen server. Low-confidence results never burn remote work and must become
     * REVIEW/CONTEXT at the validated apply layer.
     */
    public BrainCompletion routeAfterLocal(BrainRequest request,BrainCompletion localResult,double localConfidence,boolean allowRemote)throws BrainException{
        if(localResult==null)throw new BrainException("LOCAL_RESULT_MISSING","Local brain produced no completion");
        if(localConfidence>=LocalBrainConfig.ACCEPT_LOCAL_CONFIDENCE)return localResult;
        if(localConfidence>=LocalBrainConfig.TRY_DEEP_CONFIDENCE&&allowRemote&&deep.isAvailable())return deep.classify(request);
        return localResult;
    }

    public boolean deepAvailable(){return deep.isAvailable();}
    public String deepModel(){return deep.model();}
}
