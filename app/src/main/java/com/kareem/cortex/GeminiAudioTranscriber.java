package com.kareem.cortex;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class GeminiAudioTranscriber {
    public interface Callback{void ok(TranscriptResult r);void fail(Exception e);}
    private static final String MODEL="gemini-3.6-flash";
    private static final String PROMPT="Transcribe this audio verbatim. Speech may switch between Egyptian Arabic and English. Preserve Egyptian Arabic as spoken, preserve every spoken English word in Latin letters, and do not translate, summarize, paraphrase, or convert Egyptian Arabic to Modern Standard Arabic. Return only the transcript text.";
    private GeminiAudioTranscriber(){}

    public static void transcribe(Context context,File audio,Callback cb){
        final Context app=context.getApplicationContext();
        new Thread(()->{try{cb.ok(call(app,audio));}catch(Exception e){cb.fail(e);}},"CortexGeminiASR").start();
    }

    private static TranscriptResult call(Context context,File audio)throws Exception{
        if(audio==null||!audio.exists()||audio.length()==0)throw new IllegalArgumentException("Missing audio file");
        String key=GeminiKeyStore.get(context);if(key.isEmpty())throw new IllegalStateException("Gemini API key not configured");
        byte[] bytes=readBytes(audio);
        String b64=Base64.encodeToString(bytes,Base64.NO_WRAP);
        JSONObject inline=new JSONObject().put("mimeType",mime(audio)).put("data",b64);
        JSONArray parts=new JSONArray().put(new JSONObject().put("text",PROMPT)).put(new JSONObject().put("inlineData",inline));
        JSONArray contents=new JSONArray().put(new JSONObject().put("role","user").put("parts",parts));
        JSONObject cfg=new JSONObject().put("temperature",0).put("maxOutputTokens",2048);
        JSONObject req=new JSONObject().put("contents",contents).put("generationConfig",cfg);
        String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent?key="+java.net.URLEncoder.encode(key,"UTF-8");
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(120000);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
        try(OutputStream out=c.getOutputStream()){out.write(req.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());c.disconnect();
        if(code<200||code>=300)throw new IOException("Gemini ASR HTTP "+code+": "+compact(body));
        JSONObject root=new JSONObject(body);String text=extractText(root).trim();
        text=text.replaceAll("^```(?:text)?\\s*"," ").replaceAll("```$"," ").replaceAll("\\s+"," ").trim();
        if(text.isEmpty())throw new IOException("Gemini returned an empty transcript");
        long duration=duration(audio);TranscriptResult r=new TranscriptResult();r.text=text;r.rawTranscript=text;r.providerMergedTranscript=text;r.engine=MODEL+"+audio";r.version="gemini-audio-v3";r.durationMs=duration;r.processedDurationMs=duration;r.coverage=duration>0?1.0:0;r.language=detectLanguage(text);r.rawProviderResponse=body;return r;
    }

    private static String extractText(JSONObject root){
        JSONArray cs=root.optJSONArray("candidates");if(cs==null||cs.length()==0)return "";JSONObject c=cs.optJSONObject(0);if(c==null)return "";JSONObject content=c.optJSONObject("content");if(content==null)return "";JSONArray parts=content.optJSONArray("parts");if(parts==null)return "";StringBuilder b=new StringBuilder();for(int i=0;i<parts.length();i++){JSONObject p=parts.optJSONObject(i);if(p==null)continue;String t=p.optString("text","");if(!t.isEmpty()){if(b.length()>0)b.append(' ');b.append(t);}}return b.toString();
    }
    private static byte[] readBytes(File f)throws Exception{try(InputStream in=new BufferedInputStream(new FileInputStream(f));ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[65536];for(int n;(n=in.read(buf))!=-1;)b.write(buf,0,n);return b.toByteArray();}}
    private static String read(InputStream in)throws Exception{if(in==null)return "";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}
    private static long duration(File f){try{MediaMetadataRetriever m=new MediaMetadataRetriever();m.setDataSource(f.getAbsolutePath());String d=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);m.release();return d==null?0:Long.parseLong(d);}catch(Exception e){return 0;}}
    private static String mime(File f){String n=f.getName().toLowerCase(Locale.ROOT);if(n.endsWith(".wav"))return "audio/wav";if(n.endsWith(".m4a"))return "audio/mp4";if(n.endsWith(".mp3"))return "audio/mpeg";if(n.endsWith(".ogg"))return "audio/ogg";return "audio/wav";}
    private static String detectLanguage(String s){int ar=0,la=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c>=0x0600&&c<=0x06ff)ar++;else if((c>='A'&&c<='Z')||(c>='a'&&c<='z'))la++;}if(ar>0&&la>0)return "Arabic+English";if(ar>0)return "Arabic";if(la>0)return "English";return "auto";}
    private static String compact(String s){if(s==null)return "";String x=s.replaceAll("\\s+"," ").trim();return x.length()>500?x.substring(0,500)+"…":x;}
}
