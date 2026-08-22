package com.kareem.cortex;

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
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class GroqAudioTranscriber {
    public interface Callback{void ok(TranscriptResult r);void fail(Exception e);}

    private static final String ENDPOINT="https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String MODEL="whisper-large-v3";
    private static final String VERSION="groq-audio-v2";

    private GroqAudioTranscriber(){}

    public static void transcribe(Context context,File audio,Callback cb){
        final Context app=context.getApplicationContext();
        new Thread(()->{
            try{
                TranscriptResult first=call(app,audio);
                String warning=qualityWarning(first,audio);
                if(warning!=null){
                    first.qualityWarning=warning;
                    throw new QualityException(warning);
                }
                cb.ok(cleanAfterQualityPass(first));
            }catch(Exception firstError){
                if(!isTransient(firstError)){
                    cb.fail(firstError);
                    return;
                }
                try{
                    TranscriptResult retry=call(app,audio);
                    retry.engine=MODEL+"+network_retry";
                    String warning=qualityWarning(retry,audio);
                    if(warning!=null){
                        retry.qualityWarning=warning;
                        throw new QualityException(warning);
                    }
                    cb.ok(cleanAfterQualityPass(retry));
                }catch(Exception retryError){
                    cb.fail(new IOException(
                            "Groq failed after network retry. First: "+message(firstError)+
                            " | Retry: "+message(retryError)+
                            " | file_bytes="+(audio==null?0:audio.length()),retryError));
                }
            }
        },"CortexGroqASR").start();
    }

    private static TranscriptResult call(Context context,File audio) throws Exception {
        if(audio==null||!audio.exists()||audio.length()==0)throw new IllegalArgumentException("Missing audio file");
        String key=GroqKeyStore.get(context);
        if(key.isEmpty())throw new IllegalStateException("Groq API key not configured");

        String boundary="----CortexGroq"+UUID.randomUUID().toString().replace("-","");
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Authorization","Bearer "+key);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        c.setChunkedStreamingMode(64*1024);

        try(OutputStream out=c.getOutputStream()){
            field(out,boundary,"model",MODEL);
            field(out,boundary,"response_format","verbose_json");
            field(out,boundary,"temperature","0");
            field(out,boundary,"timestamp_granularities[]","segment");
            file(out,boundary,"file",audio,mime(audio));
            write(out,"--"+boundary+"--\r\n");
        }

        int code=c.getResponseCode();
        InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        String body=read(stream);
        c.disconnect();
        if(code<200||code>=300)throw new HttpStatusException(code,"Groq ASR HTTP "+code+": "+compactError(body));

        JSONObject json=new JSONObject(body);
        TranscriptResult r=new TranscriptResult();
        r.rawProviderResponse=body;
        r.language=json.optString("language","auto");
        r.engine=MODEL;
        r.version=VERSION;
        r.durationMs=duration(audio);
        if(r.durationMs<=0){
            double providerDuration=json.optDouble("duration",0);
            if(providerDuration>0)r.durationMs=toMs(providerDuration);
        }

        String topLevel=json.optString("text","").trim();
        String merged=parseSegments(json,r);
        r.providerMergedTranscript=merged;
        r.text=!merged.isEmpty()?merged:dedupeRepeatedText(topLevel);
        if(r.text.isEmpty())throw new IOException("Groq returned an empty transcript");

        if(r.processedDurationMs>0&&r.durationMs>0){
            r.coverage=Math.min(1.0,(double)r.processedDurationMs/(double)r.durationMs);
        }else{
            r.coverage=0;
        }
        return r;
    }

    private static String parseSegments(JSONObject json,TranscriptResult r){
        JSONArray arr=json.optJSONArray("segments");
        if(arr==null||arr.length()==0)return "";
        StringBuilder merged=new StringBuilder();
        long lastUniqueEnd=0;
        String previousNormalized="";
        long previousStart=-1;
        long previousEnd=-1;

        for(int i=0;i<arr.length();i++){
            JSONObject s=arr.optJSONObject(i);
            if(s==null)continue;
            String text=s.optString("text","").trim();
            if(text.isEmpty())continue;
            long start=toMs(s.optDouble("start",0));
            long end=toMs(s.optDouble("end",0));
            if(end<start)end=start;
            String normalized=normalizeForDedup(text);
            if(normalized.isEmpty())continue;

            boolean sameAsPrevious=normalized.equals(previousNormalized);
            boolean nearOrOverlapping=previousEnd>=0 && (start<=previousEnd+1200 || (previousStart>=0&&Math.abs(start-previousStart)<2500));
            if(sameAsPrevious && nearOrOverlapping){
                continue;
            }

            float confidence=(float)s.optDouble("confidence",0);
            r.segments.add(new TranscriptResult.Segment(start,end,text,confidence));
            lastUniqueEnd=Math.max(lastUniqueEnd,end);
            if(merged.length()>0)merged.append(' ');
            merged.append(text);
            previousNormalized=normalized;
            previousStart=start;
            previousEnd=end;
        }
        r.processedDurationMs=lastUniqueEnd;
        return dedupeRepeatedText(merged.toString().trim());
    }

    private static String qualityWarning(TranscriptResult r,File audio){
        String text=r==null||r.text==null?"":r.text.trim();
        if(text.isEmpty())return "Groq transcript is empty";
        if(text.toLowerCase(Locale.ROOT).contains("<hesitation>"))return "Groq transcript contains <hesitation>";

        int words=countWords(text);
        long durationMs=r.durationMs>0?r.durationMs:duration(audio);
        long durationSec=Math.max(1,Math.round(durationMs/1000.0));
        if(durationSec>=8&&words<Math.max(4,(int)Math.ceil(durationSec/2.5))){
            return "Groq transcript failed completeness checks: "+words+" words for "+durationSec+" seconds";
        }

        if(r.processedDurationMs>0&&durationMs>0){
            double coverage=(double)r.processedDurationMs/(double)durationMs;
            r.coverage=Math.min(1.0,coverage);
            if(durationMs>=7000&&coverage<0.70){
                return String.format(Locale.US,"Groq transcript truncated: %.0f%% unique timestamp coverage",coverage*100.0);
            }
            if(durationMs>=7000 && durationMs-r.processedDurationMs>1800){
                return "Groq transcript ended "+Math.round((durationMs-r.processedDurationMs)/1000.0)+"s before the audio ended";
            }
        }
        return null;
    }

    private static TranscriptResult cleanAfterQualityPass(TranscriptResult r){
        r.qualityWarning="";
        r.text=cleanup(r.text);
        r.providerMergedTranscript=cleanup(r.providerMergedTranscript);
        return r;
    }

    private static String dedupeRepeatedText(String text){
        String s=cleanup(text);
        if(s.isEmpty())return s;
        String[] words=s.split("\\s+");
        if(words.length>=4 && words.length%2==0){
            int half=words.length/2;
            boolean same=true;
            for(int i=0;i<half;i++){
                if(!normalizeForDedup(words[i]).equals(normalizeForDedup(words[i+half]))){same=false;break;}
            }
            if(same){
                StringBuilder out=new StringBuilder();
                for(int i=0;i<half;i++){if(i>0)out.append(' ');out.append(words[i]);}
                return out.toString().trim();
            }
        }
        return s;
    }

    private static String normalizeForDedup(String s){
        if(s==null)return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}،؛؟]+"," ")
                .replaceAll("\\s+"," ")
                .trim();
    }

    private static boolean isTransient(Exception e){
        if(e instanceof HttpStatusException){
            int code=((HttpStatusException)e).code;
            return code==408||code==409||code==429||code>=500;
        }
        return e instanceof SocketTimeoutException||
                e instanceof ConnectException||
                e instanceof SocketException||
                e instanceof UnknownHostException;
    }

    private static String message(Exception e){
        if(e==null)return "Unknown error";
        String m=e.getMessage();
        return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();
    }

    private static int countWords(String s){
        String t=s==null?"":s.trim();
        return t.isEmpty()?0:t.split("\\s+").length;
    }

    private static String cleanup(String s){
        if(s==null)return "";
        return s.replace("<hesitation>"," ").replaceAll("\\s+"," ").trim();
    }

    private static long toMs(double seconds){
        if(seconds<=0)return 0;
        return Math.round(seconds*1000.0);
    }

    private static void field(OutputStream out,String boundary,String name,String value) throws Exception {
        write(out,"--"+boundary+"\r\n");
        write(out,"Content-Disposition: form-data; name=\""+name+"\"\r\n\r\n");
        write(out,value+"\r\n");
    }

    private static void file(OutputStream out,String boundary,String name,File f,String mime) throws Exception {
        write(out,"--"+boundary+"\r\n");
        write(out,"Content-Disposition: form-data; name=\""+name+"\"; filename=\""+f.getName().replace("\"","")+"\"\r\n");
        write(out,"Content-Type: "+mime+"\r\n\r\n");
        try(InputStream in=new BufferedInputStream(new FileInputStream(f))){
            byte[] buf=new byte[64*1024];
            for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);
        }
        write(out,"\r\n");
    }

    private static void write(OutputStream out,String s) throws Exception {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(InputStream in) throws Exception {
        if(in==null)return "";
        try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];
            for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);
            return b.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String compactError(String body){
        if(body==null)return "Unknown error";
        try{
            JSONObject j=new JSONObject(body);
            JSONObject error=j.optJSONObject("error");
            if(error!=null){
                String nested=error.optString("message","");
                if(!nested.isEmpty())return nested;
            }
            String direct=j.optString("message","");
            if(!direct.isEmpty())return direct;
        }catch(Exception ignored){}
        String s=body.replaceAll("\\s+"," ").trim();
        return s.length()>240?s.substring(0,240)+"…":s;
    }

    private static String mime(File f){
        String n=f.getName().toLowerCase(Locale.US);
        if(n.endsWith(".wav"))return "audio/wav";
        if(n.endsWith(".mp3"))return "audio/mpeg";
        if(n.endsWith(".ogg"))return "audio/ogg";
        if(n.endsWith(".flac"))return "audio/flac";
        if(n.endsWith(".m4a"))return "audio/mp4";
        if(n.endsWith(".mp4"))return "audio/mp4";
        if(n.endsWith(".webm"))return "audio/webm";
        if(n.endsWith(".mpeg")||n.endsWith(".mpga"))return "audio/mpeg";
        return "application/octet-stream";
    }

    private static long duration(File audio){
        MediaMetadataRetriever m=new MediaMetadataRetriever();
        try{
            m.setDataSource(audio.getAbsolutePath());
            String d=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d==null?0:Long.parseLong(d);
        }catch(Exception ignored){return 0;}
        finally{try{m.release();}catch(Exception ignored){}}
    }

    private static final class QualityException extends IOException {
        QualityException(String message){super(message);}
    }

    private static final class HttpStatusException extends IOException {
        final int code;
        HttpStatusException(int code,String message){super(message);this.code=code;}
    }
}
