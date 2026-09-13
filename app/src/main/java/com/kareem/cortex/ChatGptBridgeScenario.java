package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable benchmark case for Cortex-vs-reference-vs-ChatGPT adjudication.
 * It deliberately carries original pipeline input separately from Cortex output.
 */
public final class ChatGptBridgeScenario {
    public final String id;
    public final String category;
    public final JSONObject originalInput;
    public final JSONObject cortexOutput;
    public final JSONObject referenceEvidence;

    public ChatGptBridgeScenario(String id, String category, JSONObject originalInput,
                                 JSONObject cortexOutput, JSONObject referenceEvidence) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("id required");
        this.id = id;
        this.category = category == null ? "UNSPECIFIED" : category;
        this.originalInput = originalInput == null ? new JSONObject() : originalInput;
        this.cortexOutput = cortexOutput == null ? new JSONObject() : cortexOutput;
        this.referenceEvidence = referenceEvidence == null ? new JSONObject() : referenceEvidence;
    }

    public JSONObject asRequestInput() throws JSONException {
        return new JSONObject()
                .put("scenarioId", id)
                .put("category", category)
                .put("pipelineInput", originalInput);
    }

    public JSONObject asCortexOutput() throws JSONException {
        return new JSONObject()
                .put("scenarioId", id)
                .put("category", category)
                .put("output", cortexOutput);
    }

    public JSONObject asReferenceEvidence() throws JSONException {
        return new JSONObject()
                .put("scenarioId", id)
                .put("category", category)
                .put("reference", referenceEvidence);
    }

    /** Adds standardized evidence for file receipt/open/extract scenarios. */
    public static JSONObject fileReference(
            String sourceName,
            String mimeType,
            long sizeBytes,
            String sha256,
            boolean receiveExpected,
            boolean openExpected,
            boolean extractionExpected,
            JSONObject expectedExtractedFacts) throws JSONException {
        return new JSONObject()
                .put("kind", "FILE_FLOW")
                .put("sourceName", sourceName == null ? "" : sourceName)
                .put("mimeType", mimeType == null ? "" : mimeType)
                .put("sizeBytes", sizeBytes)
                .put("sha256", sha256 == null ? "" : sha256)
                .put("expectedStages", new JSONObject()
                        .put("receive", receiveExpected)
                        .put("open", openExpected)
                        .put("extract", extractionExpected))
                .put("expectedExtractedFacts", expectedExtractedFacts == null ? new JSONObject() : expectedExtractedFacts)
                .put("requiredChecks", new JSONArray()
                        .put("source identity preserved")
                        .put("MIME/type handling correct")
                        .put("stored/reference target resolvable")
                        .put("FileProvider/open path valid")
                        .put("extracted facts match source")
                        .put("no synthetic fixture enters canonical evidence"));
    }
}
