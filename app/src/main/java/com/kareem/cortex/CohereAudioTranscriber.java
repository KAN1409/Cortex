package com.kareem.cortex;

import android.content.Context;
import android.media.MediaMetadataRetriever;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class CohereAudioTranscriber {
    public interface Callback{void ok(TranscriptResult r);void fail(Exception e);}
    private static final String ENDPOINT="https://api.cohere.com/v2/audio/transcriptions";
    private static final String MODEL="cohere-transcribe-arabic-07-2026";
    private CohereAudioTranscriber(){}

    public static void transcribe(Context context,File audio,Callback cb){
        final Context app=context.getApplicationContext();
        new Thread(()->{
            try{cb.ok(call(app,audio));}
            catch(Exception e){cb.fail(e);}
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
        String text=json.optString("text","").trim();
        if(text.isEmpty())throw new java.io.IOException("Cohere returned an empty transcript");

        TranscriptResult r=new TranscriptResult();
        r.text=text;
        r.language="ar";
        r.engine=MODEL;
        r.version="07-2026";
        r.durationMs=duration(audio);
        r.segments.add(new TranscriptResult.Segment(0,r.durationMs,text,0));
        return r;
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
        String n=f.getName().toLowerCase(java.util.Locale.US);
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
