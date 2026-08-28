package com.kareem.cortex;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Syntax parser only. Semantic safety belongs to CognitiveResultValidator. */
public final class CognitiveResultParser {
    private CognitiveResultParser(){}

    public static Outcome parse(String raw){
        String json=extract(raw);if(json==null)return Outcome.error("INVALID_JSON","no complete JSON object");
        try{
            JSONObject root=new JSONObject(json);CognitiveDisposition disposition;
            try{disposition=CognitiveDisposition.valueOf(root.optString("disposition","").trim().toUpperCase(Locale.ROOT));}
            catch(Throwable e){return Outcome.error("INVALID_DISPOSITION","unsupported disposition");}
            double confidence=confidence(root.opt("confidence"));if(confidence<0)return Outcome.error("INVALID_CONFIDENCE","confidence must be 0..1 or 0..100");
            JSONArray a=root.optJSONArray("items");if(a==null)a=new JSONArray();if(a.length()>5)return Outcome.error("INVALID_ITEMS","more than 5 items");
            ArrayList<CognitiveItem> items=new ArrayList<>();
            for(int i=0;i<a.length();i++){
                JSONObject x=a.optJSONObject(i);if(x==null)return Outcome.error("INVALID_ITEM","item is not an object");CognitiveKind kind;
                try{kind=CognitiveKind.valueOf(x.optString("kind","").trim().toUpperCase(Locale.ROOT));}
                catch(Throwable e){return Outcome.error("INVALID_KIND","unsupported cognitive kind");}
                String summary=x.optString("summary","").trim();int importance=number(x,"importance",40),urgency=number(x,"urgency",30);String person=x.isNull("person")?"":clip(x.optString("person",""),120);long due=parseTime(x.opt("due_at"));
                items.add(new CognitiveItem(kind,summary,importance,urgency,person,due,x.optBoolean("requires_user_action",false),x.optBoolean("requires_follow_up",false),x.optBoolean("requires_content_extraction",false)));
            }
            return Outcome.ok(new CognitiveResult(disposition,confidence,clip(root.optString("reason",""),500),items));
        }catch(Throwable e){return Outcome.error("INVALID_JSON",e.getClass().getSimpleName());}
    }

    private static String extract(String raw){String x=raw==null?"":raw.replace("```json","").replace("```","").trim();int start=x.indexOf('{');if(start<0)return null;boolean in=false,esc=false;int depth=0;for(int i=start;i<x.length();i++){char c=x.charAt(i);if(in){if(esc){esc=false;continue;}if(c=='\\'){esc=true;continue;}if(c=='\"')in=false;continue;}if(c=='\"'){in=true;continue;}if(c=='{')depth++;else if(c=='}'){depth--;if(depth==0)return x.substring(start,i+1);if(depth<0)return null;}}return null;}
    private static double confidence(Object raw){try{double x=Double.parseDouble(String.valueOf(raw));if(Double.isNaN(x)||Double.isInfinite(x)||x<0||x>100)return-1;if(x>1)x/=100.0;return x;}catch(Throwable e){return-1;}}
    private static int number(JSONObject o,String key,int fallback){try{return Math.max(0,Math.min(100,(int)Math.round(Double.parseDouble(String.valueOf(o.opt(key))))));}catch(Throwable e){return fallback;}}
    private static long parseTime(Object raw){if(raw==null||raw==JSONObject.NULL)return 0;try{if(raw instanceof Number)return Math.max(0,((Number)raw).longValue());String x=String.valueOf(raw).trim();if(x.isEmpty())return 0;if(x.matches("\\d{10,13}")){long v=Long.parseLong(x);return x.length()==10?v*1000L:v;}String[] p={"yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mmXXX","yyyy-MM-dd HH:mm"};for(String f:p)try{SimpleDateFormat d=new SimpleDateFormat(f,Locale.US);d.setLenient(false);Date parsed=d.parse(x);if(parsed!=null)return parsed.getTime();}catch(Throwable ignored){}}catch(Throwable ignored){}return 0;}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n);}

    public static final class Outcome{
        public final CognitiveResult result;public final String status,error;
        private Outcome(CognitiveResult r,String s,String e){result=r;status=s;error=e;}
        public boolean valid(){return result!=null&&"VALID".equals(status);}
        static Outcome ok(CognitiveResult r){return new Outcome(r,"VALID","");}
        static Outcome error(String s,String e){return new Outcome(null,s,e==null?"":e);}
    }
}
