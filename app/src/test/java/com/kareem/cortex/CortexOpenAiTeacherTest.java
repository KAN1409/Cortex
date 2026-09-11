package com.kareem.cortex;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CortexOpenAiTeacherTest {

    @Test public void requestUsesBoundedGpt56SolPolicySchema()throws Exception{
        JSONObject pack=new JSONObject()
                .put("schemaVersion",2)
                .put("architecture",new JSONObject().put("version","cortex_intelligence_layers_002"))
                .put("activeSituations",new JSONArray())
                .put("personalModel",new JSONObject())
                .put("priorityCandidates",new JSONArray())
                .put("uncertainCases",new JSONArray())
                .put("recentOutcomes",new JSONArray())
                .put("system",new JSONObject());

        JSONObject req=CortexOpenAiTeacher.request(pack);
        assertEquals("openai/gpt-5.6-sol",req.getString("model"));

        JSONObject responseFormat=req.getJSONObject("response_format");
        assertEquals("json_schema",responseFormat.getString("type"));
        JSONObject schema=responseFormat.getJSONObject("json_schema").getJSONObject("schema");
        assertFalse(schema.getBoolean("additionalProperties"));
        JSONArray required=schema.getJSONArray("required");
        assertTrue(required.toString().contains("attentionThreshold"));
        assertTrue(required.toString().contains("interruptionPenaltyScale"));
    }

    @Test public void parserAcceptsBoundedPolicy()throws Exception{
        JSONObject policy=new JSONObject()
                .put("version","teacher-test-1")
                .put("ttlMs",86400000)
                .put("attentionThreshold",.74)
                .put("maxNowItems",6)
                .put("interruptionPenaltyScale",.25)
                .put("featureWeights",new JSONObject())
                .put("boosts",new JSONArray())
                .put("teacherNotes","bounded test");

        JSONObject response=new JSONObject().put("choices",new JSONArray().put(
                new JSONObject().put("message",new JSONObject().put("content",policy.toString()))));

        JSONObject parsed=CortexOpenAiTeacher.parsePolicy(response.toString());
        assertEquals("teacher-test-1",parsed.getString("version"));
        assertEquals(.74,parsed.getDouble("attentionThreshold"),.0001);
        assertTrue(parsed.getLong("generatedAt")>0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void parserRejectsUnboundedThreshold()throws Exception{
        JSONObject policy=new JSONObject()
                .put("version","bad-policy")
                .put("ttlMs",86400000)
                .put("attentionThreshold",1.5)
                .put("maxNowItems",6)
                .put("interruptionPenaltyScale",.25)
                .put("featureWeights",new JSONObject())
                .put("boosts",new JSONArray())
                .put("teacherNotes","bad");

        JSONObject response=new JSONObject().put("choices",new JSONArray().put(
                new JSONObject().put("message",new JSONObject().put("content",policy.toString()))));

        CortexOpenAiTeacher.parsePolicy(response.toString());
    }
}
