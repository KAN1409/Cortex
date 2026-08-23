package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.*;

/** Grounded Ask router. Fast retrieval is the default UI path; local generation is an optional refinement. */
public final class LocalAskRouter {
    private LocalAskRouter(){}

    public static final class Result {
        public final GroundedAnswer grounded;
        public final String answer,provider,error;
        public final float tokensPerSecond;
        public final int tokensGenerated;
        public final long durationMs,totalMs,retrievalMs,promptBuildMs,modelLoadMs,generationMs;
        public final boolean cacheHit;
        Result(GroundedAnswer g,String a,String p,String e,float tps,int tokens,long modelDuration,long total,long retrieval,long prompt,long load,long generation,boolean hit){grounded=g;answer=a;provider=p;error=e==null?"":e;tokensPerSecond=tps;tokensGenerated=tokens;durationMs=modelDuration;totalMs=total;retrievalMs=retrieval;promptBuildMs=prompt;modelLoadMs=load;generationMs=generation;cacheHit=hit;}
    }

    /** Returns as soon as deterministic Vault retrieval has produced a grounded answer. */
    public static Result fast(Context ctx,VaultDb db,String question){
        long wall=SystemClock.elapsedRealtime();InteractionTelemetry.log(db,"Brain","ask_cortex","request_started",0,0,"running","Fast grounded ask received",null);long rt=SystemClock.elapsedRealtime();GroundedAnswer g=SecondBrainEngine.ask(db,question);long retrieval=SystemClock.elapsedRealtime()-rt,total=SystemClock.elapsedRealtime()-wall;try{InteractionTelemetry.log(db,"Brain","ask_cortex","complete",0,total,"grounded-fast",g.sources.size()+" grounded source(s)",new JSONObject().put("source_count",g.sources.size()).put("retrieval_ms",retrieval));}catch(Exception ignored){}return new Result(g,g.answer,"grounded-fast","",0,0,0,total,retrieval,0,0,0,false);
    }

    /** Optional local-Qwen refinement. Never required for the user to receive an answer. */
    public static Result ask(Context ctx,VaultDb db,String question){
        long wall=SystemClock.elapsedRealtime();InteractionTelemetry.log(db,"Brain","ask_cortex","request_started",0,0,"running","Local refinement requested",null);
        long rt=SystemClock.elapsedRealtime();GroundedAnswer g=SecondBrainEngine.ask(db,question);long retrieval=SystemClock.elapsedRealtime()-rt;
        try{InteractionTelemetry.log(db,"Brain","ask_cortex","retrieval_complete",0,retrieval,"ok",g.sources.size()+" grounded source(s)",new JSONObject().put("source_count",g.sources.size()).put("open_loops",g.openLoops.size()).put("decisions",g.decisions.size()));}catch(Exception ignored){}
        if(!LocalModelManager.installed(ctx)){long total=SystemClock.elapsedRealtime()-wall;return new Result(g,g.answer,"deterministic-grounded","Local Qwen runtime is not ready",0,0,0,total,retrieval,0,0,0,false);}
        try{
            long pt=SystemClock.elapsedRealtime();String prompt=buildPrompt(question,g);String system="You are Cortex, a private second-brain assistant. Answer ONLY from the supplied memory evidence. Never invent facts. Preserve Egyptian Arabic and English code-switching naturally when present. If evidence is insufficient, say that clearly. Be concise and useful. Do not reveal chain-of-thought. /no_think";long promptMs=SystemClock.elapsedRealtime()-pt;
            LocalLlmBridge.CompletionResult r=LocalLlmBridge.completeCached(LocalModelManager.modelFile(ctx).getAbsolutePath(),prompt,system,180);
            String text=clean(r.getText());long total=SystemClock.elapsedRealtime()-wall;JSONObject perf=new JSONObject().put("total_ms",total).put("retrieval_ms",retrieval).put("prompt_build_ms",promptMs).put("model_call_ms",r.getDurationMs()).put("model_load_ms",r.getModelLoadMs()).put("generation_ms",r.getGenerationMs()).put("cache_hit",r.getCacheHit()).put("tokens",r.getTokensGenerated()).put("tokens_per_second",r.getTokensPerSecond()).put("source_count",g.sources.size());
            InteractionTelemetry.log(db,"Brain","ask_cortex","model_complete",0,r.getDurationMs(),"ok",r.getCacheHit()?"Warm local Qwen completion":"Cold local Qwen completion",perf);
            if(text.isEmpty())return new Result(g,g.answer,"deterministic-grounded","Local Qwen returned empty text",r.getTokensPerSecond(),r.getTokensGenerated(),r.getDurationMs(),total,retrieval,promptMs,r.getModelLoadMs(),r.getGenerationMs(),r.getCacheHit());
            InteractionTelemetry.log(db,"Brain","ask_cortex","complete",0,total,"ok","Local refinement ready",perf);return new Result(g,text,"local-qwen","",r.getTokensPerSecond(),r.getTokensGenerated(),r.getDurationMs(),total,retrieval,promptMs,r.getModelLoadMs(),r.getGenerationMs(),r.getCacheHit());
        }catch(Throwable t){long total=SystemClock.elapsedRealtime()-wall;String err="Local Qwen failed: "+t.getClass().getSimpleName()+(t.getMessage()==null?"":": "+t.getMessage());InteractionTelemetry.log(db,"Brain","ask_cortex","complete",0,total,"fallback",err,null);return new Result(g,g.answer,"deterministic-grounded",err,0,0,0,total,retrieval,0,0,0,false);}
    }

    static String buildPrompt(String q,GroundedAnswer g){StringBuilder s=new StringBuilder();s.append("USER QUESTION:\n").append(q).append("\n\nMEMORY EVIDENCE:\n");int n=Math.min(6,g.sources.size());for(int i=0;i<n;i++){KnowledgeItem k=g.sources.get(i).item;String body=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);if(body==null)body="";body=body.replace('\u0000',' ').trim();if(body.length()>650)body=body.substring(0,650)+"…";s.append("[M").append(i+1).append("] ").append(k.title==null?"Memory":k.title).append("\n").append(body).append("\n\n");}if(!g.openLoops.isEmpty()){s.append("OPEN LOOPS:\n");for(String x:g.openLoops)s.append("- ").append(x).append('\n');s.append('\n');}if(!g.decisions.isEmpty()){s.append("DECISIONS:\n");for(String x:g.decisions)s.append("- ").append(x).append('\n');s.append('\n');}s.append("Answer using only that evidence. When useful, cite [M1], [M2], etc. /no_think");return s.toString();}
    static String clean(String x){if(x==null)return"";String s=x.trim();return s.replaceAll("(?s)<think>.*?</think>","").trim();}static boolean empty(String s){return s==null||s.trim().isEmpty();}
}
