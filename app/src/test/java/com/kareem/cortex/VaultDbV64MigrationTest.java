package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.io.File;

@RunWith(RobolectricTestRunner.class)
public class VaultDbV64MigrationTest {
    Context context;File file;
    @Before public void before(){context=ApplicationProvider.getApplicationContext();file=context.getDatabasePath("cortex.db");if(file.exists())file.delete();}
    @After public void after(){if(file.exists())file.delete();}

    @Test public void v6DataSurvivesAndIsClassifiedNonDestructively(){
        file.getParentFile().mkdirs();SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(file,null);
        old.execSQL("CREATE TABLE knowledge_items(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT NOT NULL,source TEXT,title TEXT NOT NULL,raw_text TEXT,extracted_text TEXT,summary TEXT,category TEXT,tags TEXT,attachment_path TEXT,status TEXT,fingerprint TEXT,analysis_error TEXT,metadata_json TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE entities(id INTEGER PRIMARY KEY AUTOINCREMENT,item_id INTEGER NOT NULL,kind TEXT,value TEXT,confidence REAL,created_at INTEGER NOT NULL)");
        old.execSQL("INSERT INTO knowledge_items(type,source,title,raw_text,status,created_at,updated_at) VALUES('SCREENSHOT','screenshot-folder','Legacy screenshot','evidence','analyzed',1000,1000)");
        old.execSQL("INSERT INTO knowledge_items(type,source,title,raw_text,status,created_at,updated_at) VALUES('TEXT','manual','Long term fact','remember me','analyzed',2000,2000)");
        old.setVersion(6);old.close();

        VaultDb migrated=new VaultDb(context);SQLiteDatabase db=migrated.getWritableDatabase();assertEquals(7,db.getVersion());
        assertEquals(2,count(db,"SELECT COUNT(*) FROM knowledge_items"));
        assertEquals(1,count(db,"SELECT COUNT(*) FROM ue_legacy_classification WHERE classification='captured_artifact'"));
        assertEquals(1,count(db,"SELECT COUNT(*) FROM ue_legacy_classification WHERE classification='memory_candidate'"));
        assertTrue(table(db,"ue_raw_observations"));assertTrue(table(db,"ue_streams"));assertTrue(table(db,"ue_semantic_events"));assertTrue(table(db,"ue_situations"));assertTrue(table(db,"ue_attention_items"));assertTrue(table(db,"ue_memory_promotions"));assertTrue(table(db,"ue_pipeline_stages"));assertTrue(table(db,"ue_reprocess_requests"));
        migrated.close();
    }

    @Test public void canonicalPresentationRemovesImmediateDuplicateWords(){assertEquals("Hey, I'm recording this voice for trial only.",CanonicalPresentation.dedupeWords("Hey, I'm recording this voice voice for trial only."));}

    private static long count(SQLiteDatabase db,String sql){Cursor c=db.rawQuery(sql,null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    private static boolean table(SQLiteDatabase db,String name){Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{name});boolean yes=c.moveToFirst();c.close();return yes;}
}
