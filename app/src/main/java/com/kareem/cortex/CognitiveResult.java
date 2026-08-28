package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Provider-neutral structured result. Model output is not authoritative until validated. */
public final class CognitiveResult {
    public final CognitiveDisposition disposition;
    public final double confidence;
    public final String reason;
    public final List<CognitiveItem> items;

    public CognitiveResult(CognitiveDisposition disposition,double confidence,String reason,List<CognitiveItem> items){
        this.disposition=disposition;this.confidence=Math.max(0,Math.min(1,confidence));this.reason=n(reason);
        ArrayList<CognitiveItem> copy=new ArrayList<>();if(items!=null)copy.addAll(items);this.items=Collections.unmodifiableList(copy);
    }

    public JSONObject toJson(){JSONObject o=new JSONObject();try{o.put("disposition",disposition==null?JSONObject.NULL:disposition.name());o.put("confidence",confidence);o.put("reason",reason);JSONArray a=new JSONArray();for(CognitiveItem item:items)if(item!=null)a.put(item.toJson());o.put("items",a);}catch(Throwable ignored){}return o;}
    private static String n(String s){return s==null?"":s.trim();}
}
