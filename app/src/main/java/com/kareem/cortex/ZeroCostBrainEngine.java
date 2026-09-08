package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.*;

/** Local-only Brain route used whenever the UI requests the former External/Combined modes. */
public final class ZeroCostBrainEngine {
    private static final int MAX_QWEN_TOKENS=192;
    private ZeroCostBrainEngine(){}

    public static LocalAskRouter.Result ask(Context ctx,VaultDb db,String question,String requestedMode,long focalItemId,LocalAskRouter.Progress progress){
        String mode="external".equals(requestedMode)?"external":"combined";long wall=SystemClock.elapsedRealtime();long job=createJob(db,question,mode,focalItemId);GroundedAnswer grounded=empty(question);long retrieval=0;
        try{
            AiJobStore.start(db,job,"Understanding request","understanding");emit(progress,job,"Understanding request",8);
            if("combined".equals(mode)){
                AiJobStore.progress(db,job,"Searching your Cortex","retrieval",24,"All retrieval remains on this phone");emit(progress,job,"Searching your Cortex",24);long rt=SystemClock.elapsedRealtime();grounded=SecondBrainEngine.ask(db,question,focalItemId);retrieval=SystemClock.elapsedRealtime()-rt;AiJobStore.linkSources(db,job,grounded);
            }
            AiJobStore.progress(db,job,"Using local AI","local_ai",52,"Gemini Nano preferred · local Qwen fallback · no paid API");emit(progress,job,"Using local AI",52);
            String prompt=buildPrompt(question,grounded,"combined".equals(mode));
            LocalText answer=generateLocal(ctx,prompt);
            long total=SystemClock.elapsedRealtime()-wall;
            if(!answer.ok){
                if("combined".equals(mode)){
                    String fallback=grounded.answer==null?"":grounded.answer;JSONObject out=json(fallback,"deterministic-grounded",mode,grounded.sources.size(),total).put("local_ai_error",answer.error);AiJobStore.complete(db,job,out.toString(),"Grounded answer ready","Local generative AI unavailable; deterministic Cortex grounding used");emit(progress,job,"Grounded answer ready",100);return new LocalAskRouter.Result(job,grounded,fallback,"deterministic-grounded",answer.error,mode,0,0,0,total,retrieval,0,0,0,false);
                }
                String unavailable="Local AI isn't ready yet. Open Settings → Local Brain to prepare Gemini Nano, or install the optional local Qwen model. Cortex did not call a paid API.";AiJobStore.complete(db,job,json(unavailable,"local-unavailable",mode,0,total).put("local_ai_error",answer.error).toString(),"Local AI unavailable",answer.error);emit(progress,job,"Local AI unavailable",100);return new LocalAskRouter.Result(job,grounded,unavailable,"local-unavailable",answer.error,mode,0,0,0,total,retrieval,0,0,0,false);
            }
            JSONObject out=json(answer.text,answer.provider,mode,grounded.sources.size(),total).put("model",answer.model).put("paid_api_used",false);AiJobStore.complete(db,job,out.toString(),"Answer ready",answer.provider+(answer.model.isEmpty()?"":" · "+answer.model));emit(progress,job,"Answer ready",100);
            try{DiagnosticsLog.info(db,"ZeroCostBrainEngine","answer","ok",focalItemId,0,0,job,0,total,new JSONObject().put("provider",answer.provider).put("model",answer.model).put("mode",mode).put("source_count",grounded.sources.size()).put("paid_api_used",false));}catch(Throwable ignored){}
            return new LocalAskRouter.Result(job,grounded,answer.text,answer.provider,"",mode,answer.tps,answer.tokens,answer.durationMs,total,retrieval,0,0,answer.durationMs,false);
        }catch(Throwable t){long total=SystemClock.elapsedRealtime()-wall;String err=safe(t);AiJobStore.fail(db,job,err,"Local Brain stopped safely");emit(progress,job,"Local Brain stopped safely",100);String fallback="combined".equals(mode)&&grounded!=null&&grounded.answer!=null?grounded.answer:"Local Brain stopped safely. No paid API was called.";return new LocalAskRouter.Result(job,grounded==null?empty(question):grounded,fallback,"failed",err,mode,0,0,0,total,retrieval,0,0,0,false);}
    }

    private static LocalText generateLocal(Context ctx,String prompt){
        try{
            NanoLocalBrain.Result n=NanoLocalBrain.generateBlocking(ctx,prompt);if(n.getOk())return new LocalText(true,n.getText(),"gemini-nano",n.getModel(),n.getDurationMs(),0,0,"");String nanoError=n.getError();
            if(LocalModelManager.installed(ctx)){
                try{String system="You are Cortex, a private on-device assistant. Follow the user's request directly. Never claim an action happened unless the prompt proves it. Do not reveal chain-of-thought. /no_think";LocalLlmBridge.CompletionResult q=LocalLlmBridge.completeCached(LocalModelManager.modelFile(ctx).getAbsolutePath(),prompt,system,MAX_QWEN_TOKENS);String text=LocalAskRouter.clean(q.getText());if(!text.isEmpty())return new LocalText(true,text,"local-qwen",LocalModelManager.MODEL_NAME,q.getDurationMs(),q.getTokensPerSecond(),q.getTokensGenerated(),"");return new LocalText(false,"","","",0,0,0,"Gemini Nano: "+nanoError+" | Qwen returned empty text");}catch(Throwable q){return new LocalText(false,"","","",0,0,0,"Gemini Nano: "+nanoError+" | Qwen: "+safe(q));}
            }
            return new LocalText(false,"","","",0,0,0,"Gemini Nano: "+nanoError+" | local Qwen not installed");
        }catch(Throwable t){return new LocalText(false,"","","",0,0,0,safe(t));}
    }

    static String buildPrompt(String question,GroundedAnswer g,boolean combined){
        StringBuilder b=new StringBuilder();b.append("You are Cortex, an on-device assistant. Answer naturally and concisely. Never expose chain-of-thought.\n");
        if(combined){b.append("For claims about the user's life, use ONLY the Cortex evidence below. If evidence is missing, say you don't know instead of inventing personal facts. General reasoning is allowed, but never fabricate a saved memory or completed action.\n\n");}
        b.append("USER REQUEST:\n").append(clip(question,900)).append("\n");
        if(combined&&g!=null){b.append("\nCORTEX GROUNDED DRAFT:\n").append(clip(g.answer,1200)).append("\n\nEVIDENCE:\n");for(int i=0;i<Math.min(5,g.sources.size());i++){KnowledgeItem k=g.sources.get(i).item;String body=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);b.append("[M").append(i+1).append("] ").append(clip(k.title,100)).append("\n").append(clip(body,600)).append("\n");}if(!g.openLoops.isEmpty()){b.append("\nOPEN LOOPS:\n");for(int i=0;i<Math.min(5,g.openLoops.size());i++)b.append("- ").append(clip(g.openLoops.get(i),260)).append('\n');}if(!g.decisions.isEmpty()){b.append("\nDECISIONS:\n");for(int i=0;i<Math.min(5,g.decisions.size());i++)b.append("- ").append(clip(g.decisions.get(i),260)).append('\n');}}
        b.append("\nReturn only the useful final answer.");return b.toString();
    }

    private static long createJob(VaultDb db,String q,String mode,long focal){try{return AiJobStore.create(db,"brain_local_zero_cost",mode,new JSONObject().put("question",q).put("paid_api_allowed",false).put("focal_item_id",focal).toString(),70);}catch(Exception e){return AiJobStore.create(db,"brain_local_zero_cost",mode,"{}",70);}}
    private static GroundedAnswer empty(String q){return new GroundedAnswer(q,"",0,new ArrayList<SemanticHit>(),new ArrayList<String>(),new ArrayList<String>());}
    private static JSONObject json(String answer,String provider,String mode,int sources,long total){JSONObject o=new JSONObject();try{o.put("answer",answer==null?"":answer);o.put("provider",provider);o.put("source_mode",mode);o.put("source_count",sources);o.put("total_ms",total);o.put("paid_api_used",false);}catch(Exception ignored){}return o;}
    private static void emit(LocalAskRouter.Progress p,long id,String label,int percent){if(p!=null)try{p.stage(id,label,percent);}catch(Throwable ignored){}}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}private static boolean empty(String s){return s==null||s.trim().isEmpty();}private static String safe(Throwable t){if(t==null)return"unknown error";String m=t.getMessage();return t.getClass().getSimpleName()+(m==null||m.trim().isEmpty()?"":": "+m.trim());}
    private static final class LocalText{final boolean ok;final String text,provider,model,error;final long durationMs;final float tps;final int tokens;LocalText(boolean o,String x,String p,String m,long d,float s,int n,String e){ok=o;text=x;provider=p;model=m;durationMs=d;tps=s;tokens=n;error=e;}}
}
