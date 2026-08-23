package com.kareem.cortex;

import android.database.Cursor;

/**
 * Conservative learner over explicit Review Queue feedback.
 * Ordinary confirm/dismiss feedback needs repetition before changing policy.
 * "Ignore similar" is intentionally stronger and applies immediately.
 */
public final class AdaptiveRelevanceLearning {
    public static final String VERSION="relevance_learning_003";
    private AdaptiveRelevanceLearning(){}

    public static final class Profile {
        public final String source,candidateKind;
        public final int confirms,rejects,ignoreSimilar,total;
        public final double confirmRate;
        Profile(String source,String kind,int confirms,int rejects,int ignoreSimilar){this.source=n(source);this.candidateKind=n(kind);this.confirms=confirms;this.rejects=rejects;this.ignoreSimilar=ignoreSimilar;this.total=confirms+rejects;this.confirmRate=total<=0?0.5:((double)confirms/(double)total);}
        public boolean enough(){return total>=4;}
        public boolean strongPositive(){return total>=5&&confirmRate>=0.80;}
        public boolean strongNegative(){return total>=4&&confirmRate<=0.20;}
        public String summary(){return total+" feedback • "+confirms+" confirm • "+rejects+" reject"+(ignoreSimilar>0?" • "+ignoreSimilar+" ignore-similar":"");}
    }

    public static MasterRelevanceFilter.Decision adapt(VaultDb db,String source,MasterRelevanceFilter.Decision d){
        if(db==null||d==null)return d;String candidate=d.reviewable()?d.candidateKind:(d.durable()?d.disposition.name():"");if(candidate.isEmpty())return d;Profile p=profile(db,source,candidate);
        if(p.ignoreSimilar>0){if(d.reviewable())return new MasterRelevanceFilter.Decision(MasterRelevanceFilter.Disposition.CONTEXT,Math.min(35,d.importance),"explicit ignore-similar preference for this source and candidate kind","",0.92);if(d.durable())return new MasterRelevanceFilter.Decision(MasterRelevanceFilter.Disposition.REVIEW,Math.max(45,d.importance),"explicit ignore-similar preference conflicts with this durable guess • confirmation required",candidate,Math.min(0.60,d.confidence));}
        if(!p.enough())return d;
        if(d.reviewable()&&p.strongPositive()){MasterRelevanceFilter.Disposition target=parse(candidate);if(target!=null){double c=Math.max(0.83,Math.min(0.90,d.confidence+0.18));return new MasterRelevanceFilter.Decision(target,Math.max(58,d.importance),"learned from repeated confirmed similar reviews • "+p.summary(),"",c);}}
        if(d.reviewable()&&p.strongNegative())return new MasterRelevanceFilter.Decision(MasterRelevanceFilter.Disposition.CONTEXT,Math.min(39,d.importance),"learned from repeated rejected similar reviews • "+p.summary(),"",0.78);
        if(d.durable()&&p.strongNegative())return new MasterRelevanceFilter.Decision(MasterRelevanceFilter.Disposition.REVIEW,Math.max(48,d.importance),"historically rejected similar "+candidate.toLowerCase()+" items • confirmation required",candidate,Math.min(0.69,d.confidence));
        return d;
    }

    /** Indexed hot-path query; no JOIN and no per-row JSON parsing. */
    public static Profile profile(VaultDb db,String source,String candidateKind){
        CognitiveStore.ensure(db);String src=n(source),kind=n(candidateKind).toUpperCase();int confirm=0,reject=0,ignore=0;
        Cursor c=db.getReadableDatabase().query("feedback_events",new String[]{"event_type"},"candidate_kind=? AND source_key=?",new String[]{kind,src},null,null,"created_at DESC","50");
        while(c.moveToNext()){String event=n(c.getString(0));if("confirm".equals(event))confirm++;else if("dismiss".equals(event)||"not_action".equals(event)||"not_important".equals(event))reject++;else if("ignore_similar".equals(event)){reject++;ignore++;}}c.close();return new Profile(src,kind,confirm,reject,ignore);
    }

    private static MasterRelevanceFilter.Disposition parse(String x){try{return MasterRelevanceFilter.Disposition.valueOf(n(x).toUpperCase());}catch(Exception e){return null;}}
    private static String n(String s){return s==null?"":s.trim();}
}
