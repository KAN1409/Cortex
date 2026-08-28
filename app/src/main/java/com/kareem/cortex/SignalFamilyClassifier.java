package com.kareem.cortex;

/** Lightweight family classification only; never decides importance or final cognitive outcome. */
public final class SignalFamilyClassifier {
    private SignalFamilyClassifier(){}
    public static CognitiveSignalV2.SignalFamily classify(MasterRelevanceFilter.Signal signal){
        return CognitiveSignalV2.classify(signal);
    }
}
