package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Builds a grounded request that lets ChatGPT judge Cortex without inventing evidence. */
public final class ChatGptAdjudicationRequest {
    private ChatGptAdjudicationRequest() {}

    public static JSONObject buildPayload(
            JSONObject originalPipelineInput,
            JSONObject cortexOutput,
            JSONObject expectedOrReference,
            JSONArray evidence,
            String testIntent,
            boolean allowTeachingRecommendations) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("testIntent", testIntent == null ? "" : testIntent);
        payload.put("originalPipelineInput", originalPipelineInput == null ? JSONObject.NULL : originalPipelineInput);
        payload.put("cortexOutput", cortexOutput == null ? JSONObject.NULL : cortexOutput);
        payload.put("expectedOrReference", expectedOrReference == null ? JSONObject.NULL : expectedOrReference);
        payload.put("evidence", evidence == null ? new JSONArray() : evidence);
        payload.put("allowTeachingRecommendations", allowTeachingRecommendations);

        JSONObject rules = new JSONObject();
        rules.put("evidenceIsAuthoritative", true);
        rules.put("doNotInventMissingEvidence", true);
        rules.put("compareAgainstOriginalPipelineInput", true);
        rules.put("independentlySolveSameGroundedCase", true);
        rules.put("compareCortexToIndependentChatGptResult", true);
        rules.put("returnOneVerdictForThisTestOnly", true);
        rules.put("teachingMustNotMutateCanonicalState", true);
        payload.put("judgingRules", rules);
        return payload;
    }
}
