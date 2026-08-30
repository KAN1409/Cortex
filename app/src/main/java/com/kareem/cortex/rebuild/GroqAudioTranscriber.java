package com.kareem.cortex.rebuild;

import android.content.Context;
import android.media.MediaMetadataRetriever;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cortex Groq fallback with the same three-candidate quality benchmark, but candidates run in
 * parallel and every HTTP request has a hard disconnect deadline so the UI cannot sit forever.
 */
public final class GroqAudioTranscriber {
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String V3 = "whisper-large-v3";
    private static final String TURBO = "whisper-large-v3-turbo";
    private static final String VERSION = "groq-audio-v6-parallel-benchmark";
    private static final String MIN_PROMPT = "مصري + English code-switching. اكتب الكلام كما قيل exactly; keep English in Latin letters. مثال: هنجرب recording على model جديد.";
    private static final long CANDIDATE_HARD_TIMEOUT_MS = 50_000L;
    private static final long BENCHMARK_TIMEOUT_MS = 58_000L;
    private static final ScheduledExecutorService TIMEOUTS = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "cortex-groq-timeout"); t.setDaemon(true); return t;
    });

    private GroqAudioTranscriber() {}

    private static final class Metrics {
        double avgLogprob = Double.NaN, avgCompressionRatio = Double.NaN, avgNoSpeechProb = Double.NaN;
        int count;
    }
    private static final class Candidate {
        final String label; final TranscriptResult result; final String warning; final double score; final Metrics metrics;
        Candidate(String label, TranscriptResult result, String warning, double score, Metrics metrics) {
            this.label=label; this.result=result; this.warning=warning; this.score=score; this.metrics=metrics;
        }
    }
    private static final class CandidateRun {
        final Candidate candidate; final String status;
        CandidateRun(Candidate candidate, String status) { this.candidate=candidate; this.status=status; }
    }

    public static TranscriptResult transcribe(Context context, File audio) throws Exception {
        if (audio == null || !audio.exists() || audio.length() == 0) throw new IllegalArgumentException("Missing audio file");
        if (GroqKeyStore.get(context).isEmpty()) throw new IllegalStateException("Groq API key not configured");

        ExecutorService pool = Executors.newFixedThreadPool(3, r -> new Thread(r, "cortex-groq-candidate"));
        List<Future<CandidateRun>> futures = new ArrayList<>();
        futures.add(pool.submit(candidateTask(context, audio, V3, MIN_PROMPT, "v3_prompt")));
        futures.add(pool.submit(candidateTask(context, audio, V3, null, "v3_no_prompt")));
        futures.add(pool.submit(candidateTask(context, audio, TURBO, MIN_PROMPT, "turbo_prompt")));

        ArrayList<Candidate> all = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BENCHMARK_TIMEOUT_MS);
        try {
            for (Future<CandidateRun> future : futures) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    future.cancel(true);
                    errors.add("benchmark deadline reached");
                    continue;
                }
                try {
                    CandidateRun run = future.get(remaining, TimeUnit.NANOSECONDS);
                    if (run.candidate != null) all.add(run.candidate);
                    errors.add(run.status);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    errors.add("candidate timed out");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    errors.add(cause == null ? "candidate failed" : message(cause));
                }
            }
        } finally {
            for (Future<CandidateRun> future : futures) if (!future.isDone()) future.cancel(true);
            pool.shutdownNow();
        }

        Candidate best = null;
        for (Candidate c : all) if (c.warning == null && (best == null || c.score > best.score)) best = c;
        if (best == null) throw new IOException("Groq candidates all failed quality checks: " + join(errors));

        JSONObject diag = new JSONObject();
        diag.put("selected", best.label);
        diag.put("selection_score", round3(best.score));
        JSONArray candidates = new JSONArray();
        for (Candidate c : all) {
            JSONObject j = new JSONObject();
            j.put("label", c.label);
            j.put("engine", c.result.engine);
            j.put("language", c.result.language);
            j.put("score", round3(c.score));
            j.put("warning", c.warning == null ? "" : c.warning);
            j.put("coverage", round3(c.result.coverage));
            j.put("arabic_ratio", round3(scriptRatio(c.result.text, true)));
            j.put("latin_ratio", round3(scriptRatio(c.result.text, false)));
            if (c.metrics.count > 0) {
                j.put("metric_segments", c.metrics.count);
                j.put("avg_logprob", round3(c.metrics.avgLogprob));
                j.put("avg_compression_ratio", round3(c.metrics.avgCompressionRatio));
                j.put("avg_no_speech_prob", round3(c.metrics.avgNoSpeechProb));
            }
            j.put("text", c.result.text);
            j.put("raw_text", c.result.rawTranscript);
            candidates.put(j);
        }
        diag.put("candidates", candidates);
        diag.put("benchmark_status", new JSONArray(errors));
        try { diag.put("selected_raw_provider", new JSONObject(best.result.rawProviderResponse)); }
        catch (Exception e) { diag.put("selected_raw_provider_text", best.result.rawProviderResponse); }
        best.result.rawProviderResponse = diag.toString();
        best.result.qualityWarning = "";
        best.result.text = cleanup(best.result.text);
        best.result.providerMergedTranscript = cleanup(best.result.providerMergedTranscript);
        return best.result;
    }

    private static Callable<CandidateRun> candidateTask(Context ctx, File audio, String model, String prompt, String label) {
        Context app = ctx.getApplicationContext();
        return () -> {
            try {
                TranscriptResult r = call(app, audio, model, prompt, label);
                String warning = qualityWarning(r, audio);
                Metrics m = metrics(r.rawProviderResponse);
                double score = score(r, warning, m);
                Candidate candidate = new Candidate(label, r, warning, score, m);
                return new CandidateRun(candidate, label + ": " + (warning == null ? "accepted score=" + Math.round(score) : warning));
            } catch (Exception e) {
                return new CandidateRun(null, label + ": " + message(e));
            }
        };
    }

    private static TranscriptResult call(Context context, File audio, String model, String prompt, String label) throws Exception {
        String key = GroqKeyStore.get(context);
        if (key.isEmpty()) throw new IllegalStateException("Groq API key not configured");
        String boundary = "----CortexGroq" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        AtomicBoolean forcedTimeout = new AtomicBoolean(false);
        ScheduledFuture<?> guard = TIMEOUTS.schedule(() -> {
            forcedTimeout.set(true);
            try { c.disconnect(); } catch (Throwable ignored) {}
        }, CANDIDATE_HARD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(12_000);
            c.setReadTimeout(45_000);
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            c.setChunkedStreamingMode(64 * 1024);
            try (OutputStream out = c.getOutputStream()) {
                field(out,boundary,"model",model);
                field(out,boundary,"response_format","verbose_json");
                field(out,boundary,"temperature","0");
                field(out,boundary,"timestamp_granularities[]","segment");
                if (prompt != null && !prompt.isEmpty()) field(out,boundary,"prompt",prompt);
                file(out,boundary,"file",audio,mime(audio));
                write(out,"--"+boundary+"--\r\n");
            }
            int code = c.getResponseCode();
            String body = read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
            if (forcedTimeout.get()) throw new SocketTimeoutException("Groq ASR hard timeout");
            if (code < 200 || code >= 300) throw new IOException("Groq ASR HTTP " + code + ": " + compact(body));

            JSONObject json = new JSONObject(body);
            TranscriptResult r = new TranscriptResult();
            r.rawProviderResponse = body;
            r.language = json.optString("language", "auto");
            r.engine = model + "+" + label;
            r.version = VERSION;
            r.durationMs = duration(audio);
            if (r.durationMs <= 0) { double d = json.optDouble("duration", 0); if (d > 0) r.durationMs = toMs(d); }
            String top = json.optString("text", "").trim();
            r.rawTranscript = top;
            String merged = parseSegments(json, r);
            r.providerMergedTranscript = merged;
            r.text = !merged.isEmpty() ? merged : dedupeRepeatedText(top);
            if (r.text.isEmpty()) throw new IOException("Groq returned an empty transcript");
            if (r.processedDurationMs > 0 && r.durationMs > 0) r.coverage = Math.min(1.0, (double)r.processedDurationMs / r.durationMs);
            return r;
        } catch (IOException e) {
            if (forcedTimeout.get()) throw new SocketTimeoutException("Groq ASR hard timeout");
            throw e;
        } finally {
            guard.cancel(false);
            try { c.disconnect(); } catch (Throwable ignored) {}
        }
    }

    private static Metrics metrics(String raw) {
        Metrics m = new Metrics();
        if (raw == null || raw.isEmpty()) return m;
        try {
            JSONArray arr = new JSONObject(raw).optJSONArray("segments");
            if (arr == null) return m;
            double lp=0,cr=0,ns=0; int n=0;
            for (int i=0;i<arr.length();i++) {
                JSONObject s=arr.optJSONObject(i); if(s==null)continue;
                if(!s.has("avg_logprob")&&!s.has("compression_ratio")&&!s.has("no_speech_prob"))continue;
                lp+=s.optDouble("avg_logprob",0); cr+=s.optDouble("compression_ratio",0); ns+=s.optDouble("no_speech_prob",0); n++;
            }
            if(n>0){m.count=n;m.avgLogprob=lp/n;m.avgCompressionRatio=cr/n;m.avgNoSpeechProb=ns/n;}
        } catch (Exception ignored) {}
        return m;
    }

    private static double score(TranscriptResult r, String warning, Metrics m) {
        if (warning != null) return -1000;
        double s = 55.0 * Math.max(0, Math.min(1, r.coverage));
        double ar = scriptRatio(r.text,true), la=scriptRatio(r.text,false);
        if(ar>0.08&&la>0.04)s+=25; else if(ar>0.08)s+=8;
        if(isArabicLanguage(r.language)&&ar<0.02&&la>0.70)s-=200;
        int words=countWords(r.text);
        if(r.durationMs>0){double wps=words/(r.durationMs/1000.0);if(wps>=0.8&&wps<=4.5)s+=8;else s-=15;}
        if(m.count>0){
            if(m.avgLogprob>=-0.35)s+=18; else if(m.avgLogprob>=-0.60)s+=10; else if(m.avgLogprob>=-0.90)s+=2; else s-=12;
            if(m.avgNoSpeechProb<=0.10)s+=8; else if(m.avgNoSpeechProb<=0.25)s+=3; else if(m.avgNoSpeechProb>=0.55)s-=12;
            if(m.avgCompressionRatio>=0.7&&m.avgCompressionRatio<=2.4)s+=6; else if(m.avgCompressionRatio>3.0)s-=12;
        }
        return s;
    }

    private static String qualityWarning(TranscriptResult r, File audio) {
        String text=r==null||r.text==null?"":r.text.trim();
        if(text.isEmpty())return "empty transcript";
        if(text.toLowerCase(Locale.ROOT).contains("<hesitation>"))return "contains <hesitation>";
        long durationMs=r.durationMs>0?r.durationMs:duration(audio); long sec=Math.max(1,Math.round(durationMs/1000.0)); int words=countWords(text);
        if(sec>=8&&words<Math.max(4,(int)Math.ceil(sec/2.5)))return words+" words for "+sec+" seconds";
        if(r.processedDurationMs>0&&durationMs>0){double cov=(double)r.processedDurationMs/durationMs;r.coverage=Math.min(1,cov);if(durationMs>=7000&&cov<0.70)return Math.round(cov*100)+"% timestamp coverage";if(durationMs>=7000&&durationMs-r.processedDurationMs>1800)return "ended "+Math.round((durationMs-r.processedDurationMs)/1000.0)+"s early";}
        double ar=scriptRatio(text,true),la=scriptRatio(text,false);if(isArabicLanguage(r.language)&&durationMs>=7000&&ar<0.02&&la>0.70)return "LANGUAGE_COLLAPSE_TO_ENGLISH";
        Metrics m=metrics(r.rawProviderResponse);if(m.count>0&&m.avgNoSpeechProb>0.75)return "HIGH_NO_SPEECH_PROBABILITY";if(m.count>0&&m.avgCompressionRatio>4.0)return "ABNORMAL_COMPRESSION_RATIO";
        return null;
    }

    private static String parseSegments(JSONObject json, TranscriptResult r) {
        JSONArray arr=json.optJSONArray("segments"); if(arr==null||arr.length()==0)return "";
        StringBuilder merged=new StringBuilder(); long lastEnd=0,prevStart=-1,prevEnd=-1; String prev="";
        for(int i=0;i<arr.length();i++){
            JSONObject s=arr.optJSONObject(i);if(s==null)continue;String text=s.optString("text","").trim();if(text.isEmpty())continue;
            long start=toMs(s.optDouble("start",0)),end=toMs(s.optDouble("end",0));if(end<start)end=start;String n=normalizeForDedup(text);if(n.isEmpty())continue;
            boolean same=n.equals(prev),near=prevEnd>=0&&(start<=prevEnd+1200||(prevStart>=0&&Math.abs(start-prevStart)<2500));if(same&&near)continue;
            float confidence=(float)Math.max(0,Math.min(1,Math.exp(s.optDouble("avg_logprob",0))));r.segments.add(new TranscriptResult.Segment(start,end,text,confidence));lastEnd=Math.max(lastEnd,end);if(merged.length()>0)merged.append(' ');merged.append(text);prev=n;prevStart=start;prevEnd=end;
        }
        r.processedDurationMs=lastEnd;return dedupeRepeatedText(merged.toString().trim());
    }

    private static String dedupeRepeatedText(String text){String s=cleanup(text);if(s.isEmpty())return s;String[] w=s.split("\\s+");if(w.length>=4&&w.length%2==0){int h=w.length/2;boolean same=true;for(int i=0;i<h;i++)if(!normalizeForDedup(w[i]).equals(normalizeForDedup(w[i+h]))){same=false;break;}if(same){StringBuilder o=new StringBuilder();for(int i=0;i<h;i++){if(i>0)o.append(' ');o.append(w[i]);}return o.toString().trim();}}return s;}
    private static String normalizeForDedup(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}،؛؟]+"," ").replaceAll("\\s+"," ").trim();}
    private static String cleanup(String s){return s==null?"":s.replace("<hesitation>"," ").replaceAll("\\s+"," ").trim();}
    private static boolean isArabicLanguage(String lang){if(lang==null)return false;String x=lang.toLowerCase(Locale.ROOT);return x.startsWith("ar")||x.contains("arabic");}
    private static double scriptRatio(String s,boolean arabic){int target=0,letters=0;if(s==null)return 0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetter(c)){letters++;if(arabic?(c>=0x0600&&c<=0x06ff):((c>='A'&&c<='Z')||(c>='a'&&c<='z')))target++;}}return letters==0?0:(double)target/letters;}
    private static int countWords(String s){String t=s==null?"":s.trim();return t.isEmpty()?0:t.split("\\s+").length;}
    private static long toMs(double sec){return sec<=0?0:Math.round(sec*1000);}
    private static double round3(double x){if(Double.isNaN(x)||Double.isInfinite(x))return 0;return Math.round(x*1000.0)/1000.0;}
    private static String join(ArrayList<String> xs){StringBuilder b=new StringBuilder();for(String x:xs){if(b.length()>0)b.append(" | ");b.append(x);}return b.toString();}
    private static String message(Throwable e){if(e==null)return "Unknown error";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();}
    private static String compact(String s){if(s==null)return "";String x=s.replaceAll("\\s+"," ").trim();return x.length()>500?x.substring(0,500)+"…":x;}
    private static String mime(File f){String n=f.getName().toLowerCase(Locale.ROOT);if(n.endsWith(".wav"))return "audio/wav";if(n.endsWith(".m4a")||n.endsWith(".mp4"))return "audio/mp4";if(n.endsWith(".mp3"))return "audio/mpeg";if(n.endsWith(".ogg"))return "audio/ogg";return "application/octet-stream";}
    private static long duration(File f){MediaMetadataRetriever m=null;try{m=new MediaMetadataRetriever();m.setDataSource(f.getAbsolutePath());String d=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);return d==null?0:Long.parseLong(d);}catch(Throwable e){return 0;}finally{if(m!=null)try{m.release();}catch(Throwable ignored){}}}
    private static void field(OutputStream out,String boundary,String name,String value)throws Exception{write(out,"--"+boundary+"\r\n");write(out,"Content-Disposition: form-data; name=\""+name+"\"\r\n\r\n");write(out,value+"\r\n");}
    private static void file(OutputStream out,String boundary,String name,File f,String mime)throws Exception{write(out,"--"+boundary+"\r\n");write(out,"Content-Disposition: form-data; name=\""+name+"\"; filename=\""+f.getName().replace("\"","")+"\"\r\n");write(out,"Content-Type: "+mime+"\r\n\r\n");try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] buf=new byte[65_536];for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);}write(out,"\r\n");}
    private static void write(OutputStream out,String s)throws Exception{out.write(s.getBytes(StandardCharsets.UTF_8));}
    private static String read(InputStream in)throws Exception{if(in==null)return "";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}
}
