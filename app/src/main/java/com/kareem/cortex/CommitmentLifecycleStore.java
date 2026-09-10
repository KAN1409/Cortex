package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Persistent v70 commitment ledger built from correlated semantic evidence.
 *
 * This store is additive and cognitive-only. It never writes production Now/projection tables.
 * Current lifecycle truth is event-time ordered: late old evidence may be audited but cannot
 * reopen, re-date, or otherwise regress a newer commitment state.
 */
public final class CommitmentLifecycleStore {
    public static final String VERSION = "commitment_lifecycle_store_001";

    public static final String OPEN = "open";
    public static final String FULFILLED = "fulfilled";
    public static final String WITHDRAWN = "withdrawn";
    public static final String EXPIRED = "expired";
    public static final String SUPERSEDED = "superseded";

    private CommitmentLifecycleStore() {}

    public static final class Record {
        public final long id;
        public final String commitmentKey;
        public final long situationId;
        public final String linkKey;
        public final String subject;
        public final String summary;
        public final String state;
        public final long deadlineAt;
        public final boolean deadlineHasTime;
        public final String deadlineExpression;
        public final double confidence;
        public final long openedAt;
        public final long lastChangedAt;
        public final long lastEvidenceAt;
        public final long sourceSemanticEventId;
        public final long terminalAt;
        public final int revision;

        Record(long id,String commitmentKey,long situationId,String linkKey,String subject,String summary,
               String state,long deadlineAt,boolean deadlineHasTime,String deadlineExpression,
               double confidence,long openedAt,long lastChangedAt,long lastEvidenceAt,
               long sourceSemanticEventId,long terminalAt,int revision) {
            this.id=id;this.commitmentKey=n(commitmentKey);this.situationId=situationId;this.linkKey=n(linkKey);
            this.subject=n(subject);this.summary=n(summary);this.state=n(state);this.deadlineAt=deadlineAt;
            this.deadlineHasTime=deadlineHasTime;this.deadlineExpression=n(deadlineExpression);
            this.confidence=clamp01(confidence);this.openedAt=openedAt;this.lastChangedAt=lastChangedAt;
            this.lastEvidenceAt=lastEvidenceAt;this.sourceSemanticEventId=sourceSemanticEventId;
            this.terminalAt=terminalAt;this.revision=Math.max(1,revision);
        }

        public boolean isOpen(){return OPEN.equals(state);}
        public boolean isOverdue(long nowAt){return isOpen()&&deadlineAt>0&&nowAt>0&&nowAt>deadlineAt;}
    }

    public static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_commitments("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "commitment_key TEXT NOT NULL UNIQUE,"+
                "situation_id INTEGER NOT NULL,"+
                "link_key TEXT NOT NULL,"+
                "subject TEXT,"+
                "summary TEXT,"+
                "state TEXT NOT NULL,"+
                "deadline_at INTEGER NOT NULL DEFAULT 0,"+
                "deadline_has_time INTEGER NOT NULL DEFAULT 0,"+
                "deadline_expression TEXT,"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "opened_at INTEGER NOT NULL,"+
                "last_changed_at INTEGER NOT NULL,"+
                "last_evidence_at INTEGER NOT NULL,"+
                "source_semantic_event_id INTEGER NOT NULL DEFAULT 0,"+
                "terminal_at INTEGER NOT NULL DEFAULT 0,"+
                "revision INTEGER NOT NULL DEFAULT 1,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_commitments_state_deadline ON ue_commitments(state,deadline_at,last_evidence_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_commitments_situation ON ue_commitments(situation_id,last_evidence_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS ue_commitment_transitions("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "commitment_id INTEGER NOT NULL,"+
                "semantic_event_id INTEGER NOT NULL DEFAULT 0,"+
                "from_state TEXT,"+
                "to_state TEXT NOT NULL,"+
                "kind TEXT NOT NULL,"+
                "old_deadline_at INTEGER NOT NULL DEFAULT 0,"+
                "new_deadline_at INTEGER NOT NULL DEFAULT 0,"+
                "evidence_text TEXT,"+
                "occurred_at INTEGER NOT NULL,"+
                "created_at INTEGER NOT NULL,"+
                "UNIQUE(commitment_id,semantic_event_id,kind))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_commitment_transitions_commitment ON ue_commitment_transitions(commitment_id,occurred_at ASC,id ASC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS ue_commitment_semantic_evaluations("+
                "semantic_event_id INTEGER PRIMARY KEY,"+
                "situation_id INTEGER NOT NULL DEFAULT 0,"+
                "classification TEXT NOT NULL,"+
                "reason TEXT,"+
                "processed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_commitment_eval_situation ON ue_commitment_semantic_evaluations(situation_id,processed_at DESC)");
    }

    /** Bounded idempotent backfill over semantic evidence already attached to situations. */
    public static int rebuild(SQLiteDatabase db,int limit) {
        ensure(db);
        int cap=Math.max(1,Math.min(500,limit));
        String sql="SELECT e.id,m.situation_id,COALESCE(ss.correlation_key,''),COALESCE(e.semantic_type,''),"+
                "COALESCE(e.intent,''),COALESCE(e.subject,''),COALESCE(e.summary,''),COALESCE(e.confidence,0),"+
                "COALESCE(e.occurred_at,0),COALESCE(u.state,'open') "+
                "FROM ue_semantic_events e "+
                "JOIN ue_situation_members_v2 m ON m.semantic_event_id=e.id "+
                "LEFT JOIN ue_situation_state_v2 ss ON ss.situation_id=m.situation_id "+
                "LEFT JOIN ue_situations u ON u.id=m.situation_id "+
                "WHERE e.semantic_state='complete' AND e.superseded_by=0 "+
                "AND NOT EXISTS(SELECT 1 FROM ue_commitment_semantic_evaluations x WHERE x.semantic_event_id=e.id) "+
                "GROUP BY e.id ORDER BY e.id ASC LIMIT ?";
        Cursor c=db.rawQuery(sql,new String[]{String.valueOf(cap)});
        int done=0;
        try {
            while(c.moveToNext()) {
                observeSemantic(db,c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),
                        c.getString(5),c.getString(6),c.getDouble(7),c.getLong(8),c.getString(9));
                done++;
            }
        } finally { c.close(); }
        return done;
    }

    public static boolean hasBacklog(SQLiteDatabase db) {
        ensure(db);
        Cursor c=db.rawQuery("SELECT 1 FROM ue_semantic_events e JOIN ue_situation_members_v2 m ON m.semantic_event_id=e.id WHERE e.semantic_state='complete' AND e.superseded_by=0 AND NOT EXISTS(SELECT 1 FROM ue_commitment_semantic_evaluations x WHERE x.semantic_event_id=e.id) LIMIT 1",null);
        try{return c.moveToFirst();}finally{c.close();}
    }

    /** Package-visible for deterministic tests and the stateful rebuild path. */
    static Record observeSemantic(SQLiteDatabase db,long semanticEventId,long situationId,String linkKey,
                                  String type,String intent,String subject,String summary,double confidence,
                                  long occurredAt,String situationState) {
        ensure(db);
        long now=System.currentTimeMillis();
        long when=occurredAt>0?occurredAt:now;
        String key="situation|"+situationId;
        String all=norm(type+" "+intent+" "+subject+" "+summary);
        Record old=findByKey(db,key);

        String terminal=terminalState(type,intent,all);
        boolean commitmentLike=isCommitmentLike(type,intent,all);
        boolean rescheduleHint=isReschedule(all);
        TemporalResolver.Resolution resolution=TemporalResolver.resolveExpression(subject+" "+summary,when);
        long parsedDeadline=resolution==null?0L:resolution.when;
        boolean parsedHasTime=resolution!=null&&resolution.hasTime;
        String deadlineExpression=resolution==null?"":n(subject+" "+summary);

        if(old==null&&!commitmentLike&&terminal==null) {
            evaluation(db,semanticEventId,situationId,"none","no commitment or lifecycle evidence",now);
            return null;
        }

        if(old!=null&&old.lastEvidenceAt>0&&when<old.lastEvidenceAt) {
            evaluation(db,semanticEventId,situationId,"late_ignored","older evidence cannot regress current commitment truth",now);
            return old;
        }

        String newState=old==null?OPEN:old.state;
        String transitionKind=old==null?"OPENED":"UPDATED";
        long oldDeadline=old==null?0L:old.deadlineAt;
        long newDeadline=oldDeadline;
        boolean newHasTime=old!=null&&old.deadlineHasTime;
        String newDeadlineExpression=old==null?"":old.deadlineExpression;

        if(terminal!=null) {
            newState=terminal;
            transitionKind=terminal.toUpperCase(Locale.ROOT);
            newDeadline=0L;
            newHasTime=false;
            newDeadlineExpression="";
        } else if(parsedDeadline>0) {
            newDeadline=parsedDeadline;
            newHasTime=parsedHasTime;
            newDeadlineExpression=deadlineExpression;
            if(old!=null&&oldDeadline>0&&oldDeadline!=parsedDeadline) transitionKind="RESCHEDULED";
            else if(old!=null&&rescheduleHint) transitionKind="RESCHEDULED";
            else if(old!=null&&oldDeadline==0) transitionKind="DEADLINE_SET";
        } else if(rescheduleHint&&old!=null) {
            // A reschedule claim without a resolvable target is evidence, but must not erase a known date.
            transitionKind="RESCHEDULE_PENDING_DATE";
        }

        if(old!=null&&!old.isOpen()&&terminal==null) {
            evaluation(db,semanticEventId,situationId,"terminal_ignored","non-terminal evidence cannot reopen a terminal commitment",now);
            return old;
        }

        if(isResolvedSituation(situationState)&&terminal==null&&old!=null&&old.isOpen()) {
            newState=FULFILLED;
            transitionKind="FULFILLED";
            newDeadline=0L;
            newHasTime=false;
            newDeadlineExpression="";
        }

        ContentValues v=new ContentValues();
        v.put("commitment_key",key);
        v.put("situation_id",situationId);
        v.put("link_key",n(linkKey).isEmpty()?key:n(linkKey));
        v.put("subject",n(subject).isEmpty()&&old!=null?old.subject:n(subject));
        v.put("summary",n(summary).isEmpty()&&old!=null?old.summary:n(summary));
        v.put("state",newState);
        v.put("deadline_at",newDeadline);
        v.put("deadline_has_time",newHasTime?1:0);
        v.put("deadline_expression",newDeadlineExpression);
        v.put("confidence",old==null?clamp01(confidence):weighted(old.confidence,clamp01(confidence)));
        v.put("last_evidence_at",when);
        v.put("source_semantic_event_id",semanticEventId);
        v.put("updated_at",now);
        if(old==null) {
            v.put("opened_at",when);
            v.put("last_changed_at",when);
            v.put("terminal_at",isTerminal(newState)?when:0L);
            v.put("revision",1);
            v.put("created_at",now);
            long id=db.insertOrThrow("ue_commitments",null,v);
            transition(db,id,semanticEventId,"",newState,transitionKind,0L,newDeadline,summary,when,now);
        } else {
            boolean changed=!old.state.equals(newState)||oldDeadline!=newDeadline||!old.summary.equals(n(summary))||!old.subject.equals(n(subject));
            v.put("last_changed_at",changed?when:old.lastChangedAt);
            v.put("terminal_at",isTerminal(newState)?when:0L);
            v.put("revision",changed?old.revision+1:old.revision);
            db.update("ue_commitments",v,"id=?",new String[]{String.valueOf(old.id)});
            transition(db,old.id,semanticEventId,old.state,newState,transitionKind,oldDeadline,newDeadline,summary,when,now);
        }

        evaluation(db,semanticEventId,situationId,transitionKind.toLowerCase(Locale.ROOT),
                "commitment lifecycle evaluated",now);
        return findByKey(db,key);
    }

    public static List<Record> loadOpen(SQLiteDatabase db,int limit) {
        ensure(db);
        Cursor c=db.rawQuery("SELECT id,commitment_key,situation_id,link_key,subject,summary,state,deadline_at,deadline_has_time,deadline_expression,confidence,opened_at,last_changed_at,last_evidence_at,source_semantic_event_id,terminal_at,revision FROM ue_commitments WHERE state='open' ORDER BY CASE WHEN deadline_at>0 THEN 0 ELSE 1 END,deadline_at ASC,last_evidence_at DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,Math.min(500,limit)))});
        ArrayList<Record> out=new ArrayList<>();
        try{while(c.moveToNext())out.add(from(c));}finally{c.close();}
        return Collections.unmodifiableList(out);
    }

    public static List<Record> loadAll(SQLiteDatabase db,int limit) {
        ensure(db);
        Cursor c=db.rawQuery("SELECT id,commitment_key,situation_id,link_key,subject,summary,state,deadline_at,deadline_has_time,deadline_expression,confidence,opened_at,last_changed_at,last_evidence_at,source_semantic_event_id,terminal_at,revision FROM ue_commitments ORDER BY last_evidence_at DESC,id DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,Math.min(500,limit)))});
        ArrayList<Record> out=new ArrayList<>();
        try{while(c.moveToNext())out.add(from(c));}finally{c.close();}
        return Collections.unmodifiableList(out);
    }

    public static Record findForSituation(SQLiteDatabase db,long situationId) {
        ensure(db);Cursor c=db.rawQuery("SELECT id,commitment_key,situation_id,link_key,subject,summary,state,deadline_at,deadline_has_time,deadline_expression,confidence,opened_at,last_changed_at,last_evidence_at,source_semantic_event_id,terminal_at,revision FROM ue_commitments WHERE situation_id=? ORDER BY last_evidence_at DESC,id DESC LIMIT 1",new String[]{String.valueOf(situationId)});
        try{return c.moveToFirst()?from(c):null;}finally{c.close();}
    }

    public static long countOpen(SQLiteDatabase db){ensure(db);return scalar(db,"SELECT COUNT(*) FROM ue_commitments WHERE state='open'");}
    public static long countOverdue(SQLiteDatabase db,long nowAt){ensure(db);return nowAt<=0?0:scalar(db,"SELECT COUNT(*) FROM ue_commitments WHERE state='open' AND deadline_at>0 AND deadline_at<"+nowAt);}

    private static Record findByKey(SQLiteDatabase db,String key) {
        Cursor c=db.rawQuery("SELECT id,commitment_key,situation_id,link_key,subject,summary,state,deadline_at,deadline_has_time,deadline_expression,confidence,opened_at,last_changed_at,last_evidence_at,source_semantic_event_id,terminal_at,revision FROM ue_commitments WHERE commitment_key=? LIMIT 1",new String[]{key});
        try{return c.moveToFirst()?from(c):null;}finally{c.close();}
    }

    private static Record from(Cursor c) {
        return new Record(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),
                c.getString(6),c.getLong(7),c.getInt(8)==1,c.getString(9),c.getDouble(10),c.getLong(11),
                c.getLong(12),c.getLong(13),c.getLong(14),c.getLong(15),c.getInt(16));
    }

    private static void transition(SQLiteDatabase db,long commitmentId,long semanticEventId,String from,String to,
                                   String kind,long oldDeadline,long newDeadline,String evidence,long when,long now) {
        ContentValues t=new ContentValues();t.put("commitment_id",commitmentId);t.put("semantic_event_id",semanticEventId);
        t.put("from_state",n(from));t.put("to_state",n(to));t.put("kind",n(kind));t.put("old_deadline_at",oldDeadline);
        t.put("new_deadline_at",newDeadline);t.put("evidence_text",n(evidence));t.put("occurred_at",when);t.put("created_at",now);
        db.insertWithOnConflict("ue_commitment_transitions",null,t,SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void evaluation(SQLiteDatabase db,long semanticEventId,long situationId,String classification,String reason,long now) {
        ContentValues v=new ContentValues();v.put("semantic_event_id",semanticEventId);v.put("situation_id",situationId);
        v.put("classification",n(classification));v.put("reason",n(reason));v.put("processed_at",now);
        db.insertWithOnConflict("ue_commitment_semantic_evaluations",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String terminalState(String type,String intent,String all) {
        String ti=norm(type+" "+intent);
        if(any(ti,"withdrawn","cancelled","canceled")||any(all,"cancelled","canceled","withdrawn","no longer needed","disregard","ignore this request","اتلغى","اتلغت","ملغي","ملغى","إلغاء","الغاء","مش محتاج"))return WITHDRAWN;
        if(any(ti,"expired")||any(all,"expired","deadline passed","window closed","offer expired","انتهت الصلاحية","انتهى الموعد","انتهت المهلة"))return EXPIRED;
        if(any(ti,"superseded","replaced")||any(all,"superseded by","replaced by","use the latest request","استبدل الطلب","الطلب الجديد بديل"))return SUPERSEDED;
        if(any(ti,"fulfilled","completed","resolved")||any(all,"completed","fulfilled","quotation sent","file sent","email sent","submitted successfully","payment completed","paid successfully","delivered successfully","تم الإرسال","تم الارسال","اتبعت","اتبعث","تم الدفع","تم التسليم","اكتمل الطلب","خلصت المهمة"))return FULFILLED;
        return null;
    }

    private static boolean isCommitmentLike(String type,String intent,String all) {
        String ti=norm(type+" "+intent);
        return any(ti,"commitment","action_request","action required","required_response","required response","follow_up","follow up","pending_response","pending response","waiting","task","request","command","send","reply","respond","confirm","submit","pay","deliver","promise","awaiting","pending") ||
                any(all,"please send","please reply","please confirm","need to send","need to reply","need to submit","need to pay","due today","due tomorrow","follow up","waiting for","مطلوب","لازم ابعت","لازم أبعث","لازم ارد","لازم أرد","محتاج ابعت","محتاج أبعث","في انتظار","مستني رد","ميعاد التسليم","موعد التسليم");
    }

    private static boolean isReschedule(String all) {
        return any(all,"rescheduled","postponed","moved to","delayed until","new deadline","new due date","اتأجل","اتأجلت","تأجل","تأجلت","ميعاد جديد","موعد جديد","اتنقل ل","تم التأجيل");
    }

    private static boolean isResolvedSituation(String state) {
        String s=norm(state);return s.equals("resolved")||s.equals("closed")||s.equals("completed")||s.equals("dismissed");
    }
    private static boolean isTerminal(String state){return FULFILLED.equals(state)||WITHDRAWN.equals(state)||EXPIRED.equals(state)||SUPERSEDED.equals(state);}
    private static boolean any(String s,String... xs){for(String x:xs)if(s.contains(norm(x)))return true;return false;}
    private static String norm(String s){return n(s).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private static String n(String s){return s==null?"":s.trim();}
    private static double clamp01(double x){if(Double.isNaN(x))return 0.0;return Math.max(0.0,Math.min(1.0,x));}
    private static double weighted(double a,double b){return clamp01((a+b)/2.0);}
    private static long scalar(SQLiteDatabase db,String sql){Cursor c=db.rawQuery(sql,null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
}
