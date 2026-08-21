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
            final boolean whisperOnly=WhisperRuntimeState.consumeWhisperOnly(ctx,item.id);

            MultilingualWhisperTranscriber.transcribe(ctx,f,new MultilingualWhisperTranscriber.Callback(){
                @Override public void ok(TranscriptResult t){finish(t,cb);}
                @Override public void fail(Exception whisperError){
                    if(whisperOnly){
                        cb.fail(new IllegalStateException("Whisper-only retry failed — "+safe(whisperError),whisperError));
                        return;
                    }
                    SystemAudioTranscriber.transcribe(ctx,f,new SystemAudioTranscriber.Callback(){
                        public void ok(TranscriptResult t){
                            t.engine=t.engine+"_fallback_after_whisper["+compact(whisperError)+"]";
                            finish(t,cb);
                        }
                        public void fail(Exception androidError){
                            cb.fail(new IllegalStateException("Whisper failed: "+safe(whisperError)+"; Android fallback failed: "+safe(androidError),androidError));
                        }
                    });
                }
            });
        }catch(Exception e){cb.fail(e);}
    }

    private static String safe(Throwable e){return e==null?"unknown":e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
    private static String compact(Throwable e){
        String x=safe(e).replace('\n',' ').replace('\r',' ').replace('[','(').replace(']',')');
        return x.length()>96?x.substring(0,96):x;
    }

    private static void finish(TranscriptResult t,Callback cb){
        try{
            AnalysisResult r=LocalAnalyzer.analyze(t.text,"text/plain");
            r.extractedText=t.text;
            r.engine=t.engine+"+local_rules";
            r.version="3";
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
