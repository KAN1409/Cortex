package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.*;

/** Source-mode router for Brain. Cloud routes are explicit and never run from Your Data mode. */
public final class BrainRouter {
    private BrainRouter(){}

    public static LocalAskRouter.Result fast(Context ctx,VaultDb db,String question,String mode,LocalAskRouter.Progress progress){
        String m=normalize(mode);if("your_data".equals(m))return LocalAskRouter.fast(ctx,db,question,progress);
        return cloud(ctx,db,question,m,progress);
    }

    private static LocalAskRouter.Result cloud(Context ctx,VaultDb db,String question,String mode,LocalAskRouter.Progress progress){
        long wall=SystemClock.elapsedRealtime();long job=createJob(db,question,mode);boolean combined="combined".equals(mode);GroundedAnswer grounded=emptyGrounding(question);long retrieval=0;int privateFound=0;
        try{
            AiJobStore.start(db,job,"Understanding request","understanding");emit(progress,job,"Understanding request",8);
            if(combined){AiJobStore.progress(db,job,"Searching your Cortex","retrieval",24,"Selecting private evidence for Combined mode");emit(progress,job,"Searching your Cortex",24);long rt=SystemClock.elapsedRealtime();GroundedAnswer found=SecondBrainEngine.ask(db,question);retrieval=SystemClock.elapsedRealtime()-rt;privateFound=found.sources.size();grounded=CloudEvidencePolicy.filter(ctx,found);AiJobStore.linkSources(db,job,grounded);}
            String detail=combined?("Explicit Combined route · "+grounded.sources.size()+" cloud-allowed Cortex source"+(grounded.sources.size()==1?"":"s")+(privateFound>grounded.sources.size()?" · "+(privateFound-grounded.sources.size())+" local-only source(s) withheld":"")):"No Cortex memory is sent in External mode";
            AiJobStore.progress(db,job,combined?"Combining sources":"Using external AI","external",combined?48:36,detail);emit(progress,job,combined?"Combining sources":"Using external AI",combined?48:36);
            ExternalBrainProvider.Result x=ExternalBrainProvider.ask(ctx,question,grounded,combined);long total=SystemClock.elapsedRealtime()-wall;
            JSONObject out=new JSONObject().put("answer",x.text).put("provider","gemini").put("source_mode",mode).put("source_count",grounded.sources.size()).put("private_found",privateFound).put("withheld_local_only",Math.max(0,privateFound-grounded.sources.size())).put("total_ms",total);
            AiJobStore.modelRun(db,job,1,"primary","cloud","gemini-3.6-flash",mode,"complete",Fingerprint.text(question+"|"+mode),x.durationMs,0,0,0.78,new JSONObject().put("source_count",grounded.sources.size()).put("withheld_local_only",Math.max(0,privateFound-grounded.sources.size())).toString(),"");
            AiJobStore.complete(db,job,out.toString(),"Answer ready",combined?detail:"External AI answer; no Cortex memory sent");emit(progress,job,"Answer ready",100);
            return new LocalAskRouter.Result(job,grounded,x.text,combined?"gemini-combined":"gemini-external","",mode,0,0,x.durationMs,total,retrieval,0,0,x.durationMs,false);
        }catch(Throwable t){
            long total=SystemClock.elapsedRealtime()-wall;String err=t.getClass().getSimpleName()+(t.getMessage()==null?"":": "+t.getMessage());AiJobStore.fail(db,job,err,"External route unavailable");
            if(combined){
                try{
                    emit(progress,job,"External unavailable · using your Cortex",72);
                    LocalAskRouter.Result local=LocalAskRouter.fast(ctx,db,question,progress);
                    String answer="External AI is unavailable right now, so Brain answered from your Cortex data only.\n\n"+local.answer;
                    return new LocalAskRouter.Result(local.jobId,local.grounded,answer,"combined-local-fallback",err,"combined",local.tokensPerSecond,local.tokensGenerated,local.durationMs,SystemClock.elapsedRealtime()-wall,local.retrievalMs,local.promptBuildMs,local.modelLoadMs,local.generationMs,local.cacheHit);
                }catch(Throwable fallbackError){err=err+" | local fallback: "+fallbackError.getClass().getSimpleName();}
            }
            emit(progress,job,"External route unavailable",100);String answer=GeminiKeyStore.has(ctx)?"Brain couldn't reach the configured external AI right now. Your Cortex data was not changed.":"External AI isn't configured yet. Add a Gemini API key in Settings. Your Cortex data stays local unless you explicitly choose Combined mode.";return new LocalAskRouter.Result(job,grounded,answer,"failed",err,mode,0,0,0,total,retrieval,0,0,0,false);
        }
    }

    private static long createJob(VaultDb db,String q,String mode){try{return AiJobStore.create(db,"brain_"+mode,mode,new JSONObject().put("question",q).put("explicit_cloud_route",true).toString(),70);}catch(Exception e){return AiJobStore.create(db,"brain_"+mode,mode,"{}",70);}}
    private static GroundedAnswer emptyGrounding(String q){return new GroundedAnswer(q,"",0,new ArrayList<SemanticHit>(),new ArrayList<String>(),new ArrayList<String>());}
    private static String normalize(String m){return"external".equals(m)||"combined".equals(m)?m:"your_data";}
    private static void emit(LocalAskRouter.Progress p,long id,String label,int percent){if(p!=null)try{p.stage(id,label,percent);}catch(Throwable ignored){}}
}
