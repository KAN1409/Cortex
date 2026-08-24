package com.kareem.cortex;

import android.content.Context;
import android.graphics.Bitmap;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bundled offline Arabic OCR. Raw output is diagnostic; only centrally-gated text reaches Cortex evidence. */
public final class ArabicOcr {
    public interface Callback { void done(String text,String status); }
    public interface DetailedCallback { void done(Result result); }

    public static final class Result {
        public final String rawText,text,status,gateReason;
        public final int meanConfidence;
        public final boolean accepted;
        public final OcrGarbageGate.ArabicDecision gate;
        Result(String raw,String acceptedText,String status,int confidence,OcrGarbageGate.ArabicDecision gate){
            this.rawText=n(raw);this.text=n(acceptedText);this.status=n(status);this.meanConfidence=Math.max(0,Math.min(100,confidence));
            this.gate=gate;this.accepted=gate!=null&&gate.accepted;this.gateReason=gate==null?"":gate.reason;
        }
    }

    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private static final long MIN_MODEL_BYTES=500_000;
    private ArabicOcr(){}

    /** Compatibility path when no Latin cross-check is available. */
    public static void recognize(Context context,File image,Callback cb){
        recognizeDetailed(context,image,"",r->cb.done(r.text,r.status));
    }
    public static void recognizeDetailed(Context context,File image,DetailedCallback cb){recognizeDetailed(context,image,"",cb);}

    /** Production/test path: raw Tesseract is preserved, but only gate-approved Arabic is returned as evidence. */
    public static void recognizeDetailed(Context context,File image,String latinEvidence,DetailedCallback cb){
        Context app=context.getApplicationContext();
        EXEC.execute(()->{
            Bitmap bmp=null;TessBaseAPI tess=null;
            try{
                File model=ensureBundledModel(app);
                if(model==null||!model.exists()||model.length()<MIN_MODEL_BYTES){cb.done(failed("Arabic OCR model missing from app assets"));return;}

                // Tesseract previously decoded the original photo at full resolution here. Modern
                // phone images can exceed the app's safe heap/native-memory budget, causing a hard
                // process crash before Java can report an exception. Always work on a bounded copy.
                bmp=SafeImageDecoder.decode(image,2000,3_200_000L);
                if(bmp==null){cb.done(failed("Arabic OCR could not decode image safely"));return;}

                tess=new TessBaseAPI();
                String dataPath=new File(app.getFilesDir(),"tesseract").getAbsolutePath();
                if(!tess.init(dataPath,"ara")){cb.done(failed("Arabic OCR init failed"));return;}
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
                tess.setImage(bmp);
                String raw=n(tess.getUTF8Text());
                int confidence=0;try{confidence=tess.meanConfidence();}catch(Throwable ignored){}
                OcrGarbageGate.ArabicDecision gate=OcrGarbageGate.evaluateArabic(raw,confidence,latinEvidence);
                String status;
                if(raw.isEmpty())status="Arabic OCR ready • no Arabic text detected • bundled offline model";
                else if(gate.accepted)status="Arabic OCR accepted • confidence "+confidence+"% • "+gate.compactMetrics();
                else status="Arabic OCR rejected • confidence "+confidence+"% • "+gate.reason;
                cb.done(new Result(raw,gate.accepted?raw:"",status,confidence,gate));
            }catch(Throwable e){
                cb.done(failed("Arabic OCR unavailable: "+e.getClass().getSimpleName()));
            }finally{
                try{if(tess!=null)tess.recycle();}catch(Throwable ignored){}
                try{if(bmp!=null&&!bmp.isRecycled())bmp.recycle();}catch(Throwable ignored){}
            }
        });
    }

    public static boolean modelReady(Context ctx){try{File f=ensureBundledModel(ctx.getApplicationContext());return f.exists()&&f.length()>=MIN_MODEL_BYTES;}catch(Exception e){return false;}}

    private static Result failed(String status){OcrGarbageGate.ArabicDecision g=OcrGarbageGate.evaluateArabic("",0,"");return new Result("","",status,0,g);}
    private static String n(String s){return s==null?"":s.trim();}

    private static File ensureBundledModel(Context ctx)throws Exception{
        File root=new File(ctx.getFilesDir(),"tesseract");File dir=new File(root,"tessdata");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create tessdata directory");
        File target=new File(dir,"ara.traineddata");if(target.exists()&&target.length()>=MIN_MODEL_BYTES)return target;
        File tmp=new File(dir,"ara.traineddata.part");if(tmp.exists())tmp.delete();
        try(InputStream in=new BufferedInputStream(ctx.getAssets().open("tessdata/ara.traineddata"));OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){
            byte[] buf=new byte[64*1024];int n;long total=0;while((n=in.read(buf))!=-1){out.write(buf,0,n);total+=n;if(total>25_000_000)throw new IOException("Unexpected Arabic OCR model size");}
        }
        if(tmp.length()<MIN_MODEL_BYTES){tmp.delete();throw new IOException("Bundled Arabic OCR model incomplete");}
        if(target.exists()&&!target.delete())throw new IOException("Cannot replace Arabic OCR model");
        if(!tmp.renameTo(target)){copy(tmp,target);tmp.delete();}
        return target;
    }

    private static void copy(File a,File b)throws IOException{try(InputStream in=new FileInputStream(a);OutputStream out=new FileOutputStream(b)){byte[] buf=new byte[64*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}}
}
