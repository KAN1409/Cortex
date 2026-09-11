package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Authoritative Cortex intelligence architecture.
 *
 * Every kind of intelligence has one owner. Higher layers may consume lower-layer state, but they
 * must not silently rewrite it. ChatGPT is a teacher of final judgment policy, never a second
 * source of truth for evidence or knowledge.
 */
public final class CortexIntelligenceArchitecture {
    public static final String VERSION = "cortex_intelligence_layers_002";

    public enum Layer {
        EVIDENCE(10, "Evidence", "UniversalEventStore / raw source stores", false,
                "Immutable observations and source provenance."),
        PERCEPTION(20, "Perception", "OCR / ASR / vision / semantic extraction", false,
                "Extracted observations and mentions; no priority decisions."),
        KNOWLEDGE(30, "Canonical knowledge", "Knowledge V2 + entity graph", false,
                "Grounded facts, events, entities and relationships with provenance and time."),
        WORLD_STATE(40, "World state", "StatefulMeaningStore + CognitiveWorldState", false,
                "Current people/project/situation/commitment state across time."),
        PERSONAL_MODEL(50, "Personal model", "NEXUS + feedback learning", false,
                "Interests, important relationships, goals, patterns and interruption preferences."),
        TRIAGE(60, "Candidate triage", "AttentionDecisionEngine + quality gates", false,
                "Cheap local pruning and feature scoring; broad enough to preserve uncertain valuable cases."),
        REASONING(70, "Selective reasoning", "internal Brain / reasoning routes", false,
                "Only ambiguous or high-value candidates receive deeper reasoning and planning."),
        JUDGMENT(80, "Final judgment", "CortexAttentionJudge", true,
                "Single owner decides whether, when and how strongly Cortex should interrupt or surface."),
        ACTION(90, "Action and approval", "proposal + action dispatcher", false,
                "Prepare deterministic actions and require approval where appropriate."),
        EXPERIENCE(100, "Experience and learning", "Now / contextual resurfacing / feedback", false,
                "Render one materialized result, capture outcomes, and feed learning upstream.");

        public final int order;
        public final String label;
        public final String owner;
        public final boolean teacherPolicyWritable;
        public final String purpose;

        Layer(int order, String label, String owner, boolean teacherPolicyWritable, String purpose) {
            this.order = order;
            this.label = label;
            this.owner = owner;
            this.teacherPolicyWritable = teacherPolicyWritable;
            this.purpose = purpose;
        }
    }

    private CortexIntelligenceArchitecture() {}

    public static boolean canTeacherWrite(Layer layer) {
        return layer != null && layer.teacherPolicyWritable;
    }

    /** Compact machine-readable contract included in every teacher Context Pack. */
    public static JSONObject teacherContract() {
        JSONObject root = new JSONObject();
        try {
            root.put("version", VERSION);
            root.put("flow", "Evidence > Perception > Knowledge > WorldState > PersonalModel > Triage > SelectiveReasoning > Judgment > Action > Experience");
            root.put("teacherRole", "Teach bounded JUDGMENT policy from grounded state and outcomes; do not become a source of truth or per-event dependency.");
            root.put("reasoningRule", "Reason only on uncertainty/high value. Easy cases stay local and deterministic.");
            root.put("hardRules", new JSONArray()
                    .put("Never mutate or strengthen raw evidence")
                    .put("Never create canonical facts without grounded evidence")
                    .put("Never bypass self-reference/provenance guards")
                    .put("Never execute external actions directly")
                    .put("Policy changes must be versioned, bounded, expiring and reversible")
                    .put("Prefer silence when expected value does not exceed interruption cost"));
            JSONArray layers = new JSONArray();
            for (Layer layer : Layer.values()) {
                JSONObject item = new JSONObject();
                item.put("order", layer.order);
                item.put("id", layer.name());
                item.put("label", layer.label);
                item.put("owner", layer.owner);
                item.put("teacherPolicyWritable", layer.teacherPolicyWritable);
                item.put("purpose", layer.purpose);
                layers.put(item);
            }
            root.put("layers", layers);
        } catch (Exception ignored) {}
        return root;
    }
}
