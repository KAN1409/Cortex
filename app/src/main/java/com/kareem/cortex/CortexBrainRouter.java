package com.kareem.cortex;

import android.content.Context;
import java.util.Collections;

/** Local-first provider router. Semantic confidence is validated before optional escalation. */
public final class CortexBrainRouter {
    private final LocalQwenBrain local;
    private final DeepQwenBrain deep;

    public CortexBrainRouter(Context context){local=new LocalQwenBrain(context);deep=new DeepQwenBrain(context);}

    /** Canonical typed V2 route. */
    public RoutedCognitiveResult classify(CognitiveInput input,boolean allowRemote)throws BrainException{
        CognitiveResult localResult=local.classify(input);
        if(localResult.confidence>=LocalBrainConfig.ACCEPT_LOCAL_CONFIDENCE)
            return new RoutedCognitiveResult(localResult,local.provider(),local.model(),false);
        if(localResult.confidence>=LocalBrainConfig.TRY_DEEP_CONFIDENCE&&allowRemote&&deep.isAvailable()){
            CognitiveResult deepResult=deep.classify(input);
            if(deepResult.confidence<LocalBrainConfig.TRY_DEEP_CONFIDENCE)
                deepResult=asReview(deepResult,"Deep Brain remained below confidence threshold");
            return new RoutedCognitiveResult(deepResult,deep.provider(),deep.model(),true);
        }
        return new RoutedCognitiveResult(asReview(localResult,allowRemote?"Deep Brain unavailable or confidence below routing threshold":"Remote escalation blocked by policy"),local.provider(),local.model(),false);
    }

    public BrainCompletion classifyLocal(BrainRequest request)throws BrainException{return local.classify(request);}

    /** Migration-compatible completion route used by older V2 internals until typed apply is wired. */
    public BrainCompletion routeAfterLocal(BrainRequest request,BrainCompletion localResult,double localConfidence,boolean allowRemote)throws BrainException{
        if(localResult==null)throw new BrainException("LOCAL_RESULT_MISSING","Local brain produced no completion");
        if(localConfidence>=LocalBrainConfig.ACCEPT_LOCAL_CONFIDENCE)return localResult;
        if(localConfidence>=LocalBrainConfig.TRY_DEEP_CONFIDENCE&&allowRemote&&deep.isAvailable())return deep.classify(request);
        return localResult;
    }

    public boolean deepAvailable(){return deep.isAvailable();}
    public String deepModel(){return deep.model();}

    private static CognitiveResult asReview(CognitiveResult r,String reason){
        String why=r==null?reason:((r.reason==null||r.reason.trim().isEmpty())?reason:r.reason+" · "+reason);
        double confidence=r==null?0:r.confidence;
        return new CognitiveResult(CognitiveDisposition.REVIEW,confidence,why,Collections.<CognitiveItem>emptyList());
    }

    public static final class RoutedCognitiveResult{
        public final CognitiveResult result;public final String provider,model;public final boolean escalated;
        RoutedCognitiveResult(CognitiveResult result,String provider,String model,boolean escalated){this.result=result;this.provider=provider;this.model=model;this.escalated=escalated;}
    }
}
