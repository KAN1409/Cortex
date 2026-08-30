package com.kareem.cortex.rebuild;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** URL/video reader + GPT-OSS summarizer. */
public final class WebSummaryEngine {
    public interface Callback{void done(String summary,Exception error);}
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"cortex-web-summary"));
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String MODEL="openai/gpt-oss-120b";
    private static final String GROQ="https://api.groq.com/openai/v1/chat/completions";
    private WebSummaryEngine(){}

    public static void summarize(Context context,String url,Callback callback){Context app=context.getApplicationContext();IO.execute(()->{String result="";Exception error=null;try{String text=readPage(url);if(text.trim().isEmpty())throw new IOException("No readable content found at this URL");result=summarizeWithGroq(app,url,text);}catch(Exception e){error=e;}String r=result;Exception x=error;MAIN.post(()->{if(callback!=null)callback.done(r,x);});});}

    private static String readPage(String target)throws Exception{
        String reader="https://r.jina.ai/"+target;
        try{return fetch(reader,60000);}catch(Exception readerError){String raw=fetch(target,45000);return stripHtml(raw);}
    }

    private static String summarizeWithGroq(Context context,String url,String text)throws Exception{
        String key=GroqKeyStore.get(context);if(key==null||key.trim().isEmpty())throw new IllegalStateException("Groq key is required for URL summarization");
        String clipped=text.length()>52000?text.substring(0,52000):text;
        boolean video=url.contains("youtube.com")||url.contains("youtu.be")||url.contains("vimeo.com");
        String prompt="Summarize the grounded source below for the Cortex user. Keep it concise but useful. " +
                (video?"This is a video URL: prioritize transcript/spoken content when present; if the reader only exposed metadata, clearly say the summary is based only on available page text. ":"This is a web page: summarize its main claims, key details and actionable points. ")+
                "Do not invent material missing from SOURCE. Return plain text with a short overview followed by 3-7 key points.\n\nURL: "+url+"\n\nSOURCE:\n"+clipped;
        JSONObject req=new JSONObject();req.put("model",MODEL);req.put("reasoning_effort","medium");req.put("reasoning_format","hidden");req.put("max_completion_tokens",1200);req.put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",prompt)));
        HttpURLConnection c=(HttpURLConnection)new URL(GROQ).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(12000);c.setReadTimeout(45000);c.setRequestProperty("Authorization","Bearer "+key.trim());c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");try(OutputStream out=c.getOutputStream()){out.write(req.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();String response=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());c.disconnect();if(code<200||code>=300)throw new IOException("GPT-OSS summary HTTP "+code+": "+clip(response,300));JSONObject root=new JSONObject(response);JSONArray choices=root.optJSONArray("choices");JSONObject choice=choices==null||choices.length()==0?null:choices.optJSONObject(0);JSONObject message=choice==null?null:choice.optJSONObject("message");String content=message==null?"":message.optString("content","").trim();if(content.isEmpty())throw new IOException("GPT-OSS returned no summary");return content;
    }

    private static String fetch(String url,int timeout)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(timeout);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Cortex/0.8 Android");c.setRequestProperty("Accept","text/plain,text/markdown,text/html,application/xhtml+xml,*/*;q=0.5");int code=c.getResponseCode();String body=read(code>=200&&code<400?c.getInputStream():c.getErrorStream());c.disconnect();if(code<200||code>=400)throw new IOException("Source HTTP "+code);return body;}
    private static String stripHtml(String html){String x=html==null?"":html;x=x.replaceAll("(?is)<script.*?</script>"," ").replaceAll("(?is)<style.*?</style>"," ").replaceAll("(?s)<[^>]+>"," ");x=x.replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">");return x.replaceAll("\\s+"," ").trim();}
    private static String read(InputStream in)throws Exception{if(in==null)return"";try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[]buf=new byte[8192];for(int n;(n=x.read(buf))!=-1;)b.write(buf,0,n);return b.toString("UTF-8");}}
    private static String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
