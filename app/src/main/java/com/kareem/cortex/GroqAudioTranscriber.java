package com.kareem.cortex;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class GroqAudioTranscriber {
    public interface Callback{void ok(TranscriptResult r);void fail(Exception e);}

    private static final String ENDPOINT="https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String V3="whisper-large-v3";
    private static final String TURBO="whisper-large-v3-turbo";
    private static final String VERSION="groq-audio-v4-candidate-benchmark";
    private static final String MIN_PROMPT="مصري + English code-switching. اكتب الكلام كما قيل exactly; keep English in Latin letters. مثال: هنجرب recording على model جديد.";

    private GroqAudioTranscriber(){}

    private static final class Candidate {
        final String label; final TranscriptResult r; final String warning; final double score;
        Candidate(String l,TranscriptResult x,String w,double s){label=l;r=x;warning=w;score=s;}
    }

    public static void transcribe(Context context,File audio,Callback cb){
        final Context app=context.getApplicationContext();
        new Thread(()->{
            ArrayList<Candidate> all=new ArrayList<>();
            ArrayList<String> errors=new ArrayList<>();
            runCandidate(app,audio,V3,MIN_PROMPT,"v3_prompt",all,errors);
            runCandidate(app,audio,V3,null,"v3_no_prompt",all,errors);
            runCandidate(app,audio,TURBO,MIN_PROMPT,"turbo_prompt",all,errors);

            Candidate best=null;
            for(Candidate c:all){
                if(c.warning!=null)continue;
                if(best==null||c.score>best.score)best=c;
            }
            if(best==null){
                cb.fail(new IOException("Groq candidates all failed quality checks: "+join(errors)));
                return;
            }

            try{
                JSONObject diag=new JSONObject();
                diag.put("selected",best.label);
                JSONArray a=new JSONArray();
                for(Candidate c:all){
                    JSONObject j=new JSONObject();
                    j.put("label",c.label);j.put("engine",c.r.engine);j.put("language",c.r.language);
                    j.put("score",c.score);j.put("warning",c.warning==null?"":c.warning);
                    j.put("coverage",c.r.coverage);j.put("arabic_ratio",scriptRatio(c.r.text,true));j.put("latin_ratio",scriptRatio(c.r.text,false));
                    j.put("text",c.r.text);j.put("raw_text",c.r.rawTranscript);
                    a.put(j);
                }
                diag.put("candidates",a);diag.put("selected_raw_provider",best.r.rawProviderResponse);
                best.r.rawProviderResponse=diag.toString();
            }catch(Exception ignored){}
            cb.ok(cleanAfterQualityPass(best.r));
        },"CortexGroqASR").start();
    }

    private static void runCandidate(Context ctx,File audio,String model,String prompt,String label,ArrayList<Candidate> out,ArrayList<String> errors){
        try{
            TranscriptResult r=call(ctx,audio,model,prompt,label);
            String warning=qualityWarning(r,audio);
            double score=score(r,warning);
            out.add(new Candidate(label,r,warning,score));
            errors.add(label+": "+(warning==null?"accepted score="+Math.round(score):warning));
        }catch(Exception e){errors.add(label+": "+message(e));}
    }

    private static TranscriptResult call(Context context,File audio,String model,String prompt,String label) throws Exception {
        if(audio==null||!audio.exists()||audio.length()==0)throw new IllegalArgumentException("Missing audio file");
        String key=GroqKeyStore.get(context);if(key.isEmpty())throw new IllegalStateException("Groq API key not configured");
        String boundary="----CortexGroq"+UUID.randomUUID().toString().replace("-","");
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(120000);
        c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);c.setChunkedStreamingMode(64*1024);
        try(OutputStream out=c.getOutputStream()){
            field(out,boundary,"model",model);field(out,boundary,"response_format","verbose_json");field(out,boundary,"temperature","0");field(out,boundary,"task","transcribe");field(out,boundary,"timestamp_granularities[]","segment");
            if(prompt!=null&&!prompt.isEmpty())field(out,boundary,"prompt",prompt);
            file(out,boundary,"file",audio,mime(audio));write(out,"--"+boundary+"--\r\n");
        }
        int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();String body=read(stream);c.disconnect();
        if(code<200||code>=300)throw new HttpStatusException(code,"Groq ASR HTTP "+code+": "+compactError(body));
        JSONObject json=new JSONObject(body);TranscriptResult r=new TranscriptResult();r.rawProviderResponse=body;r.language=json.optString("language","auto");r.engine=model+"+"+label;r.version=VERSION;r.durationMs=duration(audio);
        if(r.durationMs<=0){double d=json.optDouble("duration",0);if(d>0)r.durationMs=toMs(d);}String top=json.optString("text","").trim();r.rawTranscript=top;String merged=parseSegments(json,r);r.providerMergedTranscript=merged;r.text=!merged.isEmpty()?merged:dedupeRepeatedText(top);if(r.text.isEmpty())throw new IOException("Groq returned an empty transcript");
        if(r.processedDurationMs>0&&r.durationMs>0)r.coverage=Math.min(1.0,(double)r.processedDurationMs/r.durationMs);return r;
    }

    private static double score(TranscriptResult r,String warning){
        if(warning!=null)return -1000;
        double s=60.0*Math.max(0,Math.min(1,r.coverage));
        double ar=scriptRatio(r.text,true),la=scriptRatio(r.text,false);
        if(ar>0.08&&la>0.04)s+=30; else if(ar>0.08)s+=10;
        if(isArabicLanguage(r.language)&&ar<0.02&&la>0.70)s-=200;
        int words=countWords(r.text); if(r.durationMs>0){double wps=words/(r.durationMs/1000.0);if(wps>=0.8&&wps<=4.5)s+=10;else s-=15;}
        return s;
    }

    private static String qualityWarning(TranscriptResult r,File audio){
        String text=r==null||r.text==null?"":r.text.trim();if(text.isEmpty())return "empty transcript";if(text.toLowerCase(Locale.ROOT).contains("<hesitation>"))return "contains <hesitation>";
        long durationMs=r.durationMs>0?r.durationMs:duration(audio);long sec=Math.max(1,Math.round(durationMs/1000.0));int words=countWords(text);
        if(sec>=8&&words<Math.max(4,(int)Math.ceil(sec/2.5)))return words+" words for "+sec+" seconds";
        if(r.processedDurationMs>0&&durationMs>0){double cov=(double)r.processedDurationMs/durationMs;r.coverage=Math.min(1,cov);if(durationMs>=7000&&cov<0.70)return String.format(Locale.US,"%.0f%% timestamp coverage",cov*100);if(durationMs>=7000&&durationMs-r.processedDurationMs>1800)return "ended "+Math.round((durationMs-r.processedDurationMs)/1000.0)+"s early";}
        double ar=scriptRatio(text,true),la=scriptRatio(text,false);if(isArabicLanguage(r.language)&&durationMs>=7000&&ar<0.02&&la>0.70)return "LANGUAGE_COLLAPSE_TO_ENGLISH";
        return null;
    }

    private static boolean isArabicLanguage(String lang){if(lang==null)return false;String x=lang.toLowerCase(Locale.ROOT);return x.startsWith("ar")||x.contains("arabic");}
    private static double scriptRatio(String s,boolean arabic){int target=0,letters=0;if(s==null)return 0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isLetter(c)){letters++;if(arabic?(c>=0x0600&&c<=0x06ff):((c>='A'&&c<='Z')||(c>='a'&&c<='z')))target++;}}return letters==0?0:(double)target/letters;}

    private static String parseSegments(JSONObject json,TranscriptResult r){JSONArray arr=json.optJSONArray("segments");if(arr==null||arr.length()==0)return "";StringBuilder merged=new StringBuilder();long lastEnd=0,prevStart=-1,prevEnd=-1;String prev="";for(int i=0;i<arr.length();i++){JSONObject s=arr.optJSONObject(i);if(s==null)continue;String text=s.optString("text","").trim();if(text.isEmpty())continue;long start=toMs(s.optDouble("start",0)),end=toMs(s.optDouble("end",0));if(end<start)end=start;String n=normalizeForDedup(text);if(n.isEmpty())continue;boolean same=n.equals(prev);boolean near=prevEnd>=0&&(start<=prevEnd+1200||(prevStart>=0&&Math.abs(start-prevStart)<2500));if(same&&near)continue;float confidence=(float)s.optDouble("confidence",0);r.segments.add(new TranscriptResult.Segment(start,end,text,confidence));lastEnd=Math.max(lastEnd,end);if(merged.length()>0)merged.append(' ');merged.append(text);prev=n;prevStart=start;prevEnd=end;}r.processedDurationMs=lastEnd;return dedupeRepeatedText(merged.toString().trim());}
    private static TranscriptResult cleanAfterQualityPass(TranscriptResult r){r.qualityWarning="";r.text=cleanup(r.text);r.providerMergedTranscript=cleanup(r.providerMergedTranscript);return r;}
    private static String dedupeRepeatedText(String text){String s=cleanup(text);if(s.isEmpty())return s;String[] w=s.split("\\s+");if(w.length>=4&&w.length%2==0){int h=w.length/2;boolean same=true;for(int i=0;i<h;i++)if(!normalizeForDedup(w[i]).equals(normalizeForDedup(w[i+h]))){same=false;break;}if(same){StringBuilder o=new StringBuilder();for(int i=0;i<h;i++){if(i>0)o.append(' ');o.append(w[i]);}return o.toString().trim();}}return s;}
    private static String normalizeForDedup(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}،؛؟]+"," ").replaceAll("\\s+"," ").trim();}
    private static String join(ArrayList<String> xs){StringBuilder b=new StringBuilder();for(String x:xs){if(b.length()>0)b.append(" | ");b.append(x);}return b.toString();}
    private static String message(Exception e){if(e==null)return "Unknown error";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();}
    private static int countWords(String s){String t=s==null?"":s.trim();return t.isEmpty()?0:t.split("\\s+").length;}
    private static String cleanup(String s){return s==null?"":s.replace("<hesitation>"," ").replaceAll("\\s+"," ").trim();}
    private static long toMs(double sec){return sec<=0?0:Math.round(sec*1000);}
    private static void field(OutputStream out,String boundary,String name,String value)throws Exception{write(out,"--"+boundary+"\r\n");write(out,"Content-Disposition: form-data; name=\""+name+"\"\r\n\r\n");write(out,value+"\r\n");}
    private static void file(OutputStream out,String boundary,String name,File f,String mime)throws Exception{write(out,"--"+boundary+"\r\n");write(out,"Content-Disposition: form-data; name=\""+name+"\"; filename=\""+f.getName().replace("\"","")+"\"\r\n");write(out,"Content-Type: "+mime+"\r\n\r\n");try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] buf=new byte[65536];for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);}write(out,"\r\n");}
    private static void write(OutputStream out,String s)throws Exception{out.write(s.getBytes(StandardCharsets.UTF_8));}
    private static String read(InputStream in)throws Exception{if(in==null)return "";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString(StandardCharsets.UTF_8.name());}}
    private static String compactError(String body){if(body==null)return "Unknown error";try{JSONObject j=new JSONObject(body);JSONObject e=j.optJSONObject("error");if(e!=null&&!e.optString("message","").isEmpty())return e.optString("message");if(!j.optString("message","").isEmpty())return j.optString("message");}catch(Exception ignored){}String s=body.replaceAll("\\s+"," ").trim();return s.length()>240?s.substring(0,240)+"…":s;}
    private static String mime(File f){String n=f.getName().toLowerCase(Locale.US);if(n.endsWith(".wav"))return "audio/wav";if(n.endsWith(".mp3"))return "audio/mpeg";if(n.endsWith(".ogg"))return "audio/ogg";if(n.endsWith(".flac"))return "audio/flac";if(n.endsWith(".m4a")||n.endsWith(".mp4"))return "audio/mp4";if(n.endsWith(".webm"))return "audio/webm";return "application/octet-stream";}
    private static long duration(File audio){MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(audio.getAbsolutePath());String d=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);return d==null?0:Long.parseLong(d);}catch(Exception e){return 0;}finally{try{m.release();}catch(Exception ignored){}}}
    private static final class HttpStatusException extends IOException{final int code;HttpStatusException(int c,String m){super(m);code=c;}}
}
