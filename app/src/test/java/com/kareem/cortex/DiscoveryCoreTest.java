package com.kareem.cortex;

import static org.junit.Assert.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class DiscoveryCoreTest {
    private KnowledgeItem item(String category,String title,String metadata){
        return new KnowledgeItem(1,"TEXT","manual",title,"","",title,category,"","","analyzed","","",metadata,1000,1000);
    }

    @Test public void explicitSpaceWins(){
        assertEquals("WORK",DiscoveryPolicy.inferSpace(item("Health","anything","{\"space\":\"WORK\"}")));
    }

    @Test public void exactProcurementReferenceIsStable(){
        assertEquals("PR-0262",DiscoveryPolicy.exactReference("Please review PR 0262 ceiling scope"));
        assertEquals("PO-123/7",DiscoveryPolicy.exactReference("PO#123/7 approved"));
    }

    @Test public void contradictionPolicyFindsMaterialStatusConflict(){
        assertTrue(DiscoveryPolicy.contradictory("approved","revision"));
        assertTrue(DiscoveryPolicy.contradictory("completed","pending"));
        assertFalse(DiscoveryPolicy.contradictory("approved","approved"));
    }

    @Test public void rawCountsAreRejectedAsNoise(){
        assertTrue(DiscoveryPolicy.isTrivial("Work statistics","17 records observed"));
        DiscoveryCritic.Verdict v=DiscoveryCritic.judge("MEANINGFUL_CHANGE","Work statistics","17 records observed",2,.9,.9);
        assertFalse(v.keep);
    }

    @Test public void usefulCandidateCanPassCritic(){
        DiscoveryCritic.Verdict v=DiscoveryCritic.judge("CONTRADICTION","Conflicting approval state",
                "One source says approved while a later source requests revision.",2,.86,.80);
        assertTrue(v.keep);
    }

    @Test public void schemaIsAdditiveAndCreatesCoreTables(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            DiscoverySchema.ensure(db);
            assertTrue(table(db,"discovery_annotations"));
            assertTrue(table(db,"discovery_history_revisions"));
            assertTrue(table(db,"discovery_candidates"));
            assertTrue(table(db,"discovery_candidate_evidence"));
        }finally{db.close();}
    }

    private boolean table(SQLiteDatabase db,String name){
        Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{name});
        boolean yes=c.moveToFirst();c.close();return yes;
    }
}
