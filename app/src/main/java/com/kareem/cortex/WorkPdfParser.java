package com.kareem.cortex;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
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
    public static final String VERSION="work_pdf_parser_002";
    private static volatile boolean initialized=false;
    private static final int OCR_DPI=160;
    private static final long OCR_TIMEOUT_SECONDS=40;
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
        Bitmap bitmap=null;File temp=null;
        try{
            bitmap=renderer.renderImageWithDPI(pageIndex,OCR_DPI);
            if(bitmap==null)return "";
            String latin=latinBlocking(bitmap);
            String arabic="";
            try{
                temp=File.createTempFile("cortex_work_pdf_", ".png", context.getCacheDir());
                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                    if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Could not encode OCR page");
                }
                arabic=arabicBlocking(context,temp,latin);
            }catch(Throwable ignored){}
            return mergeOcr(latin,arabic);
        }catch(Throwable t){
            CapabilitySupervisor.recordFailure(context,CapabilitySupervisor.Capability.OCR_NATIVE,t);
            return "";
        }finally{
            try{if(temp!=null&&temp.exists())temp.delete();}catch(Throwable ignored){}
            try{if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();}catch(Throwable ignored){}
        }
    }

    private static String latinBlocking(Bitmap bitmap)throws InterruptedException{
        if(bitmap==null)return "";
        final CountDownLatch latch=new CountDownLatch(1);
        final AtomicReference<String> text=new AtomicReference<>("");
        final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try{
            recognizer.process(InputImage.fromBitmap(bitmap,0))
                    .addOnSuccessListener(result->{text.set(clean(result==null?"":result.getText()));latch.countDown();})
                    .addOnFailureListener(error->latch.countDown());
            latch.await(OCR_TIMEOUT_SECONDS,TimeUnit.SECONDS);
            return clean(text.get());
        }finally{
            try{recognizer.close();}catch(Throwable ignored){}
        }
    }

    private static String arabicBlocking(Context context,File image,String latinEvidence)throws InterruptedException{
        final CountDownLatch latch=new CountDownLatch(1);
        final AtomicReference<String> text=new AtomicReference<>("");
        ArabicOcr.recognizeDetailed(context,image,latinEvidence,result->{
            if(result!=null&&result.accepted)text.set(clean(result.text));
            latch.countDown();
        });
        latch.await(OCR_TIMEOUT_SECONDS,TimeUnit.SECONDS);
        return clean(text.get());
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
}
