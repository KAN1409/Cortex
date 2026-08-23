package com.kareem.cortex;

import android.content.Context;
import java.util.*;

/** Grounded Ask Cortex router: retrieval stays deterministic, generation can run on local Qwen. */
public final class LocalAskRouter {
    private LocalAskRouter(){}

    public static final class Result {
        public final GroundedAnswer grounded;
        public final String answer,provider,error;
        public final float tokensPerSecond;
        public final int tokensGenerated;
        public final long durationMs;
        Result(GroundedAnswer g,String a,String p,String e,float tps,int tokens,long ms){grounded=g;answer=a;provider=p;error=e==null?"":e;tokensPerSecond=tps;tokensGenerated=tokens;durationMs=ms;}
    }

    public static Result ask(Context ctx,VaultDb db,String question){
        GroundedAnswer g=SecondBrainEngine.ask(db,question);
        if(!LocalModelManager.installed(ctx))return new Result(g,g.answer,"deterministic-grounded","Local Qwen runtime is not ready",0,0,0);
        try{
            String prompt=buildPrompt(question,g);
            String system="You are Cortex, a private second-brain assistant. Answer ONLY from the supplied memory evidence. Never invent facts. Preserve Egyptian Arabic and English code-switching naturally when present. If evidence is insufficient, say that clearly. Be concise but useful. Do not reveal chain-of-thought. /no_think";
            LocalLlmBridge.CompletionResult r=LocalLlmBridge.completeOnce(LocalModelManager.modelFile(ctx).getAbsolutePath(),prompt,system,320);
            String text=clean(r.getText());
            if(text.isEmpty())return new Result(g,g.answer,"deterministic-grounded","Local Qwen returned empty text",r.getTokensPerSecond(),r.getTokensGenerated(),r.getDurationMs());
            return new Result(g,text,"local-qwen","",r.getTokensPerSecond(),r.getTokensGenerated(),r.getDurationMs());
        }catch(Throwable t){
            return new Result(g,g.answer,"deterministic-grounded","Local Qwen failed: "+t.getClass().getSimpleName()+(t.getMessage()==null?"":": "+t.getMessage()),0,0,0);
        }
    }

    static String buildPrompt(String q,GroundedAnswer g){
        StringBuilder s=new StringBuilder();
        s.append("USER QUESTION:\n").append(q).append("\n\nMEMORY EVIDENCE:\n");
        int n=Math.min(7,g.sources.size());
        for(int i=0;i<n;i++){
            KnowledgeItem k=g.sources.get(i).item;
            String body=!empty(k.summary)?k.summary:(!empty(k.extractedText)?k.extractedText:k.rawText);
            if(body==null)body="";body=body.replace('\u0000',' ').trim();if(body.length()>750)body=body.substring(0,750)+"…";
            s.append("[M").append(i+1).append("] ").append(k.title==null?"Memory":k.title).append("\n").append(body).append("\n\n");
        }
        if(!g.openLoops.isEmpty()){s.append("OPEN LOOPS:\n");for(String x:g.openLoops)s.append("- ").append(x).append('\n');s.append('\n');}
        if(!g.decisions.isEmpty()){s.append("DECISIONS:\n");for(String x:g.decisions)s.append("- ").append(x).append('\n');s.append('\n');}
        s.append("Answer the question using only that evidence. When useful, cite evidence as [M1], [M2], etc. /no_think");
        return s.toString();
    }
    static String clean(String x){if(x==null)return"";String s=x.trim();s=s.replaceAll("(?s)<think>.*?</think>","").trim();return s;}
    static boolean empty(String s){return s==null||s.trim().isEmpty();}
}
