package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Durable source of truth for Cortex action execution and verification. */
public final class CortexActionReceiptStore {
    public static final String CREATED="CREATED",VALIDATED="VALIDATED",NEEDS_DETAILS="NEEDS_DETAILS",USER_APPROVED="USER_APPROVED",DISPATCHING="DISPATCHING",DISPATCHED="DISPATCHED",HANDED_OFF="HANDED_OFF",EFFECT_OBSERVED="EFFECT_OBSERVED",VERIFYING="VERIFYING",VERIFIED="VERIFIED",FAILED="FAILED",CANCELLED="CANCELLED",UNKNOWN="UNKNOWN",ROLLED_BACK="ROLLED_BACK";
    public static final String V_PENDING="PENDING",V_NOT_VERIFIED="NOT_VERIFIED",V_VERIFIED="VERIFIED",V_FAILED="FAILED";

    private CortexActionReceiptStore(){}

    public static final class Receipt {
        public final long receiptId,sourceItemId,createdAt,validatedAt,approvedAt,dispatchedAt,observedAt,verifiedAt,updatedAt;
        public final String requestId,canonicalActionId,executor,verificationMethod,inputHash,idempotencyKey,executionStatus,verificationStatus,resultSummary,evidenceType,evidenceReference,evidenceHash,failureCode,failureReason,undoReference,metadataJson;
        public final boolean undoSupported;
        Receipt(Cursor c){
            receiptId=g(c,"receipt_id");requestId=s(c,"request_id");canonicalActionId=s(c,"canonical_action_id");sourceItemId=g(c,"source_item_id");createdAt=g(c,"created_at");validatedAt=g(c,"validated_at");approvedAt=g(c,"approved_at");dispatchedAt=g(c,"dispatched_at");observedAt=g(c,"observed_at");verifiedAt=g(c,"verified_at");executor=s(c,"executor");verificationMethod=s(c,"verification_method");inputHash=s(c,"input_hash");idempotencyKey=s(c,"idempotency_key");executionStatus=s(c,"execution_status");verificationStatus=s(c,"verification_status");resultSummary=s(c,"result_summary");evidenceType=s(c,"evidence_type");evidenceReference=s(c,"evidence_reference");evidenceHash=s(c,"evidence_hash");failureCode=s(c,"failure_code");failureReason=s(c,"failure_reason");undoSupported=g(c,"undo_supported")!=0;undoReference=s(c,"undo_reference");metadataJson=s(c,"metadata_json");updatedAt=g(c,"updated_at");
        }
    }

    public static void ensure(VaultDb helper){if(helper!=null)ensure(helper.getWritableDatabase());}
    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS action_receipts(receipt_id INTEGER PRIMARY KEY AUTOINCREMENT,request_id TEXT NOT NULL,canonical_action_id TEXT NOT NULL,source_item_id INTEGER DEFAULT 0,created_at INTEGER NOT NULL,validated_at INTEGER DEFAULT 0,approved_at INTEGER DEFAULT 0,dispatched_at INTEGER DEFAULT 0,observed_at INTEGER DEFAULT 0,verified_at INTEGER DEFAULT 0,executor TEXT NOT NULL DEFAULT '',verification_method TEXT NOT NULL DEFAULT '',input_hash TEXT NOT NULL DEFAULT '',idempotency_key TEXT NOT NULL DEFAULT '',execution_status TEXT NOT NULL DEFAULT 'CREATED',verification_status TEXT NOT NULL DEFAULT 'PENDING',result_summary TEXT NOT NULL DEFAULT '',evidence_type TEXT NOT NULL DEFAULT '',evidence_reference TEXT NOT NULL DEFAULT '',evidence_hash TEXT NOT NULL DEFAULT '',failure_code TEXT NOT NULL DEFAULT '',failure_reason TEXT NOT NULL DEFAULT '',undo_supported INTEGER NOT NULL DEFAULT 0,undo_reference TEXT NOT NULL DEFAULT '',metadata_json TEXT NOT NULL DEFAULT '{}',updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_action_receipts_request ON action_receipts(request_id,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_action_receipts_action ON action_receipts(canonical_action_id,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_action_receipts_status ON action_receipts(execution_status,verification_status,updated_at DESC)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_action_receipts_idempotency ON action_receipts(canonical_action_id,idempotency_key) WHERE idempotency_key<>''");
    }

    public static long begin(VaultDb db,String requestId,String canonicalActionId,long sourceItemId,String executor,String inputHash,String idempotencyKey,boolean undoSupported,String metadataJson){
        if(db==null)throw new IllegalArgumentException("db required");
        CortexActionRegistry.Action contract=CortexActionRegistry.require(canonicalActionId);
        String request=clean(requestId),key=clean(idempotencyKey);if(request.isEmpty())throw new IllegalArgumentException("request_id required");
        ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("request_id",request);v.put("canonical_action_id",contract.actionId);v.put("source_item_id",Math.max(0,sourceItemId));v.put("created_at",now);v.put("executor",clean(executor));v.put("input_hash",clean(inputHash));v.put("idempotency_key",key);v.put("execution_status",CREATED);v.put("verification_status",V_PENDING);v.put("undo_supported",undoSupported?1:0);v.put("metadata_json",clean(metadataJson).isEmpty()?"{}":metadataJson);v.put("updated_at",now);
        long id=db.getWritableDatabase().insertWithOnConflict("action_receipts",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id>0)return id;
        if(key.isEmpty())throw new IllegalStateException("Could not create action receipt");
        Cursor c=db.getReadableDatabase().query("action_receipts",new String[]{"receipt_id"},"canonical_action_id=? AND idempotency_key=?",new String[]{contract.actionId,key},null,null,null,"1");long existing=c.moveToFirst()?c.getLong(0):0;c.close();if(existing<=0)throw new IllegalStateException("Could not resolve idempotent action receipt");return existing;
    }

    public static Receipt get(VaultDb db,long receiptId){if(db==null||receiptId<=0)return null;ensure(db);Cursor c=db.getReadableDatabase().query("action_receipts",null,"receipt_id=?",new String[]{String.valueOf(receiptId)},null,null,null,"1");Receipt r=c.moveToFirst()?new Receipt(c):null;c.close();return r;}

    public static boolean markValidated(VaultDb db,long id){return advance(db,id,VALIDATED,V_PENDING,"",null);}
    public static boolean markNeedsDetails(VaultDb db,long id,String reason){ContentValues e=new ContentValues();e.put("failure_reason",clean(reason));return advance(db,id,NEEDS_DETAILS,V_NOT_VERIFIED,"",e);}
    public static boolean markApproved(VaultDb db,long id){return advance(db,id,USER_APPROVED,V_PENDING,"",null);}
    public static boolean markDispatching(VaultDb db,long id){return advance(db,id,DISPATCHING,V_PENDING,"",null);}
    public static boolean markDispatched(VaultDb db,long id,String summary){ContentValues e=new ContentValues();e.put("dispatched_at",System.currentTimeMillis());return advance(db,id,DISPATCHED,V_PENDING,summary,e);}
    public static boolean markHandedOff(VaultDb db,long id,String summary,String evidenceReference){ContentValues e=new ContentValues();long now=System.currentTimeMillis();e.put("dispatched_at",now);e.put("evidence_type","android_intent_handoff");e.put("evidence_reference",clean(evidenceReference));return advance(db,id,HANDED_OFF,V_NOT_VERIFIED,summary,e);}
    public static boolean markEffectObserved(VaultDb db,long id,String summary,String evidenceType,String evidenceReference,String evidenceHash){ContentValues e=evidence(evidenceType,evidenceReference,evidenceHash);e.put("observed_at",System.currentTimeMillis());return advance(db,id,EFFECT_OBSERVED,V_PENDING,summary,e);}
    public static boolean markVerifying(VaultDb db,long id,String method){ContentValues e=new ContentValues();e.put("verification_method",clean(method));return advance(db,id,VERIFYING,V_PENDING,"",e);}
    public static boolean markVerified(VaultDb db,long id,String summary,String evidenceType,String evidenceReference,String evidenceHash,String method){
        if(clean(evidenceType).isEmpty()||clean(evidenceReference).isEmpty()||clean(evidenceHash).isEmpty()||clean(method).isEmpty())return false;
        ContentValues e=evidence(evidenceType,evidenceReference,evidenceHash);e.put("verification_method",clean(method));e.put("verified_at",System.currentTimeMillis());return advance(db,id,VERIFIED,V_VERIFIED,summary,e);
    }
    public static boolean markFailed(VaultDb db,long id,String code,String reason){ContentValues e=new ContentValues();e.put("failure_code",clean(code));e.put("failure_reason",clean(reason));return advance(db,id,FAILED,V_FAILED,"",e);}
    public static boolean markCancelled(VaultDb db,long id,String reason){ContentValues e=new ContentValues();e.put("failure_reason",clean(reason));return advance(db,id,CANCELLED,V_NOT_VERIFIED,"",e);}
    public static boolean markUnknown(VaultDb db,long id,String reason){ContentValues e=new ContentValues();e.put("failure_reason",clean(reason));return advance(db,id,UNKNOWN,V_NOT_VERIFIED,"",e);}
    public static boolean markRolledBack(VaultDb db,long id,String undoReference,String summary){ContentValues e=new ContentValues();e.put("undo_reference",clean(undoReference));return advance(db,id,ROLLED_BACK,V_NOT_VERIFIED,summary,e);}

    private static boolean advance(VaultDb db,long id,String next,String verification,String summary,ContentValues extras){
        if(db==null||id<=0)return false;ensure(db);Receipt current=get(db,id);if(current==null||!allowed(current.executionStatus,next))return false;ContentValues v=extras==null?new ContentValues():extras;long now=System.currentTimeMillis();v.put("execution_status",next);v.put("verification_status",verification);if(!clean(summary).isEmpty())v.put("result_summary",summary);if(VALIDATED.equals(next))v.put("validated_at",now);if(USER_APPROVED.equals(next))v.put("approved_at",now);v.put("updated_at",now);return db.getWritableDatabase().update("action_receipts",v,"receipt_id=?",new String[]{String.valueOf(id)})==1;
    }

    private static boolean allowed(String from,String to){
        if(from.equals(to))return true;
        if(CREATED.equals(from))return set(VALIDATED,NEEDS_DETAILS,CANCELLED,FAILED).contains(to);
        if(VALIDATED.equals(from))return set(USER_APPROVED,NEEDS_DETAILS,CANCELLED,FAILED).contains(to);
        if(NEEDS_DETAILS.equals(from))return set(VALIDATED,CANCELLED,FAILED).contains(to);
        if(USER_APPROVED.equals(from))return set(DISPATCHING,CANCELLED,FAILED).contains(to);
        if(DISPATCHING.equals(from))return set(DISPATCHED,HANDED_OFF,CANCELLED,FAILED,UNKNOWN).contains(to);
        if(DISPATCHED.equals(from))return set(EFFECT_OBSERVED,VERIFYING,HANDED_OFF,FAILED,UNKNOWN).contains(to);
        if(HANDED_OFF.equals(from))return set(EFFECT_OBSERVED,VERIFYING,FAILED,UNKNOWN).contains(to);
        if(EFFECT_OBSERVED.equals(from))return set(VERIFYING,VERIFIED,FAILED,UNKNOWN).contains(to);
        if(VERIFYING.equals(from))return set(VERIFIED,FAILED,UNKNOWN).contains(to);
        if(VERIFIED.equals(from))return ROLLED_BACK.equals(to);
        return false;
    }

    private static ContentValues evidence(String type,String reference,String hash){ContentValues e=new ContentValues();e.put("evidence_type",clean(type));e.put("evidence_reference",clean(reference));e.put("evidence_hash",clean(hash));return e;}
    private static Set<String> set(String... values){return new HashSet<>(Arrays.asList(values));}
    private static String clean(String s){return s==null?"":s.trim();}
    private static String s(Cursor c,String name){int i=c.getColumnIndex(name);return i<0||c.isNull(i)?"":c.getString(i);}private static long g(Cursor c,String name){int i=c.getColumnIndex(name);return i<0||c.isNull(i)?0:c.getLong(i);}
}
