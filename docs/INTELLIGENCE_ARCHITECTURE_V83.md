# Cortex Intelligence Architecture v83

## Product invariant

Cortex is one intelligence. Internal engines are implementation details. The user should not decide whether Now, NEXUS, Brain, PicBrain, or Knowledge is responsible for a situation.

## Canonical flow

`Evidence -> Perception -> Canonical Knowledge -> World State -> Personal Model -> Attention/Judgment -> Reasoning -> Action/Approval -> Experience/Learning`

Trust, provenance, privacy, and the self-reference firewall are cross-cutting guardrails across the entire flow.

## Layer ownership

| Order | Layer | Single owner | May decide what appears in Now? | ChatGPT teacher writes? |
|---|---|---|---|---|
| 10 | Evidence | UniversalEventStore + raw source stores | No | No |
| 20 | Perception | OCR / ASR / vision / semantic extraction | No | No |
| 30 | Canonical Knowledge | Knowledge V2 + entity graph | No | No |
| 40 | World State | StatefulMeaningStore + CognitiveWorldState | No | No |
| 50 | Personal Model | NEXUS + feedback learning | No | No |
| 60 | Attention / Judgment | AttentionDecisionEngine + CortexAttentionJudge | **Yes, exclusively** | **Policy only** |
| 70 | Reasoning | internal Brain routes | No; it advises Judgment/Action | No canonical writes |
| 80 | Action / Approval | proposal + dispatcher/executor | No | No direct execution |
| 90 | Experience / Learning | Now + contextual resurfacing + feedback | Renders the materialized decision only | Outcomes return to teacher context |

## Why this order

Evidence is preserved before interpretation. Extraction stays separate from truth. Canonical knowledge stays separate from current situation state. Personal relevance stays separate from objective state. Attention has one final owner. Expensive reasoning is escalated only after relevance has been established. Actions remain deterministic and approval-first. UI consumes a projection rather than recomputing policy.

## ChatGPT Teacher boundary

ChatGPT is attached at the policy boundary between Personal Model and Attention/Judgment.

ChatGPT normally receives only:

- active situations
- compact personal-model signals
- canonical attention candidates
- uncertain cases
- recent outcomes/feedback
- current policy metadata
- evidence references when available

ChatGPT normally returns only a bounded, versioned, expiring policy pack:

- attention threshold
- bounded feature weights
- contextual boosts/suppressions
- interruption-cost tuning
- escalation rules for uncertainty
- suggested situation merges or review requests

ChatGPT must not mutate raw evidence, invent canonical facts, bypass provenance/self-reference checks, or execute external actions.

## Migration rule

The move is shadow-first, not a big-bang rewrite.

1. Keep existing evidence and knowledge stores intact.
2. Build the canonical world-state and attention path beside legacy derived-item paths.
3. Let the teacher consume canonical context first; label any legacy fallback explicitly.
4. Compare new vs legacy decisions in shadow telemetry.
5. Switch Now to a single materialized attention projection only after coverage and quality are proven.
6. Retire duplicate final-ranking logic from NEXUS, MasterRelevanceFilter, ProactiveEngine, and Now UI after validation.

## Responsibility changes

- NEXUS becomes the Personal Model. It learns interests, patterns, relationships, goals, and interruption preferences. It does not own final surfacing.
- Brain becomes an internal reasoning service. It handles ambiguous/high-value cases and explanations; it is not a primary destination.
- ProactiveEngine becomes a candidate/state-change producer. It does not own final visibility.
- MasterRelevanceFilter becomes an upstream quality/noise gate. It does not own final priority.
- AttentionDecisionEngine + CortexAttentionJudge become the sole final attention authority.
- Now becomes a renderer of one materialized attention projection, with compatibility fallback only during migration.

## Performance rule

Fast local decisions remain the default. ChatGPT teaches policy asynchronously and selectively. The last known good local policy always works offline. No notification, screenshot, or app launch waits for a cloud model call before Cortex can function.
