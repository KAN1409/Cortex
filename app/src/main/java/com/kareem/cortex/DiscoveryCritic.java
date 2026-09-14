package com.kareem.cortex;

/** Rejects noise before a discovery can become feed-eligible. */
public final class DiscoveryCritic {
    private DiscoveryCritic(){}

    public static Verdict judge(String family,String title,String body,int evidenceCount,double confidence,double score){
        if(DiscoveryPolicy.isTrivial(title,body))return new Verdict(false,"trivial or raw-statistic output");
        if(confidence<0.58)return new Verdict(false,"insufficient confidence");
        if(score<0.52)return new Verdict(false,"insufficient expected user value");
        if(evidenceCount<1)return new Verdict(false,"no traceable evidence");
        if(("CONTRADICTION".equals(family)||"UNEXPECTED_CONNECTION".equals(family)||"MEANINGFUL_CHANGE".equals(family))&&evidenceCount<2)
            return new Verdict(false,"cross-evidence discovery requires at least two evidence items");
        return new Verdict(true,"novel, evidence-backed candidate");
    }

    public static final class Verdict{
        public final boolean keep;public final String reason;
        Verdict(boolean k,String r){keep=k;reason=r;}
    }
}
