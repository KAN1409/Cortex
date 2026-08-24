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
Initial implementation refreshed open derived intelligence by `thread_id + kind`. A second hostile review correctly identified that this could merge distinct obligations in one conversation. This was subsequently replaced with `thread_id + kind + semantic_key`; see the second review section below.

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

---

# Second hostile runtime review — disposition

Date: 2026-08-24
Scope: transaction helper behavior, queued-model staleness, durable-item identity, process death, and sensitive-context semantics.

## 1. SQLite transaction creep / helper nesting

### Literal nested-transaction claim
Not reproduced in the verified implementation.
`CognitiveStore` and `ReviewQueueStore` do not call `beginTransaction()` from helpers used inside adjudicator apply, so there was no hidden nested `beginTransaction()` crash path of the exact form described.

### Failure-propagation concern
Confirmed.
Several Review helper paths could log/catch failures or perform unchecked routing/provenance writes and still allow callers to treat the operation as successful.

Hardened:
- `CognitiveStore.linkChecked(...)` verifies that a provenance relation either inserted or already exists.
- `CognitiveStore.setDerivedRoutingChecked(...)` returns success/failure.
- Review refresh now returns false if the row update or supporting link fails.
- Review resolve returns a boolean and only resolves pending items.
- model promotion / confirmation require successful routing, provenance links, and Review resolution.
- model apply treats any helper failure as `APPLY_FAILED`, so the outer transaction does not become successful.
- `RelevanceDecisionStatusStore.writeModel/writeFinal` are now pure transaction writers; schema/DDL checks happen before `beginTransaction()` rather than inside the final apply transaction.

Invariant:
> A helper may return an existing id for an idempotent success, but it may not swallow a persistence failure and present it as a successful state transition.

## 2. MODEL_EXECUTOR queue backlog staleness

The review concern was valid to verify, but the current Slot architecture already prevented the described stale queued run from mutating state:
- `fire()` checks the Slot before queueing model work.
- `adjudicate()` re-checks `stillCurrent(...)` immediately after entering the single-thread MODEL_EXECUTOR and before context loading or Qwen inference.
- currentness requires the exact Slot object and matching generation.
- DB latest signal must also match the Slot signal.
- cleanup uses `SLOTS.remove(threadId, slot)`, so a stale finisher cannot remove a newer Slot.
- post-Qwen and transaction-time freshness checks remain in place.

Generation equality was made explicit in `isCurrent(...)` for audit clarity.

## 3. Over-aggressive `threadId + kind` dedup

Confirmed as a correctness bug.
A conversation may contain several simultaneous ACTIONs, WAITING items, or decisions.

Example:
- `Send the Galala submittal`
- later: `Call the marble supplier`

Both belong to the same WhatsApp thread and both are ACTIONs, but they must remain separate obligations.

Implemented:
- added `derived_items.semantic_key` plus an indexed route for `(thread_id, kind, state, semantic_key)`.
- added `DerivedSemanticIdentity`, a conservative lexical identity derived from the latest obligation-bearing evidence.
- deterministic and model durable upserts now refresh only `thread + kind + semantic_key`.
- Review dedup uses `thread + candidate kind + source + semantic_key` in the adjudicator path.
- older broad Review lookup overloads remain for compatibility with legacy callers.
- conservative matching intentionally prefers an occasional duplicate over merging two different obligations.

### Recurrence after resolution
A second edge case was found during implementation: a semantic fingerprint must not permanently prevent the same obligation from recurring after it was completed or expired.

`CognitiveStore.addDerived(...)` now treats `open/pending` fingerprint collisions as idempotent active occurrences, while a collision against a resolved/inactive historical item archives the old fingerprint and allows a new active occurrence.

## 4. Process death / RAM Slot loss

Confirmed as an operational gap, but the proposed mitigation `reset raw signal to CONTEXT` was rejected because it would mutate semantic truth merely because Android killed the process.

Implemented instead:
- new `AdjudicationRecovery` scans stale `relevance_adjudication` jobs older than five minutes.
- interrupted jobs are marked `PROCESS_INTERRUPTED`/failed operationally.
- unfinished model runs are marked interrupted.
- relevance `model_status` is set to `PROCESS_INTERRUPTED`.
- the raw signal's deterministic/semantic baseline is preserved.
- only if that signal is still the newest signal in the thread is it safely re-enqueued for local adjudication.
- recovery runs at Cortex process bootstrap.
- a unique periodic WorkManager recovery job runs as a belt-and-suspenders check.

Invariant:
> Process death is an execution failure, not semantic CONTEXT.

## 5. Sensitive redaction distortions

The review correctly noted that a placeholder can distort syntax.

Hardened:
- latest sensitive message still blocks model adjudication entirely.
- older sensitive messages remain available only as explicit context markers.
- structured prompt now sets `sensitive_redacted=true`.
- redacted text is labeled `[SENSITIVE CONTENT REDACTED — CONTEXT ONLY]`.
- system policy explicitly says a redacted message must never create ACTION, WAITING, or DECISION by itself.

This preserves surrounding conversation continuity without allowing a broken redacted fragment to become durable intelligence.

## Runtime validation required after the second review

1. Compile current `main` successfully.
2. Queue a stale model task behind another long Qwen run, then replace its Slot; stale queued work must not invoke Qwen or mutate state.
3. Send two different ACTIONs in one communication thread; they must create two separate open derived items.
4. Repeat the same ACTION while still open; it should refresh/support the same item.
5. Resolve that ACTION, then repeat it later; a new active occurrence must be allowed.
6. Force a Review routing/provenance persistence failure; final semantic state must remain uncommitted and execution status must become APPLY_FAILED.
7. Simulate/process-kill a running relevance job; bootstrap/WorkManager must mark it PROCESS_INTERRUPTED and re-enqueue only if its signal is still newest.
8. Older sensitive evidence must be marked context-only and must not independently create a durable result.
9. Latest sensitive evidence must block model adjudication.

## Build note

This repository does not use a checked-in Gradle wrapper. The authoritative Termux build path remains:

```bash
bash ~/Cortex/termux-build-cortex.sh
```

Do not substitute `./gradlew assembleDebug` unless a wrapper is added later.
