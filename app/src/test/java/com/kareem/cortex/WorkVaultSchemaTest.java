package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WorkVaultSchemaTest {

    @Test public void createsAllCoreWorkVaultTables(){
        Context context=ApplicationProvider.getApplicationContext();
        VaultDb vault=new VaultDb(context);
        SQLiteDatabase db=vault.getWritableDatabase();
        WorkVaultSchema.ensure(db);

        assertTrue(table(db,"work_sources"));
        assertTrue(table(db,"work_files"));
        assertTrue(table(db,"work_projects"));
        assertTrue(table(db,"work_entities"));
        assertTrue(table(db,"work_facts"));
        assertTrue(table(db,"work_relations"));
        assertTrue(table(db,"work_procurement_refs"));
        assertTrue(table(db,"work_price_records"));
        assertTrue(table(db,"work_index_jobs"));
        vault.close();
    }

    @Test public void schemaEnsureIsIdempotent(){
        Context context=ApplicationProvider.getApplicationContext();
        VaultDb vault=new VaultDb(context);
        SQLiteDatabase db=vault.getWritableDatabase();
        WorkVaultSchema.ensure(db);
        WorkVaultSchema.ensure(db);
        assertTrue(table(db,"work_files"));
        vault.close();
    }

    private static boolean table(SQLiteDatabase db,String name){
        Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{name});
        try{return c.moveToFirst();}finally{c.close();}
    }
}
