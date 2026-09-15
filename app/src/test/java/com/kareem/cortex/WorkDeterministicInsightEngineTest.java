package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class WorkDeterministicInsightEngineTest {
    SQLiteDatabase db;

    @Before public void setup()throws Exception{
        java.lang.reflect.Field f=DiscoveryV3Schema.class.getDeclaredField("ready");f.setAccessible(true);f.setBoolean(null,false);
        db=SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE knowledge_items(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT NOT NULL,source TEXT,title TEXT NOT NULL,raw_text TEXT,extracted_text TEXT,summary TEXT,category TEXT,tags TEXT,attachment_path TEXT,status TEXT DEFAULT 'queued',fingerprint TEXT,analysis_error TEXT,metadata_json TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_test_ki_fingerprint ON knowledge_items(fingerprint)");
        DiscoveryV3Schema.ensure(db);
    }
    @After public void close(){db.close();}

    private static WorkPriceComparisonEngine.Price price(long id,long file,long version,double value,long time){
        WorkPriceComparisonEngine.Price p=WorkPriceComparisonEngine.Price.of("Galala marble supply","m2",value,"EGP");
        p.id=id;p.fileId=file;p.versionId=version;p.projectId=42;p.project="Negma";p.sourceTime=time;p.fileName="quotation-"+id+".xlsx";p.documentUri="content://work/"+file;p.sheet="Prices";p.row=(int)id;p.confidence=.86;return p;
    }
    private static WorkPriceComparisonEngine.Comparison golden(){return WorkPriceComparisonEngine.compare(price(2,20,200,1180,2000),price(1,10,100,1000,1000));}

    @Test public void goldenMarbleComparisonPublishesExactlyTwoGroundedSources(){
        long id=WorkDeterministicInsightEngine.publishComparison(db,golden(),3000);assertTrue(id>0);
        try(Cursor c=db.rawQuery("SELECT state,title,what_found,evidence_count FROM discovery_v3_insights WHERE id=?",new String[]{String.valueOf(id)})){
            assertTrue(c.moveToFirst());assertEquals("published",c.getString(0));assertTrue(c.getString(1).contains("18%"));assertTrue(c.getString(2).contains("1000 EGP/m2"));assertTrue(c.getString(2).contains("1180 EGP/m2"));assertEquals(2,c.getInt(3));
        }
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM knowledge_items WHERE source='work_vault' AND status='analyzed'",null)){assertTrue(c.moveToFirst());assertEquals(2,c.getInt(0));}
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM discovery_v3_evidence",null)){assertTrue(c.moveToFirst());assertEquals(2,c.getInt(0));}
    }

    @Test public void rerunMergesSameIssueAndPreservesDismissal(){
        long first=WorkDeterministicInsightEngine.publishComparison(db,golden(),3000);assertTrue(first>0);
        db.execSQL("UPDATE discovery_v3_insights SET state='dismissed' WHERE id=?",new Object[]{first});
        long second=WorkDeterministicInsightEngine.publishComparison(db,golden(),4000);assertEquals(first,second);
        try(Cursor c=db.rawQuery("SELECT COUNT(*),MAX(state) FROM discovery_v3_insights",null)){assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));assertEquals("dismissed",c.getString(1));}
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM knowledge_items",null)){assertTrue(c.moveToFirst());assertEquals(2,c.getInt(0));}
    }

    @Test public void smallChangeDoesNotEnterNow(){
        WorkPriceComparisonEngine.Comparison x=WorkPriceComparisonEngine.compare(price(2,20,200,1050,2000),price(1,10,100,1000,1000));
        assertEquals(0,WorkDeterministicInsightEngine.publishComparison(db,x,3000));
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM discovery_v3_insights",null)){c.moveToFirst();assertEquals(0,c.getInt(0));}
    }

    @Test public void lowConfidenceSourcesDoNotPublish(){
        WorkPriceComparisonEngine.Price current=price(2,20,200,1180,2000),previous=price(1,10,100,1000,1000);current.confidence=.60;
        assertEquals(0,WorkDeterministicInsightEngine.publishComparison(db,WorkPriceComparisonEngine.compare(current,previous),3000));
    }
}
