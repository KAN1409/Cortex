# Cortex Cognitive Architecture V4

Status: active draft architecture for `architecture/pulse-memory-worlds-think-v1`.

This document replaces the previous UI-first mental model with four product surfaces built over one canonical cognitive model:

- **Pulse** — what matters now.
- **Memory** — what actually happened.
- **Worlds** — what persistent contexts mean.
- **Think** — reasoning, recall, planning and action over grounded memory.

The surfaces are projections. They are not separate truth stores.

## Product rules

### Pulse
> Don't tell me what happened. Tell me what matters.

### Memory
> Don't interpret history away. Preserve what actually happened.

### Worlds
> Don't organize by file type. Organize by meaning.

### Think
> Don't chat about my data. Reason over it.

## Canonical hierarchy

```text
Phone / user
    ↓
Evidence
    ↓
Episodes
    ↓
Memories
    ↓
Facts + Worlds + Relations
    ↓
Situations
    ↓
Pulse

Evidence + Memories + Facts + Worlds + Situations
    ↓
Think
```

No layer may destroy the provenance of the layer below it.

## Canonical objects

### Evidence
Immutable captured observation. Duplicate callbacks may update observation metadata but never rewrite the original captured payload. OCR/transcription/AI enrichment is additive.

### Episode
Stable temporal/context grouping of Evidence. Membership may evolve without changing Episode identity.

### Memory
User-retrievable episodic unit grounded in Evidence. Generated title/summary is presentation, not identity.

### World
Persistent context such as person, project, topic, organization, place, product, asset, or recurring event series. Type is an internal hint, not the navigation hierarchy.

### Fact
Versioned proposition Cortex currently believes. Observed and inferred Facts both require provenance before persistence.

### Relation
Grounded edge between canonical objects. Similarity may suggest a Relation but never silently collapse identities.

### Situation
Unresolved/currently relevant reality behind Pulse. A notification is not a Situation. Long-lived obligations keep one identity across repeated signals; event-shaped Situations require an occurrence discriminator.

### Action Proposal
Proposed execution step. Reasoning may propose; only the execution layer performs it.

### Think / Reasoning Result
Typed grounded output. Statements are `OBSERVED`, `INFERRED`, or `SUGGESTED`. Observed/inferred blocks require citations; suggestions are never historical Facts merely because a model generated them.

## Surface contracts

### Pulse
Reads Situations + supporting Worlds/Facts/Memories.

### Memory
Reads Memories + Episodes + Evidence. Search belongs here.

### Worlds
Reads Worlds + Facts + Relations + Memories + Situations.

### Think
Reads every canonical layer and mutates truth only through explicit correction/action/consolidation contracts.

## Release-blocking invariants

1. **One truth hierarchy.** Pulse, Memory, Worlds and Think are projections, not competing stores.
2. **Evidence is immutable.** Derived intelligence is additive and versioned.
3. **No unsupported personal history.** Every observed/inferred historical claim has provenance.
4. **Inference is visibly inference.** Never promote a suggestion to observed history.
5. **Situation != notification.** Situations represent unresolved reality, not source callbacks.
6. **World merge is reversible.** Identity evidence survives consolidation.
7. **Identity != similarity.** Embeddings/retrieval similarity never authorize destructive merge.
8. **Attention != confidence.** Attention and interruption are independent of epistemic confidence.
9. **AI is optional.** Deterministic capture, memory retrieval and baseline situation logic remain useful without cloud generation.
10. **User correction wins.** Corrections update derived truth while preserving original evidence.
11. **No UI-owned truth.** Cards and screens never become canonical state.

## Migration map from current Cortex

```text
raw_signals / captured items → Evidence
threads / app-session-like grouping → Episode candidates
knowledge items / retained source → Memory
entities + confirmed projects + topic clusters → World
entity/source links → Relation
derived durable propositions → Fact
derived unresolved current state → Situation
PrimeBriefStore / Today projection → Pulse read model
Ask operational + retrieval + reasoning → Think router / ReasoningResult
```

Migration is additive first. Existing rows remain readable until replacement paths pass device validation.

## Implementation state

### Stage A — Domain contracts
Implemented:

- `CognitiveDomainV4`
- canonical types and lifecycles
- surface-independent grounding categories

### Stage B — Canonical persistence and identity
Implemented in draft:

- `CognitiveSchemaV4` additive `v4_*` tables
- `CognitiveIdentityV4` deterministic Evidence/Fact/Relation/Situation identity
- conservative World identity claims with strong/medium/weak strength
- reversible `v4_world_merges`
- `CognitiveStoreV4` canonical write boundary
- generic `v4_provenance`
- `v4_legacy_map` for non-destructive backfill
- `CognitiveGroundingV4`
- identity, grounding and schema regression tests
- `CognitiveStore.ensure()` creates the additive V4 schema alongside the existing schema

The schema bootstrap only creates empty V4 tables. Existing capture, analysis, Now, Inbox, Atlas and Ask writes/reads remain on the current authoritative paths until explicit backfill/cut-over stages are validated.

There is deliberately no `pulse_cards`, `memory_screen_rows`, `world_dashboard`, or `think_answers` truth store.

### Next — Stage C: Memory projection

Build the first read projection over V4:

```text
Memory = Evidence + Episodes + grounded Memory objects
```

The first cut-over target is Memory/search rather than Pulse. This validates episodic truth, retention boundaries, and retrieval before the new Situation engine controls the home surface.

### Later

- Stage D — Worlds projection and identity resolution
- Stage E — Pulse Situation lifecycle
- Stage F — Think grounded router
- Stage G — switch primary navigation only after all four projections pass real-device validation

Final navigation target:

```text
Pulse | Memory | Worlds | Think
```
