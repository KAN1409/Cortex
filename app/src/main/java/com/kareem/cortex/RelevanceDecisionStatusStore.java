package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Non-semantic execution status for the relevance ledger.
 * Failure/supersession are never encoded as ACTION/WAITING/DECISION/REVIEW/CONTEXT.
 */
public final class RelevanceDecisionStatusStore {
    public static final String VERSION="relevance_status_002";
    private RelevanceDecisionStatusStore(){}

    public static void ensure(VaultDb db){if(db==null)return;CognitiveStore.ensure(db);ensure(db.getWritableDatabase());}
    public static void ensure(SQLiteDatabase s){if(s==null)return;addColumn(s,"relevance_evaluations","model_status","TEXT DEFAULT ''");addColumn(s,"relevance_evaluations","apply_status","TEXT DEFAULT ''");}

    /** A semantic baseline exists, but a durable/review state transition has not succeeded yet. */
    public static boolean pendingApply(VaultDb db,long signalId){if(db==null||signalId<=0)return false;ensure(db);ContentValues v=new ContentValues();v.putNull("final_disposition");v.putNull("final_candidate");v.put("final_confidence",0);v.putNull("final_engine");v.put("apply_status","PENDING");v.put("updated_at",System.currentTimeMillis());return db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)})>0;}

    public static boolean modelStatus(VaultDb db,long signalId,String status){if(db==null||signalId<=0)return false;ensure(db);ContentValues v=new ContentValues();v.put("model_status",n(status));v.put("updated_at",System.currentTimeMillis());boolean ok=db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)})>0;if(ok&&"APPLIED".equalsIgnoreCase(n(status))){long threadId=threadIdForSignal(db,signalId);CortexAttentionOrchestrator.onSignalCaptured(db,signalId,threadId);}return ok;}
    public static boolean applyStatus(VaultDb db,long signalId,String status){if(db==null||signalId<=0)return false;ensure(db);ContentValues v=new ContentValues();v.put("apply_status",n(status));v.put("updated_at",System.currentTimeMillis());return db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)})>0;}
    public static String applyStatus(VaultDb db,long signalId){if(db==null||signalId<=0)return"";ensure(db);Cursor c=db.getReadableDatabase().query("relevance_evaluations",new String[]{"apply_status"},"signal_id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");String x=c.moveToFirst()?n(c.getString(0)):"";c.close();return x;}
    public static boolean isApplied(VaultDb db,long signalId){return"APPLIED".equals(applyStatus(db,signalId));}

    /** Transaction-only writer. Call ensure(db) before beginTransaction(). */
    public static boolean writeModel(SQLiteDatabase s,long signalId,long modelRunId,MasterRelevanceFilter.Decision d){if(s==null||signalId<=0||d==null)return false;ContentValues v=new ContentValues();v.put("model_disposition",d.disposition.name());v.put("model_candidate",d.reviewable()?n(d.candidateKind).toUpperCase():"");v.put("model_confidence",d.confidence);v.put("model_run_id",Math.max(0,modelRunId));v.put("model_status","VALID");v.put("updated_at",System.currentTimeMillis());return s.update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)})>0;}

    /** Transaction-only final semantic transition writer. Call ensure(db) before beginTransaction(). */
    public static boolean writeFinal(SQLiteDatabase s,long signalId,String engine,MasterRelevanceFilter.Decision d,long reviewId,String applyStatus){if(s==null||signalId<=0||d==null)return false;ContentValues v=new ContentValues();v.put("final_disposition",d.disposition.name());v.put("final_candidate",d.reviewable()?n(d.candidateKind).toUpperCase():"");v.put("final_confidence",d.confidence);v.put("final_engine",n(engine));if(reviewId>0)v.put("review_id",reviewId);v.put("apply_status",n(applyStatus));v.put("updated_at",System.currentTimeMillis());return s.update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)})>0;}

    private static long threadIdForSignal(VaultDb db,long signalId){Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"thread_id"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;}
    private static void addColumn(SQLiteDatabase s,String table,String column,String definition){if(!hasColumn(s,table,column))s.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
    private static boolean hasColumn(SQLiteDatabase s,String table,String column){Cursor c=s.rawQuery("PRAGMA table_info("+table+")",null);boolean found=false;while(c.moveToNext()){int i=c.getColumnIndex("name");if(i>=0&&column.equals(c.getString(i))){found=true;break;}}c.close();return found;}
    private static String n(String s){return s==null?"":s.trim();}
}
