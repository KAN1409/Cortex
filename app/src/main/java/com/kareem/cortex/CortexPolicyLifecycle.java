package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/**
 * Safe lifecycle for teacher policies.
 *
 * Proposed policies are never allowed to silently replace the last known-good policy.
 * Cortex keeps a full rollback snapshot and starts accepted teacher policies in a bounded canary.
 */
public final class CortexPolicyLifecycle {
    public static final String VERSION="cortex_policy_lifecycle_001";
    private static final String PREF="cortex_policy_lifecycle";
    private static final String PREVIOUS="previous_policy";
    private static final String CANDIDATE="candidate_policy";
    private static final String STATE="state";
    private static final String REASON="reason";
    private static final String STARTED="started_at";
    private static final long CANARY_MS=24L*60L*60L*1000L;

    public enum Activation {
        CANARY,
        HELD
    }

    public static final class Result {
        public final Activation activation;
        public final String reason;
        Result(Activation activation,String reason){
            this.activation=activation;
            this.reason=reason==null?"":reason;
        }
    }

    private CortexPolicyLifecycle(){}

    private static SharedPreferences p(Context c){
        return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }

    public static Result stage(Context context,JSONObject current,JSONObject proposed,JSONObject impact){
        if(context==null||proposed==null)return new Result(Activation.HELD,"missing proposed policy");
        Context app=context.getApplicationContext();

        int candidates=impact==null?0:impact.optInt("candidateCount",0);
        int promoted=impact==null?0:impact.optInt("promoted",0);
        int deferred=impact==null?0:impact.optInt("deferred",0);
        int beforeNow=impact==null?0:impact.optInt("beforeNow",0);
        int afterNow=impact==null?0:impact.optInt("afterNow",0);
        double avgDelta=impact==null?0:impact.optDouble("averageScoreDelta",0);

        boolean explosivePromotion=candidates>=6 && promoted>Math.max(2,(int)Math.ceil(candidates*.35));
        boolean attentionExplosion=beforeNow>=2 && afterNow>Math.max(beforeNow+3,(int)Math.ceil(beforeNow*1.75));
        boolean scoreShock=Math.abs(avgDelta)>.28 && candidates>=6;

        try{
            SharedPreferences.Editor e=p(app).edit()
                    .putString(CANDIDATE,proposed.toString())
                    .putLong(STARTED,System.currentTimeMillis());

            if(explosivePromotion||attentionExplosion||scoreShock){
                String reason=explosivePromotion?"too many candidates promoted":
                        (attentionExplosion?"Now count expands too aggressively":"average judgment score changed too sharply");
                e.putString(STATE,"HELD").putString(REASON,reason).apply();
                return new Result(Activation.HELD,reason);
            }

            if(current!=null&&current.length()>0)e.putString(PREVIOUS,current.toString());
            e.putString(STATE,"CANARY").putString(REASON,
                    "bounded 24h canary; previous policy retained for rollback").apply();
            return new Result(Activation.CANARY,"bounded 24h canary");
        }catch(Throwable t){
            return new Result(Activation.HELD,"lifecycle persistence failed");
        }
    }

    public static boolean rollback(Context context,String reason){
        if(context==null)return false;
        try{
            String raw=p(context).getString(PREVIOUS,"");
            if(raw==null||raw.trim().isEmpty())return false;
            JSONObject previous=new JSONObject(raw);
            CortexPersonalPolicy.saveWithoutLifecycle(context.getApplicationContext(),previous);
            p(context).edit()
                    .putString(STATE,"ROLLED_BACK")
                    .putString(REASON,reason==null?"manual rollback":reason)
                    .apply();
            return true;
        }catch(Throwable ignored){return false;}
    }

    public static boolean rollbackIfExpired(Context context,JSONObject active){
        if(context==null||active==null)return false;
        SharedPreferences s=p(context);
        if(!"CANARY".equals(s.getString(STATE,"")))return false;
        long started=s.getLong(STARTED,0);
        if(started<=0||System.currentTimeMillis()-started<=CANARY_MS)return false;

        // A completed canary becomes the new stable policy. Keep previous only as a manual rollback point.
        s.edit().putString(STATE,"STABLE")
                .putString(REASON,"24h canary completed")
                .apply();
        return false;
    }

    public static String state(Context context){return p(context).getString(STATE,"NONE");}
    public static String reason(Context context){return p(context).getString(REASON,"");}

    public static JSONObject previous(Context context){
        try{
            String raw=p(context).getString(PREVIOUS,"");
            return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
        }catch(Throwable ignored){return new JSONObject();}
    }

    public static JSONObject candidate(Context context){
        try{
            String raw=p(context).getString(CANDIDATE,"");
            return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
        }catch(Throwable ignored){return new JSONObject();}
    }

    static void clearForTests(Context context){
        if(context!=null)p(context).edit().clear().apply();
    }
}
