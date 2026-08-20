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

    public static void analyze(Context ctx, KnowledgeItem item, Callback cb){
        try{
            if(item.attachmentPath==null || item.attachmentPath.isEmpty()) throw new IllegalArgumentException("Missing image file");
            File f=new File(item.attachmentPath); if(!f.exists()) throw new IllegalArgumentException("Image file not found");
            InputImage image=InputImage.fromFilePath(ctx, Uri.fromFile(f));
            TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(result->{
                        String ocr=result.getText()==null?"":result.getText().trim();
                        AnalysisResult r=LocalAnalyzer.analyze(ocr,"text/plain");
                        r.extractedText=ocr;
                        r.engine="mlkit_ocr+local_rules";r.version="1";
                        if(ocr.isEmpty()){
                            r.title=item.type.equals("SCREENSHOT")?"Screenshot":"Image";
                            r.summary="Image saved. No Latin-script text was detected by local OCR.";
                            r.category="Screenshots & Images";r.tags="screenshots,images";
                        }else{
                            r.category=AutoClassifier.category(ocr,"image/*");
                            if("Screenshots & Images".equals(r.category)) r.tags=AutoClassifier.tags(ocr,r.category)+",ocr";
                            if(r.title==null || r.title.trim().isEmpty() || "Untitled item".equals(r.title)) r.title="Screenshot text: "+AutoClassifier.title(ocr,"text/plain");
                        }
                        recognizer.close();cb.ok(r);
                    })
                    .addOnFailureListener(e->{recognizer.close();cb.fail(e);});
        }catch(Exception e){cb.fail(e);}
    }
}
