package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.*;

/** Source-mode router for Brain. Your Data stays local; External sends no Cortex memory; Combined may send explicitly cloud-allowed Cortex context. */
public final class BrainRouter {
    private BrainRouter(){}

    public static LocalAskRouter.Result fast(Context ctx,VaultDb db,String question,String mode,LocalAskRouter.Progress progress){return fast(ctx,db,question,mode,0,progress);}

    /** focalItemId keeps "Ask Brain about this" attached to the exact Cortex capture instead of only copying OCR text. */
    public static LocalAskRouter.Result fast(Context ctx,VaultDb db,String question,String mode,long focalItemId,LocalAskRouter.Progress progress){
        String m=normalize(mode);if("your_data".equals(m))return LocalAskRouter.fast(ctx,db,question,progress);return cloud(ctx,db,question,m,focalItemId,progress);
    }

    private static LocalAskRouter.Result cloud(Context ctx,VaultDb db,String question,String mode,long focalItemId,LocalAskRouter.Progress progress){
        long wall=SystemClock.elapsedRealtime();long job=createJob(db,question,mode,focalItemId);boolean combined="combined".equals(mode);
        GroundedAnswer grounded=emptyGrounding(question);long retrieval=0;int privateFound=0;KnowledgeItem focal=null,cloudFocal=null;String phoneContext="";boolean phoneAvailable=false,phoneSent=false;boolean fastFocal=false,fastGeneral=false,broadRetrieval=false;ContextPacketBuilder.Packet contextPacket=null;
        try{
            if(focalItemId>0)try{focal=db.getById(focalItemId);}catch(Throwable ignored){}
            broadRetrieval=combined&&needsBroadContext(question);
            if(combined&&focal!=null&&CloudEvidencePolicy.canSend(ctx,focal)&&!broadRetrieval){cloudFocal=focal;fastFocal=true;}
            if(combined&&focal==null&&!broadRetrieval)fastGeneral=true;
            AiJobStore.start(db,job,"Understanding request","understanding");emit(progress,job,"Understanding request",8);
            if(combined)try{contextPacket=ContextPacketBuilder.buildLocal(db,520);}catch(Throwable ignored){}

            // Exact state/context questions are already answered authoritatively by Cortex's local
            // operational ledger and Context Engine. Sending them to cloud adds latency and can lose
            // local-only derived state. Combined returns the authoritative local result immediately.
            if(combined&&focalItemId<=0){
                long rt=SystemClock.elapsedRealtime();GroundedAnswer operational=AskOperationalEngine.tryAnswer(db,question);retrieval=SystemClock.elapsedRealtime()-rt;
                if(operational!=null){
                    grounded=operational;AiJobStore.linkSources(db,job,grounded);long total=SystemClock.elapsedRealtime()-wall;long contextId=contextPacket==null?0:contextPacket.contextId;
                    String detail="Combined operational/context fast path · authoritative Cortex state answered locally · no operational private state sent to cloud";
                    AiJobStore.progress(db,job,"Using current Cortex state","operational_local",72,detail);emit(progress,job,"Using current Cortex state",72);
                    JSONObject out=new JSONObject().put("answer",operational.answer).put("provider","cortex-operational").put("model","deterministic-local").put("source_mode",mode).put("source_count",operational.sources.size()).put("private_found",operational.sources.size()).put("focal_item_id",0).put("focal_sent",false).put("fast_focal",false).put("fast_general",false).put("broad_retrieval",false).put("operational_fast_path",true).put("context_id",contextId).put("context_cloud_sent",false).put("phone_context_available",false).put("phone_context_sent",false).put("withheld_local_only",0).put("actions_count",0).put("actions_deferred",true).put("total_ms",total);
                    AiJobStore.complete(db,job,out.toString(),"Answer ready",detail);emit(progress,job,"Answer ready",100);
                    try{DiagnosticsLog.info(db,"BrainRouter","operational_answer","ok",0,0,0,job,0,total,new JSONObject().put("provider","cortex-operational").put("mode",mode).put("source_count",operational.sources.size()).put("context_id",contextId).put("cloud_sent",false).put("actions_deferred",true));}catch(Throwable ignored){}
                    return new LocalAskRouter.Result(job,operational,operational.answer,"cortex-operational-combined","",mode,0,0,0,total,retrieval,0,0,0,false);
                }
            }

            if(combined){
                if(fastFocal){
                    AiJobStore.progress(db,job,"Using this capture","focal_context",32,"Fast focal route: exact cloud-allowed capture attached; broad retrieval deferred");
                    emit(progress,job,"Using this capture",32);
                }else if(fastGeneral){
                    AiJobStore.progress(db,job,"Fast answer route","fast_external",28,"Question does not require broad Cortex retrieval; local Context Passport stays local and next moves are generated after the answer");
                    emit(progress,job,"Fast answer route",28);
                }else{
                    AiJobStore.progress(db,job,"Searching your Cortex","retrieval",24,"Question requires grounded Cortex history/context; selecting relevant cloud-allowed evidence");emit(progress,job,"Searching your Cortex",24);
                    long rt=SystemClock.elapsedRealtime();GroundedAnswer found=SecondBrainEngine.ask(db,question);retrieval=SystemClock.elapsedRealtime()-rt;privateFound=found.sources.size();grounded=CloudEvidencePolicy.filter(ctx,found);AiJobStore.linkSources(db,job,grounded);
                    if(focal!=null&&CloudEvidencePolicy.canSend(ctx,focal)){cloudFocal=focal;AiJobStore.progress(db,job,"Using this capture","focal_context",40,"The exact capture is cloud-eligible under its current privacy setting");emit(progress,job,"Using this capture",40);}
                    if(PrivacyPolicy.canCollect(ctx,"phone_context")){
                        try{
                            PhoneContextStore.ensure(db);String recent=PhoneContextStore.recentSummary(db,30L*60L*1000L,8);String processes=phoneQuestion(question)?PhoneContextStore.activeProcessSummary(db,25):"";StringBuilder pc=new StringBuilder();
                            if(!recent.isEmpty())pc.append("RECENT PHONE TIMELINE:\n").append(recent);if(!processes.isEmpty()){if(pc.length()>0)pc.append("\n\n");pc.append("LATEST RUNNING PROCESS STATE:\n").append(processes);}phoneContext=pc.toString();phoneAvailable=!phoneContext.isEmpty();phoneSent=phoneAvailable&&PrivacyPolicy.canUseCloud(ctx,"phone_context");
                        }catch(Throwable ignored){phoneContext="";phoneAvailable=false;phoneSent=false;}
                    }
                }
            }
            int withheld=Math.max(0,privateFound-grounded.sources.size());
            String detail;
            if(fastFocal)detail="Fast focal answer · exact capture attached · broad retrieval and next-move generation do not block answer readiness · 12s external budget";
            else if(fastGeneral)detail="Fast general answer · broad Cortex retrieval skipped because the question does not require it · local Context Passport not sent · next moves deferred · 12s external budget";
            else detail=combined?("Grounded Combined answer · "+grounded.sources.size()+" cloud-allowed Cortex source"+(grounded.sources.size()==1?"":"s")+(cloudFocal!=null?" · focal capture attached":(focal!=null?" · focal capture kept local":""))+(withheld>0?" · "+withheld+" local-only source(s) withheld":"")+(phoneAvailable?(phoneSent?" · explicitly allowed recent phone context included":" · recent phone context kept local"):"")+" · next moves deferred · 18s external budget"):"External answer only · no Cortex memory sent · next moves deferred · 18s external budget";
            String stage=(fastFocal||fastGeneral)?"Thinking":(combined?"Combining sources":"Using external AI");int stagePercent=fastFocal?46:(fastGeneral?36:(combined?52:36));
            AiJobStore.progress(db,job,stage,"external",stagePercent,detail);emit(progress,job,stage,stagePercent);

            // First-answer contract: the provider answers the user's question only. Structured actions/
            // useful next moves are a separate semantic operation mounted by ProposalUi after this
            // answer card is rendered. They must never lengthen the BRAIN_ANSWER critical path.
            String modelQuestion=question;long providerBudget=(fastFocal||fastGeneral)?12_000L:18_000L;
            ExternalBrainProvider.Result x=BrainAnswerBudget.ask(ctx,modelQuestion,grounded,combined,cloudFocal,phoneSent?phoneContext:"",providerBudget);
            String answer=x.text==null?"":x.text.trim();if(answer.isEmpty())throw new IllegalStateException("External provider returned an empty answer");long total=SystemClock.elapsedRealtime()-wall;long contextId=contextPacket==null?0:contextPacket.contextId;
            JSONObject out=new JSONObject().put("answer",answer).put("provider",x.provider).put("model",x.model).put("source_mode",mode).put("source_count",grounded.sources.size()).put("private_found",privateFound).put("focal_item_id",focalItemId).put("focal_sent",cloudFocal!=null).put("fast_focal",fastFocal).put("fast_general",fastGeneral).put("broad_retrieval",broadRetrieval).put("context_id",contextId).put("context_cloud_sent",false).put("phone_context_available",phoneAvailable).put("phone_context_sent",phoneSent).put("withheld_local_only",withheld).put("actions_count",0).put("actions_deferred",true).put("provider_budget_ms",providerBudget).put("retrieval_ms",retrieval).put("total_ms",total);
            AiJobStore.modelRun(db,job,1,"answer_first","cloud",x.model,mode,"complete",Fingerprint.text(question+"|"+mode+"|"+focalItemId),x.durationMs,0,0,0.78,new JSONObject().put("provider",x.provider).put("source_count",grounded.sources.size()).put("focal_sent",cloudFocal!=null).put("fast_focal",fastFocal).put("fast_general",fastGeneral).put("broad_retrieval",broadRetrieval).put("context_id",contextId).put("context_cloud_sent",false).put("phone_context_sent",phoneSent).put("withheld_local_only",withheld).put("actions_count",0).put("actions_deferred",true).put("provider_budget_ms",providerBudget).toString(),"");
            AiJobStore.complete(db,job,out.toString(),"Answer ready",detail);emit(progress,job,"Answer ready",100);
            try{DiagnosticsLog.info(db,"BrainRouter","external_answer","ok",focalItemId,0,0,job,0,total,new JSONObject().put("provider",x.provider).put("model",x.model).put("mode",mode).put("focal_sent",cloudFocal!=null).put("fast_focal",fastFocal).put("fast_general",fastGeneral).put("broad_retrieval",broadRetrieval).put("context_id",contextId).put("context_cloud_sent",false).put("phone_context_sent",phoneSent).put("withheld_local_only",withheld).put("actions_deferred",true).put("provider_budget_ms",providerBudget).put("retrieval_ms",retrieval));}catch(Throwable ignored){}
            return new LocalAskRouter.Result(job,grounded,answer,x.provider+(combined?"-combined":"-external"),"",mode,0,0,x.durationMs,total,retrieval,0,0,x.durationMs,false);
        }catch(Throwable t){
            long total=SystemClock.elapsedRealtime()-wall;String err=t.getClass().getSimpleName()+(t.getMessage()==null?"":": "+t.getMessage());AiJobStore.fail(db,job,err,"External route unavailable");
            try{DiagnosticsLog.error(db,"BrainRouter","external_route",t,"EXTERNAL_MODEL",focalItemId,0,0,job,0,new JSONObject().put("mode",mode).put("provider",ExternalBrainProvider.activeProviderId(ctx)).put("model",ExternalBrainProvider.activeModel(ctx)));}catch(Throwable ignored){}
            if(combined){
                try{emit(progress,job,"External unavailable · using your Cortex",72);LocalAskRouter.Result local=LocalAskRouter.fast(ctx,db,question,progress);String answer="External AI is unavailable right now, so Brain answered from your Cortex data only.\n\n"+local.answer;return new LocalAskRouter.Result(local.jobId,local.grounded,answer,"combined-local-fallback",err,"combined",local.tokensPerSecond,local.tokensGenerated,local.durationMs,SystemClock.elapsedRealtime()-wall,local.retrievalMs,local.promptBuildMs,local.modelLoadMs,local.generationMs,local.cacheHit);}catch(Throwable fallbackError){err=err+" | local fallback: "+fallbackError.getClass().getSimpleName();}
            }
            emit(progress,job,"External route unavailable",100);String answer=ExternalBrainProvider.configured(ctx)?"Brain couldn't reach the configured external AI right now. Your Cortex data was not changed.":"External AI isn't configured yet. Add an OpenRouter API key in Settings.";return new LocalAskRouter.Result(job,grounded,answer,"failed",err,mode,0,0,0,total,retrieval,0,0,0,false);
        }
    }

    /** Broad memory retrieval is reserved for questions that actually ask Cortex to connect/history/reason across sources. */
    private static boolean needsBroadContext(String q){
        if(phoneQuestion(q))return true;
        String n=LocalSemanticEmbedder.norm(q==null?"":q);
        String[] xs={"project","history","previous","recently","recent","remember","decision","decide","related","connect","compare","follow up","follow-up","appointment","calendar","what do i know","what did i","what have i","before","earlier","مشروع","فاكر","قبل كده","قبل كدة","قرار","قررت","قارن","مقارنة","اربط","ربط","متابعة","تابع","موعد","ميعاد","فكرني","كنت عملت","كنت قلت","إيه اللي أعرفه","ايه اللي اعرفه"};
        for(String x:xs)if(n.contains(LocalSemanticEmbedder.norm(x)))return true;
        return false;
    }

    private static boolean phoneQuestion(String q){String n=LocalSemanticEmbedder.norm(q==null?"":q);String[] xs={"phone context","current app","recent apps","last apps","running apps","running processes","background apps","what was i doing on my phone","what apps are running","what is running on my phone","كنت فاتح ايه","كنت فاتح إيه","كنت بعمل ايه على الموبايل","كنت بعمل إيه على الموبايل","ايه شغال على الموبايل","إيه شغال على الموبايل","آخر تطبيقات","اخر تطبيقات","آخر ابلكيشنات","اخر ابلكيشنات"};for(String x:xs)if(n.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static long createJob(VaultDb db,String q,String mode,long focalItemId){try{return AiJobStore.create(db,"brain_"+mode,mode,new JSONObject().put("question",q).put("explicit_cloud_route",true).put("focal_item_id",focalItemId).toString(),70);}catch(Exception e){return AiJobStore.create(db,"brain_"+mode,mode,"{}",70);}}
    private static GroundedAnswer emptyGrounding(String q){return new GroundedAnswer(q,"",0,new ArrayList<SemanticHit>(),new ArrayList<String>(),new ArrayList<String>());}
    private static String normalize(String m){return"external".equals(m)||"combined".equals(m)?m:"your_data";}
    private static void emit(LocalAskRouter.Progress p,long id,String label,int percent){if(p!=null)try{p.stage(id,label,percent);}catch(Throwable ignored){}}
}
