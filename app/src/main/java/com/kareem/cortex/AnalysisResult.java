package com.kareem.cortex;

import org.json.*;
import java.util.*;

public class AnalysisResult {
    public static class Entity {
        public String kind, value; public double confidence;
        public Entity(String k,String v,double c){kind=k;value=v;confidence=c;}
    }
    public static class Action {
        public String text, dueText;
        public Action(String t,String d){text=t;dueText=d;}
    }
    public static class VisionField {
        public String key,value; public double confidence;
        public VisionField(String k,String v,double c){key=k;value=v;confidence=c;}
    }

    public String title="", summary="", category="Notes", tags="", extractedText="";
    public String engine="local_rules", version="1";
    public String visionType=""; public double visionConfidence=0;
    public final ArrayList<Entity> entities=new ArrayList<>();
    public final ArrayList<Action> actions=new ArrayList<>();
    public final ArrayList<VisionField> visionFields=new ArrayList<>();

    public String toJson(){
        try{
            JSONObject o=new JSONObject();
            o.put("title",title);o.put("summary",summary);o.put("category",category);
            o.put("tags",tags);o.put("extracted_text",extractedText);
            o.put("engine",engine);o.put("version",version);
            o.put("vision_type",visionType);o.put("vision_confidence",visionConfidence);
            JSONArray es=new JSONArray();
            for(Entity e:entities){JSONObject x=new JSONObject();x.put("kind",e.kind);x.put("value",e.value);x.put("confidence",e.confidence);es.put(x);} o.put("entities",es);
            JSONArray as=new JSONArray();
            for(Action a:actions){JSONObject x=new JSONObject();x.put("text",a.text);x.put("due_text",a.dueText);as.put(x);} o.put("actions",as);
            JSONArray vs=new JSONArray();
            for(VisionField f:visionFields){JSONObject x=new JSONObject();x.put("key",f.key);x.put("value",f.value);x.put("confidence",f.confidence);vs.put(x);}o.put("vision_fields",vs);
            return o.toString();
        }catch(Exception e){ return "{}"; }
    }
}
