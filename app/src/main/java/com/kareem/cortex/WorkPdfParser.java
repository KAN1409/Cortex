package com.kareem.cortex;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Digital-PDF text extraction with exact page provenance and bounded on-device OCR fallback. */
public final class WorkPdfParser {
    public static final String VERSION="work_pdf_parser_003";
    private static volatile boolean initialized=false;
    private static final int OCR_DPI=160;
    static final long OCR_PAGE_BUDGET_MS=40_000L;
    static final long LATIN_STAGE_BUDGET_MS=20_000L;
    private WorkPdfParser(){}

    public static WorkParsedDocument parse(Context context,Uri uri)throws Exception{
        init(context);
        WorkParsedDocument out=new WorkParsedDocument();out.parserVersion=VERSION;
        try(InputStream in=context.getContentResolver().openInputStream(uri);PDDocument doc=PDDocument.load(in)){
            PDFTextStripper stripper=new PDFTextStripper();
            PDFRenderer renderer=new PDFRenderer(doc);
            int unresolvedOcrPages=0;
            for(int page=1;page<=doc.getNumberOfPages();page++){
                stripper.setStartPage(page);stripper.setEndPage(page);
                String text=clean(stripper.getText(doc));
                if(!text.isEmpty()){
                    WorkParsedDocument.Block b=new WorkParsedDocument.Block("PAGE",text);b.pageNumber=page;out.blocks.add(b);
                    continue;
                }

                String ocr=ocrPage(context,renderer,page-1);
                if(ocr.isEmpty()){
                    unresolvedOcrPages++;
                    continue;
                }
                WorkParsedDocument.Block b=new WorkParsedDocument.Block("OCR_PAGE",ocr);b.pageNumber=page;out.blocks.add(b);
            }
            out.needsOcr=unresolvedOcrPages>0;
        }
        return out;
    }

    private static String ocrPage(Context context,PDFRenderer renderer,int pageIndex){
        if(context==null||renderer==null)return "";
        if(!CapabilitySupervisor.allowed(context,CapabilitySupervisor.Capability.OCR_NATIVE))return "";
        final long startedAt=SystemClock.elapsedRealtime();
        Bitmap bitmap=null;File temp=null;
        try{
            bitmap=renderer.renderImageWithDPI(pageIndex,OCR_DPI);
            if(bitmap==null)return "";

            long remaining=remainingBudgetMs(startedAt,SystemClock.elapsedRealtime());
            OcrStageResult latin=latinBlocking(bitmap,latinStageBudgetMs(remaining));
            if(latin.failure!=null&&shouldRecordEngineFailure(latin.failure)){
                CapabilitySupervisor.recordFailure(context,CapabilitySupervisor.Capability.OCR_NATIVE,latin.failure);
            }

            remaining=remainingBudgetMs(startedAt,SystemClock.elapsedRealtime());
            String arabic="";
            if(remaining>0L){
                try{
                    temp=File.createTempFile("cortex_work_pdf_", ".png", context.getCacheDir());
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                        if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Could not encode OCR page");
                    }
                    remaining=remainingBudgetMs(startedAt,SystemClock.elapsedRealtime());
                    if(remaining>0L)arabic=arabicBlocking(context,temp,latin.text,remaining).text;
                }catch(IOException ignored){
                    // A page-specific render/encode problem must not quarantine the global OCR capability.
                }
            }
            return mergeOcr(latin.text,arabic);
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            return "";
        }catch(Throwable ignored){
            // PDF/page corruption is document-local evidence failure, not proof that OCR_NATIVE is unhealthy.
            return "";
        }finally{
            try{if(temp!=null&&temp.exists())temp.delete();}catch(Throwable ignored){}
            try{if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();}catch(Throwable ignored){}
        }
    }

    private static OcrStageResult latinBlocking(Bitmap bitmap,long timeoutMs)throws InterruptedException{
        if(bitmap==null||timeoutMs<=0L)return OcrStageResult.timeout();
        final CountDownLatch latch=new CountDownLatch(1);
        final AtomicReference<String> text=new AtomicReference<>("");
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try{
            recognizer.process(InputImage.fromBitmap(bitmap,0))
                    .addOnSuccessListener(result->{text.set(clean(result==null?"":result.getText()));latch.countDown();})
                    .addOnFailureListener(error->{failure.set(error);latch.countDown();});
            boolean completed=latch.await(timeoutMs,TimeUnit.MILLISECONDS);
            if(!completed)return OcrStageResult.timeout();
            return new OcrStageResult(clean(text.get()),true,false,failure.get());
        }finally{
            try{recognizer.close();}catch(Throwable ignored){}
        }
    }

    private static OcrStageResult arabicBlocking(Context context,File image,String latinEvidence,long timeoutMs)throws InterruptedException{
        if(timeoutMs<=0L)return OcrStageResult.timeout();
        final CountDownLatch latch=new CountDownLatch(1);
        final AtomicReference<String> text=new AtomicReference<>("");
        ArabicOcr.recognizeDetailed(context,image,latinEvidence,result->{
            if(result!=null&&result.accepted)text.set(clean(result.text));
            latch.countDown();
        });
        boolean completed=latch.await(timeoutMs,TimeUnit.MILLISECONDS);
        if(!completed)return OcrStageResult.timeout();
        return new OcrStageResult(clean(text.get()),true,false,null);
    }

    static long remainingBudgetMs(long startedAtMs,long nowMs){
        long elapsed=Math.max(0L,nowMs-startedAtMs);
        return Math.max(0L,OCR_PAGE_BUDGET_MS-elapsed);
    }

    static long latinStageBudgetMs(long remainingMs){
        return Math.max(0L,Math.min(LATIN_STAGE_BUDGET_MS,remainingMs));
    }

    static boolean shouldRecordEngineFailure(Throwable error){
        return error!=null&&!(error instanceof InterruptedException);
    }

    private static String mergeOcr(String latin,String arabic){
        String a=clean(latin),b=clean(arabic);
        if(a.isEmpty())return b;if(b.isEmpty())return a;
        String na=normalizeForCompare(a),nb=normalizeForCompare(b);
        if(!na.isEmpty()&&!nb.isEmpty()){
            if(na.contains(nb))return a;
            if(nb.contains(na))return b;
        }
        return a+"\n"+b;
    }

    private static String normalizeForCompare(String s){return clean(s).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+","");}

    private static void init(Context context){
        if(initialized)return;
        synchronized(WorkPdfParser.class){if(!initialized){PDFBoxResourceLoader.init(context.getApplicationContext());initialized=true;}}
    }
    private static String clean(String s){return s==null?"":s.replaceAll("[ \\t]+"," ").replaceAll("\\n{3,}","\\n\\n").trim();}

    private static final class OcrStageResult{
        final String text;final boolean completed,timedOut;final Throwable failure;
        OcrStageResult(String text,boolean completed,boolean timedOut,Throwable failure){this.text=clean(text);this.completed=completed;this.timedOut=timedOut;this.failure=failure;}
        static OcrStageResult timeout(){return new OcrStageResult("",false,true,null);}
    }
}
