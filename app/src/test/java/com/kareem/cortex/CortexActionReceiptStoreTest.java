package com.kareem.cortex;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class CortexActionReceiptStoreTest {
    private Context context;
    private VaultDb db;

    @Before public void setUp(){
        context=ApplicationProvider.getApplicationContext();
        context.deleteDatabase("cortex.db");
        db=new VaultDb(context);
    }

    @After public void tearDown(){
        if(db!=null)try{db.close();}catch(Throwable ignored){}
        if(context!=null)context.deleteDatabase("cortex.db");
    }

    @Test public void additiveReceiptSchemaPreservesExistingKnowledge(){
        long memory=db.insert("TEXT","test","Keep this","legacy value","Notes","","","receipt-legacy","{}");
        assertTrue(memory>0);
        CortexActionReceiptStore.ensure(db);
        assertNotNull(db.getById(memory));
        long receipt=CortexActionReceiptStore.begin(db,"req-1","task.create",memory,"CognitiveStore.addDerived","input-hash","idem-1",true,"{}");
        assertTrue(receipt>0);
        assertNotNull(db.getById(memory));
    }

    @Test public void handoffIsPersistedAsNotVerified(){
        long id=CortexActionReceiptStore.begin(db,"req-handoff","email.prepare",0,"CortexActionExecutor.emailDraft","hash","idem-handoff",false,"{}");
        assertTrue(CortexActionReceiptStore.markValidated(db,id));
        assertTrue(CortexActionReceiptStore.markApproved(db,id));
        assertTrue(CortexActionReceiptStore.markDispatching(db,id));
        assertTrue(CortexActionReceiptStore.markHandedOff(db,id,"Email draft opened","brain_action:9"));
        CortexActionReceiptStore.Receipt r=CortexActionReceiptStore.get(db,id);
        assertNotNull(r);
        assertEquals(CortexActionReceiptStore.HANDED_OFF,r.executionStatus);
        assertEquals(CortexActionReceiptStore.V_NOT_VERIFIED,r.verificationStatus);
        assertEquals(0,r.verifiedAt);
        assertFalse(CortexActionReceiptStore.VERIFIED.equals(r.executionStatus));
    }

    @Test public void verifiedStateRequiresEvidenceAndVerificationMethod(){
        long id=CortexActionReceiptStore.begin(db,"req-local","task.create",0,"CognitiveStore.addDerived","hash","idem-local",true,"{}");
        assertTrue(CortexActionReceiptStore.markValidated(db,id));
        assertTrue(CortexActionReceiptStore.markApproved(db,id));
        assertTrue(CortexActionReceiptStore.markDispatching(db,id));
        assertTrue(CortexActionReceiptStore.markDispatched(db,id,"derived written"));
        assertTrue(CortexActionReceiptStore.markVerifying(db,id,"sqlite_read_back"));
        assertFalse(CortexActionReceiptStore.markVerified(db,id,"bad","","derived:1","hash","sqlite_read_back"));
        assertEquals(CortexActionReceiptStore.VERIFYING,CortexActionReceiptStore.get(db,id).executionStatus);
        assertTrue(CortexActionReceiptStore.markVerified(db,id,"verified","local_sqlite_read_back","derived:1","evidence-hash","sqlite_read_back"));
        CortexActionReceiptStore.Receipt r=CortexActionReceiptStore.get(db,id);
        assertEquals(CortexActionReceiptStore.VERIFIED,r.executionStatus);
        assertEquals(CortexActionReceiptStore.V_VERIFIED,r.verificationStatus);
        assertTrue(r.verifiedAt>0);
    }

    @Test public void sameIdempotencyKeyReusesReceipt(){
        long a=CortexActionReceiptStore.begin(db,"req-a","task.create",0,"CognitiveStore.addDerived","hash","stable-key",true,"{}");
        long b=CortexActionReceiptStore.begin(db,"req-b","task.create",0,"CognitiveStore.addDerived","hash","stable-key",true,"{}");
        assertEquals(a,b);
    }

    @Test public void staleDispatchCanBeMarkedUnknownBeforeCreatingRetryReceipt(){
        long first=CortexActionReceiptStore.begin(db,"req-retry","email.prepare",0,"CortexActionExecutor.emailDraft","hash","idem-retry",false,"{}");
        assertTrue(CortexActionReceiptStore.markValidated(db,first));
        assertTrue(CortexActionReceiptStore.markApproved(db,first));
        assertTrue(CortexActionReceiptStore.markDispatching(db,first));
        CortexActionReceiptStore.Receipt dispatching=CortexActionReceiptStore.get(db,first);
        assertEquals(CortexActionReceiptStore.DISPATCHING,dispatching.executionStatus);
        assertTrue(dispatching.updatedAt>0);

        assertTrue(CortexActionReceiptStore.markUnknown(db,first,"stale external dispatch"));
        CortexActionReceiptStore.Receipt unknown=CortexActionReceiptStore.get(db,first);
        assertEquals(CortexActionReceiptStore.UNKNOWN,unknown.executionStatus);
        assertEquals(CortexActionReceiptStore.V_NOT_VERIFIED,unknown.verificationStatus);

        long retry=CortexActionReceiptStore.begin(db,"req-retry","email.prepare",0,"CortexActionExecutor.emailDraft","hash","idem-retry-2",false,"{\\\"user_confirmed_retry\\\":true}");
        assertTrue(retry>0);
        assertNotEquals(first,retry);
        assertEquals(CortexActionReceiptStore.CREATED,CortexActionReceiptStore.get(db,retry).executionStatus);
    }

    @Test public void cancelledSelectionCannotBecomeVerified(){
        long id=CortexActionReceiptStore.begin(db,"req-project","project.link",4,"CognitiveStore.linkChecked","hash","idem-project",true,"{}");
        assertTrue(CortexActionReceiptStore.markValidated(db,id));
        assertTrue(CortexActionReceiptStore.markApproved(db,id));
        assertTrue(CortexActionReceiptStore.markDispatching(db,id));
        assertTrue(CortexActionReceiptStore.markCancelled(db,id,"User closed project chooser"));
        assertFalse(CortexActionReceiptStore.markVerified(db,id,"wrong","local_sqlite_read_back","source_link:4->8","hash","sqlite_read_back"));
        assertEquals(CortexActionReceiptStore.CANCELLED,CortexActionReceiptStore.get(db,id).executionStatus);
    }
}
