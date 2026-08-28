# Cortex V4 — Stage C Memory Projection

Status: additive implementation checkpoint on `architecture/pulse-memory-worlds-think-v1`.

## Goal

Build the first user-history projection on the V4 truth hierarchy without switching the current product UI yet.

```text
legacy Cortex stores
      ↓ bounded/idempotent bridge
Evidence
      ↓
Episode
      ↓
Memory
      ↓
V4 Memory read/search projection
```

The current Now / Inbox / Atlas / Ask surfaces remain untouched during this checkpoint.

## Backfill policy

`CognitiveMemoryBackfillV4` migrates at most a bounded number of rows per layer on each run.

Migration order is deliberate:

1. `raw_signals` → `Evidence`
2. `signal_threads` → `Episode`
3. `knowledge_items` → grounded `Memory`

`v4_legacy_map` is the idempotence cursor. Legacy rows are never deleted or rewritten.

### Raw signals

Raw signals inside the episodic window become Evidence only.

A filtered notification or screen observation does **not** become a Memory merely because Cortex captured it.

Existing legacy retention deadlines are preserved. Durable promoted signals without a legacy expiry receive the normal 90-day V4 episodic horizon.

### Threads

A legacy signal thread becomes an Episode only after at least one member Evidence row has been mapped.

Missing prerequisites defer the thread instead of creating an ungrounded Episode.

Communication/email threads map to `CONVERSATION`; device/app context maps conservatively to the closest Episode kind.

### Knowledge items

Durable `knowledge_items` become Memories.

If a knowledge item came from a raw signal, its Memory reuses that Evidence rather than creating a second historical observation.

Manual/imported knowledge without a raw signal receives its own Evidence object first, then the Memory is created from that Evidence.

Legacy extracted text, summaries and model analyses are appended as `v4_evidence_analysis`; they never replace original evidence text.

## Memory read model

`CognitiveMemoryProjectionV4` reads canonical V4 tables only.

It deliberately does not silently mix legacy fallback rows. This makes migration coverage and equivalence measurable before cut-over.

Supported deterministic constraints:

- text retrieval over Memory title/body,
- original Evidence text,
- OCR/transcript/analysis output,
- date range,
- exact source package,
- Memory kind,
- pinned-only,
- retention visibility,
- bounded result count.

Source/kind/date/pinned filters are hard constraints rather than ranking hints.

Text matching uses literal `INSTR` rather than wildcard `LIKE`, so `%` and `_` in a user query do not change query semantics.

## Forensic drill-down

Every Memory row exposes its Evidence count.

`evidence(memoryId)` returns:

```text
Evidence ID
source type
source package
time
original captured text
asset reference
processing state
all additive analyses with engine/version
```

This is the boundary needed for the future Memory Detail screen to distinguish:

```text
ORIGINAL CAPTURE
OCR / TRANSCRIPTION
AI / ANALYSIS
```

## Retention

Active read queries hide expired unpinned Memories.

Pinned Memories remain visible past the episodic deadline. Before retention deletion is enabled, backing Evidence for pinned/long-term Memories must also be protected so a retained Memory never loses its forensic source.

## Regression gates

`CognitiveMemoryProjectionV4RegressionTest` verifies:

1. OCR text is searchable without pretending OCR is original capture text.
2. source and kind filters remain hard constraints.
3. `%` and `_` are literal search characters.
4. expired unpinned Memories disappear while pinned Memories remain.
5. legacy source-type mapping stays deterministic.

## Not yet enabled

This checkpoint does not yet:

- replace the existing UI,
- redirect current capture writes to V4,
- run retention deletion,
- add semantic/vector retrieval to V4,
- create Worlds,
- create Pulse Situations,
- route Think through V4.

Next after compile/device-safe validation: wire a forward-only V4 capture bridge and run projection equivalence diagnostics before presenting Memory as a primary navigation surface.
