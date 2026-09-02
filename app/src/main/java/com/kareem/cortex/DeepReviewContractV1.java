package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * User-triggered Cortex ↔ ChatGPT review contract.
 *
 * ChatGPT never writes Cortex truth directly. A response is accepted only when every proposed
 * cognitive item cites existing Cortex evidence IDs. Apply creates grounded derived intelligence
 * through CognitiveStore; existing evidence and terminal state are never rewritten automatically.
 */
public final class DeepReviewContractV1 {
    public static final String CONTEXT_MARKER="CORTEX_DEEP_REVIEW_CONTEXT_V1";
    public static final String RESPONSE_MARKER="CORTEX_REVIEW_V1";
    private static final int MAX_EVIDENCE=160, MAX_DERIVED=120, MAX_PROMPT_CHARS=300_000;
    private static final Set<String> ALLOWED_KINDS=new HashSet<>(Arrays.asList(
            "ACTION","WAITING","DECISION","PROJECT_CANDIDATE","GOAL_SIGNAL",
            "IDEA","OPPORTUNITY","INSIGHT","HYPOTHESIS","REVIEW"));

    private DeepReviewContractV1(){}

    public static final class PromptPack {
        public final String requestId,text; public final int evidenceCount,stateCount;
        PromptPack(String r,String t,int e,int s){requestId=r;text=t;evidenceCount=e;stateCount=s;}
    }
    public static final class Item {
        public final String kind,title,body,reason; public final int importance; public final double confidence; public final long[] evidenceIds;
        Item(String k,String t,String b,String r,int i,double c,long[] e){kind=k;title=t;body=b;reason=r;importance=i;confidence=c;evidenceIds=e;}
    }
    public static final class Action {
        public final String title,why; public final long[] evidenceIds;
        Action(String t,String w,long[] e){title=t;why=w;evidenceIds=e;}
    }
    public static final class Review {
        public final String requestId,answer,raw; public final ArrayList<Item> items; public final ArrayList<Action> actions;
        Review(String r,String a,String raw,ArrayList<Item> i,ArrayList<Action> x){requestId=r;answer=a;this.raw=raw;items=i;actions=x;}
    }
    public static final class ApplyResult {
        public final long reviewEvidenceId; public final int created; ApplyResult(long id,int n){reviewEvidenceId=id;created=n;}
    }

    public static PromptPack build(VaultDb db){
        CognitiveStore.ensure(db);String requestId="review_"+System.currentTimeMillis();JSONObject snap=new JSONObject();JSONArray evidence=new JSONArray(),derived=new JSONArray();
        try{
            snap.put("protocol",CONTEXT_MARKER);snap.put("request_id",requestId);snap.put("generated_at",System.currentTimeMillis());snap.put("cognitive_schema",CognitiveStore.schemaRevision(db));
            ArrayList<KnowledgeItem> xs=db.lexicalSearch("",MAX_EVIDENCE);int ec=0;
            for(KnowledgeItem k:xs){if(k==null)continue;JSONObject o=new JSONObject();o.put("evidence_id",k.id);o.put("type",n(k.type));o.put("source",n(k.source));o.put("title",clip(k.title,180));o.put("summary",clip(k.summary,650));o.put("extracted_text",clip(k.extractedText,850));o.put("raw_text",clip(k.rawText,700));o.put("status",n(k.status));o.put("created_at",k.createdAt);evidence.put(o);ec++;}
            Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,state,confidence,importance,updated_at FROM derived_items WHERE state IN ('open','pending') ORDER BY importance DESC,updated_at DESC LIMIT ?",new String[]{String.valueOf(MAX_DERIVED)});int sc=0;
            try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("derived_id",c.getLong(0));o.put("kind",n(c.getString(1)));o.put("title",clip(c.getString(2),180));o.put("body",clip(c.getString(3),700));o.put("state",n(c.getString(4)));o.put("confidence",c.getDouble(5));o.put("importance",c.getInt(6));o.put("updated_at",c.getLong(7));derived.put(o);sc++;}}finally{c.close();}
            snap.put("evidence",evidence);snap.put("current_cognitive_state",derived);
            String instructions=""+
                    "Act as Cortex Deep Review. Use ONLY the IDs and data in the JSON snapshot below.\n"+
                    "Your job is to reconcile the whole picture: identify what deserves attention, what is waiting, what is a decision, project/goal signals, and useful insights.\n"+
                    "Do not invent history, people, deadlines, evidence, resolution, or IDs. Do not mark anything resolved.\n"+
                    "Every priority item MUST cite at least one evidence_id present in the snapshot. If nothing is supported, return an empty priority_items array.\n"+
                    "Return exactly the marker CORTEX_REVIEW_V1 followed by one JSON object with this shape:\n"+
                    "{\"request_id\":\"<same request_id>\",\"answer\":\"short overall review\",\"priority_items\":[{\"kind\":\"ACTION|WAITING|DECISION|PROJECT_CANDIDATE|GOAL_SIGNAL|IDEA|OPPORTUNITY|INSIGHT|HYPOTHESIS|REVIEW\",\"title\":\"...\",\"body\":\"...\",\"importance\":0,\"confidence\":0.0,\"reason\":\"...\",\"evidence_ids\":[1]}],\"suggested_actions\":[{\"title\":\"...\",\"why\":\"...\",\"evidence_ids\":[1]}]}\n"+
                    "Importance is 0-100. Confidence is 0-1. Suggested actions are proposals only, never completed facts.\n\n"+
                    CONTEXT_MARKER+"\n"+snap.toString();
            if(instructions.length()>MAX_PROMPT_CHARS)instructions=instructions.substring(0,MAX_PROMPT_CHARS)+"\n[SNAPSHOT TRUNCATED SAFELY]";
            return new PromptPack(requestId,instructions,ec,sc);
        }catch(Throwable e){throw new IllegalStateException("Could not build Deep Review context",e);}
    }

    public static Review parse(VaultDb db,String input,String expectedRequestId){
        String raw=n(input).trim();if(raw.isEmpty())throw new IllegalArgumentException("Paste the ChatGPT review first");int mark=raw.indexOf(RESPONSE_MARKER);if(mark<0)throw new IllegalArgumentException("CORTEX_REVIEW_V1 marker missing");
        String tail=raw.substring(mark+RESPONSE_MARKER.length());int a=tail.indexOf('{'),b=tail.lastIndexOf('}');if(a<0||b<a)throw new IllegalArgumentException("Structured JSON response missing");
        try{
            JSONObject o=new JSONObject(tail.substring(a,b+1));String request=n(o.optString("request_id",""));if(request.isEmpty())throw new IllegalArgumentException("request_id missing");if(!empty(expectedRequestId)&&!expectedRequestId.equals(request))throw new IllegalArgumentException("This response belongs to a different Deep Review request");
            String answer=clip(o.optString("answer",""),5000);JSONArray arr=o.optJSONArray("priority_items");ArrayList<Item> items=new ArrayList<>();if(arr!=null){if(arr.length()>80)throw new IllegalArgumentException("Too many priority items");for(int i=0;i<arr.length();i++){JSONObject x=arr.optJSONObject(i);if(x==null)throw new IllegalArgumentException("priority_items must contain objects");String kind=n(x.optString("kind","")).toUpperCase(Locale.US);if(!ALLOWED_KINDS.contains(kind))throw new IllegalArgumentException("Unsupported kind: "+kind);String title=clip(x.optString("title",""),220).trim();if(title.isEmpty())throw new IllegalArgumentException("Priority title missing");String body=clip(x.optString("body",""),1600),reason=clip(x.optString("reason",""),900);int importance=Math.max(0,Math.min(100,x.optInt("importance",50)));double confidence=x.optDouble("confidence",.5);if(Double.isNaN(confidence)||confidence<0||confidence>1)throw new IllegalArgumentException("confidence must be 0..1");long[] ids=ids(db,x.optJSONArray("evidence_ids"),true);items.add(new Item(kind,title,body,reason,importance,confidence,ids));}}
            ArrayList<Action> actions=new ArrayList<>();JSONArray aa=o.optJSONArray("suggested_actions");if(aa!=null){if(aa.length()>40)throw new IllegalArgumentException("Too many suggested actions");for(int i=0;i<aa.length();i++){JSONObject x=aa.optJSONObject(i);if(x==null)continue;String title=clip(x.optString("title",""),240).trim();if(title.isEmpty())continue;actions.add(new Action(title,clip(x.optString("why",""),900),ids(db,x.optJSONArray("evidence_ids"),true)));}}
            return new Review(request,answer,raw,new ArrayList<>(items),actions);
        }catch(IllegalArgumentException e){throw e;}catch(Throwable e){throw new IllegalArgumentException("Invalid CORTEX_REVIEW_V1 JSON",e);}
    }

    public static ApplyResult apply(VaultDb db,Review review){
        if(db==null||review==null)throw new IllegalArgumentException("Review unavailable");CognitiveStore.ensure(db);long now=System.currentTimeMillis();String title="Deep Review · "+new java.text.SimpleDateFormat("dd MMM HH:mm",Locale.US).format(new Date(now));
        JSONObject meta=new JSONObject();try{meta.put("protocol",RESPONSE_MARKER);meta.put("request_id",review.requestId);meta.put("provider","ChatGPT");meta.put("user_approved",true);}catch(Throwable ignored){}
        long reviewId=db.insert("DEEP_REVIEW","chatgpt_deep_review",title,review.raw,"Cortex Review","deep-review,chatgpt,structured","",Fingerprint.text("deep-review|"+review.requestId),meta.toString());if(reviewId<0)reviewId=-reviewId;
        if(reviewId>0){ContentValues v=new ContentValues();v.put("status","analyzed");v.put("summary",clip(review.answer,1800));v.put("updated_at",now);db.getWritableDatabase().update("knowledge_items",v,"id=?",new String[]{String.valueOf(reviewId)});}
        int created=0;for(Item x:review.items){try{JSONArray idsJson=new JSONArray();ArrayList<Long> sorted=new ArrayList<>();for(long id:x.evidenceIds){idsJson.put(id);sorted.add(id);}Collections.sort(sorted);JSONObject m=new JSONObject();m.put("origin","chatgpt_deep_review");m.put("request_id",review.requestId);m.put("review_evidence_id",reviewId);m.put("evidence_ids",idsJson);m.put("reason",x.reason);String fp=Fingerprint.text("deep-review-v1|"+x.kind+"|"+LocalSemanticEmbedder.norm(x.title+" "+x.body)+"|"+sorted.toString());long id=CognitiveStore.addDerived(db,x.kind,x.title,x.body,"open",x.confidence,x.importance,fp,m.toString());if(id<=0)continue;for(long evidenceId:x.evidenceIds)CognitiveStore.link(db,"derived",id,"memory",evidenceId,"deep_review_grounding",x.confidence,"{\"request_id\":\""+escape(review.requestId)+"\"}");if(reviewId>0)CognitiveStore.link(db,"memory",reviewId,"derived",id,"deep_review_created",1.0,"{\"request_id\":\""+escape(review.requestId)+"\"}");created++;}catch(Throwable ignored){}}
        return new ApplyResult(reviewId,created);
    }

    private static long[] ids(VaultDb db,JSONArray a,boolean required){if(a==null||a.length()==0){if(required)throw new IllegalArgumentException("Every item must cite evidence_ids");return new long[0];}if(a.length()>12)throw new IllegalArgumentException("Too many evidence IDs on one item");long[] out=new long[a.length()];HashSet<Long> seen=new HashSet<>();for(int i=0;i<a.length();i++){long id=a.optLong(i,0);if(id<=0||!seen.add(id)||db.getById(id)==null)throw new IllegalArgumentException("Unknown evidence_id: "+id);out[i]=id;}return out;}
    private static String clip(String s,int max){String x=n(s).replaceAll("\\s+"," ").trim();return x.length()>max?x.substring(0,max)+"…":x;}
    private static String escape(String s){return n(s).replace("\\","\\\\").replace("\"","\\\"");}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String n(String s){return s==null?"":s;}
}
