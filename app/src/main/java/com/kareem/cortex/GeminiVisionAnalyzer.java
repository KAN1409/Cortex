package com.kareem.cortex;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Strong visual understanding path. OCR is supporting evidence, not the primary task. */
public final class GeminiVisionAnalyzer {
    private static final String MODEL="gemini-3.6-flash";
    private static final String PROMPT=
        "You are Cortex Visual Intelligence. Analyze the IMAGE ITSELF, not merely OCR. The user may save screenshots as products, design references, research, chats, documents, places, tasks, inspiration, or images with no text. Return strict JSON only. Preserve visible Arabic exactly in Arabic script and visible English in Latin script; do not transliterate Arabic. Describe meaningful visual content even if there is zero text. Schema: {"+
        "\"content_type\":\"product|design_reference|ui_reference|chat|document|receipt|web_research|place|photo|task|temporary_ui|other\","+
        "\"description\":\"clear concise description of what is actually visible\","+
        "\"visible_text\":\"important readable text only, preserving Arabic and English\","+
        "\"objects\":[{\"name\":\"\",\"type\":\"\",\"details\":\"\",\"confidence\":0.0}],"+
        "\"facts\":[{\"label\":\"brand|product|price|date|url|place|person|style|material|app|other\",\"value\":\"\"}],"+
        "\"related_topics\":[\"\"],"+
        "\"usefulness\":{\"score\":0,\"why\":\"how this image could be useful to the user, not generic filler\"},"+
        "\"suggested_actions\":[{\"id\":\"research_online|find_product|compare_prices|read_reviews|deep_hunt|extract_prompt|recreate_image|save_inspiration|add_to_project|create_reminder|summarize|reference_only|ignore\",\"label\":\"short human label\",\"reason\":\"why\",\"confidence\":0.0,\"query\":\"specific search query when applicable\"}],"+
        "\"search_query\":\"best concrete web search query if research would help, else empty\","+
        "\"recreation_prompt\":\"detailed image-generation prompt when visual recreation/inspiration is applicable, else empty\","+
        "\"privacy\":{\"level\":\"safe|possibly_sensitive\",\"reason\":\"\"}}. "+
        "Do not invent text that is not visible. Do not make every image actionable: if it is transient/low value, say so. Prefer 2-5 genuinely useful actions.";
    private GeminiVisionAnalyzer(){}

    static final class Prepared {byte[] bytes;int width,height,maxSide;Prepared(byte[] b,int w,int h,int m){bytes=b;width=w;height=h;maxSide=m;}}
    static final class VisionException extends IOException {
        final boolean retryable;final int httpCode;final long retryAfterMs;
        VisionException(String m,boolean r){this(m,r,0,0);}VisionException(String m,boolean r,int code,long wait){super(m);retryable=r;httpCode=code;retryAfterMs=Math.max(0,wait);}boolean rateLimited(){return httpCode==429||retryAfterMs>0;}
    }

    public static JSONObject analyze(Context context,KnowledgeItem item)throws Exception{
        File image=new File(item.attachmentPath==null?"":item.attachmentPath);if(!image.exists())throw new FileNotFoundException("Archived screenshot is missing");
        String key=GeminiKeyStore.get(context);if(key.isEmpty())throw new IllegalStateException("Gemini API key not configured");
        Prepared p=prepare(image,item);VisionException last=null;
        for(int attempt=1;attempt<=2;attempt++){
            try{return requestOnce(context,key,p,attempt);}
            catch(VisionException e){last=e;if(attempt>=2||!e.retryable||e.rateLimited())throw e;try{Thread.sleep(850);}catch(InterruptedException ie){Thread.currentThread().interrupt();throw e;}}
        }
        throw last==null?new IOException("Vision analysis failed without diagnostic detail"):last;
    }

    private static JSONObject requestOnce(Context context,String key,Prepared p,int attempt)throws Exception{
        long gateWait=VisionRateLimitGate.beforeRequest(context);if(gateWait>0)throw new VisionException("Gemini Vision provider cooldown • retry_after_ms="+gateWait,false,429,gateWait);
        long started=SystemClock.elapsedRealtime();String b64=Base64.encodeToString(p.bytes,Base64.NO_WRAP);
        JSONObject inline=new JSONObject().put("mimeType","image/jpeg").put("data",b64);
        JSONArray parts=new JSONArray().put(new JSONObject().put("text",PROMPT)).put(new JSONObject().put("inlineData",inline));
        JSONArray contents=new JSONArray().put(new JSONObject().put("role","user").put("parts",parts));
        JSONObject cfg=new JSONObject().put("temperature",0.1).put("maxOutputTokens",4096).put("responseMimeType","application/json");
        JSONObject req=new JSONObject().put("contents",contents).put("generationConfig",cfg);
        String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent?key="+URLEncoder.encode(key,"UTF-8");
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(120000);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
        try(OutputStream out=c.getOutputStream()){out.write(req.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());c.disconnect();long latency=SystemClock.elapsedRealtime()-started;
        if(code<200||code>=300){if(code==429){long wait=VisionRateLimitGate.markProviderRateLimited(context,body);throw new VisionException("Gemini Vision HTTP 429 • cooldown="+wait+"ms • attempt="+attempt+" • latency="+latency+"ms • "+compact(body),false,429,wait);}boolean retry=code==408||code>=500;throw new VisionException("Gemini Vision HTTP "+code+" • attempt="+attempt+" • latency="+latency+"ms • "+compact(body),retry,code,0);}
        VisionRateLimitGate.noteSuccess(context);

        JSONObject envelope=new JSONObject(body);String finish=finishReason(envelope),block=blockReason(envelope);String text=extractText(envelope).trim();
        text=text.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","").trim();
        if(text.isEmpty()){
            boolean blocked=!block.isEmpty()||"SAFETY".equalsIgnoreCase(finish)||"BLOCKLIST".equalsIgnoreCase(finish)||"PROHIBITED_CONTENT".equalsIgnoreCase(finish);
            throw new VisionException("Vision model returned no analysis • http=200 • attempt="+attempt+" • latency="+latency+"ms • finishReason="+empty(finish,"unknown")+" • blockReason="+empty(block,"none")+" • candidates="+candidateCount(envelope),!blocked,200,0);
        }
        JSONObject root;
        try{root=new JSONObject(text);}catch(JSONException e){throw new VisionException("Vision returned invalid JSON • attempt="+attempt+" • latency="+latency+"ms • finishReason="+empty(finish,"unknown")+" • prefix="+compact(text),attempt<2,200,0);}
        normalize(root);root.put("_provider",MODEL+"+vision-v50");
        JSONObject d=new JSONObject();d.put("attempt",attempt);d.put("latency_ms",latency);d.put("http_code",200);d.put("finish_reason",finish);d.put("block_reason",block);d.put("candidate_count",candidateCount(envelope));d.put("prepared_jpeg_bytes",p.bytes.length);d.put("prepared_width",p.width);d.put("prepared_height",p.height);d.put("detail_target_max_side",p.maxSide);root.put("_diagnostics",d);
        return root;
    }

    private static void normalize(JSONObject r)throws Exception{if(!r.has("content_type"))r.put("content_type","other");if(!r.has("description"))r.put("description","");if(!r.has("visible_text"))r.put("visible_text","");if(!r.has("objects"))r.put("objects",new JSONArray());if(!r.has("facts"))r.put("facts",new JSONArray());if(!r.has("related_topics"))r.put("related_topics",new JSONArray());if(!r.has("suggested_actions"))r.put("suggested_actions",new JSONArray());if(!r.has("search_query"))r.put("search_query","");if(!r.has("recreation_prompt"))r.put("recreation_prompt","");if(!r.has("usefulness"))r.put("usefulness",new JSONObject().put("score",0).put("why",""));}

    private static Prepared prepare(File f,KnowledgeItem item)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException("Could not read screenshot dimensions");
        String hints=(nz(item.category)+" "+nz(item.tags)+" "+nz(item.title)).toLowerCase(Locale.ROOT);boolean textHeavy=nz(item.extractedText).length()>350||containsAny(hints,"document","receipt","settings","chat","web","research","product","prompt","email","terminal","code");
        int target=textHeavy?3000:1800;int max=Math.max(o.outWidth,o.outHeight);int sample=1;while(max/sample>target*2)sample*=2;
        BitmapFactory.Options d=new BitmapFactory.Options();d.inSampleSize=Math.max(1,sample);Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),d);if(b==null)throw new IOException("Could not decode screenshot");
        int w=b.getWidth(),h=b.getHeight();float scale=Math.min(1f,target/(float)Math.max(w,h));Bitmap x=b;if(scale<0.999f){x=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);if(x!=b)b.recycle();}
        ByteArrayOutputStream out=new ByteArrayOutputStream();x.compress(Bitmap.CompressFormat.JPEG,textHeavy?92:88,out);int fw=x.getWidth(),fh=x.getHeight();x.recycle();return new Prepared(out.toByteArray(),fw,fh,target);
    }

    private static String extractText(JSONObject root){JSONArray cs=root.optJSONArray("candidates");if(cs==null||cs.length()==0)return"";JSONObject c=cs.optJSONObject(0);if(c==null)return"";JSONObject content=c.optJSONObject("content");if(content==null)return"";JSONArray p=content.optJSONArray("parts");if(p==null)return"";StringBuilder b=new StringBuilder();for(int i=0;i<p.length();i++){JSONObject q=p.optJSONObject(i);if(q!=null&&!q.optString("text","").isEmpty())b.append(q.optString("text","")).append('\n');}return b.toString();}
    private static String finishReason(JSONObject root){JSONArray cs=root.optJSONArray("candidates");JSONObject c=cs!=null&&cs.length()>0?cs.optJSONObject(0):null;return c==null?"":c.optString("finishReason","");}
    private static String blockReason(JSONObject root){JSONObject p=root.optJSONObject("promptFeedback");return p==null?"":p.optString("blockReason","");}
    private static int candidateCount(JSONObject root){JSONArray a=root.optJSONArray("candidates");return a==null?0:a.length();}
    private static String read(InputStream in)throws Exception{if(in==null)return"";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}
    private static String compact(String s){if(s==null)return"";String x=s.replaceAll("\\s+"," ").trim();return x.length()>500?x.substring(0,500)+"…":x;}
    private static String empty(String s,String f){return s==null||s.trim().isEmpty()?f:s.trim();}
    private static String nz(String s){return s==null?"":s;}
    private static boolean containsAny(String n,String...xs){for(String x:xs)if(n.contains(x))return true;return false;}
}
