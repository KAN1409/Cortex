package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * Strict ChatGPT -> Cortex organization boundary.
 *
 * ChatGPT may decide how Cortex data should be organized, but it can return only the
 * small, explicit operation set below. Every operation is grounded in existing evidence IDs,
 * validated locally, previewed to the user, and applied only after explicit selection.
 */
public final class CortexOrganizerContractV1 {
    public static final String CONTEXT_MARKER="CORTEX_ORGANIZER_CONTEXT_V1";
    public static final String RESPONSE_MARKER="CORTEX_ORGANIZER_RESPONSE_V1";
    private static final int MAX_EVIDENCE=140,MAX_EXISTING=100,MAX_PROMPT_CHARS=280_000,MAX_OPS=60;
    private static final Set<String> OPS=new HashSet<>(Arrays.asList(
            "TAG_EVIDENCE","LINK_EVIDENCE","CREATE_FOLLOW_UP","CREATE_PROJECT_CANDIDATE"));
    private static final Set<String> RELATIONS=new HashSet<>(Arrays.asList(
            "RELATED","SAME_TOPIC","SAME_PERSON","SAME_PROJECT"));

    private CortexOrganizerContractV1(){}

    public static final class PromptPack {
        public final String requestId,text; public final int evidenceCount,existingCount;
        PromptPack(String id,String t,int e,int x){requestId=id;text=t;evidenceCount=e;existingCount=x;}
    }

    public static final class Operation {
        public final String op,title,body,reason,relation; public final long[] evidenceIds; public final String[] tags; public final double confidence;
        Operation(String op,String title,String body,String reason,String relation,long[] ids,String[] tags,double confidence){
            this.op=op;this.title=title;this.body=body;this.reason=reason;this.relation=relation;this.evidenceIds=ids;this.tags=tags;this.confidence=confidence;
        }
        public String displayTitle(){
            if("TAG_EVIDENCE".equals(op))return "Add tags";
            if("LINK_EVIDENCE".equals(op))return "Link related evidence";
            if("CREATE_FOLLOW_UP".equals(op))return empty(title)?"Create follow-up":title;
            if("CREATE_PROJECT_CANDIDATE".equals(op))return empty(title)?"Create project candidate":title;
            return op;
        }
    }

    public static final class Plan {
        public final String requestId,summary,raw; public final ArrayList<Operation> operations;
        Plan(String id,String summary,String raw,ArrayList<Operation> ops){requestId=id;this.summary=summary;this.raw=raw;operations=ops;}
    }

    public static final class ApplyResult {
        public final long auditEvidenceId; public final int applied;
        ApplyResult(long id,int count){auditEvidenceId=id;applied=count;}
    }

    public static PromptPack build(VaultDb db){
        if(db==null)throw new IllegalArgumentException("Database unavailable");
        CognitiveStore.ensure(db);
        String requestId="organize_"+System.currentTimeMillis();
        JSONObject snap=new JSONObject();JSONArray evidence=new JSONArray(),existing=new JSONArray();int ec=0,xc=0;
        try{
            snap.put("protocol",CONTEXT_MARKER);
            snap.put("request_id",requestId);
            snap.put("generated_at",System.currentTimeMillis());
            snap.put("cognitive_schema",CognitiveStore.schemaRevision(db));

            ArrayList<KnowledgeItem> items=db.lexicalSearch("",MAX_EVIDENCE);
            for(KnowledgeItem k:items){
                if(k==null)continue;
                JSONObject o=new JSONObject();
                o.put("evidence_id",k.id);o.put("type",n(k.type));o.put("source",n(k.source));o.put("title",clip(k.title,180));
                o.put("summary",clip(k.summary,700));o.put("extracted_text",clip(k.extractedText,900));o.put("raw_text",clip(k.rawText,750));
                o.put("category",n(k.category));o.put("tags",n(k.tags));o.put("status",n(k.status));o.put("created_at",k.createdAt);
                evidence.put(o);ec++;
            }

            Cursor c=db.getReadableDatabase().rawQuery(
                    "SELECT id,kind,title,body,state,updated_at FROM derived_items WHERE state IN ('open','pending') AND kind IN ('ACTION','WAITING','PROJECT_CANDIDATE') ORDER BY updated_at DESC LIMIT ?",
                    new String[]{String.valueOf(MAX_EXISTING)});
            try{while(c.moveToNext()){
                JSONObject o=new JSONObject();o.put("id",c.getLong(0));o.put("kind",n(c.getString(1)));o.put("title",clip(c.getString(2),180));
                o.put("body",clip(c.getString(3),650));o.put("state",n(c.getString(4)));o.put("updated_at",c.getLong(5));existing.put(o);xc++;
            }}finally{c.close();}
            snap.put("evidence",evidence);snap.put("existing_organization",existing);

            String instructions=
                    "You are the organization brain for the Cortex Android app.\n"+
                    "This is NOT a chat answer and NOT a brainstorming task. Return only explicit app-organization operations.\n"+
                    "Use ONLY the IDs and facts present in the JSON snapshot. Do not invent people, projects, deadlines, facts, priorities or history.\n"+
                    "Do not give advice. Do not return creative ideas, lenses, debates, analysis branches, rankings or recommendations.\n"+
                    "Your job is only to make existing Cortex data easier to retrieve and act on.\n"+
                    "Allowed operations only:\n"+
                    "1) TAG_EVIDENCE: add a small set of useful retrieval tags to one evidence item.\n"+
                    "2) LINK_EVIDENCE: link exactly two evidence items using RELATED, SAME_TOPIC, SAME_PERSON or SAME_PROJECT.\n"+
                    "3) CREATE_FOLLOW_UP: create a concrete follow-up already supported by the evidence.\n"+
                    "4) CREATE_PROJECT_CANDIDATE: create a possible project grouping already clearly supported by the evidence.\n"+
                    "Every operation MUST cite existing evidence_ids. Never request deletion or destructive changes. Avoid duplicates already present in existing_organization.\n"+
                    "Return exactly the marker CORTEX_ORGANIZER_RESPONSE_V1 followed by one JSON object:\n"+
                    "{\"request_id\":\"<same request_id>\",\"summary\":\"short description of organization work only\",\"operations\":["+
                    "{\"op\":\"TAG_EVIDENCE\",\"evidence_ids\":[1],\"tags\":[\"tag one\",\"tag two\"],\"reason\":\"...\",\"confidence\":0.0},"+
                    "{\"op\":\"LINK_EVIDENCE\",\"evidence_ids\":[1,2],\"relation\":\"SAME_TOPIC\",\"reason\":\"...\",\"confidence\":0.0},"+
                    "{\"op\":\"CREATE_FOLLOW_UP\",\"title\":\"...\",\"body\":\"...\",\"evidence_ids\":[1],\"reason\":\"...\",\"confidence\":0.0},"+
                    "{\"op\":\"CREATE_PROJECT_CANDIDATE\",\"title\":\"...\",\"body\":\"...\",\"evidence_ids\":[1],\"reason\":\"...\",\"confidence\":0.0}]}\n"+
                    "If no organization change is clearly supported, return an empty operations array. Confidence is 0..1.\n\n"+
                    CONTEXT_MARKER+"\n"+snap.toString();
            if(instructions.length()>MAX_PROMPT_CHARS)instructions=instructions.substring(0,MAX_PROMPT_CHARS)+"\n[SNAPSHOT TRUNCATED SAFELY]";
            return new PromptPack(requestId,instructions,ec,xc);
        }catch(Throwable e){throw new IllegalStateException("Could not build Cortex organizer context",e);}
    }

    public static Plan parse(VaultDb db,String input,String expectedRequestId){
        String raw=n(input).trim();if(raw.isEmpty())throw new IllegalArgumentException("Paste the ChatGPT organizer response first");
        int marker=raw.indexOf(RESPONSE_MARKER);if(marker<0)throw new IllegalArgumentException("CORTEX_ORGANIZER_RESPONSE_V1 marker missing");
        String tail=raw.substring(marker+RESPONSE_MARKER.length());int a=tail.indexOf('{'),b=tail.lastIndexOf('}');
        if(a<0||b<a)throw new IllegalArgumentException("Structured organizer JSON missing");
        try{
            JSONObject root=new JSONObject(tail.substring(a,b+1));String requestId=n(root.optString("request_id",""));
            if(requestId.isEmpty())throw new IllegalArgumentException("request_id missing");
            if(!empty(expectedRequestId)&&!expectedRequestId.equals(requestId))throw new IllegalArgumentException("This response belongs to a different Cortex organizer request");
            String summary=clip(root.optString("summary",""),1800);
            JSONArray arr=root.optJSONArray("operations");ArrayList<Operation> ops=new ArrayList<>();
            if(arr!=null){
                if(arr.length()>MAX_OPS)throw new IllegalArgumentException("Too many organization operations");
                for(int i=0;i<arr.length();i++){
                    JSONObject x=arr.optJSONObject(i);if(x==null)throw new IllegalArgumentException("operations must contain objects");
                    String op=n(x.optString("op","")).toUpperCase(Locale.US);if(!OPS.contains(op))throw new IllegalArgumentException("Unsupported organization operation: "+op);
                    long[] ids=ids(db,x.optJSONArray("evidence_ids"));String title=clip(x.optString("title",""),220).trim();String body=clip(x.optString("body",""),1300);
                    String reason=clip(x.optString("reason",""),700);double confidence=x.optDouble("confidence",.5);
                    if(Double.isNaN(confidence)||confidence<0||confidence>1)throw new IllegalArgumentException("confidence must be 0..1");
                    String relation="";String[] tags=new String[0];
                    if("TAG_EVIDENCE".equals(op)){
                        if(ids.length!=1)throw new IllegalArgumentException("TAG_EVIDENCE requires exactly one evidence_id");
                        tags=tags(x.optJSONArray("tags"));if(tags.length==0)throw new IllegalArgumentException("TAG_EVIDENCE requires tags");
                    }else if("LINK_EVIDENCE".equals(op)){
                        if(ids.length!=2)throw new IllegalArgumentException("LINK_EVIDENCE requires exactly two evidence_ids");
                        relation=n(x.optString("relation","")).toUpperCase(Locale.US);if(!RELATIONS.contains(relation))throw new IllegalArgumentException("Unsupported relation: "+relation);
                    }else{
                        if(ids.length==0)throw new IllegalArgumentException(op+" requires evidence_ids");
                        if(title.isEmpty())throw new IllegalArgumentException(op+" requires a title");
                    }
                    ops.add(new Operation(op,title,body,reason,relation,ids,tags,confidence));
                }
            }
            return new Plan(requestId,summary,raw,ops);
        }catch(IllegalArgumentException e){throw e;}catch(Throwable e){throw new IllegalArgumentException("Invalid CORTEX_ORGANIZER_RESPONSE_V1 JSON",e);}
    }

    public static ApplyResult applySelected(VaultDb db,Plan plan,boolean[] selected){
        if(db==null||plan==null)throw new IllegalArgumentException("Organizer plan unavailable");
        if(selected==null||selected.length!=plan.operations.size())throw new IllegalArgumentException("Selection does not match organizer plan");
        CognitiveStore.ensure(db);SQLiteDatabase sql=db.getWritableDatabase();int applied=0;long auditId=0;ArrayList<Long> touched=new ArrayList<>();
        sql.beginTransaction();
        try{
            for(int i=0;i<plan.operations.size();i++){
                if(!selected[i])continue;Operation op=plan.operations.get(i);boolean ok=false;
                if("TAG_EVIDENCE".equals(op.op))ok=applyTags(db,op);
                else if("LINK_EVIDENCE".equals(op.op))ok=applyLink(db,op);
                else if("CREATE_FOLLOW_UP".equals(op.op))ok=createDerived(db,plan.requestId,op,"ACTION")>0;
                else if("CREATE_PROJECT_CANDIDATE".equals(op.op))ok=createDerived(db,plan.requestId,op,"PROJECT_CANDIDATE")>0;
                if(ok){applied++;for(long id:op.evidenceIds)if(!touched.contains(id))touched.add(id);}
            }
            if(applied>0){
                JSONObject meta=new JSONObject();meta.put("protocol",RESPONSE_MARKER);meta.put("request_id",plan.requestId);meta.put("provider","ChatGPT");meta.put("user_approved",true);meta.put("applied_operations",applied);
                auditId=db.insert("ORGANIZER_PLAN","chatgpt_organizer","Cortex organization plan",plan.raw,"Cortex Organizer","organizer,chatgpt,structured","",Fingerprint.text("organizer|"+plan.requestId),meta.toString());
                if(auditId<0)auditId=-auditId;
                if(auditId>0){ContentValues v=new ContentValues();v.put("status","analyzed");v.put("summary",clip(plan.summary,1800));v.put("updated_at",System.currentTimeMillis());sql.update("knowledge_items",v,"id=?",new String[]{String.valueOf(auditId)});for(long id:touched)CognitiveStore.linkChecked(db,"memory",auditId,"memory",id,"organizer_touched",1.0,"{\"request_id\":\""+escape(plan.requestId)+"\"}");}
            }
            sql.setTransactionSuccessful();
        }catch(Throwable e){throw new IllegalStateException("Could not apply selected organizer operations",e);}finally{sql.endTransaction();}
        return new ApplyResult(auditId,applied);
    }

    private static boolean applyTags(VaultDb db,Operation op){
        long id=op.evidenceIds[0];Cursor c=db.getReadableDatabase().query("knowledge_items",new String[]{"tags"},"id=?",new String[]{String.valueOf(id)},null,null,null,"1");
        if(!c.moveToFirst()){c.close();return false;}String current=n(c.getString(0));c.close();LinkedHashSet<String> all=new LinkedHashSet<>();
        for(String x:current.split(",")){String t=cleanTag(x);if(!t.isEmpty())all.add(t);}for(String x:op.tags){String t=cleanTag(x);if(!t.isEmpty())all.add(t);}StringBuilder out=new StringBuilder();for(String t:all){if(out.length()>0)out.append(',');out.append(t);}
        ContentValues v=new ContentValues();v.put("tags",out.toString());v.put("updated_at",System.currentTimeMillis());return db.getWritableDatabase().update("knowledge_items",v,"id=?",new String[]{String.valueOf(id)})>0;
    }

    private static boolean applyLink(VaultDb db,Operation op){
        long a=op.evidenceIds[0],b=op.evidenceIds[1];String relation="organizer_"+op.relation.toLowerCase(Locale.US);
        boolean one=CognitiveStore.linkChecked(db,"memory",a,"memory",b,relation,op.confidence,"{\"origin\":\"chatgpt_organizer\"}");
        boolean two=CognitiveStore.linkChecked(db,"memory",b,"memory",a,relation,op.confidence,"{\"origin\":\"chatgpt_organizer\"}");return one&&two;
    }

    private static long createDerived(VaultDb db,String requestId,Operation op,String kind){
        ArrayList<Long> sorted=new ArrayList<>();JSONArray refs=new JSONArray();for(long id:op.evidenceIds){sorted.add(id);refs.put(id);}Collections.sort(sorted);
        try{
            JSONObject meta=new JSONObject();meta.put("origin","chatgpt_organizer");meta.put("request_id",requestId);meta.put("evidence_ids",refs);meta.put("reason",op.reason);
            String fp=Fingerprint.text("organizer-v1|"+kind+"|"+LocalSemanticEmbedder.norm(op.title+" "+op.body)+"|"+sorted.toString());
            long id=CognitiveStore.addDerived(db,kind,op.title,op.body,"open",op.confidence,50,fp,meta.toString());
            if(id>0)for(long evidenceId:op.evidenceIds)CognitiveStore.linkChecked(db,"derived",id,"memory",evidenceId,"organizer_grounding",op.confidence,"{\"request_id\":\""+escape(requestId)+"\"}");
            return id;
        }catch(Throwable e){return 0;}
    }

    private static long[] ids(VaultDb db,JSONArray arr){
        if(arr==null||arr.length()==0)return new long[0];if(arr.length()>12)throw new IllegalArgumentException("Too many evidence IDs on one operation");
        long[] out=new long[arr.length()];HashSet<Long> seen=new HashSet<>();for(int i=0;i<arr.length();i++){long id=arr.optLong(i,0);if(id<=0||!seen.add(id)||db.getById(id)==null)throw new IllegalArgumentException("Unknown evidence_id: "+id);out[i]=id;}return out;
    }

    private static String[] tags(JSONArray arr){
        if(arr==null)return new String[0];if(arr.length()>8)throw new IllegalArgumentException("Too many tags on one operation");ArrayList<String> out=new ArrayList<>();
        for(int i=0;i<arr.length();i++){String t=cleanTag(arr.optString(i,""));if(!t.isEmpty()&&!out.contains(t))out.add(t);}return out.toArray(new String[0]);
    }

    private static String cleanTag(String s){String x=n(s).replace(',', ' ').replaceAll("\\s+"," ").trim();if(x.length()>40)x=x.substring(0,40);return x;}
    private static String clip(String s,int max){String x=n(s).replaceAll("\\s+"," ").trim();return x.length()>max?x.substring(0,max)+"…":x;}
    private static String escape(String s){return n(s).replace("\\","\\\\").replace("\"","\\\"");}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String n(String s){return s==null?"":s;}
}
