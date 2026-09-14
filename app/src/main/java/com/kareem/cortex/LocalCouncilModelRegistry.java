package com.kareem.cortex;

import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.util.*;

/**
 * Mobile-only local council model registry.
 * Models are intentionally stored outside the APK and loaded one-at-a-time by llama.cpp.
 */
public final class LocalCouncilModelRegistry {
    public static final String PRIMARY="qwen30_thinking";
    public static final String ANALYST="mistral24_analyst";
    public static final String CRITIC="qwen32_critic";

    public static final class Model {
        public final String id,role,name,fileName,url,systemPrompt;
        public final long minBytes;
        public final int maxTokens;
        Model(String i,String r,String n,String f,String u,long min,int mt,String sp){
            id=i;role=r;name=n;fileName=f;url=u;minBytes=min;maxTokens=mt;systemPrompt=sp;
        }
    }

    private static final Model ANALYST_MODEL=new Model(
            ANALYST,"independent_analyst","Mistral Small 3.1 24B · IQ3_M",
            "mistralai_Mistral-Small-3.1-24B-Instruct-2503-IQ3_M.gguf",
            "https://huggingface.co/bartowski/mistralai_Mistral-Small-3.1-24B-Instruct-2503-GGUF/resolve/main/mistralai_Mistral-Small-3.1-24B-Instruct-2503-IQ3_M.gguf?download=true",
            10_000_000_000L,700,
            "You are Cortex Independent Analyst. Analyze the evidence independently. Look for missed relationships, temporal patterns, ambiguity, hidden assumptions and alternative explanations. Never invent facts. Every conclusion must point to supplied evidence IDs. Do not agree with prior models merely because they sound confident.");

    private static final Model CRITIC_MODEL=new Model(
            CRITIC,"adversarial_critic","Qwen3 32B · IQ2_M",
            "Qwen_Qwen3-32B-IQ2_M.gguf",
            "https://huggingface.co/bartowski/Qwen_Qwen3-32B-GGUF/resolve/main/Qwen_Qwen3-32B-IQ2_M.gguf?download=true",
            10_500_000_000L,750,
            "You are Cortex Adversarial Critic. Your job is to try to falsify the proposed conclusions. Search for entity mismatch, temporal mistakes, correlation presented as causation, missing evidence, stale assumptions, cross-space leakage and unsupported certainty. Preserve useful findings only when evidence survives criticism.");

    private LocalCouncilModelRegistry(){}

    public static Model primary(){
        return new Model(PRIMARY,"primary_investigator",LocalModelManager.MODEL_NAME,
                LocalModelManager.MODEL_FILE,LocalModelManager.MODEL_URL,10_000_000_000L,900,
                "You are Cortex Primary Investigator. Generate high-value hypotheses from the supplied private evidence, connect events across time, identify missing expected steps, contradictions and non-obvious patterns. Never invent evidence. Cite evidence IDs in every material claim.");
    }
    public static Model analyst(){return ANALYST_MODEL;}
    public static Model critic(){return CRITIC_MODEL;}
    public static List<Model> council(){return Collections.unmodifiableList(Arrays.asList(primary(),analyst(),critic()));}

    public static File file(Context c,Model m){
        if(PRIMARY.equals(m.id))return LocalModelManager.modelFile(c);
        File base=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File dir=new File(base==null?c.getFilesDir():base,"cortex-council");
        if(!dir.exists())dir.mkdirs();
        return new File(dir,m.fileName);
    }
    public static File partFile(Context c,Model m){File f=file(c,m);return new File(f.getParentFile(),f.getName()+".part");}

    public static boolean ready(Context c,Model m){
        if(PRIMARY.equals(m.id))return LocalModelManager.installed(c);
        File f=file(c,m);return f.exists()&&f.length()>=m.minBytes&&gguf(f);
    }
    public static boolean present(Context c,Model m){File f=file(c,m);return f.exists()&&f.length()>=m.minBytes;}
    public static long bytes(Context c,Model m){File f=file(c,m);return f.exists()?f.length():0;}

    public static int readyCount(Context c){int n=0;for(Model m:council())if(ready(c,m))n++;return n;}
    public static boolean fullCouncilReady(Context c){return readyCount(c)==council().size();}

    private static boolean gguf(File f){
        try(InputStream in=new FileInputStream(f)){byte[] h=new byte[4];return in.read(h)==4&&h[0]=='G'&&h[1]=='G'&&h[2]=='U'&&h[3]=='F';}
        catch(Throwable t){return false;}
    }
}
