package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceCapturePipelineTest {
    @Test public void staleManualVoiceIsRecoveredToQueued(){
        Context ctx=ApplicationProvider.getApplicationContext();VaultDb db=new VaultDb(ctx);long id=0;
        try{
            String token="voice-recovery-"+System.nanoTime();id=db.insert("AUDIO","manual_recording","Voice recording","","Voice & Audio","voice,audio","/tmp/does-not-matter.wav",token,"{}");assertTrue(id>0);
            ContentValues v=new ContentValues();v.put("status","analyzing");v.put("updated_at",System.currentTimeMillis()-180_000L);db.getWritableDatabase().update("knowledge_items",v,"id=?",new String[]{String.valueOf(id)});
            int recovered=VoiceCapturePipeline.recoverStale(db);KnowledgeItem k=db.getById(id);assertTrue(recovered>=1);assertNotNull(k);assertEquals("queued",k.status);assertTrue(k.analysisError.contains("Recovered"));
        }finally{if(id>0)db.getWritableDatabase().delete("knowledge_items","id=?",new String[]{String.valueOf(id)});db.close();}
    }

    @Test public void intentionalCapturePolicyKeepsPassiveNoiseOut(){
        KnowledgeItem manual=new KnowledgeItem(-1,"TEXT","manual","x","x","","","","","","analyzed","","","{}",0,0);
        KnowledgeItem notification=new KnowledgeItem(-2,"NOTIFICATION","notification_listener","x","x","","","","","","analyzed","","","{}",0,0);
        assertTrue(IntentionalCapturePolicy.visibleInCapturedLibrary(manual));assertFalse(IntentionalCapturePolicy.visibleInCapturedLibrary(notification));
    }
}
