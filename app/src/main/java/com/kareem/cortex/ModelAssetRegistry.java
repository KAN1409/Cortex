package com.kareem.cortex;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Production-side registry of AI/runtime assets. Exact future model files remain
 * benchmark-gated; this registry describes roles without making Termux a runtime dependency.
 */
public final class ModelAssetRegistry {
    public enum State { READY, PRESENT_UNVERIFIED, MISSING, PLANNED, UNAVAILABLE }

    public static final class Asset {
        public final String id,role,name,provider,format,detail;
        public final State state;
        public final long bytes;
        public final boolean local,requiredForOfflineBaseline;
        Asset(String id,String role,String name,String provider,String format,State state,long bytes,boolean local,boolean required,String detail){
            this.id=id;this.role=role;this.name=name;this.provider=provider;this.format=format;this.state=state;this.bytes=bytes;this.local=local;this.requiredForOfflineBaseline=required;this.detail=detail==null?"":detail;
        }
        public boolean ready(){return state==State.READY;}
    }

    private ModelAssetRegistry(){}

    public static List<Asset> inventory(Context context){
        Context c=context.getApplicationContext();ArrayList<Asset> out=new ArrayList<>();

        LocalModelManager.Status primary=LocalModelManager.status(c);
        State primaryState=primary.verified?(LocalLlmRuntime.ready(c)?State.READY:State.PRESENT_UNVERIFIED):(LocalModelManager.filePresent(c)?State.PRESENT_UNVERIFIED:State.MISSING);
        out.add(new Asset("primary_llm","general_llm",LocalModelManager.MODEL_NAME,"local","GGUF",primaryState,LocalModelManager.size(c),true,true,primary.verified?(LocalLlmRuntime.ready(c)?"Verified + inference self-test ready":"Verified model; runtime self-test pending"):primary.label));

        out.add(new Asset("legacy_semantic","embedding_baseline","Local semantic baseline","cortex","built-in",State.READY,0,true,true,LocalSemanticEmbedder.DIMS+"-dim deterministic multilingual baseline; learned embedding model is planned"));

        boolean ocr=ArabicOcr.modelReady(c);
        out.add(new Asset("arabic_ocr","ocr_arabic","Arabic OCR","tesseract","traineddata",ocr?State.READY:State.UNAVAILABLE,ocr?ocrBytes(c):0,true,false,ocr?"Bundled offline Arabic OCR model ready":"Bundled Arabic OCR asset unavailable"));

        out.add(new Asset("secondary_llm","second_opinion_llm","Independent second local LLM","benchmark-gated","GGUF/compatible",State.PLANNED,0,true,false,"Exact model/quantization will be selected after on-device benchmark"));
        out.add(new Asset("router_llm","routing_filter","Fast local router/filter model","benchmark-gated","GGUF/compatible",State.PLANNED,0,true,false,"Small model role reserved; exact model not locked"));
        out.add(new Asset("learned_embedding","embedding","Multilingual learned embedding model","benchmark-gated","runtime TBD",State.PLANNED,0,true,false,"Will replace/augment the deterministic semantic baseline after benchmark"));
        out.add(new Asset("reranker","reranker","Local retrieval reranker","benchmark-gated","runtime TBD",State.PLANNED,0,true,false,"Optional; enabled only if retrieval evaluation shows measurable value"));
        out.add(new Asset("local_asr_fast","asr_fast","Fast local ASR","benchmark-gated","runtime TBD",State.PLANNED,0,true,false,"Arabic/English code-switch benchmark required before download is locked"));
        out.add(new Asset("local_asr_quality","asr_quality","Quality/arbiter local ASR","benchmark-gated","runtime TBD",State.PLANNED,0,true,false,"Selective low-confidence segment adjudication role"));
        out.add(new Asset("local_vision","vision_multimodal","Local multimodal vision model","benchmark-gated","runtime TBD",State.PLANNED,0,true,false,"Optional local screenshot/image understanding after device benchmark"));

        return Collections.unmodifiableList(out);
    }

    public static Asset find(Context c,String id){for(Asset a:inventory(c))if(a.id.equals(id))return a;return null;}
    public static int readyCount(Context c){int n=0;for(Asset a:inventory(c))if(a.ready())n++;return n;}
    public static int plannedCount(Context c){int n=0;for(Asset a:inventory(c))if(a.state==State.PLANNED)n++;return n;}

    private static long ocrBytes(Context c){try{File f=new File(new File(new File(c.getFilesDir(),"tesseract"),"tessdata"),"ara.traineddata");return f.exists()?f.length():0;}catch(Exception e){return 0;}}
}
