package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/** Measures deterministic rules, adaptive learning, local model and eventual user verdicts on the same signal. */
public final class RelevanceEvaluationStore {
    public static final String VERSION="relevance_eval_001";
    private RelevanceEvaluationStore(){}

    public static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS relevance_evaluations(id INTEGER PRIMARY KEY AUTOINCREMENT,signal_id INTEGER NOT NULL UNIQUE,thread_id INTEGER DEFAULT 0,source_key TEXT,det_disposition TEXT,det_candidate TEXT,det_confidence REAL DEFAULT 0,learned_disposition TEXT,learned_candidate TEXT,learned_confidence REAL DEFAULT 0,model_disposition TEXT,model_candidate TEXT,model_confidence REAL DEFAULT 0,model_run_id INTEGER DEFAULT 0,final_disposition TEXT,final_candidate TEXT,final_confidence REAL DEFAULT 0,final_engine TEXT,review_id INTEGER DEFAULT 0,user_verdict TEXT,user_candidate TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_eval_source ON relevance_evaluations(source_key,updated_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_eval_thread ON relevance_evaluations(thread_id,updated_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_eval_verdict ON relevance_evaluations(user_verdict,updated_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_eval_model ON relevance_evaluations(model_disposition,updated_at DESC)");
    }

    public static void deterministic(VaultDb db,long threadId,long signalId,String source,MasterRelevanceFilter.Decision base,MasterRelevanceFilter.Decision learned){
        if(db==null||signalId<=0||base==null||learned==null)return;try{ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("signal_id",signalId);v.put("thread_id",Math.max(0,threadId));v.put("source_key",n(source));putDecision(v,"det_",base);putDecision(v,"learned_",learned);v.put("final_disposition",learned.disposition.name());v.put("final_candidate",candidate(learned));v.put("final_confidence",learned.confidence);v.put("final_engine","deterministic+learning");v.put("created_at",now);v.put("updated_at",now);db.getWritableDatabase().insertWithOnConflict("relevance_evaluations",null,v,SQLiteDatabase.CONFLICT_IGNORE);ContentValues u=new ContentValues();u.put("thread_id",Math.max(0,threadId));u.put("source_key",n(source));putDecision(u,"det_",base);putDecision(u,"learned_",learned);u.put("final_disposition",learned.disposition.name());u.put("final_candidate",candidate(learned));u.put("final_confidence",learned.confidence);u.put("final_engine","deterministic+learning");u.put("updated_at",now);db.getWritableDatabase().update("relevance_evaluations",u,"signal_id=?",new String[]{String.valueOf(signalId)});}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","deterministic",e,"EVAL_DETERMINISTIC_WRITE",0,threadId,signalId,0,0,null);}}
    public static void model(VaultDb db,long signalId,long modelRunId,MasterRelevanceFilter.Decision d){if(db==null||signalId<=0||d==null)return;try{ensure(db);ContentValues v=new ContentValues();putDecision(v,"model_",d);v.put("model_run_id",Math.max(0,modelRunId));v.put("final_disposition",d.disposition.name());v.put("final_candidate",candidate(d));v.put("final_confidence",d.confidence);v.put("final_engine","local_model");v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)});}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","model",e,"EVAL_MODEL_WRITE",0,0,signalId,0,modelRunId,null);}}
    public static void finalDecision(VaultDb db,long signalId,String engine,MasterRelevanceFilter.Decision d,long reviewId){if(db==null||signalId<=0||d==null)return;try{ensure(db);ContentValues v=new ContentValues();v.put("final_disposition",d.disposition.name());v.put("final_candidate",candidate(d));v.put("final_confidence",d.confidence);v.put("final_engine",n(engine));if(reviewId>0)v.put("review_id",reviewId);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)});}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","final_decision",e,"EVAL_FINAL_WRITE",0,0,signalId,0,0,null);}}
    public static void userVerdict(VaultDb db,long signalId,String verdict,String candidateKind,long reviewId){if(db==null||signalId<=0)return;try{ensure(db);ContentValues v=new ContentValues();v.put("user_verdict",n(verdict));v.put("user_candidate",n(candidateKind).toUpperCase());if(reviewId>0)v.put("review_id",reviewId);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)});}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","user_verdict",e,"EVAL_VERDICT_WRITE",0,0,signalId,0,0,null);}}

    public static JSONObject matrix(VaultDb db){JSONObject o=new JSONObject();if(db==null)return o;try{ensure(db);o.put("total",count(db,"SELECT COUNT(*) FROM relevance_evaluations"));o.put("with_model",count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE model_disposition IS NOT NULL AND model_disposition<>''"));o.put("with_user_verdict",count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE user_verdict IS NOT NULL AND user_verdict<>''"));o.put("rule_model_disagreement",count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE model_disposition IS NOT NULL AND model_disposition<>'' AND learned_disposition<>model_disposition"));o.put("confirmed_reviews",count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE user_verdict='confirm'"));o.put("rejected_reviews",count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE user_verdict IN ('dismiss','not_action','not_important','ignore_similar')"));}catch(Throwable ignored){}return o;}

    private static void putDecision(ContentValues v,String prefix,MasterRelevanceFilter.Decision d){v.put(prefix+"disposition",d.disposition.name());v.put(prefix+"candidate",candidate(d));v.put(prefix+"confidence",d.confidence);}
    private static String candidate(MasterRelevanceFilter.Decision d){return d.reviewable()?d.candidateKind:(d.durable()?d.disposition.name():"");}
    private static long count(VaultDb db,String sql){Cursor c=db.getReadableDatabase().rawQuery(sql,null);long x=c.moveToFirst()?c.getLong(0):0;c.close();return x;}
    private static String n(String s){return s==null?"":s.trim();}
}
