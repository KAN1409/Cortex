package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Durable unresolved obligations. Distinguishes waiting, resolved, cancelled and dismissed state. */
public final class OpenLoopStore {
    private OpenLoopStore(){}
    public static final String OPEN="OPEN",WAITING="WAITING",DUE="DUE",OVERDUE="OVERDUE",RESOLVED="RESOLVED",CANCELLED="CANCELLED",DISMISSED="DISMISSED";

    public static final class Loop {
        public final long id,threadId,anchorSignalId,createdAt,updatedAt,dueAt,followUpAt;
        public final String fingerprint,type,state,subject,owner,personKey,projectKey,resolutionKind,resolutionJson;
        public final boolean userCommitted;public final double confidence;
        Loop(Cursor c){id=c.getLong(0);fingerprint=n(c.getString(1));type=n(c.getString(2));state=n(c.getString(3));subject=n(c.getString(4));owner=n(c.getString(5));personKey=n(c.getString(6));projectKey=n(c.getString(7));threadId=c.getLong(8);anchorSignalId=c.getLong(9);userCommitted=c.getInt(10)!=0;dueAt=c.getLong(11);followUpAt=c.getLong(12);confidence=c.getDouble(13);resolutionKind=n(c.getString(14));resolutionJson=n(c.getString(15));createdAt=c.getLong(16);updatedAt=c.getLong(17);}
    }

    public static long upsertIncomingRequest(VaultDb db,long signalId,long threadId,String source,String title,String body,double confidence){
        CortexAttentionSchema.ensure(db);String subject=requestSubject(title,body),semantic=DerivedSemanticIdentity.key("ACTION",body),fp=Fingerprint.text("open-loop|incoming_request|"+threadId+"|"+semantic);SQLiteDatabase sql=db.getWritableDatabase();long now=System.currentTimeMillis();
        Cursor c=sql.query("open_loops",new String[]{"id","state"},"fingerprint=?",new String[]{fp},null,null,null,"1");long id=0;String state="";if(c.moveToFirst()){id=c.getLong(0);state=n(c.getString(1));}c.close();
        JSONObject resolution=new JSONObject();try{resolution.put("expected","reply_or_matching_outgoing_action");resolution.put("thread_id",threadId);resolution.put("topic",semantic);}catch(Exception ignored){}
        if(id>0&&active(state)){ContentValues v=new ContentValues();v.put("subject",subject);v.put("anchor_signal_id",signalId);v.put("confidence",Math.max(confidence,0.55));v.put("updated_at",now);sql.update("open_loops",v,"id=?",new String[]{String.valueOf(id)});}else{
            if(id>0){ContentValues old=new ContentValues();old.put("fingerprint",Fingerprint.text("historical-open-loop|"+fp+"|"+id));old.put("updated_at",now);sql.update("open_loops",old,"id=?",new String[]{String.valueOf(id)});}
            ContentValues v=new ContentValues();v.put("fingerprint",fp);v.put("type","INCOMING_REQUEST");v.put("state",OPEN);v.put("subject",subject);v.put("owner","USER");v.put("person_key",n(title));v.put("thread_id",threadId);v.put("anchor_signal_id",signalId);v.put("confidence",Math.max(confidence,0.55));v.put("resolution_kind","REPLY_OR_ACTION");v.put("resolution_json",resolution.toString());v.put("created_at",now);v.put("updated_at",now);id=sql.insertWithOnConflict("open_loops",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
        if(id>0)attachEvidence(db,id,signalId,"created_or_reinforced",1.0);return id;
    }

    public static boolean markUserCommitted(VaultDb db,long loopId,long signalId){if(loopId<=0)return false;ContentValues v=new ContentValues();v.put("user_committed",1);v.put("updated_at",System.currentTimeMillis());boolean ok=db.getWritableDatabase().update("open_loops",v,"id=? AND state IN ('OPEN','WAITING','DUE','OVERDUE')",new String[]{String.valueOf(loopId)})>0;if(ok)attachEvidence(db,loopId,signalId,"commitment",0.9);return ok;}
    public static boolean resolve(VaultDb db,long loopId,long signalId,String relation,double confidence){return terminal(db,loopId,signalId,RESOLVED,relation,confidence);}
    public static boolean cancel(VaultDb db,long loopId,long signalId,double confidence){return terminal(db,loopId,signalId,CANCELLED,"cancels",confidence);}
    public static boolean dismiss(VaultDb db,long loopId){return terminal(db,loopId,0,DISMISSED,"dismissed_by_user",1.0);}

    private static boolean terminal(VaultDb db,long loopId,long signalId,String state,String relation,double confidence){if(loopId<=0)return false;long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("state",state);v.put("updated_at",now);v.put("resolved_at",now);boolean ok=db.getWritableDatabase().update("open_loops",v,"id=? AND state IN ('OPEN','WAITING','DUE','OVERDUE')",new String[]{String.valueOf(loopId)})>0;if(ok&&signalId>0)attachEvidence(db,loopId,signalId,relation,confidence);return ok;}

    public static Loop get(VaultDb db,long id){CortexAttentionSchema.ensure(db);Cursor c=db.getReadableDatabase().query("open_loops",cols(),"id=?",new String[]{String.valueOf(id)},null,null,null,"1");Loop x=c.moveToFirst()?new Loop(c):null;c.close();return x;}
    public static Loop activeForThread(VaultDb db,long threadId){CortexAttentionSchema.ensure(db);Cursor c=db.getReadableDatabase().query("open_loops",cols(),"thread_id=? AND state IN ('OPEN','WAITING','DUE','OVERDUE')",new String[]{String.valueOf(threadId)},null,null,"updated_at DESC","1");Loop x=c.moveToFirst()?new Loop(c):null;c.close();return x;}

    public static void attachEvidence(VaultDb db,long loopId,long signalId,String relation,double confidence){if(loopId<=0||signalId<=0)return;ContentValues v=new ContentValues();v.put("loop_id",loopId);v.put("signal_id",signalId);v.put("relation",n(relation));v.put("confidence",confidence);v.put("created_at",System.currentTimeMillis());db.getWritableDatabase().insertWithOnConflict("open_loop_evidence",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    private static String[] cols(){return new String[]{"id","fingerprint","type","state","subject","owner","person_key","project_key","thread_id","anchor_signal_id","user_committed","due_at","follow_up_at","confidence","resolution_kind","resolution_json","created_at","updated_at"};}
    private static boolean active(String s){return OPEN.equals(s)||WAITING.equals(s)||DUE.equals(s)||OVERDUE.equals(s);}
    private static String requestSubject(String title,String body){String b=n(body);String low=b.toLowerCase();String[] prefixes={"ابعتلي","ابعته","ممكن تبعت","كلمني","فكرني","send me","please send","can you send","could you send"};for(String p:prefixes){int i=low.indexOf(p.toLowerCase());if(i>=0){String x=b.substring(Math.min(b.length(),i+p.length())).trim();if(!x.isEmpty())return clip(x,110);}}return !n(title).isEmpty()?clip(n(title),110):clip(b,110);}
    private static String clip(String s,int max){s=n(s);return s.length()<=max?s:s.substring(0,max-1)+"…";}
    private static String n(String s){return s==null?"":s.trim();}
}
