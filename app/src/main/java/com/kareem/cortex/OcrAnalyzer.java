package com.kareem.cortex;

import android.content.Context;
import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;

public final class OcrAnalyzer {
    public interface Callback { void ok(AnalysisResult r); void fail(Exception e); }
    private OcrAnalyzer(){}

    public static void analyze(Context ctx,KnowledgeItem item,Callback cb){
        TextRecognizer recognizer=null;Bitmap decoded=null;
        try{
            if(item.attachmentPath==null||item.attachmentPath.isEmpty())throw new IllegalArgumentException("Missing image file");
            File f=new File(item.attachmentPath);if(!f.exists())throw new IllegalArgumentException("Image file not found");

            // Camera/gallery images can be tens of megapixels. Decode a bounded working copy so
            // ML Kit and the later Arabic OCR pass never inherit full-resolution memory pressure.
            decoded=SafeImageDecoder.decode(f,2400,4_500_000L);
            if(decoded==null)throw new IllegalArgumentException("Image could not be decoded safely");
            final Bitmap working=decoded;
            InputImage image=InputImage.fromBitmap(working,0);
            recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            final TextRecognizer active=recognizer;
            active.process(image)
                    .addOnSuccessListener(result->{
                        String latin=result.getText()==null?"":result.getText().trim();
                        try{active.close();}catch(Throwable ignored){}
                        recycle(working);
                        finish(ctx,item,f,latin,"",cb);
                    })
                    .addOnFailureListener(e->{
                        try{active.close();}catch(Throwable ignored){}
                        recycle(working);
                        finish(ctx,item,f,"","Latin OCR unavailable: "+e.getClass().getSimpleName(),cb);
                    });
        }catch(Throwable e){
            try{if(recognizer!=null)recognizer.close();}catch(Throwable ignored){}
            recycle(decoded);
            cb.fail(asException(e));
        }
    }

    private static void finish(Context ctx,KnowledgeItem item,File f,String latin,String latinStatus,Callback cb){
        ArabicOcr.recognizeDetailed(ctx,f,latin,result->{
            try{
                AnalysisResult r=VisionInterpreter.interpret(item,latin,result.text,result.status);
                if(latinStatus!=null&&!latinStatus.isEmpty())r.visionFields.add(new AnalysisResult.VisionField("Latin OCR",latinStatus,0.7));
                if(!result.rawText.isEmpty()){
                    String gate=result.accepted?"accepted":"rejected";
                    r.visionFields.add(new AnalysisResult.VisionField("Arabic evidence gate",gate+" • "+result.gateReason,result.accepted?0.95:1.0));
                }
                cb.ok(r);
            }catch(Throwable e){cb.fail(asException(e));}
        });
    }

    private static void recycle(Bitmap b){try{if(b!=null&&!b.isRecycled())b.recycle();}catch(Throwable ignored){}}
    private static Exception asException(Throwable t){return t instanceof Exception?(Exception)t:new RuntimeException(t.getClass().getSimpleName()+": "+(t.getMessage()==null?"OCR failure":t.getMessage()),t);}
}
