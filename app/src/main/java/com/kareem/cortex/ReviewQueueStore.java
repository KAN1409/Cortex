package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Persistent uncertainty queue. REVIEW items are derived candidates, never memories by default. */
public final class ReviewQueueStore {
    public static final String POLICY="review_queue_004";
    private static final long REVIEW_TTL_MS=14L*24L*60L*60L*1000L;
    private static final long HIGH_IMPORTANCE_TTL_MS=30L*24L*60L*60L*1000L;
    private ReviewQueueStore(){}

    public static final class Item {
        public final long id,threadId,signalId,createdAt;public final String candidateKind,title,body,reason,state,sourceKey;public final double confidence;public final int importance;
        Item(long id,String candidateKind,String title,String body,String reason,String state,String sourceKey,double confidence,int importance,long threadId,long signalId,long createdAt){this.id=id;this.candidateKind=n(candidateKind);this.title=n(title);this.body=n(body);this.reason=n(reason);this.state=n(state);this.sourceKey=n(sourceKey);this.confidence=confidence;this.importance=importance;this.threadId=threadId;this.signalId=signalId;this.createdAt=createdAt;}
    }

    public static long enqueue(VaultDb db,String candidateKind,String title,String body,double confidence,int importance,long threadId,long signalId,String reason,String source){
        String candidate=validCandidate(candidateKind);if(candidate.isEmpty())return 0;CognitiveStore.ensure(db);expireStale(db);String sourceKey=n(source);
        Item existing=pendingForThreadCandidate(db,threadId,candidate,sourceKey);if(existing!=null){refreshExisting(db,existing,body,confidence,importance,signalId,reason,sourceKey);return existing.id;}
        try{
            JSONObject meta=new JSONObject();meta.put("candidate_kind",candidate);meta.put("reason",n(reason));meta.put("policy_version",POLICY);meta.put("thread_id",threadId);meta.put("raw_signal_id",signalId);meta.put("source",sourceKey);meta.put("original_confidence",confidence);
            String fp=Fingerprint.text("review|"+candidate+"|"+threadId+"|"+signalId+"|"+Fingerprint.text(body));long reviewId=CognitiveStore.addDerived(db,CognitiveTypes.DerivedKind.REVIEW,reviewTitle(title,candidate),body,"pending",confidence,importance,fp,meta.toString());
            if(reviewId>0){CognitiveStore.setDerivedRouting(db,reviewId,sourceKey,threadId,signalId,candidate);if(threadId>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.THREAD,threadId,CognitiveTypes.ObjectType.DERIVED,reviewId,"needs_review",confidence,meta.toString());if(signalId>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.RAW_SIGNAL,signalId,CognitiveTypes.ObjectType.DERIVED,reviewId,CognitiveTypes.Relation.SUPPORTS,1.0,"");}
            return reviewId;
        }catch(Exception e){DiagnosticsLog.error(db,"ReviewQueueStore","enqueue",e,"REVIEW_ENQUEUE",0,threadId,signalId,0,0,null);return 0;}
    }

    public static int pendingCount(VaultDb db){CognitiveStore.ensure(db);expireStale(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM derived_items WHERE kind='REVIEW' AND state='pending'",null);int x=c.moveToFirst()?c.getInt(0):0;c.close();return x;}
    public static ArrayList<Item> pending(VaultDb db,int limit){CognitiveStore.ensure(db);expireStale(db);return pendingRaw(db,limit,"1=1",null);}

    public static Item pendingForSignal(VaultDb db,long signalId){if(signalId<=0)return null;CognitiveStore.ensure(db);expireStale(db);ArrayList<Item> xs=pendingRaw(db,1,"anchor_signal_id=?",new String[]{String.valueOf(signalId)});return xs.isEmpty()?null:xs.get(0);}
    public static Item pendingForThreadCandidate(VaultDb db,long threadId,String candidateKind){return pendingForThreadCandidate(db,threadId,candidateKind,"");}
    public static Item pendingForThreadCandidate(VaultDb db,long threadId,String candidateKind,String sourceKey){
        if(threadId<=0)return null;String kind=validCandidate(candidateKind);if(kind.isEmpty())return null;CognitiveStore.ensure(db);expireStale(db);String where="thread_id=? AND candidate_kind=?";ArrayList<String> args=new ArrayList<>();args.add(String.valueOf(threadId));args.add(kind);if(!n(sourceKey).isEmpty()){where+=" AND source_key=?";args.add(n(sourceKey));}ArrayList<Item> xs=pendingRaw(db,1,where,args.toArray(new String[0]));return xs.isEmpty()?null:xs.get(0);
    }

    /** Silence is expiration, not negative feedback. */
    public static int expireStale(VaultDb db){CognitiveStore.ensure(db);long now=System.currentTimeMillis(),normalCutoff=now-REVIEW_TTL_MS,highCutoff=now-HIGH_IMPORTANCE_TTL_MS;ContentValues v=new ContentValues();v.put("state","expired");v.put("resolved_at",now);v.put("updated_at",now);return db.getWritableDatabase().update("derived_items",v,"kind='REVIEW' AND state='pending' AND ((importance<70 AND created_at<?) OR (importance>=70 AND created_at<?))",new String[]{String.valueOf(normalCutoff),String.valueOf(highCutoff)});}

    public static long confirm(VaultDb db,long reviewId){
        Item item=get(db,reviewId);if(item==null||!"pending".equals(item.state))return 0;String candidate=validCandidate(item.candidateKind);if(candidate.isEmpty())return 0;
        try{JSONObject meta=new JSONObject();meta.put("confirmed_from_review",reviewId);meta.put("review_policy",POLICY);meta.put("thread_id",item.threadId);meta.put("raw_signal_id",item.signalId);meta.put("source",item.sourceKey);meta.put("user_confirmed",true);String fp=Fingerprint.text("review-confirmed|"+reviewId+"|"+candidate);long derived=CognitiveStore.addDerived(db,candidate,confirmedTitle(item.title,candidate),item.body,"open",Math.max(0.90,item.confidence),Math.max(55,item.importance),fp,meta.toString());if(derived<=0)return 0;CognitiveStore.setDerivedRouting(db,derived,item.sourceKey,item.threadId,item.signalId,candidate);linkConfirmed(db,item,reviewId,derived,meta.toString());resolve(db,reviewId,"confirmed");CognitiveStore.feedback(db,CognitiveTypes.ObjectType.DERIVED,reviewId,"confirm","{\"candidate_kind\":\""+candidate+"\"}",POLICY);RelevanceEvaluationStore.userVerdict(db,item.signalId,"confirm",candidate,reviewId);return derived;}catch(Exception e){DiagnosticsLog.error(db,"ReviewQueueStore","confirm",e,"REVIEW_CONFIRM",0,item.threadId,item.signalId,0,0,null);return 0;}
    }

    public static long promoteByModel(VaultDb db,long reviewId,MasterRelevanceFilter.Decision d,long modelRunId){
        Item item=get(db,reviewId);if(item==null||!"pending".equals(item.state)||d==null||!d.durable())return 0;String candidate=validCandidate(d.disposition.name());if(candidate.isEmpty())return 0;
        try{JSONObject meta=new JSONObject();meta.put("adjudicated_from_review",reviewId);meta.put("review_policy",POLICY);meta.put("thread_id",item.threadId);meta.put("raw_signal_id",item.signalId);meta.put("source",item.sourceKey);meta.put("model_run_id",modelRunId);meta.put("model_confidence",d.confidence);meta.put("model_reason",d.reason);meta.put("user_confirmed",false);String fp=Fingerprint.text("model-adjudicated|"+reviewId+"|"+candidate);long derived=CognitiveStore.addDerived(db,candidate,confirmedTitle(item.title,candidate),item.body,"open",d.confidence,Math.max(item.importance,d.importance),fp,meta.toString());if(derived<=0)return 0;CognitiveStore.setDerivedRouting(db,derived,item.sourceKey,item.threadId,item.signalId,candidate);linkConfirmed(db,item,reviewId,derived,meta.toString());resolve(db,reviewId,"adjudicated");return derived;}catch(Exception e){DiagnosticsLog.error(db,"ReviewQueueStore","model_promote",e,"REVIEW_MODEL_PROMOTE",0,item.threadId,item.signalId,0,modelRunId,null);return 0;}
    }

    public static boolean dismiss(VaultDb db,long reviewId){return resolveWithFeedback(db,reviewId,"dismissed","dismiss","{}");}
    public static boolean notAction(VaultDb db,long reviewId){return resolveWithFeedback(db,reviewId,"dismissed","not_action","{}");}
    public static boolean notImportant(VaultDb db,long reviewId){return resolveWithFeedback(db,reviewId,"dismissed","not_important","{}");}
    public static boolean ignoreSimilar(VaultDb db,long reviewId){return resolveWithFeedback(db,reviewId,"dismissed","ignore_similar","{\"scope\":\"future_similar\"}");}

    private static void refreshExisting(VaultDb db,Item existing,String body,double confidence,int importance,long signalId,String reason,String source){
        try{JSONObject meta=new JSONObject();meta.put("candidate_kind",existing.candidateKind);meta.put("reason",n(reason));meta.put("policy_version",POLICY);meta.put("thread_id",existing.threadId);meta.put("raw_signal_id",signalId);meta.put("source",n(source));meta.put("original_confidence",Math.max(existing.confidence,confidence));ContentValues v=new ContentValues();v.put("body",n(body));v.put("confidence",Math.max(existing.confidence,confidence));v.put("importance",Math.max(existing.importance,importance));v.put("metadata_json",meta.toString());v.put("source_key",n(source));v.put("thread_id",existing.threadId);v.put("anchor_signal_id",signalId);v.put("candidate_kind",existing.candidateKind);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("derived_items",v,"id=? AND kind='REVIEW' AND state='pending'",new String[]{String.valueOf(existing.id)});if(signalId>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.RAW_SIGNAL,signalId,CognitiveTypes.ObjectType.DERIVED,existing.id,CognitiveTypes.Relation.SUPPORTS,1.0,"");}catch(Exception e){DiagnosticsLog.error(db,"ReviewQueueStore","refresh_existing",e,"REVIEW_REFRESH",0,existing.threadId,signalId,0,0,null);}
    }

    private static ArrayList<Item> pendingRaw(VaultDb db,int limit,String extraWhere,String[] extraArgs){ArrayList<Item> out=new ArrayList<>();String where="kind='REVIEW' AND state='pending'"+(extraWhere==null||"1=1".equals(extraWhere)?"":" AND ("+extraWhere+")");Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"id","title","body","state","confidence","importance","metadata_json","created_at","source_key","thread_id","anchor_signal_id","candidate_kind"},where,extraArgs,null,null,"importance DESC, updated_at DESC",String.valueOf(Math.max(1,Math.min(100,limit))));while(c.moveToNext())out.add(from(c));c.close();return out;}
    private static void linkConfirmed(VaultDb db,Item item,long reviewId,long derived,String meta){CognitiveStore.link(db,CognitiveTypes.ObjectType.DERIVED,reviewId,CognitiveTypes.ObjectType.DERIVED,derived,"confirmed_as",1.0,meta);if(item.threadId>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.THREAD,item.threadId,CognitiveTypes.ObjectType.DERIVED,derived,"produced",1.0,"");if(item.signalId>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.RAW_SIGNAL,item.signalId,CognitiveTypes.ObjectType.DERIVED,derived,CognitiveTypes.Relation.SUPPORTS,1.0,"");}
    private static boolean resolveWithFeedback(VaultDb db,long id,String state,String event,String value){Item x=get(db,id);if(x==null||!"pending".equals(x.state))return false;resolve(db,id,state);CognitiveStore.feedback(db,CognitiveTypes.ObjectType.DERIVED,id,event,value,POLICY);RelevanceEvaluationStore.userVerdict(db,x.signalId,event,x.candidateKind,id);return true;}
    private static void resolve(VaultDb db,long id,String state){long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("state",state);v.put("resolved_at",now);v.put("updated_at",now);db.getWritableDatabase().update("derived_items",v,"id=? AND kind='REVIEW'",new String[]{String.valueOf(id)});}

    private static Item get(VaultDb db,long id){CognitiveStore.ensure(db);expireStale(db);Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"id","title","body","state","confidence","importance","metadata_json","created_at","source_key","thread_id","anchor_signal_id","candidate_kind"},"id=? AND kind='REVIEW'",new String[]{String.valueOf(id)},null,null,null,"1");Item x=c.moveToFirst()?from(c):null;c.close();return x;}
    private static Item from(Cursor c){long id=c.getLong(0),created=c.getLong(7),thread=c.getLong(9),signal=c.getLong(10);String title=n(c.getString(1)),body=n(c.getString(2)),state=n(c.getString(3)),meta=n(c.getString(6)),source=n(c.getString(8)),candidate=n(c.getString(11)),reason="";double confidence=c.getDouble(4);int importance=c.getInt(5);try{JSONObject o=new JSONObject(meta);reason=o.optString("reason","");if(candidate.isEmpty())candidate=o.optString("candidate_kind","");if(source.isEmpty())source=o.optString("source","");if(thread<=0)thread=o.optLong("thread_id",0);if(signal<=0)signal=o.optLong("raw_signal_id",0);}catch(Exception ignored){}return new Item(id,candidate,title,body,reason,state,source,confidence,importance,thread,signal,created);}
    private static String validCandidate(String x){String k=n(x).toUpperCase(Locale.ROOT);if(CognitiveTypes.DerivedKind.ACTION.equals(k)||CognitiveTypes.DerivedKind.WAITING.equals(k)||CognitiveTypes.DerivedKind.DECISION.equals(k)||CognitiveTypes.DerivedKind.PROJECT_CANDIDATE.equals(k)||CognitiveTypes.DerivedKind.GOAL_SIGNAL.equals(k))return k;return"";}
    private static String reviewTitle(String title,String candidate){String base=n(title),suffix=friendly(candidate);return base.isEmpty()?"Review · "+suffix:base+" · Review "+suffix;}
    private static String confirmedTitle(String title,String candidate){String x=n(title).replace(" · Review "+friendly(candidate),"").trim();return x.isEmpty()?friendly(candidate):x;}
    private static String friendly(String kind){if("ACTION".equals(kind))return"action";if("WAITING".equals(kind))return"waiting";if("DECISION".equals(kind))return"decision";if("PROJECT_CANDIDATE".equals(kind))return"project";if("GOAL_SIGNAL".equals(kind))return"goal";return"item";}
    private static String n(String s){return s==null?"":s.trim();}
}
