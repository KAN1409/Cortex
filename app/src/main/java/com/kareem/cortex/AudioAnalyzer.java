package com.kareem.cortex;

import android.content.Context;
import java.io.File;

/** Cloud-only voice analysis entry point. No local ASR model or Android recognizer is used. */
public final class AudioAnalyzer {
    public interface Callback{void ok(AnalysisResult r);void fail(Exception e);}
    private AudioAnalyzer(){}

    public static void analyze(Context ctx,KnowledgeItem item,Callback cb){
        try{
            if(item.attachmentPath==null||item.attachmentPath.isEmpty())throw new IllegalArgumentException("Missing audio file");
            File f=new File(item.attachmentPath);if(!f.exists())throw new IllegalArgumentException("Audio file not found");
            CloudAudioTranscriber.transcribe(ctx,f,new CloudAudioTranscriber.Callback(){
                @Override public void ok(TranscriptResult t){
                    try{cb.ok(toAnalysisResult(t));}catch(Exception e){cb.fail(e);}
                }
                @Override public void fail(Exception cloudError){
                    if(cloudError instanceof CloudAudioTranscriber.RetryableException){
                        CloudTranscriptionRetryWorker.enqueue(ctx,item.id);
                    }
                    cb.fail(cloudError);
                }
            });
        }catch(Exception e){cb.fail(e);}
    }

    static AnalysisResult toAnalysisResult(TranscriptResult t){
        // The cloud ASR transcript is immutable here. LocalAnalyzer may summarize/extract
        // metadata, but it never rewrites the stored verbatim transcript or its segments.
        AnalysisResult r=LocalAnalyzer.analyze(t.text,"text/plain");
        r.extractedText=t.text;
        r.engine=t.engine;
        r.version="5-cloud";
        r.category="Voice & Audio";
        r.tags="voice,audio,transcript,cloud-asr,"+AutoClassifier.tags(t.text,"Voice & Audio");
        String title=AutoClassifier.title(t.text,"text/plain");
        r.title="Voice: "+title;
        for(TranscriptResult.Segment s:t.segments)r.transcriptSegments.add(new AnalysisResult.TranscriptSegment(s.startMs,s.endMs,s.text,s.confidence));
        r.audioLanguage=t.language;
        r.audioDurationMs=t.durationMs;
        return r;
    }
}