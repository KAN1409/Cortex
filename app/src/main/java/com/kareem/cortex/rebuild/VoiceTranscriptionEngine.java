package com.kareem.cortex.rebuild;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Same production routing that was proven in Cortex:
 * Gemini 3.6 Flash primary; Groq Whisper benchmark fallback; large audio avoids Gemini Base64.
 */
public final class VoiceTranscriptionEngine {
    private VoiceTranscriptionEngine() {}

    public static TranscriptResult transcribe(Context context, File audio) throws Exception {
        boolean gemini = GeminiKeyStore.has(context), groq = GroqKeyStore.has(context);
        if (!gemini && !groq) throw new IllegalStateException("NO_ASR_PROVIDER");

        if (audio.length() > GeminiAudioTranscriber.MAX_SAFE_INLINE_BYTES) {
            if (!groq) throw new IOException("Audio too large for safe Gemini inline transcription; configure Groq fallback");
            TranscriptResult g = GroqAudioTranscriber.transcribe(context, audio);
            enrichGroqFallback(g, "Gemini skipped for mobile memory safety: large audio file");
            return validate(g);
        }

        if (gemini) {
            try {
                TranscriptResult g = GeminiAudioTranscriber.transcribe(context, audio);
                String warning = acceptabilityWarning(g);
                if (warning == null) { prepareGeminiPrimary(g, groq); return validate(g); }
                if (!groq) throw new IOException("Gemini returned an incomplete transcript: " + warning);
                TranscriptResult fallback = GroqAudioTranscriber.transcribe(context, audio);
                enrichGroqFallback(fallback, "Gemini rejected: " + warning);
                return validate(fallback);
            } catch (Exception geminiError) {
                if (!groq) throw geminiError;
                TranscriptResult fallback = GroqAudioTranscriber.transcribe(context, audio);
                enrichGroqFallback(fallback, "Gemini failed: " + message(geminiError));
                return validate(fallback);
            }
        }

        TranscriptResult fallback = GroqAudioTranscriber.transcribe(context, audio);
        enrichGroqFallback(fallback, "Gemini API key not configured");
        return validate(fallback);
    }

    private static TranscriptResult validate(TranscriptResult t) throws Exception {
        String warning = acceptabilityWarning(t);
        if (warning != null) throw new IOException("Transcript rejected before storage: " + warning);
        t.text = stripBidiControls(t.text).replaceAll("\\s+", " ").trim();
        t.rawTranscript = stripBidiControls(t.rawTranscript);
        t.providerMergedTranscript = stripBidiControls(t.providerMergedTranscript);
        if (t.text.isEmpty()) throw new IOException("Transcript rejected before storage: empty transcript");
        return t;
    }

    private static void prepareGeminiPrimary(TranscriptResult g, boolean groqConfigured) {
        try {
            String raw = g.rawProviderResponse == null ? "" : g.rawProviderResponse;
            JSONObject root = new JSONObject(); JSONArray arr = new JSONArray(); JSONObject j = new JSONObject();
            j.put("label","gemini_3_6_flash"); j.put("provider","gemini"); j.put("status","ok"); j.put("selected",true);
            j.put("engine",g.engine); j.put("language",g.language); j.put("score",round1(geminiBenchmarkScore(g)));
            j.put("file_coverage",g.coverage); j.put("timestamp_coverage_known",false);
            j.put("coverage_note","Full audio supplied; Gemini generateContent does not return ASR segment timestamps");
            j.put("arabic_ratio",round3(scriptRatio(g.text,true))); j.put("latin_ratio",round3(scriptRatio(g.text,false)));
            j.put("text",g.text); j.put("raw_text",g.rawTranscript==null?g.text:g.rawTranscript); arr.put(j);
            root.put("candidates",arr); root.put("selected","gemini_3_6_flash"); root.put("gemini_status","ok");
            root.put("asr_mode","Gemini 3.6 Flash primary; Groq fallback only");
            root.put("groq_fallback",groqConfigured?"configured_not_called":"not_configured");
            if(!raw.trim().isEmpty()){try{root.put("gemini_raw_provider",new JSONObject(raw));}catch(Exception e){root.put("gemini_raw_provider_text",raw);}}
            g.rawProviderResponse=root.toString();
        } catch (Exception ignored) {}
    }

    private static void enrichGroqFallback(TranscriptResult groq, String geminiStatus) {
        try {
            JSONObject root; try { root=new JSONObject(groq.rawProviderResponse); } catch(Exception e){ root=new JSONObject(); }
            JSONArray arr=root.optJSONArray("candidates"); if(arr==null){arr=new JSONArray();root.put("candidates",arr);}
            JSONObject j=new JSONObject(); j.put("label","gemini_3_6_flash"); j.put("provider","gemini");
            j.put("status","failed_or_unavailable"); j.put("score",-1000); j.put("timestamp_coverage_known",false);
            j.put("arabic_ratio",0); j.put("latin_ratio",0); j.put("warning",geminiStatus); arr.put(j);
            root.put("gemini_status","fallback_triggered"); root.put("gemini_error",geminiStatus);
            root.put("asr_mode","Gemini 3.6 Flash primary; Groq fallback selected");
            groq.rawProviderResponse=root.toString();
        } catch(Exception ignored) {}
    }

    private static String acceptabilityWarning(TranscriptResult t) {
        if(t==null)return "missing transcript result";
        String text=t.text==null?"":t.text.trim(); if(text.isEmpty())return "empty transcript";
        if(text.toLowerCase(Locale.US).contains("<hesitation>"))return "contains <hesitation>";
        if(t.qualityWarning!=null&&!t.qualityWarning.trim().isEmpty())return t.qualityWarning.trim();
        int words=wordCount(text);
        if(t.durationMs>=8000){int minWords=Math.max(4,(int)Math.ceil(t.durationMs/3000.0));if(words<minWords)return words+" words for "+Math.round(t.durationMs/1000.0)+" seconds";}
        if(t.durationMs>0&&t.processedDurationMs>0){double coverage=(double)t.processedDurationMs/t.durationMs;t.coverage=coverage;if(coverage<0.65)return "timestamp/file coverage "+Math.round(coverage*100)+"%";}
        return null;
    }

    private static double geminiBenchmarkScore(TranscriptResult r){String text=r==null||r.text==null?"":r.text.trim();if(text.isEmpty())return -1000;double s=55;double ar=scriptRatio(text,true),la=scriptRatio(text,false);if(ar>0.08&&la>0.04)s+=25;else if(ar>0.08||la>0.08)s+=8;int words=wordCount(text);if(r.durationMs>0){double wps=words/(r.durationMs/1000.0);if(wps>=0.8&&wps<=4.5)s+=8;else s-=15;}return s;}
    private static double scriptRatio(String s,boolean arabic){int target=0,letters=0;if(s==null)return 0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetter(c)){letters++;if(arabic?(c>=0x0600&&c<=0x06ff):((c>='A'&&c<='Z')||(c>='a'&&c<='z')))target++;}}return letters==0?0:(double)target/letters;}
    private static double round3(double x){return Math.round(x*1000.0)/1000.0;} private static double round1(double x){return Math.round(x*10.0)/10.0;}
    private static int wordCount(String text){String s=text==null?"":text.trim();return s.isEmpty()?0:s.split("\\s+").length;}
    private static String message(Throwable e){if(e==null)return "unknown error";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();}
    private static String stripBidiControls(String s){return s==null?"":s.replaceAll("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]","");}
}
