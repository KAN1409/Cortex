package com.kareem.cortex;

import android.content.Context;
import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Three-pass OCR: original, high-contrast grayscale, enlarged+contrast. Chooses best pass by quality instead of blindly merging everything. */
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
        AnalysisResult r=best.analysis;r.engine="cortex_vision_multipass_v5";r.version="5";
        r.visionFields.add(new AnalysisResult.VisionField("OCR strategy","3-pass consensus: original + contrast + enlarged contrast",1.0));
        r.visionFields.add(new AnalysisResult.VisionField("Selected OCR pass",best.name+" • quality "+String.format(Locale.US,"%.2f",best.score),0.99));
        for(Candidate x:cs)r.visionFields.add(new AnalysisResult.VisionField("OCR pass "+x.name,"quality "+String.format(Locale.US,"%.2f",x.score)+" • "+x.reason,0.95));
        ArrayList<OcrPassStore.Pass> ps=new ArrayList<>();for(Candidate x:cs)ps.add(new OcrPassStore.Pass(x.name,x.analysis.engine,x.analysis.extractedText,x.score,x==best,x.reason));
        return new Result(r,ps);
    }

    static Candidate run(Context ctx,KnowledgeItem item,String name)throws Exception{
        CountDownLatch l=new CountDownLatch(1);AtomicReference<AnalysisResult> ok=new AtomicReference<>();AtomicReference<Exception> bad=new AtomicReference<>();
        OcrAnalyzer.analyze(ctx,item,new OcrAnalyzer.Callback(){public void ok(AnalysisResult r){ok.set(r);l.countDown();}public void fail(Exception e){bad.set(e);l.countDown();}});
        if(!l.await(150,TimeUnit.SECONDS))throw new Exception("OCR pass timeout: "+name);if(ok.get()==null)throw bad.get()==null?new Exception("OCR pass failed: "+name):bad.get();
        AnalysisResult a=ok.get();double q=quality(a.extractedText);return new Candidate(name,a,q,reason(a.extractedText,q));
    }

    static double quality(String s){if(s==null||s.trim().isEmpty())return 0;int letters=0,arabic=0,latin=0,weird=0,digits=0;for(char c:s.toCharArray()){if(Character.isLetter(c)){letters++;if(c>=0x0600&&c<=0x06ff)arabic++;else if((c>='A'&&c<='Z')||(c>='a'&&c<='z'))latin++;}else if(Character.isDigit(c))digits++;else if(!Character.isWhitespace(c)&&",.;:!?()[]{}%$€£+-_/\\@#&'\"•→←=×".indexOf(c)<0)weird++;}
        int n=Math.max(1,s.length());double density=(letters+digits)/(double)n;double q=Math.min(1.0,0.18+density*0.9+Math.min(0.18,letters/700.0));q-=Math.min(0.42,weird/(double)n*2.6);String[] lines=s.split("\\r?\\n");int garbage=0;for(String line:lines)if(line.length()>5&&line.replaceAll("[\\p{L}\\p{N} ]","").length()>line.length()*0.35)garbage++;q-=Math.min(0.25,garbage*0.025);if(arabic>0&&latin>0)q+=0.04;return Math.max(0,Math.min(1,q));}
    static String reason(String s,double q){if(s==null||s.trim().isEmpty())return"empty";if(q<0.38)return"low-quality / gibberish risk";if(q<0.62)return"usable but uncertain";return"cleanest candidate";}
    static KnowledgeItem copyWithPath(KnowledgeItem k,String path){return new KnowledgeItem(k.id,k.type,k.source,k.title,k.rawText,k.extractedText,k.summary,k.category,k.tags,path,k.status,k.fingerprint,k.analysisError,k.metadataJson,k.createdAt,k.updatedAt);}
    static final class Candidate{final String name,reason;final AnalysisResult analysis;final double score;Candidate(String n,AnalysisResult a,double s,String r){name=n;analysis=a;score=s;reason=r;}}
}
