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
            if(!GroqKeyStore.has(ctx)){
                cb.fail(retryable("Groq API key not configured",null));
                return;
            }

            GroqAudioTranscriber.transcribe(ctx,f,new GroqAudioTranscriber.Callback(){
                public void ok(TranscriptResult t){
                    String warning=acceptabilityWarning(t);
                    if(warning==null){
                        finish(t,cb);
                    }else{
                        cb.fail(retryable("Groq returned an incomplete transcript: "+warning,null));
                    }
                }

                public void fail(Exception groqError){
                    cb.fail(retryable("Groq failed: "+message(groqError),groqError));
                }
            });
        }catch(Exception e){cb.fail(e);}
    }

    private static String acceptabilityWarning(TranscriptResult t){
        if(t==null)return "missing transcript result";
        String text=t.text==null?"":t.text.trim();
        if(text.isEmpty())return "empty transcript";
        if(text.toLowerCase(java.util.Locale.US).contains("<hesitation>"))return "contains <hesitation>";
        if(t.qualityWarning!=null&&!t.qualityWarning.trim().isEmpty())return t.qualityWarning.trim();

        int words=wordCount(text);
        if(t.durationMs>=8000){
            int minWords=Math.max(4,(int)Math.ceil(t.durationMs/3000.0));
            if(words<minWords)return words+" words for "+Math.round(t.durationMs/1000.0)+" seconds";
        }

        if(t.durationMs>0&&t.processedDurationMs>0){
            double coverage=(double)t.processedDurationMs/(double)t.durationMs;
            t.coverage=coverage;
            if(coverage<0.65)return "timestamp coverage "+Math.round(coverage*100)+"%";
        }
        return null;
    }

    private static int wordCount(String text){
        String s=text==null?"":text.trim();
        if(s.isEmpty())return 0;
        return s.split("\\s+").length;
    }

    private static Exception retryable(String detail,Throwable cause){
        String msg=detail==null?"Transcription failed":detail.trim();
        if(!msg.startsWith("RETRYABLE:"))msg="RETRYABLE: "+msg;
        return cause==null?new Exception(msg):new Exception(msg,cause);
    }

    private static String message(Throwable e){
        if(e==null)return "unknown error";
        String m=e.getMessage();
        return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();
    }

    private static void finish(TranscriptResult t,Callback cb){
        try{
            String warning=acceptabilityWarning(t);
            if(warning!=null){cb.fail(retryable("Transcript rejected before local analysis: "+warning,null));return;}
            String clean=t.text==null?"":t.text.replaceAll("\\s+"," ").trim();
            if(clean.isEmpty()){cb.fail(retryable("Transcript rejected before local analysis: empty transcript",null));return;}
            t.text=clean;
            AnalysisResult r=LocalAnalyzer.analyze(t.text,"text/plain");r.extractedText=t.text;r.engine=t.engine+"+local_rules";r.version=t.version;r.category="Voice & Audio";r.tags="voice,audio,transcript,"+AutoClassifier.tags(t.text,"Voice & Audio");
            String title=AutoClassifier.title(t.text,"text/plain");r.title="Voice: "+title;
            for(TranscriptResult.Segment s:t.segments)r.transcriptSegments.add(new AnalysisResult.TranscriptSegment(s.startMs,s.endMs,s.text,s.confidence));
            r.audioLanguage=t.language;r.audioDurationMs=t.durationMs;cb.ok(r);
        }catch(Exception e){cb.fail(e);}
    }
}
