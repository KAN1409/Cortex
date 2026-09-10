package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Locale;

/** Recovery-only deterministic drain for semantic events parked while native inference is quarantined. */
public final class DeterministicSemanticRecovery {
    public static final String VERSION = "deterministic_semantic_recovery_003";
    private DeterministicSemanticRecovery() {}

    public static int recover(VaultDb vault, int maxRows) {
        if (vault == null) return 0;
        SQLiteDatabase db = vault.getWritableDatabase();
        UniversalEventStore.ensure(db);
        int limit = Math.max(1, Math.min(400, maxRows));
        Cursor c = db.rawQuery(
                "SELECT e.id,r.source_key,r.technical_type,r.title,r.body,r.occurred_at " +
                "FROM ue_semantic_events e JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                "WHERE e.semantic_state='waiting' AND e.superseded_by=0 " +
                "ORDER BY e.occurred_at ASC LIMIT ?",
                new String[]{String.valueOf(limit)});
        int done = 0;
        try {
            while (c.moveToNext()) {
                apply(db,c.getLong(0),nz(c.getString(1)),nz(c.getString(2)),nz(c.getString(3)),nz(c.getString(4)),false);
                done++;
            }
        } finally { c.close(); }
        // Repair false WAITING items created by older recovery logic without touching model-authored semantics.
        repairLegacyDeterministicCommitments(db,Math.max(20,Math.min(200,limit)));
        return done;
    }

    private static void repairLegacyDeterministicCommitments(SQLiteDatabase db,int limit){
        Cursor c=db.rawQuery(
                "SELECT e.id,r.source_key,r.technical_type,r.title,r.body FROM ue_semantic_events e " +
                "JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                "WHERE e.superseded_by=0 AND e.semantic_type='commitment' " +
                "AND e.model_route LIKE 'deterministic_semantic_recovery_%' ORDER BY e.occurred_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try{
            while(c.moveToNext()){
                long id=c.getLong(0);String source=nz(c.getString(1)),tech=nz(c.getString(2)),title=nz(c.getString(3)),body=nz(c.getString(4));
                Classification x=classify(tech,title,body);
                if(!"commitment".equals(x.type))apply(db,id,source,tech,title,body,true);
            }
        }finally{c.close();}
    }

    private static void apply(SQLiteDatabase db,long eventId,String source,String technical,String title,String body,boolean repair){
        Classification x=classify(technical,title,body);
        ContentValues v=new ContentValues();
        v.put("semantic_type",x.type);v.put("intent",x.intent);
        v.put("subject",title.isEmpty()?source:title);
        v.put("summary",CanonicalPresentation.cleanBody(body.isEmpty()?title:body));
        v.put("confidence",x.confidence);v.put("semantic_state","complete");
        v.put("model_route",VERSION);v.put("reason",x.reason);
        db.update("ue_semantic_events",v,"id=?",new String[]{String.valueOf(eventId)});
        db.delete("ue_attention_items","semantic_event_id=?",new String[]{String.valueOf(eventId)});
        if(x.attentionKind!=null){
            String atTitle=CanonicalPresentation.cleanTitle("notification",x.type,title.isEmpty()?source:title,title);
            UniversalEventStore.attention(db,eventId,0,x.attentionKind,atTitle,
                    CanonicalPresentation.cleanBody(body.isEmpty()?title:body),x.priority,x.confidence,source,x.reason);
        }
        UniversalEventStore.stage(db,0,eventId,"UNDERSTANDING","complete",VERSION,
                repair?"Repaired deterministic semantic classification":"Recovered safely without native semantic runtime","");
    }

    public static boolean hasBacklog(VaultDb vault) {
        if (vault == null) return false;
        Cursor c=vault.getReadableDatabase().rawQuery(
                "SELECT 1 FROM ue_semantic_events WHERE semantic_state='waiting' AND superseded_by=0 LIMIT 1",null);
        boolean yes=c.moveToFirst();c.close();return yes;
    }

    static Classification classify(String technical, String title, String body) {
        String text=(nz(title)+"\n"+nz(body)).toLowerCase(Locale.ROOT);
        String tech=nz(technical).toLowerCase(Locale.ROOT);
        if(has(text,"security alert","password","compromised","breach","suspicious sign-in","محاولة تسجيل","كلمة المرور","اختراق","أمان"))
            return new Classification("security_alert","security","ACTION",94,.93,"deterministic security signal during native-model recovery");
        if(has(text,"payment due","payment failed","card declined","invoice due","overdue","دفع","مستحق","فاتورة","رفضت البطاقة"))
            return new Classification("payment_action","payment","ACTION",88,.89,"deterministic payment obligation during native-model recovery");
        if(has(text,"please send","send me","need you to","can you","could you","لو سمحت","محتاج منك","محتاجك","ابعت","ابعث","هات "))
            return new Classification("action_request","request","ACTION",90,.91,"explicit request detected deterministically");
        if(has(text,"we decided","decided that","agreed that","final decision","قررنا","اتفقنا","تم الاتفاق","القرار"))
            return new Classification("decision","decision","DECISION",76,.87,"explicit decision detected deterministically");

        // Commitment must describe a real actor commitment, not a generic future announcement.
        // Phrases such as "systems will be updated" / "سوف يتم تحديث الأنظمة" are information.
        boolean personalCommitment=has(text,"i will","i'll ","i promise","promise to","by tomorrow i","هبعت","هعمل","هكلم","هراجع","هخلص","هرد","هجهز");
        boolean conversationalGroupCommitment=tech.contains("conversation")&&has(text,"we will","we'll ","هنعمل","هنبعت","هنراجع","هنخلص");
        if(personalCommitment||conversationalGroupCommitment)
            return new Classification("commitment","commitment","WAITING",72,.86,"explicit actor commitment detected deterministically");

        if(has(text,"deadline","due today","due tomorrow","reminder","appointment","موعد","ميعاد","النهاردة","بكرة","تذكير"))
            return new Classification("time_sensitive_event","deadline","ACTION",78,.84,"time-sensitive event detected deterministically");
        if(tech.contains("conversation"))return new Classification("conversation_message","message",null,32,.80,"ordinary conversation retained as informational context");
        if(tech.contains("email"))return new Classification("email_event","email",null,34,.79,"ordinary email retained as informational context");
        return new Classification("notification_event","notification",null,25,.76,"meaningful notification retained as informational context during native-model recovery");
    }

    private static boolean has(String s,String...xs){for(String x:xs)if(s.contains(x))return true;return false;}
    private static String nz(String s){return s==null?"":s.trim();}
    static final class Classification {
        final String type,intent,attentionKind,reason;final int priority;final double confidence;
        Classification(String type,String intent,String attentionKind,int priority,double confidence,String reason){this.type=type;this.intent=intent;this.attentionKind=attentionKind;this.priority=priority;this.confidence=confidence;this.reason=reason;}
    }
}
