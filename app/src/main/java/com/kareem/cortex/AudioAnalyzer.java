package com.kareem.cortex;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.Locale;

/** Voice analysis uses only zero-cost Android speech infrastructure in v64. */
public final class AudioAnalyzer {
    public interface Callback{void ok(AnalysisResult r);void fail(Exception e);}
    private AudioAnalyzer(){}

    public static void analyze(Context ctx,KnowledgeItem item,Callback cb){
        try{
            if(item==null||item.attachmentPath==null||item.attachmentPath.isEmpty())throw new IllegalArgumentException("Missing audio file");
            File f=new File(item.attachmentPath);if(!f.exists()||!f.isFile())throw new IllegalArgumentException("Audio file not found");

            // The original audio is durable evidence. Transcription never deletes or rewrites it.
            // SystemAudioTranscriber itself tries Android on-device ASR first, then the phone's
            // default recognizer as a zero-user-billed fallback.
            SystemAudioTranscriber.transcribe(ctx,f,new SystemAudioTranscriber.Callback(){
                public void ok(TranscriptResult t){
                    String warning=acceptabilityWarning(t);
                    if(warning!=null){cb.fail(retryable("Android speech transcript rejected by quality gate: "+warning,null));return;}
                    enrichZeroCost(t);
                    finish(ctx,t,cb);
                }
                public void fail(Exception e){cb.fail(retryable("Zero-cost Android speech transcription failed: "+message(e),e));}
            });
        }catch(Throwable e){cb.fail(e instanceof Exception?(Exception)e:retryable("Audio analysis stopped safely: "+e.getClass().getSimpleName(),e));}
    }

    private static void enrichZeroCost(TranscriptResult t){
        try{
            JSONObject root=new JSONObject();JSONArray arr=new JSONArray();JSONObject j=new JSONObject();
            j.put("label",t.engine);j.put("provider","android");j.put("status","ok");j.put("selected",true);j.put("engine",t.engine);j.put("language",t.language);j.put("file_coverage",t.coverage);j.put("arabic_ratio",round3(scriptRatio(t.text,true)));j.put("latin_ratio",round3(scriptRatio(t.text,false)));j.put("text",t.text);arr.put(j);
            root.put("candidates",arr);root.put("selected",t.engine);root.put("asr_mode","zero-cost Android speech · on-device preferred · system fallback");root.put("paid_api_used",false);root.put("source_audio_preserved",true);t.rawProviderResponse=root.toString();
        }catch(Exception ignored){}
    }

    private static String acceptabilityWarning(TranscriptResult t){
        if(t==null)return "missing transcript result";
        String text=t.text==null?"":t.text.trim();if(text.isEmpty())return "empty transcript";
        if(text.toLowerCase(Locale.US).contains("<hesitation>"))return "contains <hesitation>";
        if(t.qualityWarning!=null&&!t.qualityWarning.trim().isEmpty())return t.qualityWarning.trim();

        String language=t.language==null?"":t.language.trim().toLowerCase(Locale.ROOT);double ar=scriptRatio(text,true),la=scriptRatio(text,false);
        boolean declaredEnglish=language.equals("en")||language.startsWith("en-")||language.contains("english");
        boolean declaredArabic=language.equals("ar")||language.startsWith("ar-")||language.contains("arabic")||language.contains("العربي");
        if(declaredEnglish&&ar>=0.72&&la<=0.20)return "declared English but transcript is predominantly Arabic script";
        if(declaredArabic&&la>=0.72&&ar<=0.20)return "declared Arabic but transcript is predominantly Latin script";

        int words=wordCount(text);
        if(t.durationMs>=8000){int minWords=Math.max(4,(int)Math.ceil(t.durationMs/3000.0));if(words<minWords)return words+" words for "+Math.round(t.durationMs/1000.0)+" seconds";}
        if(t.durationMs>0&&t.processedDurationMs>0){long tolerance=Math.max(1500L,Math.round(t.durationMs*0.25));if(t.processedDurationMs>t.durationMs+tolerance)return "timestamp coverage exceeds source duration";double coverage=(double)t.processedDurationMs/(double)t.durationMs;t.coverage=coverage;if(coverage<0.65)return "timestamp/file coverage "+Math.round(coverage*100)+"%";if(coverage>1.35)return "timestamp/file coverage "+Math.round(coverage*100)+"% exceeds source duration";}
        return null;
    }

    private static double scriptRatio(String s,boolean arabic){int target=0,letters=0;if(s==null)return 0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetter(c)){letters++;if(arabic?(c>=0x0600&&c<=0x06ff):((c>='A'&&c<='Z')||(c>='a'&&c<='z')))target++;}}return letters==0?0:(double)target/letters;}
    private static double round3(double x){return Math.round(x*1000.0)/1000.0;}private static int wordCount(String text){String s=text==null?"":text.trim();if(s.isEmpty())return 0;return s.split("\\s+").length;}
    private static Exception retryable(String detail,Throwable cause){String msg=detail==null?"Transcription failed":detail.trim();if(!msg.startsWith("RETRYABLE:"))msg="RETRYABLE: "+msg;return cause==null?new Exception(msg):new Exception(msg,cause);}private static String message(Throwable e){if(e==null)return "unknown error";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();}

    private static void finish(Context ctx,TranscriptResult t,Callback cb){
        try{
            String warning=acceptabilityWarning(t);if(warning!=null){cb.fail(retryable("Transcript rejected before local analysis: "+warning,null));return;}
            String clean=MixedBidiText.stripControls(t.text==null?"":t.text).replaceAll("\\s+"," ").trim();if(clean.isEmpty()){cb.fail(retryable("Transcript rejected before local analysis: empty transcript",null));return;}t.text=clean;
            AnalysisResult r=LocalAnalyzer.analyze(t.text,"text/plain");
            String corrected=MixedBidiText.stripControls(CorrectionEngine.apply(ctx,t.text));
            r.extractedText=corrected.trim();r.summary=MixedBidiText.stripControls(r.summary).trim();r.engine=t.engine+"+local_analysis";r.version=t.version;r.category="Voice & Audio";r.tags="voice,audio,transcript,zero-cost,"+AutoClassifier.tags(t.text,"Voice & Audio");r.title=VoiceTextPresentation.compactTitle(t.text);
            for(TranscriptResult.Segment s:t.segments)r.transcriptSegments.add(new AnalysisResult.TranscriptSegment(s.startMs,s.endMs,MixedBidiText.stripControls(s.text),s.confidence));
            r.audioLanguage=t.language;r.audioDurationMs=t.durationMs;r.audioProcessedDurationMs=t.processedDurationMs;r.audioCoverage=t.coverage;r.audioRawTranscript=MixedBidiText.stripControls(t.rawTranscript);r.audioProviderMergedTranscript=MixedBidiText.stripControls(t.providerMergedTranscript);r.audioRawProviderResponse=t.rawProviderResponse;cb.ok(r);
        }catch(Throwable e){cb.fail(e instanceof Exception?(Exception)e:retryable("Post-transcription analysis stopped safely: "+e.getClass().getSimpleName(),e));}
    }
}
