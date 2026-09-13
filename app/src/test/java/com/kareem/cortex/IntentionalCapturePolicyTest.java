package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class IntentionalCapturePolicyTest {
    private KnowledgeItem item(String type,String source){return new KnowledgeItem(1,type,source,"t","","","","","","","queued","","","",1,1);}

    @Test public void manualRecordingIsVisible(){assertTrue(IntentionalCapturePolicy.visibleInCapturedLibrary(item("AUDIO","manual_recording")));}
    @Test public void audioImportIsVisible(){assertTrue(IntentionalCapturePolicy.visibleInCapturedLibrary(item("AUDIO","audio_import")));}
    @Test public void sharedEvidenceIsVisible(){assertTrue(IntentionalCapturePolicy.visibleInCapturedLibrary(item("FILE","android_share")));}
    @Test public void passiveNotificationIsNotVisible(){assertFalse(IntentionalCapturePolicy.visibleInCapturedLibrary(item("NOTIFICATION","notification_listener")));}
    @Test public void weatherContextIsNotVisible(){assertFalse(IntentionalCapturePolicy.visibleInCapturedLibrary(item("TEXT","weather_context")));}
    @Test public void userScreenUnderstandIsVisible(){assertTrue(IntentionalCapturePolicy.visibleInCapturedLibrary(item("TEXT","screen_understand")));}
    @Test public void sourceLabelsHideInternalKeys(){assertEquals("Recorded in Cortex",IntentionalCapturePolicy.sourceLabel("manual_recording"));}
}
