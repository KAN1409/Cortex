package com.kareem.cortex;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional self-hosted Qwen3.5-4B provider using vLLM's OpenAI-compatible chat endpoint. */
public final class DeepQwenBrain implements CortexBrain {
    private final Context app;
    public DeepQwenBrain(Context context){this.app=context==null?null:context.getApplicationContext();}

    @Override public CognitiveResult classify(CognitiveInput input)throws BrainException{
        BrainCompletion completion=run(new BrainRequest(CognitivePromptBuilder.system(),CognitivePromptBuilder.build(input),Math.min(256,LocalBrainConfig.MAX_OUTPUT_TOKENS)));
        CognitiveResultParser.Outcome parsed=CognitiveResultParser.parse(completion.text);if(!parsed.valid())throw new BrainException("DEEP_INVALID_JSON",parsed.status+": "+parsed.error);
        CognitiveResultValidator.Outcome validated=CognitiveResultValidator.validate(input,parsed.result);if(!validated.valid())throw new BrainException("DEEP_INVALID_RESULT",validated.error);
        return validated.result;
    }

    @Override public BrainCompletion classify(BrainRequest input)throws BrainException{return run(input);}
    @Override public BrainCompletion synthesizePulse(BrainRequest input)throws BrainException{return run(input);}
    @Override public BrainCompletion answer(BrainRequest input)throws BrainException{return run(input);}

    private BrainCompletion run(BrainRequest input)throws BrainException{
        if(!isAvailable())throw new BrainException("DEEP_QWEN_UNAVAILABLE","Deep Qwen server is not configured");
        HttpURLConnection c=null;long started=System.currentTimeMillis();
        try{
            URL url=new URL(DeepQwenConfig.baseUrl(app)+"/v1/chat/completions");
            c=(HttpURLConnection)url.openConnection();c.setRequestMethod("POST");c.setConnectTimeout(7000);c.setReadTimeout(30000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
            String token=DeepQwenConfig.bearerToken(app);if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
            JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content",input.systemPrompt));messages.put(new JSONObject().put("role","user").put("content",input.userPrompt));
            JSONObject body=new JSONObject().put("model",DeepQwenConfig.MODEL).put("messages",messages).put("temperature",0.2).put("max_tokens",Math.min(256,input.maxTokens));
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();String raw=read(stream);
            if(code<200||code>=300)throw new BrainException("DEEP_QWEN_HTTP_"+code,"Deep Qwen HTTP "+code+": "+clip(raw,500));
            JSONObject root=new JSONObject(raw);JSONArray choices=root.optJSONArray("choices");if(choices==null||choices.length()==0)throw new BrainException("DEEP_QWEN_EMPTY","Deep Qwen returned no choices");
            JSONObject message=choices.getJSONObject(0).optJSONObject("message");String text=message==null?"":message.optString("content","");if(text.trim().isEmpty())throw new BrainException("DEEP_QWEN_EMPTY","Deep Qwen returned empty content");
            int tokens=0;JSONObject usage=root.optJSONObject("usage");if(usage!=null)tokens=Math.max(0,usage.optInt("completion_tokens",0));long latency=System.currentTimeMillis()-started;
            return new BrainCompletion(text,provider(),model(),latency,tokens,0f,false);
        }catch(BrainException e){throw e;}catch(Throwable t){throw new BrainException("DEEP_QWEN_FAILED",t.getClass().getSimpleName()+": "+(t.getMessage()==null?"":t.getMessage()),t);}finally{if(c!=null)c.disconnect();}
    }

    @Override public boolean isAvailable(){return app!=null&&DeepQwenConfig.enabled(app);}
    @Override public String provider(){return"DEEP";}
    @Override public String model(){return DeepQwenConfig.MODEL;}

    private static String read(InputStream in)throws Exception{if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){b.append(line);if(b.length()>100000)break;}}return b.toString();}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n);}
}
