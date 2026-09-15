package com.kareem.cortex;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/**
 * Maximum-power mobile-only cognitive council.
 * Heavy models are loaded one-at-a-time and pass structured reasoning to each other.
 */
public final class CognitiveCouncilOrchestrator {
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING=new java.util.concurrent.atomic.AtomicBoolean(false);
    private CognitiveCouncilOrchestrator(){}

    public static final class Result {
        public final boolean ok,publish;
        public final String title,whatFound,whyMatters,whyNow,suggestedAction,rawFinal,modelsUsed,error;
        public final double confidence;
        public final List<Long> evidenceIds;
        Result(boolean o,boolean p,String t,String wf,String wm,String wn,String a,double c,String raw,String models,String err,List<Long> ids){
            ok=o;publish=p;title=t;whatFound=wf;whyMatters=wm;whyNow=wn;suggestedAction=a;confidence=c;rawFinal=raw;modelsUsed=models;error=err;evidenceIds=Collections.unmodifiableList(new ArrayList<>(ids));
        }
    }

    public static Result run(Context ctx,VaultDb vault,long situationId){
        if(!RUNNING.compareAndSet(false,true))return fail("Another council run is already active",Collections.emptyList());
        try(CouncilExecutionLease lease=CouncilExecutionLease.acquire(ctx)){
            if(lease==null)return fail("Another council run is already active",Collections.emptyList());
            return runOwned(ctx,vault,situationId);
        }catch(Exception e){return fail("Council execution failed: "+safe(e.getMessage()),Collections.emptyList());}
        finally{RUNNING.set(false);}
    }

    private static Result runOwned(Context ctx,VaultDb vault,long situationId){
        SQLiteDatabase db=vault.getReadableDatabase();DiscoveryV3Schema.ensure(db);
        CognitiveCouncilRunRecovery.recoverWhileOwned(db,System.currentTimeMillis());
        EvidencePack pack=pack(db,situationId);
        if(pack.ids.isEmpty())return fail("No linked evidence",pack.ids);
        ArrayList<String> used=new ArrayList<>();
        ArrayList<Pass> passes=new ArrayList<>();
        long runId=startRun(db,situationId,pack);

        try{
            LocalCouncilModelRegistry.Model primary=LocalCouncilModelRegistry.primary();
            if(!LocalCouncilModelRegistry.ready(ctx,primary))return finishFailure(db,runId,"Primary 30B local brain is not ready",pack.ids);

            String investigatorPrompt=
                    "INVESTIGATION TARGET\n"+pack.header+"\n\nEVIDENCE\n"+pack.text+
                    "\n\nTask: identify only non-obvious, potentially useful findings. Test temporal order, missing expected steps, contradictions, recurrence, dependencies and latent relationships. "+
                    "For every claim cite one or more evidence IDs like [E123]. Explicitly list uncertainty and what evidence could falsify each hypothesis.";
            Pass investigator=call(ctx,primary,"investigator",investigatorPrompt);passes.add(investigator);used.add(primary.name);persistPass(db,runId,investigator);touchRun(db,runId,"running_investigator_complete",String.join(" | ",used));

            LocalCouncilModelRegistry.Model analyst=LocalCouncilModelRegistry.analyst();
            if(LocalCouncilModelRegistry.ready(ctx,analyst)){
                String prompt="Analyze the same evidence independently, then compare against the primary investigator. Do not copy it. Find missed interpretations and entity/time mistakes.\n\n"+
                        pack.header+"\n\nEVIDENCE\n"+pack.text+"\n\nPRIMARY INVESTIGATOR\n"+clip(investigator.text,6000);
                Pass p=call(ctx,analyst,"independent_analyst",prompt);passes.add(p);used.add(analyst.name);persistPass(db,runId,p);touchRun(db,runId,"running_analyst_complete",String.join(" | ",used));
            }

            LocalCouncilModelRegistry.Model critic=LocalCouncilModelRegistry.critic();
            if(LocalCouncilModelRegistry.ready(ctx,critic)){
                StringBuilder prior=new StringBuilder();for(Pass p:passes)prior.append("\n\n").append(p.role.toUpperCase(Locale.ROOT)).append("\n").append(clip(p.text,5000));
                String prompt="Attempt to falsify the following analyses using only the supplied evidence. Reject unsupported claims, bad entity merges, timeline errors, causal overreach and duplicated/trivial findings. "+
                        "Return: SURVIVES, REJECTED, MISSING_EVIDENCE, and the strongest surviving candidate with evidence IDs.\n\n"+
                        pack.header+"\n\nEVIDENCE\n"+pack.text+prior;
                Pass p=call(ctx,critic,"adversarial_critic",prompt);passes.add(p);used.add(critic.name);persistPass(db,runId,p);touchRun(db,runId,"running_critic_complete",String.join(" | ",used));
            }

            StringBuilder council=new StringBuilder();
            for(Pass p:passes)council.append("\n\n### ").append(p.role).append("\n").append(clip(p.text,5200));
            String finalPrompt=
                    "You are the final Cortex council judge. Use the original evidence and the council passes below. Publish NOTHING unless it is specific, non-trivial, grounded, useful, and survives criticism. "+
                    "Do not output chain-of-thought. Return ONLY valid JSON with keys: should_publish(boolean), title, what_found, why_matters, why_now, suggested_action, confidence(number 0..1), evidence_ids(array of numeric IDs supporting the finding). Cite those IDs in what_found. "+
                    "what_found must mention concrete subjects/statuses/relationships and preserve uncertainty. Generic statistics are forbidden.\n\n"+
                    pack.header+"\n\nEVIDENCE\n"+pack.text+"\n\nCOUNCIL"+council;
            Pass finalPass=call(ctx,primary,"final_judge",finalPrompt);used.add(primary.name+" · final");persistPass(db,runId,finalPass);touchRun(db,runId,"running_final_complete",String.join(" | ",used));

            JSONObject o=parseJson(finalPass.text);
            boolean publish=o.optBoolean("should_publish",false);
            String title=clean(o.optString("title",""));
            String found=clean(o.optString("what_found",""));
            String why=clean(o.optString("why_matters",""));
            String whyNow=clean(o.optString("why_now",""));
            String action=clean(o.optString("suggested_action",""));
            double confidence=Math.max(0,Math.min(1,o.optDouble("confidence",0)));
            LinkedHashSet<Long> cited=new LinkedHashSet<>();JSONArray references=o.optJSONArray("evidence_ids");
            if(references!=null)for(int i=0;i<references.length();i++){
                long id=references.optLong(i,-1);
                if(!pack.ids.contains(id))publish=false;else cited.add(id);
            }
            if(cited.size()<2||!Double.isFinite(confidence))publish=false;
            if(title.isEmpty()||found.isEmpty()||why.isEmpty()||action.isEmpty()||confidence<.70)publish=false;
            if(publish&&!DiscoveryV3Feed.userWorthy("COUNCIL_DISCOVERY",title,found,action,pack.ids.size(),confidence,councilScore(confidence,pack.ids.size())))publish=false;
            finishRun(db,runId,publish?"complete_candidate":"complete_silent",String.join(" | ",used),"",finalPass.text);
            return new Result(true,publish,title,found,why,whyNow,action,confidence,finalPass.text,String.join(" | ",used),"",new ArrayList<>(cited));
        }catch(Throwable t){
            String err=t.getClass().getSimpleName()+": "+safe(t.getMessage());finishRun(db,runId,"failed",String.join(" | ",used),err,"");
            return new Result(false,false,"","","","","",0,"",String.join(" | ",used),err,pack.ids);
        }finally{
            try{LocalLlmBridge.releaseCached();}catch(Throwable ignored){}
        }
    }

    private static Pass call(Context ctx,LocalCouncilModelRegistry.Model model,String role,String prompt)throws Exception{
        try{
            LocalLlmBridge.CompletionResult r=LocalLlmBridge.completeDeepCached(
                    LocalCouncilModelRegistry.file(ctx,model).getAbsolutePath(),
                    clip(prompt,14500),model.systemPrompt,model.maxTokens);
            return new Pass(role,model.id,model.name,r.getText(),r.getDurationMs(),r.getTokensGenerated(),r.getTokensPerSecond());
        }finally{
            // Maximum quality through model diversity, minimum cross-model native-memory contamination.
            try{LocalLlmBridge.releaseCached();}catch(Throwable ignored){}
        }
    }

    private static EvidencePack pack(SQLiteDatabase db,long sid){
        String label="",domain="",space="";
        Cursor s=db.rawQuery("SELECT label,domain,space FROM discovery_v3_situations WHERE id=?",new String[]{String.valueOf(sid)});
        if(s.moveToFirst()){label=str(s,0);domain=str(s,1);space=str(s,2);}s.close();

        ArrayList<Long> ids=new ArrayList<>();StringBuilder b=new StringBuilder();
        Cursor c=db.rawQuery("SELECT k.id,k.created_at,k.title,k.summary,k.extracted_text,k.source,e.quality "+
                "FROM discovery_v3_evidence e JOIN knowledge_items k ON k.id=e.item_id WHERE e.situation_id=? "+
                "ORDER BY k.created_at DESC,k.id DESC LIMIT 24",new String[]{String.valueOf(sid)});
        while(c.moveToNext()){
            long id=c.getLong(0);ids.add(id);String body=!str(c,3).isEmpty()?str(c,3):str(c,4);body=body.replaceAll("\\s+"," ").trim();
            b.append("[E").append(id).append("] time=").append(c.getLong(1)).append(" source=").append(str(c,5))
                    .append(" quality=").append(String.format(Locale.US,"%.2f",c.getDouble(6))).append("\n")
                    .append(str(c,2)).append("\n").append(clip(body,900)).append("\n\n");
        }c.close();
        // Give the council a compact state-transition ledger in addition to raw evidence.
        StringBuilder claims=new StringBuilder();
        Cursor cl=db.rawQuery("SELECT item_id,subject_label,predicate,value,confidence,observed_at FROM discovery_v3_claims WHERE situation_id=? ORDER BY observed_at ASC,id ASC LIMIT 40",new String[]{String.valueOf(sid)});
        String lastKey="";while(cl.moveToNext()){
            String key=str(cl,1)+"|"+str(cl,2)+"|"+str(cl,3);
            if(key.equals(lastKey))continue;lastKey=key;
            claims.append("[E").append(cl.getLong(0)).append("] ").append(cl.getLong(5)).append(" · ")
                    .append(str(cl,1)).append(" · ").append(str(cl,2)).append(" → ").append(str(cl,3))
                    .append(" · confidence=").append(String.format(Locale.US,"%.2f",cl.getDouble(4))).append("\n");
        }cl.close();
        if(claims.length()>0)b.append("STATE / CLAIM TIMELINE\n").append(clip(claims.toString(),4200)).append("\n");

        Cursor cov=db.rawQuery("SELECT COUNT(DISTINCT item_id),COUNT(DISTINCT COALESCE(NULLIF(source_key,''),source_type)),MIN(k.created_at),MAX(k.created_at),AVG(e.quality) FROM discovery_v3_evidence e JOIN knowledge_items k ON k.id=e.item_id WHERE e.situation_id=?",new String[]{String.valueOf(sid)});
        if(cov.moveToFirst())b.append("COVERAGE\nitems=").append(cov.getInt(0)).append(" sources=").append(cov.getInt(1))
                .append(" first=").append(cov.isNull(2)?0:cov.getLong(2)).append(" latest=").append(cov.isNull(3)?0:cov.getLong(3))
                .append(" avg_quality=").append(String.format(Locale.US,"%.2f",cov.getDouble(4))).append("\n\n");cov.close();

        String history=DiscoveryV3History.latest(db,sid);
        if(!history.isEmpty())b.append("CURRENT CORTEX HISTORY\n").append(clip(history,4500));
        return new EvidencePack(ids,"Situation: "+label+" | space="+space+" | domain="+domain,b.toString());
    }

    private static long startRun(SQLiteDatabase db,long sid,EvidencePack p){
        ContentValues v=new ContentValues();v.put("situation_id",sid);v.put("state","running");v.put("models_used","");v.put("evidence_count",p.ids.size());v.put("started_at",System.currentTimeMillis());v.put("updated_at",System.currentTimeMillis());
        return db.insertOrThrow("discovery_v3_council_runs",null,v);
    }
    private static void persistPass(SQLiteDatabase db,long runId,Pass p){
        ContentValues v=new ContentValues();v.put("run_id",runId);v.put("role",p.role);v.put("model_id",p.modelId);v.put("model_name",p.modelName);v.put("output_text",p.text);v.put("duration_ms",p.durationMs);v.put("tokens",p.tokens);v.put("tokens_per_second",p.tps);v.put("created_at",System.currentTimeMillis());db.insert("discovery_v3_council_passes",null,v);
    }
    private static void touchRun(SQLiteDatabase db,long id,String state,String models){
        ContentValues v=new ContentValues();v.put("state",state);v.put("models_used",models);v.put("updated_at",System.currentTimeMillis());
        db.update("discovery_v3_council_runs",v,"id=?",new String[]{String.valueOf(id)});
    }
    private static void finishRun(SQLiteDatabase db,long id,String state,String models,String error,String finalText){
        ContentValues v=new ContentValues();v.put("state",state);v.put("models_used",models);v.put("error",error);v.put("final_output",finalText);v.put("completed_at",System.currentTimeMillis());v.put("updated_at",System.currentTimeMillis());db.update("discovery_v3_council_runs",v,"id=?",new String[]{String.valueOf(id)});
    }
    private static Result finishFailure(SQLiteDatabase db,long id,String error,List<Long> evidence){
        finishRun(db,id,"blocked","",error,"");return new Result(false,false,"","","","","",0,"","",error,evidence);
    }
    private static Result fail(String error,List<Long> evidence){return new Result(false,false,"","","","","",0,"","",error,evidence);}

    private static JSONObject parseJson(String text)throws Exception{
        String x=text==null?"":text.trim();int a=x.indexOf('{'),b=x.lastIndexOf('}');if(a<0||b<=a)throw new JSONException("Council final output was not JSON");
        return new JSONObject(x.substring(a,b+1));
    }
    private static double councilScore(double confidence,int evidenceCount){
        double evidence=Math.min(1,.52+.16*Math.max(0,evidenceCount-1));
        return DiscoveryV3Policy.score(.90,.88,evidence,.76,confidence,.10);
    }
    private static String clean(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
    private static String safe(String s){return s==null?"":clip(s,500);}
    private static String clip(String s,int n){String x=s==null?"":s;return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String str(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}

    private static final class EvidencePack{final List<Long> ids;final String header,text;EvidencePack(List<Long> i,String h,String t){ids=i;header=h;text=t;}}
    private static final class Pass{final String role,modelId,modelName,text;final long durationMs;final int tokens;final float tps;Pass(String r,String mi,String mn,String t,long d,int tok,float p){role=r;modelId=mi;modelName=mn;text=t;durationMs=d;tokens=tok;tps=p;}}
}
