package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps legacy storage names from leaking into the v83 teacher contract. */
public final class CortexTeacherContextNormalizer {
    private CortexTeacherContextNormalizer() {}

    public static JSONObject apply(JSONObject root) {
        if (root == null) return new JSONObject();
        try {
            JSONArray candidates = root.optJSONArray("priorityCandidates");
            if (candidates != null) {
                for (int i = 0; i < candidates.length(); i++) {
                    JSONObject c = candidates.optJSONObject(i);
                    if (c == null) continue;
                    if (c.optBoolean("canonical", false)) c.put("layer", "TRIAGE");
                }
            }
            JSONArray uncertain = root.optJSONArray("uncertainCases");
            if (uncertain != null) {
                for (int i = 0; i < uncertain.length(); i++) {
                    JSONObject c = uncertain.optJSONObject(i);
                    if (c != null && c.optBoolean("canonical", false)) c.put("layer", "TRIAGE");
                }
            }
            JSONObject system = root.optJSONObject("system");
            if (system != null) {
                system.put("teacherPolicyLayer", "JUDGMENT");
                system.put("reasoningLayer", "SELECTIVE_REASONING");
            }
        } catch (Exception ignored) {}
        return root;
    }
}
