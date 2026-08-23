package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;

/**
 * Conservative v1 learner over explicit Review Queue feedback.
 *
 * It deliberately does not train a model or mutate prompts after one click. Instead it
 * aggregates repeated user decisions for the same source + candidate kind and only then
 * adjusts the deterministic relevance boundary.
 */
public final class AdaptiveRelevanceLearning {
    public static final String VERSION="relevance_learning_001";
    private static final int MAX_EVENTS=500;
    private AdaptiveRelevanceLearning(){}

    public static final class Profile {
        public final String source,candidateKind;
        public final int confirms,rejects,ignoreSimilar,total;
        public final double confirmRate;
        Profile(String source,String kind,int confirms,int rejects,int ignoreSimilar){
            this.source=n(source);this.candidateKind=n(kind);this.confirms=confirms;this.rejects=rejects;this.ignoreSimilar=ignoreSimilar;this.total=confirms+rejects;this.confirmRate=total<=0?0.5:((double)confirms/(double)total);
        }
        public boolean enough(){return total>=4;}
        public boolean strongPositive(){return total>=5&&confirmRate>=0.80;}
        public boolean strongNegative(){return (total>=4&&confirmRate<=0.20)||ignoreSimilar>=2;}
        public String summary(){return total+" feedback • "+confirms+" confirm • "+rejects+" reject"+(ignoreSimilar>0?" • "+ignoreSimilar+" ignore-similar":"");}
    }

    /**
     * Apply learned behavior without bypassing safety. Positive history can only promote an
     * existing REVIEW candidate. Negative history may demote REVIEW or force a durable guess
     * back through REVIEW. It never turns arbitrary CONTEXT into an action by itself.
     */
    public static MasterRelevanceFilter.Decision adapt(VaultDb db,String source,MasterRelevanceFilter.Decision d){
        if(db==null||d==null)return d;
        String candidate=d.reviewable()?d.candidateKind:(d.durable()?d.disposition.name():"");
        if(candidate.isEmpty())return d;
        Profile p=profile(db,source,candidate);
        if(!p.enough())return d;

        if(d.reviewable()&&p.strongPositive()){
            MasterRelevanceFilter.Disposition target=parse(candidate);
            if(target!=null){
                double c=Math.max(0.83,Math.min(0.90,d.confidence+0.18));
                return new MasterRelevanceFilter.Decision(target,Math.max(58,d.importance),"learned from repeated confirmed similar reviews • "+p.summary(),"",c);
            }
        }
        if(d.reviewable()&&p.strongNegative()){
            return new MasterRelevanceFilter.Decision(MasterRelevanceFilter.Disposition.CONTEXT,Math.min(39,d.importance),"learned from repeated rejected similar reviews • "+p.summary(),"",0.78);
        }
        if(d.durable()&&p.strongNegative()){
            // A deterministic high-confidence phrase can still be wrong for this source/user.
            // Route it through Review rather than silently suppressing it.
            return new MasterRelevanceFilter.Decision(MasterRelevanceFilter.Disposition.REVIEW,Math.max(48,d.importance),"historically rejected similar "+candidate.toLowerCase()+" items • confirmation required",candidate,Math.min(0.69,d.confidence));
        }
        return d;
    }

    public static Profile profile(VaultDb db,String source,String candidateKind){
        CognitiveStore.ensure(db);String wantedSource=n(source),wantedKind=n(candidateKind).toUpperCase();int confirm=0,reject=0,ignore=0,seen=0;
        String sql="SELECT f.event_type,d.metadata_json FROM feedback_events f JOIN derived_items d ON d.id=f.target_id WHERE f.target_type='derived' AND d.kind='REVIEW' ORDER BY f.created_at DESC LIMIT "+MAX_EVENTS;
        Cursor c=db.getReadableDatabase().rawQuery(sql,null);
        while(c.moveToNext()){
            String event=n(c.getString(0)),meta=n(c.getString(1));String src="",kind="";
            try{JSONObject o=new JSONObject(meta);src=o.optString("source","");kind=o.optString("candidate_kind","").toUpperCase();}catch(Exception ignored){}
            if(!wantedKind.equals(kind))continue;
            if(!wantedSource.isEmpty()&&!wantedSource.equals(src))continue;
            if("confirm".equals(event)){confirm++;seen++;}
            else if("dismiss".equals(event)||"not_action".equals(event)||"not_important".equals(event)){reject++;seen++;}
            else if("ignore_similar".equals(event)){reject++;ignore++;seen++;}
            if(seen>=50)break;
        }
        c.close();return new Profile(wantedSource,wantedKind,confirm,reject,ignore);
    }

    private static MasterRelevanceFilter.Disposition parse(String x){try{return MasterRelevanceFilter.Disposition.valueOf(n(x).toUpperCase());}catch(Exception e){return null;}}
    private static String n(String s){return s==null?"":s.trim();}
}
