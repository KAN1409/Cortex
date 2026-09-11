package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class KnowledgeV2GroundingTest {
    private VaultDb db;

    @After public void tearDown(){
        if(db!=null)try{db.close();}catch(Throwable ignored){}
        ApplicationProvider.<Context>getApplicationContext().deleteDatabase("cortex.db");
    }

    @Test public void exactStructuredFactCanGroundBrainWithoutDependingOnVectorRank(){
        Context c=ApplicationProvider.getApplicationContext();
        c.deleteDatabase("cortex.db");
        db=new VaultDb(c);
        SQLiteDatabase s=db.getWritableDatabase();
        KnowledgeV2Schema.ensure(s);
        long now=System.currentTimeMillis();

        ContentValues ev=new ContentValues();
        ev.put("source_type","PICBRAIN_SCREENSHOT");ev.put("source_key","grounding-test");ev.put("source_uri","content://test/grounding");ev.put("source_media_id",99);
        ev.put("raw_text","Neurontin 800 mg previously purchased for 590 EGP");ev.put("content_hash","grounding-test");ev.put("origin","EXTERNAL");ev.put("self_reference_score",0.0);ev.put("derivation_depth",0);ev.put("knowledge_eligible",1);ev.put("provenance_reason","");ev.put("captured_at",now);ev.put("observed_at",now);ev.put("created_at",now);ev.put("updated_at",now);
        long evidenceId=s.insert("kv2_evidence",null,ev);assertTrue(evidenceId>0);

        ContentValues u=new ContentValues();u.put("evidence_id",evidenceId);u.put("title","Neurontin purchase");u.put("summary","Neurontin 800 mg previously purchased for 590 EGP");u.put("category","Money & purchases");u.put("tags","medicine,purchase");u.put("engine","test");u.put("extraction_version",KnowledgeV2Schema.PIPELINE_VERSION);u.put("confidence",.9);u.put("created_at",now);u.put("updated_at",now);s.insertOrThrow("kv2_understanding",null,u);

        ContentValues f=new ContentValues();f.put("subject_type","EVIDENCE");f.put("subject_key","evidence:"+evidenceId);f.put("predicate","MENTIONS_MONEY");f.put("object_type","MONEY");f.put("object_value","590 EGP");f.put("confidence",.95);f.put("state","active");f.put("valid_from",0);f.put("valid_to",0);f.put("observed_at",now);f.put("extraction_version",KnowledgeV2Schema.PIPELINE_VERSION);f.put("fingerprint","grounding-money");f.put("metadata_json","{}");f.put("created_at",now);f.put("updated_at",now);long factId=s.insertOrThrow("kv2_facts",null,f);
        ContentValues fe=new ContentValues();fe.put("fact_id",factId);fe.put("evidence_id",evidenceId);fe.put("relation","DERIVED_FROM");fe.put("confidence",.95);fe.put("created_at",now);s.insertOrThrow("kv2_fact_evidence",null,fe);

        long projection=db.insert("SCREENSHOT_MEMORY","knowledge_v2","Neurontin purchase","Neurontin 800 mg previously purchased for 590 EGP","Money & purchases","medicine,purchase","content://test/grounding","grounding-projection","{\"canonical\":\"knowledge_v2\",\"evidence_id\":"+evidenceId+"}");
        assertTrue(projection>0);

        ArrayList<SemanticHit> hits=KnowledgeV2Grounding.search(db,"590 EGP",5);
        assertFalse(hits.isEmpty());
        assertEquals(projection,hits.get(0).item.id);
        assertTrue(hits.get(0).score>=0.16);
    }
}
