package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CommitmentLifecyclePrecisionTest {
    private SQLiteDatabase db;

    @Before public void before(){
        db=SQLiteDatabase.create(null);
        CommitmentLifecycleStore.ensure(db);
    }

    @After public void after(){if(db!=null)db.close();}

    @Test public void terminalLookingGenericEvidenceCannotInventCommitmentFromNothing(){
        CommitmentLifecycleStore.Record record=CommitmentLifecycleStore.observeSemantic(
                db,99,77,"generic|77","status_update","resolved","Package",
                "Delivery completed",.97,System.currentTimeMillis(),"open");
        assertNull(record);
        assertNull(CommitmentLifecycleStore.findForSituation(db,77));
        Cursor c=db.rawQuery("SELECT classification FROM ue_commitment_semantic_evaluations WHERE semantic_event_id=99",null);
        try{
            assertTrue(c.moveToFirst());
            assertEquals("none",c.getString(0));
        }finally{c.close();}
    }
}
