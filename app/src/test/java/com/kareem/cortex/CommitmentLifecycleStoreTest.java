package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CommitmentLifecycleStoreTest {
    private static final long HOUR=60L*60L*1000L;
    private static final long DAY=24L*HOUR;
    private static final long ANCHOR=1789020000000L;
    private SQLiteDatabase db;
    private TimeZone oldZone;

    @Before public void before(){
        oldZone=TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        db=SQLiteDatabase.create(null);
        UniversalEventStore.ensure(db);
        StatefulMeaningStore.ensure(db);
        CommitmentLifecycleStore.ensure(db);
    }

    @After public void after(){
        if(db!=null)db.close();
        TimeZone.setDefault(oldZone);
    }

    @Test public void pureTemporalResolverUsesSemanticEventTimeAsAnchor(){
        TemporalResolver.Resolution english=TemporalResolver.resolveExpression("Send quotation tomorrow at 10:00",ANCHOR);
        TemporalResolver.Resolution arabic=TemporalResolver.resolveExpression("ابعت العرض بكرة الساعة 3 م",ANCHOR);
        assertNotNull(english);
        assertNotNull(arabic);
        assertTrue(english.when>ANCHOR);
        assertTrue(arabic.when>ANCHOR);
        assertTrue(english.hasTime);
        assertTrue(arabic.hasTime);
        assertEquals(10,hourUtc(english.when));
        assertEquals(15,hourUtc(arabic.when));
        assertEquals(dayUtc(ANCHOR)+1,dayUtc(english.when));
        assertEquals(dayUtc(ANCHOR)+1,dayUtc(arabic.when));
    }

    @Test public void openCommitmentPersistsRealDeadlineThenReschedulesInPlace(){
        CommitmentLifecycleStore.Record first=CommitmentLifecycleStore.observeSemantic(
                db,1,42,"quotation|42","commitment","waiting","Quotation",
                "Send quotation tomorrow at 10:00",.94,ANCHOR,"open");
        assertNotNull(first);
        assertTrue(first.isOpen());
        assertTrue(first.deadlineAt>ANCHOR);
        long firstDeadline=first.deadlineAt;

        CommitmentLifecycleStore.Record moved=CommitmentLifecycleStore.observeSemantic(
                db,2,42,"quotation|42","commitment","waiting","Quotation",
                "Quotation rescheduled to Friday at 15:00",.96,ANCHOR+HOUR,"open");
        assertNotNull(moved);
        assertEquals(first.id,moved.id);
        assertTrue(moved.isOpen());
        assertTrue(moved.deadlineAt>0);
        assertTrue(moved.deadlineAt!=firstDeadline);
        assertEquals(1,count("SELECT COUNT(*) FROM ue_commitments"));
        assertEquals(1,count("SELECT COUNT(*) FROM ue_commitment_transitions WHERE kind='RESCHEDULED'"));
    }

    @Test public void fulfilmentClosesCommitmentAndRemovesItFromOpenWorldInput(){
        CommitmentLifecycleStore.observeSemantic(db,1,7,"task|7","commitment","waiting","PO",
                "Need to send PO tomorrow",.95,ANCHOR,"open");
        CommitmentLifecycleStore.Record done=CommitmentLifecycleStore.observeSemantic(db,2,7,"task|7",
                "commitment_completed","completed","PO","PO sent",.98,ANCHOR+2*HOUR,"open");
        assertNotNull(done);
        assertEquals(CommitmentLifecycleStore.FULFILLED,done.state);
        assertEquals(0L,done.deadlineAt);
        assertTrue(done.terminalAt>0);
        assertTrue(CommitmentLifecycleStore.loadOpen(db,20).isEmpty());
        assertEquals(1,count("SELECT COUNT(*) FROM ue_commitment_transitions WHERE kind='FULFILLED'"));
    }

    @Test public void explicitWithdrawalAndExpiryAreTerminalButOverdueAloneIsNot(){
        CommitmentLifecycleStore.Record open=CommitmentLifecycleStore.observeSemantic(db,1,8,"task|8",
                "commitment","waiting","RSVP","Need to reply today",.93,ANCHOR,"open");
        assertNotNull(open);
        assertTrue(open.isOverdue(ANCHOR+2*DAY));
        assertEquals(CommitmentLifecycleStore.OPEN,CommitmentLifecycleStore.findForSituation(db,8).state);

        CommitmentLifecycleStore.Record withdrawn=CommitmentLifecycleStore.observeSemantic(db,2,8,"task|8",
                "commitment","cancelled","RSVP","Request cancelled; no longer needed",.96,ANCHOR+2*DAY,"open");
        assertEquals(CommitmentLifecycleStore.WITHDRAWN,withdrawn.state);

        CommitmentLifecycleStore.observeSemantic(db,3,9,"offer|9","commitment","waiting","Offer",
                "Offer response due tomorrow",.92,ANCHOR,"open");
        CommitmentLifecycleStore.Record expired=CommitmentLifecycleStore.observeSemantic(db,4,9,"offer|9",
                "commitment","expired","Offer","Offer expired; response window closed",.98,ANCHOR+3*DAY,"open");
        assertEquals(CommitmentLifecycleStore.EXPIRED,expired.state);
    }

    @Test public void lateOldEvidenceCannotReopenOrRestoreAnOlderDeadline(){
        CommitmentLifecycleStore.observeSemantic(db,1,10,"quote|10","commitment","waiting","Quote",
                "Send quote tomorrow",.93,ANCHOR,"open");
        CommitmentLifecycleStore.Record done=CommitmentLifecycleStore.observeSemantic(db,3,10,"quote|10",
                "commitment_completed","completed","Quote","Quote sent",.98,ANCHOR+3*DAY,"open");
        assertEquals(CommitmentLifecycleStore.FULFILLED,done.state);

        CommitmentLifecycleStore.Record afterLate=CommitmentLifecycleStore.observeSemantic(db,2,10,"quote|10",
                "commitment","waiting","Quote","Still need to send quote tomorrow",.95,ANCHOR+DAY,"open");
        assertEquals(CommitmentLifecycleStore.FULFILLED,afterLate.state);
        assertEquals(0L,afterLate.deadlineAt);
        assertEquals(1,count("SELECT COUNT(*) FROM ue_commitment_semantic_evaluations WHERE semantic_event_id=2 AND classification='late_ignored'"));
    }

    @Test public void rebuildIsIdempotentAndMarksNonCommitmentEvidenceEvaluated(){
        long commitmentEvent=semantic("commitment","waiting","Quotation","Send quotation tomorrow",.94,ANCHOR);
        long situation=StatefulMeaningStore.correlate(db,commitmentEvent,0,"mail","commitment","Quotation","Send quotation tomorrow",.94,ANCHOR);
        long weatherEvent=semantic("weather_event","weather","New Cairo","24 degrees and clear",.97,ANCHOR+1);
        StatefulMeaningStore.correlate(db,weatherEvent,0,"weather","weather_event","New Cairo","24 degrees and clear",.97,ANCHOR+1);

        assertEquals(2,CommitmentLifecycleStore.rebuild(db,20));
        assertEquals(0,CommitmentLifecycleStore.rebuild(db,20));
        assertEquals(2,count("SELECT COUNT(*) FROM ue_commitment_semantic_evaluations"));
        assertEquals(1,count("SELECT COUNT(*) FROM ue_commitments"));
        CommitmentLifecycleStore.Record record=CommitmentLifecycleStore.findForSituation(db,situation);
        assertNotNull(record);
        assertTrue(record.deadlineAt>ANCHOR);
    }

    @Test public void loadOpenReturnsOnlyLiveCommitmentsWithPersistedDeadlines(){
        CommitmentLifecycleStore.observeSemantic(db,1,20,"a|20","commitment","waiting","A","Send A tomorrow",.91,ANCHOR,"open");
        CommitmentLifecycleStore.observeSemantic(db,2,21,"b|21","commitment","waiting","B","Send B next week",.92,ANCHOR,"open");
        CommitmentLifecycleStore.observeSemantic(db,3,21,"b|21","commitment_completed","completed","B","B sent",.98,ANCHOR+HOUR,"open");
        List<CommitmentLifecycleStore.Record> open=CommitmentLifecycleStore.loadOpen(db,20);
        assertEquals(1,open.size());
        assertEquals(20L,open.get(0).situationId);
        assertTrue(open.get(0).deadlineAt>0);
        assertFalse(open.get(0).deadlineExpression.isEmpty());
    }

    private long semantic(String type,String intent,String subject,String summary,double confidence,long at){
        long raw=UniversalEventStore.appendRaw(db,"notification","test","obs-"+at+"-"+type,
                "posted","message","conversation_notification",subject,summary,new JSONObject(),at);
        long stream=UniversalEventStore.upsertStream(db,"notification","stream-"+at+"-"+type,
                "active","h-"+at,subject,summary,"message","conversation_notification",at,true,new JSONObject());
        return UniversalEventStore.insertSemantic(db,raw,stream,1,type,intent,subject,summary,confidence,
                "complete",true,"test","commitment test",at);
    }

    private long count(String sql){Cursor c=db.rawQuery(sql,null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private int hourUtc(long ms){java.util.Calendar c=java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));c.setTimeInMillis(ms);return c.get(java.util.Calendar.HOUR_OF_DAY);}
    private long dayUtc(long ms){java.util.Calendar c=java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));c.setTimeInMillis(ms);c.set(java.util.Calendar.HOUR_OF_DAY,12);c.set(java.util.Calendar.MINUTE,0);c.set(java.util.Calendar.SECOND,0);c.set(java.util.Calendar.MILLISECOND,0);return c.getTimeInMillis()/DAY;}
}
