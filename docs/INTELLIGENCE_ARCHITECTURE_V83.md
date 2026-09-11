# Cortex Intelligence Architecture v83

## Product invariant

Cortex is one intelligence. Internal engines are implementation details. The user should not decide whether Now, NEXUS, Brain, PicBrain, or Knowledge is responsible for a situation.

## Canonical flow

`Evidence -> Perception -> Canonical Knowledge -> World State -> Personal Model -> Triage -> Selective Reasoning -> Final Judgment -> Action/Approval -> Experience/Learning`

Trust, provenance, privacy, and the self-reference firewall are cross-cutting guardrails across the entire flow.

## Layer ownership

| Order | Layer | Single owner | May decide what appears in Now? | ChatGPT teacher writes? |
|---|---|---|---|---|
| 10 | Evidence | UniversalEventStore + raw source stores | No | No |
| 20 | Perception | OCR / ASR / vision / semantic extraction | No | No |
| 30 | Canonical Knowledge | Knowledge V2 + entity graph | No | No |
| 40 | World State | StatefulMeaningStore + CognitiveWorldState | No | No |
| 50 | Personal Model | NEXUS + feedback learning | No | No |
| 60 | Candidate Triage | AttentionDecisionEngine + quality gates | No | No |
| 70 | Selective Reasoning | internal Brain routes | No; it enriches uncertain/high-value candidates | No canonical writes |
| 80 | Final Judgment | CortexAttentionJudge | **Yes, exclusively** | **Policy only** |
| 90 | Action / Approval | proposal + dispatcher/executor | No | No direct execution |
| 100 | Experience / Learning | Now + contextual resurfacing + feedback | Renders the materialized judgment only | Outcomes return to teacher context |

## Why this order

Evidence is preserved before interpretation. Extraction stays separate from truth. Canonical knowledge stays separate from current situation state. Personal relevance stays separate from objective state. Cheap triage eliminates obvious noise first. Expensive reasoning runs only on uncertain or high-value candidates. Final Judgment then has one owner that balances usefulness, urgency, confidence, personal relevance, current context, and interruption cost. Actions remain deterministic and approval-first. UI consumes a projection rather than recomputing policy.

This keeps the fast path local while still giving difficult cases access to deeper reasoning.

## ChatGPT Teacher boundary

ChatGPT teaches the Final Judgment policy. It is not inserted into the hot path of every event.

ChatGPT normally receives only:

- active situations
- compact personal-model signals
- canonical triage candidates
- uncertain cases that may deserve deeper reasoning
- recent outcomes/feedback
- current policy metadata
- evidence references when a disputed case needs inspection

ChatGPT normally returns only a bounded, versioned, expiring policy pack:

- judgment threshold
- bounded feature weights
- contextual boosts/suppressions
- interruption-cost tuning
- uncertainty escalation rules
- suggested situation merges or review requests

ChatGPT must not mutate raw evidence, invent canonical facts, bypass provenance/self-reference checks, or execute external actions.

## Migration rule

The move is shadow-first, not a big-bang rewrite.

1. Keep existing evidence and knowledge stores intact.
2. Build the canonical world-state, triage, selective-reasoning, and judgment path beside legacy derived-item paths.
3. Let the teacher consume canonical context first; label any legacy fallback explicitly.
4. Compare new vs legacy decisions in shadow telemetry.
5. Switch Now to a single materialized judgment projection only after coverage and quality are proven.
6. Retire duplicate final-ranking logic from NEXUS, MasterRelevanceFilter, ProactiveEngine, and Now UI after validation.

## Responsibility changes

- NEXUS becomes the Personal Model. It learns interests, patterns, relationships, goals, and interruption preferences. It does not own final surfacing.
- Brain becomes a selective internal reasoning service. It is invoked for ambiguous/high-value candidates and explanations; it is not a primary destination.
- ProactiveEngine becomes a candidate/state-change producer. It does not own final visibility.
- MasterRelevanceFilter becomes an upstream quality/noise gate. It does not own final priority.
- AttentionDecisionEngine becomes fast local Triage.
- CortexAttentionJudge becomes the sole final authority for surfacing and interruption timing.
- Now becomes a renderer of one materialized judgment projection, with compatibility fallback only during migration.

## Performance rule

Fast local decisions remain the default. ChatGPT teaches policy asynchronously and selectively. The last known good local policy always works offline. No notification, screenshot, or app launch waits for a cloud model call before Cortex can function.
