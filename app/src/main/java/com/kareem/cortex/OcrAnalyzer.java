package com.kareem.cortex;

import android.content.Context;
import android.net.Uri;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;

public final class OcrAnalyzer {
    public interface Callback { void ok(AnalysisResult r); void fail(Exception e); }
    private OcrAnalyzer(){}

    public static void analyze(Context ctx,KnowledgeItem item,Callback cb){
        try{
            if(item.attachmentPath==null||item.attachmentPath.isEmpty())throw new IllegalArgumentException("Missing image file");
            File f=new File(item.attachmentPath);if(!f.exists())throw new IllegalArgumentException("Image file not found");
            InputImage image=InputImage.fromFilePath(ctx,Uri.fromFile(f));
            TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(result->{
                        String latin=result.getText()==null?"":result.getText().trim();recognizer.close();
                        finish(ctx,item,f,latin,"",cb);
                    })
                    .addOnFailureListener(e->{recognizer.close();finish(ctx,item,f,"","Latin OCR unavailable: "+e.getClass().getSimpleName(),cb);});
        }catch(Exception e){cb.fail(e);}
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
            }catch(Exception e){cb.fail(e);}
        });
    }
}
