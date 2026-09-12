package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Safe promotion boundary for teacher policies. */
public final class CortexPolicyPromotion {
    public static final String VERSION="cortex_policy_promotion_002";
    private static final String PREF="cortex_policy_promotion";
    private static final String KEY_PREVIOUS="previous_policy",KEY_STATE="state",KEY_REASON="reason",KEY_POLICY="policy_version",KEY_AT="updated_at";
    private CortexPolicyPromotion(){}

    public static final class Result {
        public final boolean promoted,rejected; public final String state,reason,policyVersion;
        Result(boolean promoted,boolean rejected,String state,String reason,String policyVersion){this.promoted=promoted;this.rejected=rejected;this.state=n(state);this.reason=n(reason);this.policyVersion=n(policyVersion);}
    }

    public static Result evaluateAndPromote(Context context,JSONObject proposed,JSONObject impact){
        Context app=context.getApplicationContext();JSONObject current=CortexPersonalPolicy.current(app);String proposedVersion=proposed.optString("version","teacher");
        int candidates=impact==null?0:impact.optInt("candidateCount",0),beforeNow=impact==null?0:impact.optInt("beforeNow",0),afterNow=impact==null?0:impact.optInt("afterNow",0),promoted=impact==null?0:impact.optInt("promoted",0),deferred=impact==null?0:impact.optInt("deferred",0);double avgDelta=impact==null?0:impact.optDouble("averageScoreDelta",0);
        boolean explosiveSurface=candidates>=4&&afterNow>Math.max(beforeNow+3,(int)Math.ceil(Math.max(1,beforeNow)*1.75));
        boolean broadPromotion=candidates>=6&&promoted>=5&&promoted>deferred+3;
        boolean scoreExplosion=candidates>=6&&avgDelta>.22&&afterNow>beforeNow+2;
        if(explosiveSurface||broadPromotion||scoreExplosion){String reason=explosiveSurface?"shadow predicted excessive Now expansion":(broadPromotion?"shadow predicted broad candidate promotion":"shadow predicted excessive score inflation");rememberState(app,"REJECTED",reason,proposedVersion);return new Result(false,true,"REJECTED",reason,proposedVersion);}

        CortexPolicyLifecycle.Result lifecycle=CortexPolicyLifecycle.stage(app,current,proposed,impact);
        if(lifecycle.activation==CortexPolicyLifecycle.Activation.HELD){rememberState(app,"HELD",lifecycle.reason,proposedVersion);return new Result(false,true,"HELD",lifecycle.reason,proposedVersion);}
        app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_PREVIOUS,current.toString()).apply();
        String reason=candidates==0?"activated in bounded canary; no live canonical candidates existed to compare yet":"shadow comparison passed; bounded canary active";
        try{JSONObject activated=new JSONObject(proposed.toString());activated.put("promotionMode","CANARY");activated.put("promotedAt",System.currentTimeMillis());activated.put("previousPolicyVersion",current.optString("version","local"));CortexPersonalPolicy.saveWithoutLifecycle(app,activated);}catch(Throwable e){reason="promotion failed: "+n(e.getMessage());rememberState(app,"FAILED",reason,proposedVersion);return new Result(false,true,"FAILED",reason,proposedVersion);}
        rememberState(app,"CANARY",reason,proposedVersion);return new Result(true,false,"CANARY",reason,proposedVersion);
    }

    public static boolean rollback(Context context,String reason){Context app=context.getApplicationContext();SharedPreferences p=app.getSharedPreferences(PREF,Context.MODE_PRIVATE);String raw=p.getString(KEY_PREVIOUS,"");if(raw==null||raw.trim().isEmpty())return false;try{JSONObject previous=new JSONObject(raw);CortexPersonalPolicy.saveWithoutLifecycle(app,previous);CortexPolicyLifecycle.rollback(app,n(reason).isEmpty()?"manual rollback":reason);rememberState(app,"ROLLED_BACK",n(reason).isEmpty()?"manual rollback":reason,previous.optString("version","previous"));return true;}catch(Throwable ignored){return false;}}
    public static String status(Context context){SharedPreferences p=context.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);String state=p.getString(KEY_STATE,"NONE"),version=p.getString(KEY_POLICY,""),reason=p.getString(KEY_REASON,"");return state+(version==null||version.isEmpty()?"":" · "+version)+(reason==null||reason.isEmpty()?"":"\n"+reason);}
    public static boolean canRollback(Context context){String raw=context.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_PREVIOUS,"");return raw!=null&&!raw.trim().isEmpty();}
    private static void rememberState(Context context,String state,String reason,String policy){context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_STATE,n(state)).putString(KEY_REASON,n(reason)).putString(KEY_POLICY,n(policy)).putLong(KEY_AT,System.currentTimeMillis()).apply();}
    private static String n(String s){return s==null?"":s.trim();}
}
