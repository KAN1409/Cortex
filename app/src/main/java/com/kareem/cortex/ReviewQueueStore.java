package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * Persistent uncertainty queue. REVIEW items are derived intelligence candidates,
 * not memories. User decisions become feedback and may promote a confirmed derived item.
 */
public final class ReviewQueueStore {
    public static final String POLICY="review_queue_001";
    private ReviewQueueStore(){}

    public static final class Item {
        public final long id,threadId,signalId,createdAt;
        public final String candidateKind,title,body,reason,state;
        public final double confidence;
        public final int importance;
        Item(long id,String candidateKind,String title,String body,String reason,String state,double confidence,int importance,long threadId,long signalId,long createdAt){
            this.id=id;this.candidateKind=n(candidateKind);this.title=n(title);this.body=n(body);this.reason=n(reason);this.state=n(state);this.confidence=confidence;this.importance=importance;this.threadId=threadId;this.signalId=signalId;this.createdAt=createdAt;
        }
    }

    public static long enqueue(VaultDb db,String candidateKind,String title,String body,double confidence,int importance,long threadId,long signalId,String reason,String source){
        String candidate=validCandidate(candidateKind);if(candidate.isEmpty())return 0;CognitiveStore.ensure(db);
        try{
            JSONObject meta=new JSONObject();meta.put("candidate_kind",candidate);meta.put("reason",n(reason));meta.put("policy_version",POLICY);meta.put("thread_id",threadId);meta.put("raw_signal_id",signalId);meta.put("source",n(source));meta.put("original_confidence",confidence);
            String fp=Fingerprint.text("review|"+candidate+"|"+threadId+"|"+signalId+"|"+Fingerprint.text(body));
            long reviewId=CognitiveStore.addDerived(db,CognitiveTypes.DerivedKind.REVIEW,reviewTitle(title,candidate),body,"pending",confidence,importance,fp,meta.toString());
            if(reviewId>0){
                if(threadId>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.THREAD,threadId,CognitiveTypes.ObjectType.DERIVED,reviewId,"needs_review",confidence,meta.toString());
                if(signalId>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.RAW_SIGNAL,signalId,CognitiveTypes.ObjectType.DERIVED,reviewId,CognitiveTypes.Relation.SUPPORTS,1.0,"");
            }
            return reviewId;
        }catch(Exception ignored){return 0;}
    }

    public static int pendingCount(VaultDb db){CognitiveStore.ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM derived_items WHERE kind='REVIEW' AND state='pending'",null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}

    public static ArrayList<Item> pending(VaultDb db,int limit){
        CognitiveStore.ensure(db);ArrayList<Item> out=new ArrayList<>();int lim=Math.max(1,Math.min(100,limit));
        Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"id","title","body","state","confidence","importance","metadata_json","created_at"},"kind='REVIEW' AND state='pending'",null,null,null,"importance DESC, updated_at DESC",String.valueOf(lim));
        while(c.moveToNext())out.add(from(c));c.close();return out;
    }

    public static long confirm(VaultDb db,long reviewId){
        Item item=get(db,reviewId);if(item==null||!"pending".equals(item.state))return 0;String candidate=validCandidate(item.candidateKind);if(candidate.isEmpty())return 0;
        try{
            JSONObject meta=new JSONObject();meta.put("confirmed_from_review",reviewId);meta.put("review_policy",POLICY);meta.put("thread_id",item.threadId);meta.put("raw_signal_id",item.signalId);meta.put("user_confirmed",true);
            String fp=Fingerprint.text("review-confirmed|"+reviewId+"|"+candidate);
            long derived=CognitiveStore.addDerived(db,candidate,confirmedTitle(item.title,candidate),item.body,"open",Math.max(0.90,item.confidence),Math.max(55,item.importance),fp,meta.toString());
            if(derived<=0)return 0;
            CognitiveStore.link(db,CognitiveTypes.ObjectType.DERIVED,reviewId,CognitiveTypes.ObjectType.DERIVED,derived,"confirmed_as",1.0,meta.toString());
            if(item.threadId>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.THREAD,item.threadId,CognitiveTypes.ObjectType.DERIVED,derived,"produced",1.0,"");
            if(item.signalId>0)CognitiveStore.link(db,CognitiveTypes.ObjectType.RAW_SIGNAL,item.signalId,CognitiveTypes.ObjectType.DERIVED,derived,CognitiveTypes.Relation.SUPPORTS,1.0,"");
            resolve(db,reviewId,"confirmed");CognitiveStore.feedback(db,CognitiveTypes.ObjectType.DERIVED,reviewId,"confirm","{\"candidate_kind\":\""+candidate+"\"}",POLICY);return derived;
        }catch(Exception ignored){return 0;}
    }

    public static boolean dismiss(VaultDb db,long reviewId){return resolveWithFeedback(db,reviewId,"dismissed","dismiss","{}");}
    public static boolean notAction(VaultDb db,long reviewId){return resolveWithFeedback(db,reviewId,"dismissed","not_action","{}");}
    public static boolean notImportant(VaultDb db,long reviewId){return resolveWithFeedback(db,reviewId,"dismissed","not_important","{}");}
    public static boolean ignoreSimilar(VaultDb db,long reviewId){return resolveWithFeedback(db,reviewId,"dismissed","ignore_similar","{\"scope\":\"future_similar\"}");}

    private static boolean resolveWithFeedback(VaultDb db,long id,String state,String event,String value){Item x=get(db,id);if(x==null||!"pending".equals(x.state))return false;resolve(db,id,state);CognitiveStore.feedback(db,CognitiveTypes.ObjectType.DERIVED,id,event,value,POLICY);return true;}
    private static void resolve(VaultDb db,long id,String state){long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("state",state);v.put("resolved_at",now);v.put("updated_at",now);db.getWritableDatabase().update("derived_items",v,"id=? AND kind='REVIEW'",new String[]{String.valueOf(id)});}

    private static Item get(VaultDb db,long id){
        CognitiveStore.ensure(db);Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"id","title","body","state","confidence","importance","metadata_json","created_at"},"id=? AND kind='REVIEW'",new String[]{String.valueOf(id)},null,null,null,"1");Item x=c.moveToFirst()?from(c):null;c.close();return x;
    }
    private static Item from(Cursor c){
        long id=c.getLong(0),created=c.getLong(7);String title=n(c.getString(1)),body=n(c.getString(2)),state=n(c.getString(3)),meta=n(c.getString(6)),candidate="",reason="";double confidence=c.getDouble(4);int importance=c.getInt(5);long thread=0,signal=0;
        try{JSONObject o=new JSONObject(meta);candidate=o.optString("candidate_kind","");reason=o.optString("reason","");thread=o.optLong("thread_id",0);signal=o.optLong("raw_signal_id",0);}catch(Exception ignored){}
        return new Item(id,candidate,title,body,reason,state,confidence,importance,thread,signal,created);
    }
    private static String validCandidate(String x){String k=n(x).toUpperCase();if(CognitiveTypes.DerivedKind.ACTION.equals(k)||CognitiveTypes.DerivedKind.WAITING.equals(k)||CognitiveTypes.DerivedKind.DECISION.equals(k)||CognitiveTypes.DerivedKind.PROJECT_CANDIDATE.equals(k)||CognitiveTypes.DerivedKind.GOAL_SIGNAL.equals(k))return k;return"";}
    private static String reviewTitle(String title,String candidate){String base=n(title);String suffix=friendly(candidate);return base.isEmpty()?"Review · "+suffix:base+" · Review "+suffix;}
    private static String confirmedTitle(String title,String candidate){String x=n(title).replace(" · Review "+friendly(candidate),"").trim();return x.isEmpty()?friendly(candidate):x;}
    private static String friendly(String kind){if("ACTION".equals(kind))return"action";if("WAITING".equals(kind))return"waiting";if("DECISION".equals(kind))return"decision";if("PROJECT_CANDIDATE".equals(kind))return"project";if("GOAL_SIGNAL".equals(kind))return"goal";return"item";}
    private static String n(String s){return s==null?"":s.trim();}
}
