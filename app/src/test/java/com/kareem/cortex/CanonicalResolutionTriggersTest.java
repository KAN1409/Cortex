package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class CanonicalResolutionTriggersTest {
    @Test public void resolvingAttentionMirrorAlsoResolvesCanonicalSituation() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try {
            db.execSQL("CREATE TABLE derived_items(id INTEGER PRIMARY KEY,candidate_kind TEXT,state TEXT,updated_at INTEGER,thread_id INTEGER)");
            CanonicalResolutionTriggers.ensure(db);

            ContentValues s=new ContentValues();
            s.put("situation_key","test|1");s.put("kind","commitment");s.put("title","Shipment");s.put("summary","Confirm shipment");
            s.put("state","open");s.put("priority",85);s.put("confidence",.8);s.put("opened_at",1L);s.put("last_changed_at",1L);s.put("created_at",1L);s.put("updated_at",1L);
            long situationId=db.insertOrThrow("ue_situations",null,s);

            ContentValues a=new ContentValues();
            a.put("semantic_event_id",77L);a.put("situation_id",situationId);a.put("kind","ACTION");a.put("title","Shipment");a.put("body","Confirm shipment");
            a.put("state","open");a.put("priority",85);a.put("confidence",.8);a.put("source_key","whatsapp");a.put("reason","FINAL_JUDGE: test");a.put("created_at",1L);a.put("updated_at",1L);
            long attentionId=db.insertOrThrow("ue_attention_items",null,a);

            ContentValues d=new ContentValues();
            d.put("id",UniversalEventStore.ATTENTION_COMPAT_OFFSET+attentionId);d.put("candidate_kind","UE_ATTENTION");d.put("state","open");d.put("updated_at",1L);d.put("thread_id",situationId);
            db.insertOrThrow("derived_items",null,d);

            ContentValues resolved=new ContentValues();resolved.put("state","resolved");resolved.put("updated_at",12345L);
            assertEquals(1,db.update("derived_items",resolved,"id=?",new String[]{String.valueOf(UniversalEventStore.ATTENTION_COMPAT_OFFSET+attentionId)}));

            assertEquals("resolved",singleText(db,"SELECT state FROM ue_attention_items WHERE id=?",attentionId));
            assertEquals("resolved",singleText(db,"SELECT state FROM ue_situations WHERE id=?",situationId));
            assertEquals(12345L,singleLong(db,"SELECT resolved_at FROM ue_attention_items WHERE id=?",attentionId));
            assertEquals(12345L,singleLong(db,"SELECT resolved_at FROM ue_situations WHERE id=?",situationId));
        } finally { db.close(); }
    }

    private static String singleText(SQLiteDatabase db,String sql,long id){Cursor c=db.rawQuery(sql,new String[]{String.valueOf(id)});try{c.moveToFirst();return c.getString(0);}finally{c.close();}}
    private static long singleLong(SQLiteDatabase db,String sql,long id){Cursor c=db.rawQuery(sql,new String[]{String.valueOf(id)});try{c.moveToFirst();return c.getLong(0);}finally{c.close();}}
}
