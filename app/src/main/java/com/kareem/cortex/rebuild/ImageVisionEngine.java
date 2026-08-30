package com.kareem.cortex.rebuild;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multimodal perception only. Produces grounded image text/description before Cortex Brain decides
 * meaning. Groq Qwen vision is primary because the existing Groq credential already powers Cortex
 * cognition/ASR fallback; Gemini remains a fully independent fallback instead of a hard requirement.
 */
public final class ImageVisionEngine {
    private static final String GROQ_MODEL="qwen/qwen3.6-27b";
    private static final String GEMINI_MODEL="gemini-3.6-flash";
    private static final String GROQ_ENDPOINT="https://api.groq.com/openai/v1/chat/completions";
    private static final long HARD_TIMEOUT_MS=45_000L;
    private static final int MAX_SIDE=2200;
    private static final ScheduledExecutorService TIMEOUTS=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"cortex-vision-timeout");t.setDaemon(true);return t;});
    private static final String PROMPT=
            "Analyze this user-captured image as evidence. Be literal and conservative. Extract visible text exactly as readable, including Arabic and English without translating it. " +
            "Describe only visible objects/content. Do not infer medical use, dosage, identity, intent, ownership, diagnosis, urgency, or hidden context. " +
            "For product packaging, capture brand/product name, strength/quantity, manufacturer and other printed facts only when visibly readable. " +
            "If text is unclear, preserve uncertainty instead of guessing. Return JSON only with keys: summary (string), extracted_text (string), visible_entities (array of strings), urls (array of strings), barcodes (array of strings), uncertainties (array of strings).";

    private ImageVisionEngine(){}

    public static Result analyze(Context context,File image,String sourceMime)throws Exception{
        if(image==null||!image.isFile()||image.length()==0)throw new IllegalArgumentException("Image file missing");
        Prepared prepared=prepare(image,sourceMime);
        String groqKey=clean(GroqKeyStore.get(context));
        String geminiKey=clean(GeminiKeyStore.get(context));
        if(groqKey.isEmpty()&&geminiKey.isEmpty())throw new IllegalStateException("Configure a Groq or Gemini API key for photo understanding");

        Exception groqError=null;
        if(!groqKey.isEmpty()){
            try{return analyzeGroq(groqKey,prepared);}catch(Exception e){groqError=e;}
        }
        if(!geminiKey.isEmpty()){
            try{return analyzeGemini(geminiKey,prepared);}catch(Exception geminiError){
                if(groqError==null)throw geminiError;
                IOException combined=new IOException("Photo vision providers failed · Groq: "+compact(message(groqError),180)+" · Gemini: "+compact(message(geminiError),180));
                combined.addSuppressed(groqError);combined.addSuppressed(geminiError);throw combined;
            }
        }
        throw groqError==null?new IllegalStateException("No photo vision provider is available"):groqError;
    }

    private static Result analyzeGroq(String key,Prepared prepared)throws Exception{
        String b64=Base64.encodeToString(prepared.bytes,Base64.NO_WRAP);
        JSONArray content=new JSONArray()
                .put(new JSONObject().put("type","text").put("text",PROMPT))
                .put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:"+prepared.mime+";base64,"+b64)));
        JSONObject request=new JSONObject()
                .put("model",GROQ_MODEL)
                .put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",content)))
                .put("temperature",0)
                .put("max_completion_tokens",1800)
                .put("stream",false)
                .put("response_format",new JSONObject().put("type","json_object"));

        HttpURLConnection c=(HttpURLConnection)new URL(GROQ_ENDPOINT).openConnection();
        AtomicBoolean forced=new AtomicBoolean(false);
        ScheduledFuture<?> guard=TIMEOUTS.schedule(()->{forced.set(true);try{c.disconnect();}catch(Throwable ignored){}},HARD_TIMEOUT_MS,TimeUnit.MILLISECONDS);
        try{
            c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(12_000);c.setReadTimeout(40_000);
            c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
            try(OutputStream out=c.getOutputStream()){out.write(request.toString().getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode();String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());
            if(forced.get())throw new SocketTimeoutException("Groq Vision hard timeout");
            if(code<200||code>=300)throw new IOException("Groq Vision HTTP "+code+": "+compact(body,500));
            JSONObject root=new JSONObject(body);JSONArray choices=root.optJSONArray("choices");JSONObject choice=choices==null||choices.length()==0?null:choices.optJSONObject(0);JSONObject msg=choice==null?null:choice.optJSONObject("message");String text=msg==null?"":clean(msg.optString("content"));
            Result r=parseResult(text,"groq",GROQ_MODEL,body);
            if(r.summary.isEmpty()&&r.extractedText.isEmpty())throw new IOException("Groq Vision returned no grounded content");
            return r;
        }catch(IOException e){if(forced.get())throw new SocketTimeoutException("Groq Vision hard timeout");throw e;}
        finally{guard.cancel(false);try{c.disconnect();}catch(Throwable ignored){}}
    }

    private static Result analyzeGemini(String key,Prepared prepared)throws Exception{
        String b64=Base64.encodeToString(prepared.bytes,Base64.NO_WRAP);
        JSONObject inline=new JSONObject().put("mimeType",prepared.mime).put("data",b64);
        JSONArray parts=new JSONArray().put(new JSONObject().put("text",PROMPT)).put(new JSONObject().put("inlineData",inline));
        JSONObject request=new JSONObject()
                .put("contents",new JSONArray().put(new JSONObject().put("role","user").put("parts",parts)))
                .put("generationConfig",new JSONObject().put("temperature",0).put("maxOutputTokens",1800).put("responseMimeType","application/json"));

        String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+GEMINI_MODEL+":generateContent?key="+java.net.URLEncoder.encode(key,"UTF-8");
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        AtomicBoolean forced=new AtomicBoolean(false);
        ScheduledFuture<?> guard=TIMEOUTS.schedule(()->{forced.set(true);try{c.disconnect();}catch(Throwable ignored){}},HARD_TIMEOUT_MS,TimeUnit.MILLISECONDS);
        try{
            c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(12_000);c.setReadTimeout(40_000);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
            try(OutputStream out=c.getOutputStream()){out.write(request.toString().getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode();String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());
            if(forced.get())throw new SocketTimeoutException("Gemini Vision hard timeout");
            if(code<200||code>=300)throw new IOException("Gemini Vision HTTP "+code+": "+compact(body,500));
            Result r=parseResult(extractGeminiText(new JSONObject(body)),"gemini",GEMINI_MODEL,body);
            if(r.summary.isEmpty()&&r.extractedText.isEmpty())throw new IOException("Gemini Vision returned no grounded content");
            return r;
        }catch(IOException e){if(forced.get())throw new SocketTimeoutException("Gemini Vision hard timeout");throw e;}
        finally{guard.cancel(false);try{c.disconnect();}catch(Throwable ignored){}}
    }

    private static Result parseResult(String text,String provider,String model,String raw)throws Exception{
        String json=clean(text);if(json.startsWith("```"))json=json.replaceFirst("^```(?:json)?\\s*","").replaceFirst("```\\s*$","").trim();
        JSONObject j=new JSONObject(json);Result r=new Result();r.provider=provider;r.model=model;r.summary=clean(j.optString("summary"));r.extractedText=clean(j.optString("extracted_text"));r.visibleEntities=copyArray(j.optJSONArray("visible_entities"));r.urls=copyArray(j.optJSONArray("urls"));r.barcodes=copyArray(j.optJSONArray("barcodes"));r.uncertainties=copyArray(j.optJSONArray("uncertainties"));r.rawProviderResponse=raw;return r;
    }

    private static Prepared prepare(File f,String sourceMime)throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),bounds);
        int w=Math.max(1,bounds.outWidth),h=Math.max(1,bounds.outHeight),sample=1;
        while(Math.max(w/sample,h/sample)>MAX_SIDE)sample*=2;
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=Math.max(1,sample);opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),opts);
        if(b==null){byte[] raw=readBounded(f,8_000_000L);String mime=sourceMime==null||!sourceMime.startsWith("image/")?"image/jpeg":sourceMime;return new Prepared(raw,mime);}
        int rotation=orientation(f);if(rotation!=0){Matrix m=new Matrix();m.postRotate(rotation);Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}
        ByteArrayOutputStream out=new ByteArrayOutputStream();int quality=90;b.compress(Bitmap.CompressFormat.JPEG,quality,out);while(out.size()>7_000_000&&quality>55){quality-=10;out.reset();b.compress(Bitmap.CompressFormat.JPEG,quality,out);}b.recycle();
        if(out.size()==0||out.size()>8_000_000)throw new IOException("Image is too large for safe mobile vision analysis");return new Prepared(out.toByteArray(),"image/jpeg");
    }

    private static int orientation(File f){try{ExifInterface e=new ExifInterface(f.getAbsolutePath());int o=e.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);if(o==ExifInterface.ORIENTATION_ROTATE_90)return 90;if(o==ExifInterface.ORIENTATION_ROTATE_180)return 180;if(o==ExifInterface.ORIENTATION_ROTATE_270)return 270;}catch(Throwable ignored){}return 0;}
    private static byte[] readBounded(File f,long max)throws Exception{if(f.length()<=0||f.length()>max)throw new IOException("Image outside safe inline size");try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream((int)f.length())){byte[] buf=new byte[65_536];long total=0;for(int n;(n=in.read(buf))!=-1;){total+=n;if(total>max)throw new IOException("Image grew beyond safe inline size");out.write(buf,0,n);}return out.toByteArray();}}
    private static String extractGeminiText(JSONObject root){JSONArray cs=root.optJSONArray("candidates");if(cs==null||cs.length()==0)return"";JSONObject candidate=cs.optJSONObject(0);JSONObject content=candidate==null?null:candidate.optJSONObject("content");JSONArray ps=content==null?null:content.optJSONArray("parts");if(ps==null)return"";StringBuilder b=new StringBuilder();for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);String t=p==null?"":p.optString("text","");if(!t.isEmpty())b.append(t);}return b.toString();}
    private static JSONArray copyArray(JSONArray a){JSONArray out=new JSONArray();if(a!=null)for(int i=0;i<a.length();i++){String s=clean(a.optString(i));if(!s.isEmpty())out.put(s);}return out;}
    private static String read(InputStream in)throws Exception{if(in==null)return"";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}
    private static String clean(String s){return s==null?"":s.trim();}private static String compact(String s,int n){String x=clean(s).replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}private static String message(Throwable e){if(e==null)return"unknown error";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();}

    private static final class Prepared{final byte[] bytes;final String mime;Prepared(byte[] bytes,String mime){this.bytes=bytes;this.mime=mime;}}
    public static final class Result{
        public String provider="",model="",summary="",extractedText="",rawProviderResponse="";public JSONArray visibleEntities=new JSONArray(),urls=new JSONArray(),barcodes=new JSONArray(),uncertainties=new JSONArray();
        public String brainText(){StringBuilder b=new StringBuilder();b.append("IMAGE EVIDENCE\nVision summary: ").append(summary);if(!extractedText.isEmpty())b.append("\nVisible text (OCR): ").append(extractedText);if(visibleEntities.length()>0)b.append("\nVisible entities/objects: ").append(visibleEntities);if(urls.length()>0)b.append("\nVisible URLs: ").append(urls);if(barcodes.length()>0)b.append("\nVisible barcodes: ").append(barcodes);if(uncertainties.length()>0)b.append("\nUncertainties: ").append(uncertainties);b.append("\nTreat only these visible observations as grounded. Do not infer the user's intent from the image alone.");return b.toString();}
        public JSONObject toJson()throws Exception{return new JSONObject().put("provider",provider).put("model",model).put("summary",summary).put("extracted_text",extractedText).put("visible_entities",visibleEntities).put("urls",urls).put("barcodes",barcodes).put("uncertainties",uncertainties).put("analyzed_at",System.currentTimeMillis());}
    }
}
