package com.kareem.cortex;

import android.content.Context;
import android.media.MediaMetadataRetriever;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class CohereAudioTranscriber {
    public interface Callback{void ok(TranscriptResult r);void fail(Exception e);}
    private static final String ENDPOINT="https://api.cohere.com/v2/audio/transcriptions";
    private static final String MODEL="cohere-transcribe-arabic-07-2026";
    private CohereAudioTranscriber(){}

    public static void transcribe(Context context,File audio,Callback cb){
        final Context app=context.getApplicationContext();
        new Thread(()->{
            try{
                TranscriptResult first=call(app,audio);
                String firstWarning=qualityWarning(first,audio);
                if(firstWarning==null){cb.ok(cleanAfterQualityPass(first));return;}

                TranscriptResult retry=call(app,audio);
                retry.engine=MODEL+"+full_audio_retry";
                String retryWarning=qualityWarning(retry,audio);
                if(retryWarning==null){cb.ok(cleanAfterQualityPass(retry));return;}

                throw new java.io.IOException(
                        "Cohere incomplete after full-audio retry. First: "+firstWarning+
                        " | Retry: "+retryWarning+
                        " | file_bytes="+audio.length());
            }catch(Exception e){cb.fail(e);}
        },"CortexCohereASR").start();
    }

    private static TranscriptResult call(Context context,File audio) throws Exception {
        if(audio==null||!audio.exists()||audio.length()==0)throw new IllegalArgumentException("Missing audio file");
        String key=CohereKeyStore.get(context);
        if(key.isEmpty())throw new IllegalStateException("Cohere API key not configured");

        String boundary="----Cortex"+UUID.randomUUID().toString().replace("-","");
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
            field(out,boundary,"language","ar");
            file(out,boundary,"file",audio,mime(audio));
            write(out,"--"+boundary+"--\r\n");
        }

        int code=c.getResponseCode();
        InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        String body=read(stream);
        c.disconnect();
        if(code<200||code>=300)throw new java.io.IOException("Cohere ASR HTTP "+code+": "+compactError(body));

        JSONObject json=new JSONObject(body);
        TranscriptResult r=new TranscriptResult();
        r.rawProviderResponse=body;
        r.language=json.optString("language","ar");
        r.engine=MODEL;
        r.version="07-2026";
        r.durationMs=duration(audio);

        String topLevel=json.optString("text","").trim();
        String merged=parseSegments(json,r);
        r.providerMergedTranscript=merged;
        r.text=!merged.isEmpty()?merged:topLevel;
        if(r.text.isEmpty())throw new java.io.IOException("Cohere returned an empty transcript");

        if(r.segments.isEmpty()){
            r.processedDurationMs=0;
            r.coverage=0;
        }else{
            r.processedDurationMs=r.segments.get(r.segments.size()-1).endMs;
            r.coverage=r.durationMs>0?Math.min(1.0,(double)r.processedDurationMs/(double)r.durationMs):0;
        }
        return r;
    }

    private static String parseSegments(JSONObject json,TranscriptResult r){
        JSONArray arr=json.optJSONArray("segments");
        if(arr==null||arr.length()==0)return "";
        StringBuilder merged=new StringBuilder();
        long lastEnd=0;
        for(int i=0;i<arr.length();i++){
            JSONObject s=arr.optJSONObject(i);
            if(s==null)continue;
            String text=s.optString("text","").trim();
            if(text.isEmpty())continue;
            long start=toMs(s.optDouble("start",s.optDouble("start_time",0)));
            long end=toMs(s.optDouble("end",s.optDouble("end_time",0)));
            if(end<start)end=start;
            float confidence=(float)s.optDouble("confidence",0);
            r.segments.add(new TranscriptResult.Segment(start,end,text,confidence));
            lastEnd=Math.max(lastEnd,end);
            if(merged.length()>0)merged.append(' ');
            merged.append(text);
        }
        r.processedDurationMs=lastEnd;
        return merged.toString().trim();
    }

    private static long toMs(double value){
        if(value<=0)return 0;
        return Math.round(value*1000.0);
    }

    private static String qualityWarning(TranscriptResult r,File audio){
        String text=r==null||r.text==null?"":r.text.trim();
        if(text.isEmpty())return "Cohere transcript is empty";
        if(text.toLowerCase(Locale.ROOT).contains("<hesitation>"))return "Cohere transcript contains <hesitation>";

        int words=countWords(text);
        long durationMs=r.durationMs>0?r.durationMs:duration(audio);
        long durationSec=Math.max(1,Math.round(durationMs/1000.0));
        if(durationSec>=8&&words<Math.max(4,durationSec/2)){
            return "Cohere transcript failed completeness checks: "+words+" words for "+durationSec+" seconds";
        }

        if(r.processedDurationMs>0&&durationMs>0){
            double coverage=(double)r.processedDurationMs/(double)durationMs;
            r.coverage=Math.min(1.0,coverage);
            if(durationMs>=8000&&coverage<0.60){
                return String.format(Locale.US,"Cohere transcript truncated: %.0f%% timestamp coverage",coverage*100.0);
            }
        }
        return null;
    }

    private static TranscriptResult cleanAfterQualityPass(TranscriptResult r){
        if(r==null)return null;
        r.qualityWarning="";
        r.text=cleanup(r.text);
        r.providerMergedTranscript=cleanup(r.providerMergedTranscript);
        return r;
    }

    private static String cleanup(String s){
        if(s==null)return "";
        return s.replace("<hesitation>"," ").replaceAll("\\s+"," ").trim();
    }

    private static int countWords(String s){
        String t=s==null?"":s.trim();
        if(t.isEmpty())return 0;
        return t.split("\\s+").length;
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
            String m=j.optString("message","");
            if(!m.isEmpty())return m;
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
}
