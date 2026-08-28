package com.kareem.cortex;

import static org.junit.Assert.*;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;

public final class CognitiveStateInvariantV2RegressionTest {
    @Test public void everyRawSignalGetsExplicitCognitiveState(){
        SQLiteDatabase db=SQLiteDatabase.create(null);try{
            CognitiveSchema.ensure(db);CognitiveStateBackfillV2.installForTest(db);
            long first=insert(db,"CONTEXT","");
            assertEquals("CONTEXT_ONLY",state(db,first));
            long queued=insert(db,"CONTEXT","PENDING_ADJUDICATION");
            assertEquals("LOCAL_QUEUED",state(db,queued));
            ContentValues running=new ContentValues();running.put("cognitive_state","PENDING_ADJUDICATION");running.put("final_reason","LOCAL Qwen3-1.7B analyzing");db.update("raw_signals",running,"id=?",new String[]{String.valueOf(queued)});
            assertEquals("LOCAL_RUNNING",state(db,queued));
            Cursor c=db.rawQuery("SELECT COUNT(*) FROM raw_signals WHERE cognitive_state IS NULL OR TRIM(cognitive_state)=''",null);try{assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0));}finally{c.close();}
        }finally{db.close();}
    }

    @Test public void cognitiveContractsStayIndependentFromLegacyDisposition(){
        assertEquals(CognitiveDisposition.DERIVE,CognitiveDisposition.valueOf("DERIVE"));
        assertEquals(CognitiveKind.EVENT,CognitiveKind.valueOf("EVENT"));
        assertTrue(CognitiveSignalV2.awaitingAdjudication("LOCAL_QUEUED"));
        assertTrue(CognitiveSignalV2.terminal("DERIVED"));
        assertFalse(CognitiveSignalV2.terminal("MODEL_FAILED"));
    }

    private static long insert(SQLiteDatabase db,String disposition,String cognitiveState){
        long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("kind","notification");v.put("source","test");v.put("title","x");v.put("body","x");v.put("metadata_json","{}");v.put("fingerprint","fp-"+now+"-"+Math.random());v.put("content_hash","hash");v.put("state","filtered");v.put("disposition",disposition);v.put("importance",20);v.put("confidence",.9);v.put("policy_version","test");v.put("filter_engine","test");v.put("reason","test");if(cognitiveState!=null)v.put("cognitive_state",cognitiveState);v.put("occurred_at",now);v.put("retention_until",now+10000);v.put("created_at",now);v.put("updated_at",now);return db.insertOrThrow("raw_signals",null,v);
    }
    private static String state(SQLiteDatabase db,long id){Cursor c=db.rawQuery("SELECT cognitive_state FROM raw_signals WHERE id=?",new String[]{String.valueOf(id)});try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}
}
