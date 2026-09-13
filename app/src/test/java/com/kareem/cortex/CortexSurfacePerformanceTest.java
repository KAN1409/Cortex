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
import org.robolectric.annotation.Config;

/** Regression gate for instant top-level surfaces. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class CortexSurfacePerformanceTest {
    private Context context;
    private VaultDb vault;

    @Before public void setUp(){
        context=ApplicationProvider.getApplicationContext();
        context.deleteDatabase("cortex.db");
        vault=new VaultDb(context);
        SQLiteDatabase db=vault.getWritableDatabase();
        UniversalEventStore.ensure(db);
        StatefulMeaningStore.ensure(db);
        CortexJudgmentTraceStore.ensure(db);
        CortexJudgedBriefProjection.invalidateForTests();
    }

    @After public void tearDown(){
        CortexJudgedBriefProjection.invalidateForTests();
        if(vault!=null)try{vault.close();}catch(Throwable ignored){}
        context.deleteDatabase("cortex.db");
    }

    @Test public void immediateNowRefreshIsPureReadAndCoalesced(){
        long before=count("SELECT COUNT(*) FROM cortex_judgment_trace");
        PrimeBriefStore.Snapshot first=CortexJudgedBriefProjection.load(context,vault);
        PrimeBriefStore.Snapshot second=CortexJudgedBriefProjection.load(context,vault);
        assertNotNull(first);
        assertSame("onCreate/onResume should share the same immediate projection",first,second);
        assertEquals("opening Now must not create judgment traces",before,count("SELECT COUNT(*) FROM cortex_judgment_trace"));
    }

    private long count(String sql){
        Cursor c=vault.getReadableDatabase().rawQuery(sql,null);
        try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}
    }
}
