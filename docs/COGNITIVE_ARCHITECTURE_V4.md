# Cortex Cognitive Architecture V4

Status: design contract for `architecture/pulse-memory-worlds-think-v1`.

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

---

# 1. Evidence

`Evidence` is an immutable captured observation.

Examples:

- one notification revision,
- one meaningful accessibility screen state,
- one voice recording,
- one OCR result attached to an image,
- one shared URL or file,
- one manually entered note,
- one foreground-app transition.

Evidence is the strongest truth boundary in Cortex. AI may enrich it but never rewrite it.

## Identity

```text
Evidence
- id
- sourceType
- occurredAt
- capturedAt
- sourcePackage?
- externalId?
- originalText?
- normalizedText?
- contentHash?
- assetRef?
- metadata
- sensitivity
- retention
- processing
```

### `EvidenceSourceType`

```text
NOTIFICATION
SCREEN
VOICE
IMAGE
SHARE
NOTE
LINK
FILE
APP_ACTIVITY
CALENDAR
CONTACT
LOCATION
SYSTEM
```

### `Sensitivity`

```text
NORMAL
PRIVATE
RESTRICTED
```

`RESTRICTED` evidence is local-only unless the user explicitly overrides the policy.

### `RetentionClass`

```text
EPISODIC_90_DAY
PINNED
LONG_TERM_SOURCE
TRANSIENT
```

### `ProcessingState`

```text
CAPTURED
ENRICHING
READY
FAILED
```

Processing state is not the truth status of the evidence. A failed OCR job does not invalidate the original image.

## Evidence invariants

1. Original captured payload is immutable.
2. OCR/transcription/AI outputs are additive analysis records.
3. Duplicate callbacks may update capture metadata but must not invent duplicate evidence.
4. Password/secure-window content never becomes Evidence.
5. Every derived claim must ultimately point to one or more Evidence IDs or explicit user input.

---

# 2. Episode

`Episode` groups evidence that belongs to one coherent real-world or device-use event.

Examples:

- a WhatsApp conversation burst,
- a Chrome research session,
- a drive to work,
- a hospital visit,
- a document-review session,
- a voice-note recording and its later transcript.

An Episode is not a summary. It is a temporal grouping boundary.

```text
Episode
- id
- kind
- startedAt
- endedAt?
- primarySourcePackage?
- evidenceIds[]
- participantWorldIds[]
- topicWorldIds[]
- state
```

### `EpisodeKind`

```text
CONVERSATION
APP_SESSION
RESEARCH
MEETING
TRAVEL
CAPTURE
DOCUMENT_WORK
HEALTH_EVENT
GENERIC
```

### `EpisodeState`

```text
OPEN
CLOSED
REOPENED
```

## Episode rules

- Grouping may change as more evidence arrives.
- Episode identity must remain stable after creation.
- Re-grouping evidence must not delete evidence.
- One evidence item has one primary episode but may have secondary relations to other episodes/worlds.

---

# 3. Memory

`Memory` is a user-retrievable episodic unit grounded in evidence.

Evidence answers **what was captured**.
Memory answers **what happened that is worth being retrievable as one unit**.

Examples:

- "Ahmed asked for the latest plan."
- a screenshot and its OCR as one memory,
- a voice note plus transcript,
- a 20-minute Chrome research session represented as one memory with useful moments,
- a manually entered note.

```text
Memory
- id
- kind
- title?
- body
- startedAt
- endedAt?
- evidenceIds[]
- episodeId?
- sourcePackage?
- worldIds[]
- importance
- pinned
- retentionClass
- createdAt
- updatedAt
```

### `MemoryKind`

```text
MOMENT
CONVERSATION
SCREEN_CONTEXT
VOICE
IMAGE
DOCUMENT
NOTE
LINK
APP_SESSION
EPISODE_SUMMARY
```

## Memory granularity

Memory supports three presentation levels:

```text
Moment → Episode → Day
```

The database does not need three competing truth tables. These are grouping/read-model levels over grounded memories.

## Memory invariants

1. A Memory cannot contain a personal historical claim unsupported by Evidence or explicit user input.
2. AI-generated prose must never replace the original evidence text.
3. Memory presentation may collapse noise, but forensic source evidence remains reachable.
4. Deleting a Memory does not silently delete shared Evidence/assets required by another retained Memory.

---

# 4. World

`World` is a persistent context that makes multiple memories more useful when understood together.

A World can represent a person, project, health subject, company, car, place, trip, product, recurring topic, or other durable context.

Examples:

```text
Cortex
Health
Kia Sportage
Villa Al-Haram
NextCare
Nasser
Ahmed
Dubai trip
Camera research
```

The user does not need to care about the internal type in normal navigation.

```text
World
- id
- canonicalName
- typeHint
- maturity
- summary?
- aliases[]
- createdAt
- lastActiveAt
- archivedAt?
```

### `WorldTypeHint`

```text
PERSON
PROJECT
TOPIC
ORGANIZATION
PLACE
PRODUCT
ASSET
EVENT_SERIES
OTHER
```

Type is a hint, not the navigation hierarchy.

### `WorldMaturity`

```text
EMERGING
ESTABLISHED
DORMANT
ARCHIVED
```

## World identity rules

- High-confidence aliases may resolve automatically.
- Medium-confidence identities may be jointly retrieved without destructive merge.
- Low-confidence candidates remain separate.
- User corrections outrank model inference.
- Merges must remain reversible.
- Source identities are never destroyed by a World merge.

## World read model

A World should expose state, not merely a list of posts:

```text
Overview
Active threads
Recent decisions
Open questions
Important facts
People / related worlds
Important files
Recent activity
```

Every displayed state statement must be grounded in Facts or Memories.

---

# 5. Fact

`Fact` is an atomic proposition Cortex currently believes is useful beyond one raw event.

Examples:

- "The current Cortex branch is cleanup/repo-consolidation."
- "Ahmed is associated with Project X."
- "The project deadline is Sunday."

```text
Fact
- id
- subjectWorldId?
- predicate
- value
- grounding
- confidence
- validFrom?
- validUntil?
- evidenceIds[]
- memoryIds[]
- supersedesFactId?
- status
```

### `GroundingKind`

```text
OBSERVED
INFERRED
```

A recommendation is not a Fact.

### `FactStatus`

```text
ACTIVE
SUPERSEDED
DISPUTED
RETRACTED
```

## Fact rules

- Facts are versioned, not overwritten.
- Conflicting facts may coexist while one is disputed.
- `INFERRED` must always expose supporting evidence.
- User correction can retract or supersede a Fact.
- Confidence is confidence in the proposition, not attention score.

---

# 6. Relation

Relations connect canonical objects without collapsing their identities.

```text
Relation
- id
- sourceType
- sourceId
- targetType
- targetId
- relationType
- grounding
- confidence
- evidenceIds[]
- createdAt
- updatedAt
```

Examples:

```text
MEMORY → ABOUT → WORLD
WORLD → RELATED_TO → WORLD
FACT → SUPPORTED_BY → MEMORY
SITUATION → ABOUT → WORLD
EPISODE → INVOLVES → WORLD
```

---

# 7. Situation

`Situation` is the canonical unit behind Pulse.

A Situation is an unresolved or currently relevant state constructed from multiple signals. It is deliberately not the same object as a Memory or Fact.

Examples:

- Ahmed is waiting for the revised plan.
- A medical result is expected today.
- A deadline moved and now affects the user's plan.
- A meeting is approaching and preparation is incomplete.
- Several related events form a pattern worth surfacing.

```text
Situation
- id
- kind
- state
- headline
- explanation
- worldIds[]
- evidenceIds[]
- memoryIds[]
- factIds[]
- createdAt
- relevantFrom?
- relevantUntil?
- lastEvaluatedAt
- attentionScore
- interruptionScore
- confidence
- suggestedActions[]
```

### `SituationKind`

```text
COMMITMENT
WAITING
DEADLINE
UPCOMING_EVENT
MEANINGFUL_CHANGE
RISK
DECISION
OPPORTUNITY
UNRESOLVED_QUESTION
PREPARATION
PATTERN
FOLLOW_UP
```

This taxonomy is internal. Pulse copy should describe the real situation instead of exposing enum labels.

### `SituationState`

```text
DETECTED
RELEVANT
SURFACED
DEFERRED
WAITING
RESOLVED
CANCELLED
DISMISSED
```

### Lifecycle

```text
DETECTED
   ↓
RELEVANT
   ↓
SURFACED
 ┌─┼──────────┐
 ↓ ↓          ↓
DEFERRED   WAITING   RESOLVED
  │           │
  └────→ RELEVANT
```

Cancellation means reality changed.
Dismissal means the user does not want Cortex to keep surfacing it.

## Situation invariants

1. Situation identity consolidates duplicate signals about the same unresolved reality.
2. A notification is never automatically a Situation.
3. Importance and interruption are separate calculations.
4. A Situation may exist without being shown in Pulse.
5. Pulse should normally show 0–3 primary Situations, not fill the screen.
6. Resolution requires evidence, explicit user action, or deterministic lifecycle logic; viewing a card does not resolve it.

---

# 8. Action Proposal

Actions are proposals until an execution boundary accepts them.

```text
ActionProposal
- id
- situationId?
- worldId?
- type
- label
- risk
- payload
- state
```

### `ActionRisk`

```text
SAFE
CONFIRMATION_REQUIRED
SENSITIVE
BLOCKED
```

### `ActionState`

```text
PROPOSED
CONFIRMED
EXECUTING
COMPLETED
FAILED
CANCELLED
```

Reasoning may propose actions. Only the execution layer performs them.

---

# 9. Think / Reasoning Result

Think is not modeled as a chat transcript first. It is a reasoning request that produces typed grounded output.

```text
ThoughtRequest
- query
- contextWorldId?
- contextSituationId?
- explicitMemoryIds[]
- requestedAt
```

### `ThoughtIntent`

```text
RECALL
EXPLAIN
COMPARE
PLAN
REFLECT
ACT
FIND
```

A `ReasoningResult` contains blocks rather than assuming every answer is one text bubble.

```text
ReasoningResult
- intent
- blocks[]
- evidenceIds[]
- memoryIds[]
- factIds[]
- insufficientEvidence
- generatedAt
```

### `ReasoningBlockType`

```text
ANSWER
OBSERVATION
INFERENCE
SUGGESTION
EVIDENCE
TIMELINE
COMPARISON
PLAN
LIST
ASSET_RESULT
ACTION_PROPOSAL
WARNING
```

### Grounding contract

Every meaningful statement presented by Think is classified as:

```text
OBSERVED  — directly supported by evidence/facts.
INFERRED  — reasoning from supported evidence.
SUGGESTED — a recommendation, not a historical claim.
```

Rules:

1. `OBSERVED` requires citations to canonical evidence/memory/fact IDs.
2. `INFERRED` requires supporting citations and must remain visibly inferential.
3. `SUGGESTED` must never be written back as a historical Fact merely because the model proposed it.
4. If evidence is insufficient, Think says so explicitly.
5. Unknown citation IDs invalidate the affected block.

---

# 10. Surface contracts

## Pulse

Reads:

```text
Situations + supporting Worlds/Facts/Memories
```

Never uses capture mechanics as user-facing cards.

Success includes an empty state:

```text
You're clear.
Nothing needs your attention right now.
```

## Memory

Reads:

```text
Memories + Episodes + Evidence
```

Primary jobs:

```text
browse
search
inspect
verify
correct
pin
delete
connect
```

Search belongs here rather than requiring a separate top-level Search destination.

## Worlds

Reads:

```text
Worlds + Facts + Relations + Memories + Situations
```

Worlds are contextual workspaces, not entity-type folders.

## Think

Reads all canonical layers but mutates truth only through explicit correction/action contracts.

```text
Evidence
Memory
Worlds
Facts
Situations
```

---

# 11. Cross-layer invariants

These are release-blocking architecture rules.

1. **One truth hierarchy.** Pulse, Memory, Worlds and Think are projections, not competing stores.
2. **Evidence is immutable.** Derived intelligence is additive and versioned.
3. **No unsupported personal history.** Every historical claim has provenance.
4. **Inference is visibly inference.** Never promote a suggestion to observed history.
5. **Situation != notification.** Situations represent unresolved reality, not source callbacks.
6. **World merge is reversible.** Identity evidence survives consolidation.
7. **Attention != confidence.** Attention and interruption are independent of epistemic confidence.
8. **AI is optional.** Deterministic capture, memory retrieval and baseline situation logic remain useful without cloud generation.
9. **User correction wins.** Corrections update derived truth while preserving original evidence.
10. **No UI-owned truth.** Cards and screens never become canonical state.

---

# 12. Migration map from current Cortex

This is a migration aid only; legacy names do not define the new architecture.

```text
raw_signals / captured items
    → Evidence

threads / app-session-like grouping
    → Episode candidates

knowledge items / retained source
    → Memory

entities + confirmed projects + topic clusters
    → World

entity/source links
    → Relation

derived_items that describe durable propositions
    → Fact

derived_items that describe unresolved current state
    → Situation

PrimeBriefStore / Today projection
    → Pulse read model

Ask operational + retrieval + reasoning paths
    → Think router / ReasoningResult
```

Migration must be additive first. Existing rows remain readable until their replacement path has passed device validation.

---

# 13. Implementation order

## Stage A — contracts only

- Introduce domain types and invariants.
- No UI replacement.
- No destructive schema migration.

## Stage B — canonical persistence

- Add Evidence/Episode/Memory/World/Fact/Situation schema alongside legacy tables.
- Add provenance and versioning first.
- Backfill incrementally.

## Stage C — Memory projection

- Build 90-day episodic Memory over the canonical model.
- Add deterministic search and forensic source inspection.

## Stage D — Worlds

- Build persistent context formation, maturity and reversible identity merge.

## Stage E — Pulse

- Replace card-level derived-item thinking with Situation lifecycle and consolidation.

## Stage F — Think

- Route recall/reason/plan/action through typed grounded results.

## Stage G — replace old navigation

Only after the four new projections pass real-device validation does Cortex switch its primary navigation to:

```text
Pulse | Memory | Worlds | Think
```

Old surfaces are removed only after equivalent or better functionality is verified.
