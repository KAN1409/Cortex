package com.kareem.cortex;

import org.json.*;
import java.util.*;

public class AnalysisResult {
    public static class Entity { public String kind,value;public double confidence;public Entity(String k,String v,double c){kind=k;value=v;confidence=c;} }
    public static class Action { public String text,dueText;public Action(String t,String d){text=t;dueText=d;} }
    public static class VisionField { public String key,value;public double confidence;public VisionField(String k,String v,double c){key=k;value=v;confidence=c;} }
    public static class TranscriptSegment { public long startMs,endMs;public String text;public float confidence;public TranscriptSegment(long s,long e,String t,float c){startMs=s;endMs=e;text=t;confidence=c;} }

    public String title="",summary="",category="Notes",tags="",extractedText="";
    public String engine="local_rules",version="1",visionType="",audioLanguage="",audioRawTranscript="",audioProviderMergedTranscript="",audioRawProviderResponse="";
    public double visionConfidence=0,audioCoverage=0;public long audioDurationMs=0,audioProcessedDurationMs=0;
    public final ArrayList<Entity> entities=new ArrayList<>();public final ArrayList<Action> actions=new ArrayList<>();public final ArrayList<VisionField> visionFields=new ArrayList<>();public final ArrayList<TranscriptSegment> transcriptSegments=new ArrayList<>();

    public String toJson(){try{
        JSONObject o=new JSONObject();o.put("title",title);o.put("summary",summary);o.put("category",category);o.put("tags",tags);o.put("extracted_text",extractedText);o.put("engine",engine);o.put("version",version);o.put("vision_type",visionType);o.put("vision_confidence",visionConfidence);o.put("audio_language",audioLanguage);o.put("audio_duration_ms",audioDurationMs);o.put("audio_processed_duration_ms",audioProcessedDurationMs);o.put("audio_coverage",audioCoverage);o.put("audio_raw_transcript",audioRawTranscript);o.put("audio_provider_merged_transcript",audioProviderMergedTranscript);o.put("audio_raw_provider_response",audioRawProviderResponse);
        JSONArray es=new JSONArray();for(Entity e:entities){JSONObject x=new JSONObject();x.put("kind",e.kind);x.put("value",e.value);x.put("confidence",e.confidence);es.put(x);}o.put("entities",es);
        JSONArray as=new JSONArray();for(Action a:actions){JSONObject x=new JSONObject();x.put("text",a.text);x.put("due_text",a.dueText);as.put(x);}o.put("actions",as);
        JSONArray vs=new JSONArray();for(VisionField f:visionFields){JSONObject x=new JSONObject();x.put("key",f.key);x.put("value",f.value);x.put("confidence",f.confidence);vs.put(x);}o.put("vision_fields",vs);
        JSONArray ts=new JSONArray();for(TranscriptSegment s:transcriptSegments){JSONObject x=new JSONObject();x.put("start_ms",s.startMs);x.put("end_ms",s.endMs);x.put("text",s.text);x.put("confidence",s.confidence);ts.put(x);}o.put("transcript_segments",ts);return o.toString();
    }catch(Exception e){return "{}";}}
}
