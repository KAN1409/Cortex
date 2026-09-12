package com.kareem.cortex;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Counterfactual teacher comparison plus a live current-policy snapshot. Read-only. */
public final class CortexTeacherImpact {
    public static final String VERSION="cortex_teacher_impact_002";
    private static final String PREF="cortex_teacher_impact",KEY="latest";
    private CortexTeacherImpact(){}

    public static JSONObject compare(Context context,JSONObject proposed)throws Exception{return comparePolicies(context,CortexPersonalPolicy.current(context.getApplicationContext()),proposed,"COUNTERFACTUAL");}

    public static JSONObject liveCurrent(Context context){
        try{JSONObject current=CortexPersonalPolicy.current(context.getApplicationContext());return comparePolicies(context,current,current,"LIVE_CURRENT");}
        catch(Throwable t){try{return new JSONObject().put("version",VERSION).put("mode","LIVE_CURRENT").put("error",t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));}catch(Exception ignored){return new JSONObject();}}
    }

    private static JSONObject comparePolicies(Context context,JSONObject current,JSONObject proposed,String mode)throws Exception{
        Context app=context.getApplicationContext();VaultDb db=new VaultDb(app);
        try{
            List<AttentionDecisionEngine.Candidate> candidates=CognitiveShadowStore.loadCandidates(db.getReadableDatabase(),System.currentTimeMillis());CortexAttentionJudge.RuntimeContext rt=CortexAttentionJudge.RuntimeContext.neutral();int beforeNow=0,afterNow=0,promoted=0,deferred=0,unchanged=0;double totalDelta=0;JSONArray examples=new JSONArray();
            for(AttentionDecisionEngine.Candidate candidate:candidates){CortexAttentionJudge.Judgment before=CortexAttentionJudge.evaluateWithPolicy(app,candidate,rt,current),after=CortexAttentionJudge.evaluateWithPolicy(app,candidate,rt,proposed);if(before.surfaceNow)beforeNow++;if(after.surfaceNow)afterNow++;if(!before.surfaceNow&&after.surfaceNow)promoted++;else if(before.surfaceNow&&!after.surfaceNow)deferred++;else unchanged++;double delta=after.score-before.score;totalDelta+=delta;if((before.surfaceNow!=after.surfaceNow||Math.abs(delta)>=.08||"LIVE_CURRENT".equals(mode))&&examples.length()<12){JSONObject e=new JSONObject();e.put("situationId",candidate.situationId);e.put("subject",clip(candidate.subject,120));e.put("beforeScore",round(before.score));e.put("afterScore",round(after.score));e.put("beforeNow",before.surfaceNow);e.put("afterNow",after.surfaceNow);e.put("reason",after.reason);examples.put(e);}}
            JSONObject out=new JSONObject();out.put("version",VERSION);out.put("mode",mode);out.put("createdAt",System.currentTimeMillis());out.put("currentPolicy",current.optString("version","local"));out.put("proposedPolicy",proposed.optString("version","teacher"));out.put("candidateCount",candidates.size());out.put("beforeNow",beforeNow);out.put("afterNow",afterNow);out.put("promoted",promoted);out.put("deferred",deferred);out.put("unchanged",unchanged);out.put("averageScoreDelta",candidates.isEmpty()?0:round(totalDelta/candidates.size()));out.put("examples",examples);return out;
        }finally{try{db.close();}catch(Throwable ignored){}}
    }

    public static void save(Context context,JSONObject report){if(context==null||report==null)return;context.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,report.toString()).apply();}
    public static JSONObject latest(Context context){try{String raw=context.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"");return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);}catch(Throwable ignored){return new JSONObject();}}
    public static String summary(Context context){JSONObject r=latest(context);if(r.length()==0)return"No shadow comparison yet";return r.optString("currentPolicy","?")+" → "+r.optString("proposedPolicy","?")+"\nCandidates "+r.optInt("candidateCount",0)+" · Now "+r.optInt("beforeNow",0)+" → "+r.optInt("afterNow",0)+" · promoted "+r.optInt("promoted",0)+" · deferred "+r.optInt("deferred",0)+"\nAvg score Δ "+String.format(java.util.Locale.US,"%.3f",r.optDouble("averageScoreDelta",0));}
    private static double round(double x){return Math.round(x*1000.0)/1000.0;}private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
