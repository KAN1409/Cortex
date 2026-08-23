package com.kareem.cortex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bundled offline Arabic OCR with a conservative wrong-script/gibberish gate. */
public final class ArabicOcr {
    public interface Callback { void done(String text,String status); }
    public interface DetailedCallback { void done(Result result); }

    public static final class Result {
        public final String rawText,text,status,gateReason;
        public final int meanConfidence;
        public final boolean accepted;
        Result(String raw,String acceptedText,String status,int confidence,boolean accepted,String reason){
            this.rawText=n(raw);this.text=n(acceptedText);this.status=n(status);this.meanConfidence=Math.max(0,Math.min(100,confidence));this.accepted=accepted;this.gateReason=n(reason);
        }
    }

    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private static final long MIN_MODEL_BYTES=500_000;
    private ArabicOcr(){}

    /** Compatibility path: downstream production receives only accepted Arabic evidence. */
    public static void recognize(Context context,File image,Callback cb){
        recognizeDetailed(context,image,r->cb.done(r.text,r.status));
    }

    /** Test/diagnostic path preserves raw Tesseract output while production can use r.text safely. */
    public static void recognizeDetailed(Context context,File image,DetailedCallback cb){
        Context app=context.getApplicationContext();
        EXEC.execute(()->{
            Bitmap bmp=null;TessBaseAPI tess=null;
            try{
                File model=ensureBundledModel(app);
                if(model==null||!model.exists()||model.length()<MIN_MODEL_BYTES){cb.done(failed("Arabic OCR model missing from app assets"));return;}
                bmp=BitmapFactory.decodeFile(image.getAbsolutePath());
                if(bmp==null){cb.done(failed("Arabic OCR could not decode image"));return;}
                tess=new TessBaseAPI();
                String dataPath=new File(app.getFilesDir(),"tesseract").getAbsolutePath();
                if(!tess.init(dataPath,"ara")){cb.done(failed("Arabic OCR init failed"));return;}
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
                tess.setImage(bmp);
                String raw=n(tess.getUTF8Text());
                int confidence=0;try{confidence=tess.meanConfidence();}catch(Throwable ignored){}
                Gate g=gate(raw,confidence);
                String status;
                if(raw.isEmpty())status="Arabic OCR ready • no Arabic text detected • bundled offline model";
                else if(g.accept)status="Arabic OCR accepted • confidence "+confidence+"% • bundled offline model";
                else status="Arabic OCR rejected • confidence "+confidence+"% • "+g.reason;
                cb.done(new Result(raw,g.accept?raw:"",status,confidence,g.accept,g.reason));
            }catch(Exception e){
                cb.done(failed("Arabic OCR unavailable: "+e.getClass().getSimpleName()));
            }finally{
                try{if(tess!=null)tess.recycle();}catch(Exception ignored){}
                try{if(bmp!=null&&!bmp.isRecycled())bmp.recycle();}catch(Exception ignored){}
            }
        });
    }

    /** Conservative evidence gate: false negatives are preferable to poisoning Cortex with wrong-script text. */
    static Gate gate(String text,int confidence){
        String x=n(text);if(x.isEmpty())return new Gate(true,"empty");
        int arabic=0,letters=0,digits=0;for(int i=0;i<x.length();i++){char c=x.charAt(i);if(Character.isLetter(c)){letters++;if(isArabic(c))arabic++;}else if(Character.isDigit(c))digits++;}
        if(arabic<2)return new Gate(false,"no credible Arabic-script evidence");
        if(confidence<42)return new Gate(false,"low Tesseract confidence / wrong-script risk");

        String[] tokens=x.split("\\s+");int wordTokens=0,arabicWords=0,shortTokens=0,common=0;
        HashSet<String> commonWords=new HashSet<>(Arrays.asList("من","في","على","عن","مع","الى","إلى","هو","هي","هذا","هذه","تم","لا","نعم","كان","كانت","انا","أنا","انت","أنت","احنا","إحنا","اللي","ده","دي","مش","عشان","بعد","قبل","يوم","اليوم","بكره","بكرة"));
        for(String raw:tokens){String t=raw.replaceAll("[^\\p{L}\\p{N}]","");if(t.isEmpty())continue;wordTokens++;if(t.length()<=2)shortTokens++;int ar=0,alnum=0;for(int i=0;i<t.length();i++){char c=t.charAt(i);if(Character.isLetterOrDigit(c)){alnum++;if(isArabic(c))ar++;}}if(alnum>0&&ar/(double)alnum>=0.72&&ar>=2)arabicWords++;if(commonWords.contains(t))common++;}
        double digitShare=digits/(double)Math.max(1,letters+digits),shortShare=shortTokens/(double)Math.max(1,wordTokens),arabicWordShare=arabicWords/(double)Math.max(1,wordTokens);
        if(confidence<55&&digitShare>0.20)return new Gate(false,"digit-heavy low-confidence Arabic hallucination risk");
        if(confidence<52&&wordTokens>=4&&arabicWordShare<0.45)return new Gate(false,"fragmented low-confidence Arabic text");
        if(x.length()>120&&confidence<65&&common==0&&(digitShare>0.10||shortShare>0.28))return new Gate(false,"long Arabic output lacks language plausibility");
        return new Gate(true,"accepted");
    }

    public static boolean modelReady(Context ctx){try{File f=ensureBundledModel(ctx.getApplicationContext());return f.exists()&&f.length()>=MIN_MODEL_BYTES;}catch(Exception e){return false;}}

    private static Result failed(String status){return new Result("","",status,0,false,status);}
    private static boolean isArabic(char c){return (c>=0x0600&&c<=0x06ff)||(c>=0x0750&&c<=0x077f)||(c>=0x08a0&&c<=0x08ff);}
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
    static final class Gate{final boolean accept;final String reason;Gate(boolean a,String r){accept=a;reason=r;}}
}
