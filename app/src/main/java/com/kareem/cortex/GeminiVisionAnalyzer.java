package com.kareem.cortex;

import android.content.Context;
import android.graphics.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

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

    public static JSONObject analyze(Context context,KnowledgeItem item)throws Exception{
        File image=new File(item.attachmentPath==null?"":item.attachmentPath);if(!image.exists())throw new FileNotFoundException("Archived screenshot is missing");
        String key=GeminiKeyStore.get(context);if(key.isEmpty())throw new IllegalStateException("Gemini API key not configured");
        byte[] jpeg=prepare(image);String b64=Base64.encodeToString(jpeg,Base64.NO_WRAP);
        JSONObject inline=new JSONObject().put("mimeType","image/jpeg").put("data",b64);
        JSONArray parts=new JSONArray().put(new JSONObject().put("text",PROMPT)).put(new JSONObject().put("inlineData",inline));
        JSONArray contents=new JSONArray().put(new JSONObject().put("role","user").put("parts",parts));
        JSONObject cfg=new JSONObject().put("temperature",0.1).put("maxOutputTokens",4096).put("responseMimeType","application/json");
        JSONObject req=new JSONObject().put("contents",contents).put("generationConfig",cfg);
        String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent?key="+URLEncoder.encode(key,"UTF-8");
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(120000);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
        try(OutputStream out=c.getOutputStream()){out.write(req.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());c.disconnect();if(code<200||code>=300)throw new IOException("Gemini Vision HTTP "+code+": "+compact(body));
        JSONObject envelope=new JSONObject(body);String text=extractText(envelope).trim();text=text.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","").trim();if(text.isEmpty())throw new IOException("Vision model returned no analysis");
        JSONObject root=new JSONObject(text);normalize(root);root.put("_provider",MODEL+"+vision-v47");return root;
    }

    private static void normalize(JSONObject r)throws Exception{if(!r.has("content_type"))r.put("content_type","other");if(!r.has("description"))r.put("description","");if(!r.has("visible_text"))r.put("visible_text","");if(!r.has("objects"))r.put("objects",new JSONArray());if(!r.has("facts"))r.put("facts",new JSONArray());if(!r.has("related_topics"))r.put("related_topics",new JSONArray());if(!r.has("suggested_actions"))r.put("suggested_actions",new JSONArray());if(!r.has("search_query"))r.put("search_query","");if(!r.has("recreation_prompt"))r.put("recreation_prompt","");if(!r.has("usefulness"))r.put("usefulness",new JSONObject().put("score",0).put("why",""));}
    private static byte[] prepare(File f)throws Exception{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),o);int max=Math.max(o.outWidth,o.outHeight);int sample=1;while(max/sample>1800)sample*=2;BitmapFactory.Options d=new BitmapFactory.Options();d.inSampleSize=Math.max(1,sample);Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),d);if(b==null)throw new IOException("Could not decode screenshot");int w=b.getWidth(),h=b.getHeight();float scale=Math.min(1f,1800f/Math.max(w,h));Bitmap x=b;if(scale<0.999f){x=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);if(x!=b)b.recycle();}ByteArrayOutputStream out=new ByteArrayOutputStream();x.compress(Bitmap.CompressFormat.JPEG,88,out);x.recycle();return out.toByteArray();}
    private static String extractText(JSONObject root){JSONArray cs=root.optJSONArray("candidates");if(cs==null||cs.length()==0)return"";JSONObject c=cs.optJSONObject(0);if(c==null)return"";JSONObject content=c.optJSONObject("content");if(content==null)return"";JSONArray p=content.optJSONArray("parts");if(p==null)return"";StringBuilder b=new StringBuilder();for(int i=0;i<p.length();i++){JSONObject q=p.optJSONObject(i);if(q!=null&&!q.optString("text","").isEmpty())b.append(q.optString("text","")).append('\n');}return b.toString();}
    private static String read(InputStream in)throws Exception{if(in==null)return"";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}
    private static String compact(String s){if(s==null)return"";String x=s.replaceAll("\\s+"," ").trim();return x.length()>500?x.substring(0,500)+"…":x;}
}
