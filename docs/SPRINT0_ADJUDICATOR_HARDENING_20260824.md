# Cortex Sprint 0 — Adjudicator Production Hardening

Date: 2026-08-24
Source: user-provided concurrency/correctness review, verified against current `main` before implementation.

## Product invariant

Failure state is not a semantic disposition.

Semantic dispositions remain only:
- ACTION
- WAITING
- DECISION
- REVIEW
- CONTEXT
- plus existing deterministic IGNORE/MEMORY where applicable.

Execution outcomes such as INVALID MODEL OUTPUT, SUPERSEDED, APPLY FAILED, or SENSITIVE BLOCK are tracked separately in model runs / diagnostics / relevance execution status.

## P0 review disposition

### P0.1 / P0.2 — debounce race / removing a newer future
The submitted review described a two-map implementation (`LATEST_SIGNAL` + `SCHEDULED_FUTURES`). The verified `main` at review time actually had one `LATEST` map, so that exact interleaving was not literally present.

The underlying authority/cancellation concern was valid and was fixed more strongly:
- one `Slot` per thread in one `ConcurrentHashMap`
- replacement through `SLOTS.compute(...)`
- generation id per slot
- old timer cancelled inside the same per-thread compute
- completion cleanup uses `SLOTS.remove(threadId, mySlot)` compare-and-remove

### P0.3 — authority removed before Qwen / no post-Qwen freshness guard
Confirmed and fixed.
- slot remains authoritative throughout inference
- freshness is checked before inference
- freshness is checked again after inference
- DB guard uses newest signal ordered by `occurred_at DESC, id DESC`
- final apply re-checks freshness inside its short transaction
- superseded model output is telemetry only and performs no semantic mutation

### Scheduler/model executor separation
Implemented.
- `SCHEDULER` only handles debounce timers
- `MODEL_EXECUTOR` is single-threaded for local Qwen
- one long Qwen run no longer blocks debounce timers for other threads

### P0.4 — low-confidence durable result marked as durable while final policy says REVIEW
Confirmed and fixed.
When an ACTION/WAITING/DECISION model opinion is below auto-promote but above Review floor, the signal is marked with the actual `reviewDecision` (REVIEW + candidate), not the raw durable model decision.

### P0.5 — malformed output becomes fake CONTEXT
Confirmed and fixed.
Typed validation statuses now distinguish:
- VALID
- NORMALIZED
- INVALID_JSON
- INVALID_DISPOSITION
- INVALID_CANDIDATE
- INVALID_CONFIDENCE
- INVALID_SEMANTICS

Invalid model output has no synthetic semantic decision, does not enter adaptive learning, and does not populate model semantic evaluation fields. The model run records the invalid outcome instead.

### P0.6 — persistence failure could still finalize ACTION/REVIEW
Confirmed and fixed in both the model and deterministic thread paths.
- derived/review persistence success is required before signal/final semantic transition
- a failed persistence operation rolls back the transition
- final semantic fields are cleared to PENDING before deterministic application and written only with the successful apply transaction
- APPLY_FAILED is separate execution status

### P0.7 — final apply needed a transaction
Confirmed and fixed.
- Qwen runs outside any DB transaction
- post-model freshness and semantic application happen inside a short transaction
- deterministic thread decisions also use a short atomic application transaction

## P1 review disposition

### Typed baseline instead of lifecycle string inference
Implemented for model adjudication.
`ThreadSnapshot` now carries a typed baseline `MasterRelevanceFilter.Decision`, preferring learned deterministic evaluation fields and falling back to raw signal semantics. Lifecycle state is retained only for lifecycle gating.

### REVIEW_FLOOR
Implemented at `0.60` for creation of new model Reviews.
Existing deterministic/user-facing Review items are preserved even when the new model opinion is lower-confidence or CONTEXT.

### Existing Review + model CONTEXT
Implemented explicitly as `PRESERVE_REVIEW`.
A local model disagreement cannot silently dismiss an existing human-review item.

### Duplicate durable intelligence
Implemented in deterministic and model thread paths.
Open derived intelligence is refreshed by `thread_id + kind` instead of creating a new ACTION/WAITING/DECISION for each added notification context.

### Prompt injection / structure
Implemented.
- message content is explicitly UNTRUSTED DATA in system and user instructions
- communication context is structured JSON
- model receives bounded per-message text

### Directionality
Implemented in the structured prompt.
Values are normalized to:
- `RECEIVED_FROM_OTHER`
- `SENT_BY_SELF`

Current Android notification ingestion defaults to received when no explicit direction exists. Future outgoing capture paths must supply direction metadata.

### Sensitive content
Implemented conservatively.
- sensitive latest message blocks model adjudication
- sensitive older messages are redacted from the model prompt rather than blinding the whole recent thread

### Deterministic ordering
Implemented.
- adjudicator load: `occurred_at DESC, id DESC`
- thread recent context: `occurred_at DESC, id DESC`
- active-thread tie breaks also use id

### Raw model text retention
Implemented.
- production telemetry stores raw output hash + character count
- clipped raw model text is retained only under `BuildConfig.DEBUG`

## Additional correctness issue found during verification — dual authority
A pre-existing issue was discovered outside the submitted review:
- `RawSignalStore` computed a fast decision
- `ThreadRelevanceEngine` then computed a thread-aware decision
- the old fast decision could still promote the signal afterward

This could cause promotion despite a conflicting thread decision and could create duplicate derived intelligence.

Fixed:
- thread-aware decision is authoritative when available
- fast gate is authoritative only for signals with no thread-aware policy
- knowledge-item materialization occurs only after the authoritative thread transition reports `APPLIED`
- thread-aware promotion does not create a second derived item

## Additional real-device issue found from user screenshots — Google Messages classification
`com.google.android.apps.messaging` was not guaranteed to match the old `messages` source substring rule.

Fixed:
- `messaging` sources are communication sources
- notification metadata `notification_kind=message` also forces communication classification
- `notification_kind=email` can force email classification

## Runtime validation required
All changes above are code-complete only until the Android project compiles and the installed build is exercised on-device.

Required validation after compile:
1. Burst two notifications in one thread during/around local-model inference; only newest applicable result may mutate state.
2. Invalid JSON model output must not appear as CONTEXT in semantic evaluation.
3. Model ACTION at confidence between Review floor and auto-promote must end as REVIEW(candidate=ACTION).
4. Existing Review + model CONTEXT must remain Review.
5. Persistence failure injection should leave no final durable semantic transition.
6. Same-thread repeated obligation should refresh one open derived item, not create duplicates.
7. Google Messages notification should be classified into a communication thread.
8. Relevance evaluation failures/supersession must be visible as non-semantic execution status/model-run state.
