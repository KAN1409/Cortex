package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Expresses Cortex's already-derived world state through the same V5 decision contract.
 * It does not invent new cognition; that is intentional, because the differential should
 * reveal what Cortex failed to infer/link/prioritize compared with the teacher.
 */
public final class CognitivePacketStudentAdapter {
    private CognitivePacketStudentAdapter(){}

    public static JSONObject decide(JSONObject packet){
        JSONObject out=new JSONObject();JSONArray decisions=new JSONArray();
        try{
            out.put("schema_version",CognitiveDecisionContract.VERSION);
            out.put("summary","Cortex student projection of its current derived state.");
            JSONObject state=packet==null?null:packet.optJSONObject("current_state");
            if(state!=null){
                emit(decisions,state.optJSONArray("attention"),"SURFACE_NOW","Cortex currently classifies this as live attention.");
                emit(decisions,state.optJSONArray("waiting"),"WATCH","Cortex currently classifies this as waiting on a dependency.");
                emit(decisions,state.optJSONArray("decisions"),"ASK_USER","Cortex currently classifies this as an unresolved decision.");
                emit(decisions,state.optJSONArray("goals"),"STORE","Cortex currently retains this as goal/project context.");
            }
            if(decisions.length()==0){
                JSONArray evidence=packet==null?null:packet.optJSONArray("new_evidence");
                if(evidence!=null&&evidence.length()>0){JSONObject e=evidence.optJSONObject(0);if(e!=null){JSONArray refs=new JSONArray().put(e.optString("ref"));decisions.put(new JSONObject().put("type","STORE").put("target_ref","").put("evidence_refs",refs).put("confidence",0.40).put("reason","Cortex has evidence but no live derived situation for it.").put("proposed_state",new JSONObject()).put("next_action",JSONObject.NULL));}}
            }
            out.put("decisions",decisions);
        }catch(Exception e){throw new IllegalStateException("Cannot project Cortex student decision",e);}
        return out;
    }

    private static void emit(JSONArray out,JSONArray items,String type,String reason)throws Exception{
        if(items==null)return;for(int i=0;i<items.length();i++){JSONObject x=items.optJSONObject(i);if(x==null)continue;String ref=x.optString("ref","");if(ref.isEmpty())continue;double confidence=x.optDouble("confidence",0.65);if(confidence<=0)confidence=0.65;if(confidence>1)confidence=1;
            JSONObject d=new JSONObject().put("type",type).put("target_ref",ref).put("evidence_refs",new JSONArray()).put("confidence",confidence).put("reason",reason).put("proposed_state",new JSONObject().put("kind",x.optString("kind")).put("state",x.optString("state")).put("importance",x.optInt("importance"))).put("next_action",JSONObject.NULL);out.put(d);
        }
    }
}
