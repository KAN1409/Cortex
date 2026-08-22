package com.kareem.cortex;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.Locale;

public final class AudioAnalyzer {
    public interface Callback{void ok(AnalysisResult r);void fail(Exception e);}
    private AudioAnalyzer(){}

    public static void analyze(Context ctx,KnowledgeItem item,Callback cb){
        try{
            if(item.attachmentPath==null||item.attachmentPath.isEmpty())throw new IllegalArgumentException("Missing audio file");
            File f=new File(item.attachmentPath);if(!f.exists())throw new IllegalArgumentException("Audio file not found");
            boolean groq=GroqKeyStore.has(ctx),gemini=GeminiKeyStore.has(ctx);
            if(!groq&&!gemini){cb.fail(retryable("No ASR provider configured. Add Groq and/or Gemini API key",null));return;}
            if(!groq){runGeminiOnly(ctx,f,cb);return;}

            GroqAudioTranscriber.transcribe(ctx,f,new GroqAudioTranscriber.Callback(){
                public void ok(TranscriptResult t){
                    String warning=acceptabilityWarning(t);
                    if(warning!=null){
                        if(gemini)runGeminiOnly(ctx,f,cb);
                        else cb.fail(retryable("Groq returned an incomplete transcript: "+warning,null));
                        return;
                    }
                    if(!gemini){enrichWithGemini(t,null,"NOT_CONFIGURED");finish(t,cb);return;}
                    GeminiAudioTranscriber.transcribe(ctx,f,new GeminiAudioTranscriber.Callback(){
                        public void ok(TranscriptResult g){enrichWithGemini(t,g,null);finish(t,cb);}
                        public void fail(Exception e){enrichWithGemini(t,null,message(e));finish(t,cb);}
                    });
                }
                public void fail(Exception groqError){
                    if(gemini)runGeminiOnly(ctx,f,cb);
                    else cb.fail(retryable("Groq failed: "+message(groqError),groqError));
                }
            });
        }catch(Exception e){cb.fail(e);}
    }

    private static void runGeminiOnly(Context ctx,File f,Callback cb){
        GeminiAudioTranscriber.transcribe(ctx,f,new GeminiAudioTranscriber.Callback(){
            public void ok(TranscriptResult g){String w=acceptabilityWarning(g);if(w==null)finish(g,cb);else cb.fail(retryable("Gemini returned an incomplete transcript: "+w,null));}
            public void fail(Exception e){cb.fail(retryable("Gemini failed: "+message(e),e));}
        });
    }

    private static void enrichWithGemini(TranscriptResult groq,TranscriptResult gemini,String geminiError){
        try{
            JSONObject root;
            try{root=new JSONObject(groq.rawProviderResponse);}catch(Exception e){root=new JSONObject();}
            JSONArray arr=root.optJSONArray("candidates");if(arr==null){arr=new JSONArray();root.put("candidates",arr);}
            if(gemini!=null){
                JSONObject j=new JSONObject();j.put("label","gemini_2_5_flash");j.put("provider","gemini");j.put("status","ok");j.put("engine",gemini.engine);j.put("language",gemini.language);j.put("score",round1(geminiBenchmarkScore(gemini)));j.put("warning",acceptabilityWarning(gemini)==null?"":acceptabilityWarning(gemini));j.put("coverage",0);j.put("coverage_note","Gemini generateContent does not return ASR segment timestamps; full audio was supplied");j.put("arabic_ratio",round3(scriptRatio(gemini.text,true)));j.put("latin_ratio",round3(scriptRatio(gemini.text,false)));j.put("text",gemini.text);j.put("raw_text",gemini.rawTranscript);arr.put(j);
                root.put("gemini_status","ok");root.put("gemini_raw_provider",new JSONObject(gemini.rawProviderResponse));
            }else if(geminiError!=null){
                boolean missing="NOT_CONFIGURED".equals(geminiError);
                JSONObject j=new JSONObject();j.put("label","gemini_2_5_flash");j.put("provider","gemini");j.put("status",missing?"not_configured":"failed");j.put("score",-1000);j.put("coverage",0);j.put("arabic_ratio",0);j.put("latin_ratio",0);j.put("warning",missing?"Gemini API key not configured":geminiError);arr.put(j);
                root.put("gemini_status",missing?"not_configured":"failed");root.put("gemini_error",missing?"Gemini API key not configured":geminiError);
            }
            root.put("benchmark_mode","Groq Whisper candidates + Gemini 2.5 Flash side-by-side; Groq winner remains selected in v25 unless Groq fails");
            groq.rawProviderResponse=root.toString();
        }catch(Exception ignored){}
    }

    private static double geminiBenchmarkScore(TranscriptResult r){
        String text=r==null||r.text==null?"":r.text.trim();if(text.isEmpty())return -1000;
        double s=55;double ar=scriptRatio(text,true),la=scriptRatio(text,false);if(ar>0.08&&la>0.04)s+=25;else if(ar>0.08||la>0.08)s+=8;
        int words=wordCount(text);if(r.durationMs>0){double wps=words/(r.durationMs/1000.0);if(wps>=0.8&&wps<=4.5)s+=8;else s-=15;}return s;
    }

    private static String acceptabilityWarning(TranscriptResult t){
        if(t==null)return "missing transcript result";
        String text=t.text==null?"":t.text.trim();
        if(text.isEmpty())return "empty transcript";
        if(text.toLowerCase(Locale.US).contains("<hesitation>"))return "contains <hesitation>";
        if(t.qualityWarning!=null&&!t.qualityWarning.trim().isEmpty())return t.qualityWarning.trim();
        int words=wordCount(text);
        if(t.durationMs>=8000){int minWords=Math.max(4,(int)Math.ceil(t.durationMs/3000.0));if(words<minWords)return words+" words for "+Math.round(t.durationMs/1000.0)+" seconds";}
        if(t.durationMs>0&&t.processedDurationMs>0){double coverage=(double)t.processedDurationMs/(double)t.durationMs;t.coverage=coverage;if(coverage<0.65)return "timestamp coverage "+Math.round(coverage*100)+"%";}
        return null;
    }

    private static double scriptRatio(String s,boolean arabic){int target=0,letters=0;if(s==null)return 0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetter(c)){letters++;if(arabic?(c>=0x0600&&c<=0x06ff):((c>='A'&&c<='Z')||(c>='a'&&c<='z')))target++;}}return letters==0?0:(double)target/letters;}
    private static double round3(double x){return Math.round(x*1000.0)/1000.0;}private static double round1(double x){return Math.round(x*10.0)/10.0;}
    private static int wordCount(String text){String s=text==null?"":text.trim();if(s.isEmpty())return 0;return s.split("\\s+").length;}
    private static Exception retryable(String detail,Throwable cause){String msg=detail==null?"Transcription failed":detail.trim();if(!msg.startsWith("RETRYABLE:"))msg="RETRYABLE: "+msg;return cause==null?new Exception(msg):new Exception(msg,cause);}
    private static String message(Throwable e){if(e==null)return "unknown error";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();}

    private static void finish(TranscriptResult t,Callback cb){
        try{
            String warning=acceptabilityWarning(t);
            if(warning!=null){cb.fail(retryable("Transcript rejected before local analysis: "+warning,null));return;}
            String clean=t.text==null?"":t.text.replaceAll("\\s+"," ").trim();
            if(clean.isEmpty()){cb.fail(retryable("Transcript rejected before local analysis: empty transcript",null));return;}
            t.text=clean;
            AnalysisResult r=LocalAnalyzer.analyze(t.text,"text/plain");
            r.extractedText=t.text;r.engine=t.engine+"+local_analysis";r.version=t.version;r.category="Voice & Audio";r.tags="voice,audio,transcript,"+AutoClassifier.tags(t.text,"Voice & Audio");r.title="Voice: "+AutoClassifier.title(t.text,"text/plain");
            for(TranscriptResult.Segment s:t.segments)r.transcriptSegments.add(new AnalysisResult.TranscriptSegment(s.startMs,s.endMs,s.text,s.confidence));
            r.audioLanguage=t.language;r.audioDurationMs=t.durationMs;r.audioProcessedDurationMs=t.processedDurationMs;r.audioCoverage=t.coverage;r.audioRawTranscript=t.rawTranscript;r.audioProviderMergedTranscript=t.providerMergedTranscript;r.audioRawProviderResponse=t.rawProviderResponse;cb.ok(r);
        }catch(Exception e){cb.fail(e);}
    }
}
