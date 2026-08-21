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

            // v1.0.7: use one multilingual model for Arabic/English code-switching.
            // If Whisper cannot run (model/network/ABI/imported non-WAV), retain the Android recognizer as fallback.
            MultilingualWhisperTranscriber.transcribe(ctx,f,new MultilingualWhisperTranscriber.Callback(){
                @Override public void ok(TranscriptResult t){finish(t,cb);}
                @Override public void fail(Exception whisperError){
                    SystemAudioTranscriber.transcribe(ctx,f,new SystemAudioTranscriber.Callback(){
                        public void ok(TranscriptResult t){t.engine=t.engine+"_fallback_after_whisper";finish(t,cb);}
                        public void fail(Exception androidError){
                            String a=whisperError==null?"unknown":whisperError.getMessage();
                            String b=androidError==null?"unknown":androidError.getMessage();
                            cb.fail(new IllegalStateException("Whisper failed: "+a+"; Android fallback failed: "+b,androidError));
                        }
                    });
                }
            });
        }catch(Exception e){cb.fail(e);}
    }

    private static void finish(TranscriptResult t,Callback cb){
        try{
            AnalysisResult r=LocalAnalyzer.analyze(t.text,"text/plain");
            r.extractedText=t.text;
            r.engine=t.engine+"+local_rules";
            r.version="2";
            r.category="Voice & Audio";
            r.tags="voice,audio,transcript,"+AutoClassifier.tags(t.text,"Voice & Audio");
            String title=AutoClassifier.title(t.text,"text/plain");
            r.title="Voice: "+title;
            for(TranscriptResult.Segment s:t.segments)r.transcriptSegments.add(new AnalysisResult.TranscriptSegment(s.startMs,s.endMs,s.text,s.confidence));
            r.audioLanguage=t.language;
            r.audioDurationMs=t.durationMs;
            cb.ok(r);
        }catch(Exception e){cb.fail(e);}
    }
}
