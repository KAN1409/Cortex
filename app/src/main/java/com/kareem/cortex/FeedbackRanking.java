package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashMap;
import java.util.Map;

/**
 * Bounded learning from NOW feedback (Useful / Acted / Not useful / Wrong / Already knew).
 *
 * Feedback is pooled per finding family within a domain (e.g. PRICE_CHANGE in WORK). Each event's
 * stored weight fades with a 30-day half-life, the faded sum is squashed with tanh, and the result
 * moves a finding's ranking score by at most +/-30%. It only reorders NOW: it never changes the
 * quality gate, so feedback cannot hide a category outright or push weak findings into NOW.
 */
public final class FeedbackRanking {
    static final double CAP=0.30;
    static final double HALF_LIFE_MS=30d*24d*60d*60d*1000d;
    /** About three net "useful" taps reach ~76% of the cap; one tap moves the score ~10%. */
    static final double SCALE=3.0;
    static final long HORIZON_MS=180L*24L*60L*60L*1000L;
    private FeedbackRanking(){}

    static String key(String family,String domain){return (family==null?"":family)+"|"+(domain==null?"":domain);}

    /** Faded contribution of one feedback event. Future timestamps (clock changes) count as now. */
    static double faded(double weight,long createdAt,long now){
        if(!Double.isFinite(weight))return 0;
        long age=Math.max(0,now-createdAt);
        return weight*Math.pow(0.5,age/HALF_LIFE_MS);
    }

    /** Ranking multiplier in [1-CAP, 1+CAP] for an accumulated faded sum. */
    static double multiplier(double fadedSum){
        if(!Double.isFinite(fadedSum))return 1;
        return 1+CAP*Math.tanh(fadedSum/SCALE);
    }

    /** Multiplier per family|domain from stored feedback; missing keys mean 1.0. */
    public static Map<String,Double> load(SQLiteDatabase db,long now){
        HashMap<String,Double> sums=new HashMap<>();
        try(Cursor c=db.rawQuery("SELECT f.family,COALESCE(i.domain,''),f.weight,f.created_at FROM discovery_v3_feedback f "+
                "LEFT JOIN discovery_v3_insights i ON i.id=f.insight_id WHERE f.created_at>=?",new String[]{String.valueOf(now-HORIZON_MS)})){
            while(c.moveToNext()){
                String k=key(c.getString(0),c.getString(1));
                Double prev=sums.get(k);
                sums.put(k,(prev==null?0:prev)+faded(c.getDouble(2),c.getLong(3),now));
            }
        }
        HashMap<String,Double> out=new HashMap<>();
        for(Map.Entry<String,Double> e:sums.entrySet())out.put(e.getKey(),multiplier(e.getValue()));
        return out;
    }

    static double adjusted(Map<String,Double> multipliers,String family,String domain,double score){
        Double m=multipliers==null?null:multipliers.get(key(family,domain));
        return score*(m==null?1:m);
    }
}
