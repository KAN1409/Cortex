package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveRetentionV4RegressionTest {
    @Test public void pinnedMemoryProtectsItsBackingEvidence(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            CognitiveSchemaV4.ensure(db);long now=System.currentTimeMillis();
            ContentValues e=new ContentValues();e.put("id","ev_pin");e.put("identity_key","ev_pin_key");e.put("source_type","NOTE");e.put("occurred_at",now);e.put("captured_at",now);e.put("sensitivity","NORMAL");e.put("retention_class","EPISODIC_90_DAY");e.put("expires_at",now-1);e.put("processing_state","READY");e.put("created_at",now);e.put("updated_at",now);assertTrue(db.insert("v4_evidence",null,e)>0);
            ContentValues m=new ContentValues();m.put("id","mem_pin");m.put("identity_key","mem_pin_key");m.put("kind","NOTE");m.put("body","important note");m.put("started_at",now);m.put("importance",1.0);m.put("pinned",1);m.put("retention_class","PINNED");m.put("expires_at",0);m.put("state","ACTIVE");m.put("created_at",now);m.put("updated_at",now);assertTrue(db.insert("v4_memories",null,m)>0);
            ContentValues l=new ContentValues();l.put("memory_id","mem_pin");l.put("evidence_id","ev_pin");l.put("role","supports");l.put("ordinal",0);l.put("created_at",now);assertTrue(db.insert("v4_memory_evidence",null,l)>0);
            assertEquals(1,CognitiveRetentionV4.reconcilePinnedEvidence(db));
            Cursor c=db.query("v4_evidence",new String[]{"retention_class","expires_at"},"id='ev_pin'",null,null,null,null);assertTrue(c.moveToFirst());assertEquals("PINNED",c.getString(0));assertEquals(0,c.getLong(1));c.close();
        }finally{db.close();}
    }
}
