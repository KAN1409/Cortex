package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Real-data quality ledger for the relevance pipeline.
 *
 * It keeps deterministic rules, adaptive policy, raw local-model output, final policy and
 * eventual user evidence separate so Cortex can measure which layer actually adds value.
 * Audit labels written here are evaluation-only: they do not enter AdaptiveRelevanceLearning.
 */
public final class RelevanceEvaluationStore {
    public static final String VERSION="relevance_eval_003";
    private RelevanceEvaluationStore(){}

    public static final class EvalCase {
        public final long signalId,threadId,updatedAt;
        public final String source,title,body,detDisposition,detCandidate,learnedDisposition,learnedCandidate,modelDisposition,modelCandidate,finalDisposition,finalCandidate,finalEngine,userVerdict,userCandidate;
        public final double detConfidence,learnedConfidence,modelConfidence,finalConfidence;
        EvalCase(long signalId,long threadId,long updatedAt,String source,String title,String body,String detDisposition,String detCandidate,double detConfidence,String learnedDisposition,String learnedCandidate,double learnedConfidence,String modelDisposition,String modelCandidate,double modelConfidence,String finalDisposition,String finalCandidate,double finalConfidence,String finalEngine,String userVerdict,String userCandidate){
            this.signalId=signalId;this.threadId=threadId;this.updatedAt=updatedAt;this.source=n(source);this.title=n(title);this.body=n(body);this.detDisposition=n(detDisposition);this.detCandidate=n(detCandidate);this.detConfidence=detConfidence;this.learnedDisposition=n(learnedDisposition);this.learnedCandidate=n(learnedCandidate);this.learnedConfidence=learnedConfidence;this.modelDisposition=n(modelDisposition);this.modelCandidate=n(modelCandidate);this.modelConfidence=modelConfidence;this.finalDisposition=n(finalDisposition);this.finalCandidate=n(finalCandidate);this.finalConfidence=finalConfidence;this.finalEngine=n(finalEngine);this.userVerdict=n(userVerdict);this.userCandidate=n(userCandidate);
        }
        public boolean hasModel(){return !modelDisposition.isEmpty();}
        public boolean disagreement(){return hasModel()&&!semantic(learnedDisposition,learnedCandidate).equals(semantic(modelDisposition,modelCandidate));}
    }

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

    public static void model(VaultDb db,long signalId,long modelRunId,MasterRelevanceFilter.Decision d){if(db==null||signalId<=0||d==null)return;try{ensure(db);ContentValues v=new ContentValues();putDecision(v,"model_",d);v.put("model_run_id",Math.max(0,modelRunId));v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)});}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","model",e,"EVAL_MODEL_WRITE",0,0,signalId,0,modelRunId,null);}}

    public static void finalDecision(VaultDb db,long signalId,String engine,MasterRelevanceFilter.Decision d,long reviewId){if(db==null||signalId<=0||d==null)return;try{ensure(db);ContentValues v=new ContentValues();v.put("final_disposition",d.disposition.name());v.put("final_candidate",candidate(d));v.put("final_confidence",d.confidence);v.put("final_engine",n(engine));if(reviewId>0)v.put("review_id",reviewId);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)});}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","final_decision",e,"EVAL_FINAL_WRITE",0,0,signalId,0,0,null);}}

    /** Operational Review verdict. This may inform AdaptiveRelevanceLearning through feedback_events elsewhere. */
    public static void userVerdict(VaultDb db,long signalId,String verdict,String candidateKind,long reviewId){if(db==null||signalId<=0)return;try{ensure(db);ContentValues v=new ContentValues();v.put("user_verdict",n(verdict));v.put("user_candidate",n(candidateKind).toUpperCase(Locale.ROOT));if(reviewId>0)v.put("review_id",reviewId);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=?",new String[]{String.valueOf(signalId)});}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","user_verdict",e,"EVAL_VERDICT_WRITE",0,0,signalId,0,0,null);}}

    /** Evaluation-only ground truth. It never writes feedback_events or changes learned behavior. */
    public static boolean auditLabel(VaultDb db,long signalId,String expectedDisposition){
        String label=validLabel(expectedDisposition);if(db==null||signalId<=0||label.isEmpty())return false;try{ensure(db);ContentValues v=new ContentValues();v.put("user_verdict","audit_label");v.put("user_candidate",label);v.put("updated_at",System.currentTimeMillis());int changed=db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=? AND COALESCE(user_verdict,'')=''",new String[]{String.valueOf(signalId)});if(changed>0)DiagnosticsLog.info(db,"RelevanceEvaluationStore","audit_label","recorded",0,0,signalId,0,0,0,new JSONObject().put("expected",label));return changed>0;}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","audit_label",e,"EVAL_AUDIT_LABEL",0,0,signalId,0,0,null);return false;}
    }

    public static boolean auditSkip(VaultDb db,long signalId){if(db==null||signalId<=0)return false;try{ensure(db);ContentValues v=new ContentValues();v.put("user_verdict","audit_skip");v.put("user_candidate","");v.put("updated_at",System.currentTimeMillis());return db.getWritableDatabase().update("relevance_evaluations",v,"signal_id=? AND COALESCE(user_verdict,'')=''",new String[]{String.valueOf(signalId)})>0;}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","audit_skip",e,"EVAL_AUDIT_SKIP",0,0,signalId,0,0,null);return false;}}

    /** Prioritized unlabeled real cases: high-risk durable outputs, disagreements, then Reviews/context. */
    public static ArrayList<EvalCase> auditQueue(VaultDb db,int limit){
        ArrayList<EvalCase> out=new ArrayList<>();if(db==null)return out;try{ensure(db);int lim=Math.max(1,Math.min(50,limit));String sql="SELECT e.signal_id,e.thread_id,e.updated_at,e.source_key,COALESCE(r.title,''),COALESCE(r.body,''),e.det_disposition,e.det_candidate,e.det_confidence,e.learned_disposition,e.learned_candidate,e.learned_confidence,e.model_disposition,e.model_candidate,e.model_confidence,e.final_disposition,e.final_candidate,e.final_confidence,e.final_engine,e.user_verdict,e.user_candidate FROM relevance_evaluations e LEFT JOIN raw_signals r ON r.id=e.signal_id WHERE COALESCE(e.user_verdict,'')='' ORDER BY CASE WHEN e.final_disposition IN ('ACTION','WAITING','DECISION') THEN 0 WHEN COALESCE(e.model_disposition,'')<>'' AND (COALESCE(e.learned_disposition,'')<>COALESCE(e.model_disposition,'') OR COALESCE(e.learned_candidate,'')<>COALESCE(e.model_candidate,'')) THEN 1 WHEN e.final_disposition='REVIEW' THEN 2 ELSE 3 END,e.updated_at DESC LIMIT "+lim;Cursor c=db.getReadableDatabase().rawQuery(sql,null);while(c.moveToNext())out.add(from(c));c.close();}catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","audit_queue",e,"EVAL_AUDIT_QUEUE",0,0,0,0,0,null);}return out;
    }

    public static JSONObject matrix(VaultDb db){
        JSONObject o=new JSONObject();if(db==null)return o;try{
            ensure(db);
            long total=count(db,"SELECT COUNT(*) FROM relevance_evaluations");long model=count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE COALESCE(model_disposition,'')<>''");long audit=count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE user_verdict='audit_label'");long reviewVerdicts=count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE user_verdict IN ('confirm','dismiss','not_action','not_important','ignore_similar')");long confirm=count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE user_verdict='confirm'");long reject=count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE user_verdict IN ('dismiss','not_action','not_important','ignore_similar')");
            o.put("total",total);o.put("with_model",model);o.put("audit_labels",audit);o.put("review_verdicts",reviewVerdicts);o.put("with_user_verdict",audit+reviewVerdicts);o.put("learned_changed_rule",count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE COALESCE(det_disposition,'')<>COALESCE(learned_disposition,'') OR COALESCE(det_candidate,'')<>COALESCE(learned_candidate,'')"));o.put("rule_model_disagreement",semanticDisagreements(db));o.put("confirmed_reviews",confirm);o.put("rejected_reviews",reject);o.put("review_acceptance_rate",ratio(confirm,confirm+reject));o.put("final_actions",count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE final_disposition='ACTION'"));o.put("unverified_final_actions",count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE final_disposition='ACTION' AND COALESCE(user_verdict,'')=''"));o.put("observed_false_actions",count(db,"SELECT COUNT(*) FROM relevance_evaluations WHERE final_disposition='ACTION' AND ((user_verdict='audit_label' AND user_candidate<>'ACTION') OR user_verdict='not_action')"));o.put("thread_gap_violations",threadGapViolations(db));

            Comparison q=compareLayers(db);o.put("exact_ground_truth_cases",q.truth);o.put("adaptive_helped",q.adaptiveHelp);o.put("adaptive_harmed",q.adaptiveHarm);o.put("model_helped",q.modelHelp);o.put("model_harmed",q.modelHarm);o.put("final_correct",q.finalCorrect);o.put("final_accuracy_on_exact_labels",ratio(q.finalCorrect,q.truth));o.put("model_high_confidence_cases",q.highTotal);o.put("model_high_confidence_correct",q.highCorrect);o.put("model_high_confidence_accuracy",ratio(q.highCorrect,q.highTotal));o.put("model_medium_confidence_cases",q.midTotal);o.put("model_medium_confidence_correct",q.midCorrect);o.put("model_low_confidence_cases",q.lowTotal);o.put("model_low_confidence_correct",q.lowCorrect);
        }catch(Throwable e){DiagnosticsLog.error(db,"RelevanceEvaluationStore","matrix",e,"EVAL_MATRIX",0,0,0,0,0,null);}return o;
    }

    private static Comparison compareLayers(VaultDb db){
        Comparison q=new Comparison();Cursor c=db.getReadableDatabase().query("relevance_evaluations",new String[]{"det_disposition","det_candidate","learned_disposition","learned_candidate","model_disposition","model_candidate","model_confidence","final_disposition","final_candidate","user_verdict","user_candidate"},"user_verdict='audit_label' OR user_verdict='confirm'",null,null,null,null);
        while(c.moveToNext()){
            String verdict=n(c.getString(9)),truth=n(c.getString(10)).toUpperCase(Locale.ROOT);if(truth.isEmpty())continue;boolean exact="audit_label".equals(verdict);q.truth++;
            boolean det=matches(c.getString(0),c.getString(1),truth,exact);boolean learned=matches(c.getString(2),c.getString(3),truth,exact);String md=n(c.getString(4));boolean hasModel=!md.isEmpty(),model=hasModel&&matches(md,c.getString(5),truth,exact);boolean fin=matches(c.getString(7),c.getString(8),truth,exact);if(!det&&learned)q.adaptiveHelp++;if(det&&!learned)q.adaptiveHarm++;if(hasModel&&model&&!learned)q.modelHelp++;if(hasModel&&!model&&learned)q.modelHarm++;if(fin)q.finalCorrect++;
            if(hasModel){double conf=c.getDouble(6);if(conf>=0.80){q.highTotal++;if(model)q.highCorrect++;}else if(conf>=0.60){q.midTotal++;if(model)q.midCorrect++;}else{q.lowTotal++;if(model)q.lowCorrect++;}}
        }c.close();return q;
    }

    private static boolean matches(String disposition,String candidate,String truth,boolean exactDisposition){String d=n(disposition).toUpperCase(Locale.ROOT);return exactDisposition?truth.equals(d):truth.equals(semantic(d,candidate));}
    private static long semanticDisagreements(VaultDb db){long n=0;Cursor c=db.getReadableDatabase().query("relevance_evaluations",new String[]{"learned_disposition","learned_candidate","model_disposition","model_candidate"},"COALESCE(model_disposition,'')<>''",null,null,null,null);while(c.moveToNext())if(!semantic(c.getString(0),c.getString(1)).equals(semantic(c.getString(2),c.getString(3))))n++;c.close();return n;}

    /** A thread episode containing a >48h internal gap indicates a boundary failure. */
    private static long threadGapViolations(VaultDb db){long bad=0,lastThread=-1,lastTime=0;Cursor c=db.getReadableDatabase().rawQuery("SELECT thread_id,occurred_at FROM raw_signals WHERE thread_id>0 ORDER BY thread_id ASC,occurred_at ASC",null);while(c.moveToNext()){long thread=c.getLong(0),time=c.getLong(1);if(thread==lastThread&&lastTime>0&&time-lastTime>SignalThreadStore.THREAD_EXPIRY_MS)bad++;lastThread=thread;lastTime=time;}c.close();return bad;}

    private static EvalCase from(Cursor c){return new EvalCase(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getDouble(8),c.getString(9),c.getString(10),c.getDouble(11),c.getString(12),c.getString(13),c.getDouble(14),c.getString(15),c.getString(16),c.getDouble(17),c.getString(18),c.getString(19),c.getString(20));}
    private static void putDecision(ContentValues v,String prefix,MasterRelevanceFilter.Decision d){v.put(prefix+"disposition",d.disposition.name());v.put(prefix+"candidate",candidate(d));v.put(prefix+"confidence",d.confidence);}
    private static String candidate(MasterRelevanceFilter.Decision d){return d.reviewable()?d.candidateKind:(d.durable()?d.disposition.name():"");}
    private static String semantic(String disposition,String candidate){String d=n(disposition).toUpperCase(Locale.ROOT),c=n(candidate).toUpperCase(Locale.ROOT);return "REVIEW".equals(d)&&!c.isEmpty()?c:d;}
    private static String validLabel(String x){String s=n(x).toUpperCase(Locale.ROOT);try{MasterRelevanceFilter.Disposition.valueOf(s);return s;}catch(Exception e){return"";}}
    private static long count(VaultDb db,String sql){Cursor c=db.getReadableDatabase().rawQuery(sql,null);long x=c.moveToFirst()?c.getLong(0):0;c.close();return x;}
    private static double ratio(long a,long b){return b<=0?0:((double)a/(double)b);}
    private static String n(String s){return s==null?"":s.trim();}
    private static final class Comparison {long truth,adaptiveHelp,adaptiveHarm,modelHelp,modelHarm,finalCorrect,highTotal,highCorrect,midTotal,midCorrect,lowTotal,lowCorrect;}
}
