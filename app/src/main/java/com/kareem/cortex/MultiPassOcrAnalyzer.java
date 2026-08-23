package com.kareem.cortex;

import android.content.Context;
import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Three-pass OCR with one shared evidence-quality definition. */
public final class MultiPassOcrAnalyzer {
    private MultiPassOcrAnalyzer(){}
    public static final class Result {public final AnalysisResult analysis;public final ArrayList<OcrPassStore.Pass> passes;Result(AnalysisResult a,ArrayList<OcrPassStore.Pass> p){analysis=a;passes=p;}}

    public static Result analyze(Context ctx,KnowledgeItem original)throws Exception{
        File src=new File(original.attachmentPath);if(!src.exists())throw new IllegalArgumentException("Image file missing");File cache=new File(ctx.getCacheDir(),"ocr_variants");cache.mkdirs();
        ArrayList<Candidate> cs=new ArrayList<>();File c=null,e=null;try{
            cs.add(run(ctx,original,"original"));
            c=OcrImageVariants.contrast(src,cache);cs.add(run(ctx,copyWithPath(original,c.getAbsolutePath()),"contrast"));
            e=OcrImageVariants.enlarged(src,cache);cs.add(run(ctx,copyWithPath(original,e.getAbsolutePath()),"enlarged_contrast"));
        }finally{if(c!=null)c.delete();if(e!=null)e.delete();}
        Candidate best=cs.get(0);for(Candidate x:cs)if(x.score>best.score)best=x;
        AnalysisResult r=best.analysis;r.engine="cortex_vision_multipass_v6";r.version="6";
        r.visionFields.add(new AnalysisResult.VisionField("OCR strategy","3-pass safe evidence: original + contrast + enlarged contrast",1.0));
        r.visionFields.add(new AnalysisResult.VisionField("Selected OCR pass",best.name+" • quality "+String.format(Locale.US,"%.2f",best.score)+" • "+best.label,0.99));
        for(Candidate x:cs)r.visionFields.add(new AnalysisResult.VisionField("OCR pass "+x.name,"quality "+String.format(Locale.US,"%.2f",x.score)+" • "+x.label+" • "+x.reason,0.95));
        ArrayList<OcrPassStore.Pass> ps=new ArrayList<>();for(Candidate x:cs)ps.add(new OcrPassStore.Pass(x.name,x.analysis.engine,x.analysis.extractedText,x.score,x==best,x.reason));
        return new Result(r,ps);
    }

    static Candidate run(Context ctx,KnowledgeItem item,String name)throws Exception{
        CountDownLatch l=new CountDownLatch(1);AtomicReference<AnalysisResult> ok=new AtomicReference<>();AtomicReference<Exception> bad=new AtomicReference<>();
        OcrAnalyzer.analyze(ctx,item,new OcrAnalyzer.Callback(){public void ok(AnalysisResult r){ok.set(r);l.countDown();}public void fail(Exception e){bad.set(e);l.countDown();}});
        if(!l.await(150,TimeUnit.SECONDS))throw new Exception("OCR pass timeout: "+name);if(ok.get()==null)throw bad.get()==null?new Exception("OCR pass failed: "+name):bad.get();
        AnalysisResult a=ok.get();OcrGarbageGate.Quality q=OcrGarbageGate.assessText(a.extractedText);return new Candidate(name,a,q.score,q.label,q.reason);
    }

    static double quality(String s){return OcrGarbageGate.scoreCandidate(s);}
    static String reason(String s,double ignored){return OcrGarbageGate.candidateReason(s);}
    static KnowledgeItem copyWithPath(KnowledgeItem k,String path){return new KnowledgeItem(k.id,k.type,k.source,k.title,k.rawText,k.extractedText,k.summary,k.category,k.tags,path,k.status,k.fingerprint,k.analysisError,k.metadataJson,k.createdAt,k.updatedAt);}
    static final class Candidate{final String name,label,reason;final AnalysisResult analysis;final double score;Candidate(String n,AnalysisResult a,double s,String l,String r){name=n;analysis=a;score=s;label=l;reason=r;}}
}
