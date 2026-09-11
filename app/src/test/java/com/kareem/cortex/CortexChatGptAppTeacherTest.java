package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CortexChatGptAppTeacherTest {

    @Test public void acceptsValidChatGptPolicy()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        JSONObject policy=new JSONObject()
                .put("version","chatgpt-teacher-test")
                .put("ttlMs",86400000)
                .put("attentionThreshold",.73)
                .put("maxNowItems",6)
                .put("interruptionPenaltyScale",.25)
                .put("featureWeights",new JSONObject().put("urgency",.2))
                .put("boosts",new JSONArray().put(new JSONObject().put("match","negma").put("weight",.2)))
                .put("teacherNotes","test");

        CortexChatGptAppTeacher.ImportResult r=
                CortexChatGptAppTeacher.importText(context,policy.toString());

        assertTrue(r.error,r.ok);
        assertEquals("chatgpt-teacher-test",CortexPersonalPolicy.version(context));
    }


    @Test public void acceptsChatGptFeatureBoostShape()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        JSONObject policy=new JSONObject()
                .put("version","chatgpt-teacher-conservative-v1")
                .put("ttlMs",604800000)
                .put("attentionThreshold",.76)
                .put("maxNowItems",5)
                .put("interruptionPenaltyScale",.30)
                .put("featureWeights",new JSONObject().put("priority",.32))
                .put("boosts",new JSONArray().put(
                        new JSONObject().put("feature","securityRisk").put("weight",.22)))
                .put("teacherNotes","test");

        CortexChatGptAppTeacher.ImportResult r=
                CortexChatGptAppTeacher.importText(context,policy.toString());

        assertTrue(r.error,r.ok);
        assertEquals("chatgpt-teacher-conservative-v1",CortexPersonalPolicy.version(context));
    }

    @Test public void rejectsOutOfBoundsPolicy()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        JSONObject policy=new JSONObject()
                .put("version","bad")
                .put("ttlMs",86400000)
                .put("attentionThreshold",1.3)
                .put("maxNowItems",6)
                .put("interruptionPenaltyScale",.25)
                .put("featureWeights",new JSONObject())
                .put("boosts",new JSONArray())
                .put("teacherNotes","bad");

        CortexChatGptAppTeacher.ImportResult r=
                CortexChatGptAppTeacher.importText(context,policy.toString());

        assertFalse(r.ok);
        assertTrue(r.error.contains("attentionThreshold"));
    }

    @Test public void extractsJsonFromSharedText()throws Exception{
        String shared="ChatGPT result:\n{\"version\":\"chatgpt-teacher-share\",\"ttlMs\":86400000,\"attentionThreshold\":0.72,\"maxNowItems\":7,\"interruptionPenaltyScale\":0.24,\"featureWeights\":{},\"boosts\":[],\"teacherNotes\":\"ok\"}\n";
        JSONObject p=CortexChatGptAppTeacher.extractPolicy(shared);
        assertEquals("chatgpt-teacher-share",p.getString("version"));
    }
}
