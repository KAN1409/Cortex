package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveSituationEnrichmentV4RegressionTest {
    private static SQLiteDatabase db(){
        SQLiteDatabase sql=SQLiteDatabase.create(null);
        sql.execSQL("CREATE TABLE v4_situations(id TEXT PRIMARY KEY,state TEXT NOT NULL,headline TEXT,relevant_from INTEGER DEFAULT 0,relevant_until INTEGER DEFAULT 0,attention_score REAL DEFAULT 0,interruption_score REAL DEFAULT 0,confidence REAL DEFAULT 0,last_evaluated_at INTEGER DEFAULT 0,updated_at INTEGER DEFAULT 0)");
        return sql;
    }

    @Test public void richerExplicitTimeUpdatesGroundingWithoutResettingDeferredState(){
        SQLiteDatabase sql=db();try{
            ContentValues v=new ContentValues();v.put("id","si_deadline");v.put("state","DEFERRED");v.put("headline","Send design file");v.put("relevant_from",1000);v.put("relevant_until",0);v.put("attention_score",.91);v.put("interruption_score",.10);v.put("confidence",.70);v.put("updated_at",2000);assertTrue(sql.insert("v4_situations",null,v)>=0);
            CognitiveSituationEngineV4.Candidate richer=new CognitiveSituationEngineV4.Candidate(CognitiveDomainV4.SituationKind.DEADLINE,"Send design file","Memory contains an explicit deadline signal.",Long.valueOf(1000),Long.valueOf(5000),.56,.24,.82);
            assertTrue(CognitiveSituationEngineV4.refreshExistingGrounding(sql,"si_deadline",richer,3000));
            Cursor c=sql.rawQuery("SELECT state,relevant_until,attention_score,interruption_score,confidence,updated_at FROM v4_situations WHERE id='si_deadline'",null);try{assertTrue(c.moveToFirst());assertEquals("DEFERRED",c.getString(0));assertEquals(5000,c.getLong(1));assertEquals(.91,c.getDouble(2),.0001);assertEquals(.24,c.getDouble(3),.0001);assertEquals(.82,c.getDouble(4),.0001);assertEquals(3000,c.getLong(5));}finally{c.close();}
        }finally{sql.close();}
    }

    @Test public void terminalSituationIsNeverReopenedByRicherGrounding(){
        SQLiteDatabase sql=db();try{
            ContentValues v=new ContentValues();v.put("id","si_done");v.put("state","RESOLVED");v.put("headline","Done");v.put("relevant_until",0);v.put("attention_score",.2);v.put("interruption_score",.1);v.put("confidence",.7);v.put("updated_at",2000);assertTrue(sql.insert("v4_situations",null,v)>=0);
            CognitiveSituationEngineV4.Candidate richer=new CognitiveSituationEngineV4.Candidate(CognitiveDomainV4.SituationKind.DEADLINE,"Done","deadline",Long.valueOf(1000),Long.valueOf(5000),.8,.5,.9);
            assertFalse(CognitiveSituationEngineV4.refreshExistingGrounding(sql,"si_done",richer,3000));
            Cursor c=sql.rawQuery("SELECT state,relevant_until,updated_at FROM v4_situations WHERE id='si_done'",null);try{assertTrue(c.moveToFirst());assertEquals("RESOLVED",c.getString(0));assertEquals(0,c.getLong(1));assertEquals(2000,c.getLong(2));}finally{c.close();}
        }finally{sql.close();}
    }
}
