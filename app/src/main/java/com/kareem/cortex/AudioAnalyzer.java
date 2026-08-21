package com.kareem.cortex;

import android.content.Context;
import java.io.File;

public final class AudioAnalyzer {
    public interface Callback{void ok(AnalysisResult r);void fail(Exception e);}
    private AudioAnalyzer(){}

    public static void analyze(Context ctx,KnowledgeItem item,Callback cb){
        try{
            if(item.attachmentPath==null||item.attachmentPath.isEmpty())throw new IllegalArgumentException("Missing audio file");
            File f=new File(item.attachmentPath);if(!f.exists())throw new IllegalArgumentException("Audio file not found");
            SystemAudioTranscriber.transcribe(ctx,f,new SystemAudioTranscriber.Callback(){
                public void ok(TranscriptResult t){
                    try{
                        AnalysisResult r=LocalAnalyzer.analyze(t.text,"text/plain");r.extractedText=t.text;r.engine=t.engine+"+local_rules";r.version="1";r.category="Voice & Audio";r.tags="voice,audio,transcript,"+AutoClassifier.tags(t.text,"Voice & Audio");
                        String title=AutoClassifier.title(t.text,"text/plain");r.title="Voice: "+title;
                        for(TranscriptResult.Segment s:t.segments)r.transcriptSegments.add(new AnalysisResult.TranscriptSegment(s.startMs,s.endMs,s.text,s.confidence));
                        r.audioLanguage=t.language;r.audioDurationMs=t.durationMs;cb.ok(r);
                    }catch(Exception e){cb.fail(e);}
                }
                public void fail(Exception e){cb.fail(e);}
            });
        }catch(Exception e){cb.fail(e);}
    }
}
