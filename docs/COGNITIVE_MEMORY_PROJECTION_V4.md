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

### Threads

A legacy signal thread becomes an Episode only after at least one member Evidence row has been mapped.

Missing prerequisites defer the thread instead of creating an ungrounded Episode.

Communication/email threads map to `CONVERSATION`; device/app context maps conservatively to the closest Episode kind.

### Knowledge items

Durable `knowledge_items` become Memories.

If a knowledge item came from a raw signal, its Memory reuses that Evidence rather than creating a second historical observation.

Manual/imported knowledge without a raw signal receives its own Evidence object first, then the Memory is created from that Evidence.

Legacy extracted text, summaries and model analyses are appended as `v4_evidence_analysis`; they never replace original evidence text.

## Forward capture bridge

`CognitiveMemoryForwardBridgeV4` writes a canonical Evidence revision immediately after a new raw signal is accepted by the existing capture path. The V4 write is failure-isolated: a V4 error must never cause the legacy capture to fail.

The forward bridge performs only deterministic local persistence. It does not run AI, search, embeddings or migration work inside notification/accessibility capture callbacks.

## Realtime durable projection

`CognitiveRealtimeProjectionV4` closes the latency gap between a meaningful live connector event and Stage-E Pulse.

It is deliberately **not** a second relevance governor. It runs asynchronously only after the existing legacy relevance pipeline has already promoted the signal into a durable `knowledge_item`.

For a promoted notification it:

1. reuses the already-written immutable V4 Evidence;
2. reads additive `CONNECTOR_ENRICHMENT` text when the trusted connector payload is richer than the legacy/native preview;
3. materializes the same deterministic Memory identity that bounded backfill would later create;
4. records the normal legacy→Memory map so later backfill remains idempotent;
5. refreshes Situations and reconciles existing Deep Brain state.

This means a tunneled WhatsApp request such as an explicit deadline can become a canonical Memory/Situation promptly instead of waiting for the next startup/WorkManager migration batch.

The original Evidence text is never overwritten. Connector text remains additive analysis provenance; only the interpretive Memory projection may prefer the richer trusted analysis text.

Context-only or ignored notifications still stop before Memory because `promoted_item_id` remains zero.

## Historical rescue

`CognitiveMemoryHistoricalRescueV4` rescues still-present `raw_signals` within the 90-day episodic window even when their old legacy short-retention deadline has passed. It cannot reconstruct rows already deleted by older builds.

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

Text matching uses literal `INSTR`, so `%` and `_` in a user query do not change query semantics.

## Forensic drill-down

Every Memory row exposes its Evidence count.

`evidence(memoryId)` returns Evidence ID, source type/package, time, original captured text, asset reference, processing state and additive analyses with engine/version.

This is the boundary needed for the future Memory Detail screen to distinguish ORIGINAL CAPTURE, OCR/TRANSCRIPTION and AI/ANALYSIS.

## Retention

Active read queries hide expired unpinned Memories.

Pinned Memories remain visible past the episodic deadline. Backing Evidence for pinned/long-term Memories is protected before destructive V4 retention is enabled.

## Equivalence gate

`CognitiveMemoryEquivalenceV4` measures:

- eligible raw signals vs mapped V4 Evidence;
- eligible threads vs mapped V4 Episodes;
- eligible knowledge items vs mapped V4 Memories;
- active Memories without Evidence;
- broken Memory→Evidence links;
- legacy mappings whose canonical target is missing;
- user-visible content-loss mismatches.

A future Memory UI cut-over requires both `migrationComplete == true` and `integrityClean == true`. Equivalent release gate: `cutoverReady == true`.

## Validation checkpoint — 2026-08-28

Real Termux compile gate passed at branch head `45575e7b07aee75958d3680c2ec122f90f6b2b99`:

- `:app:assembleDebug` — PASS
- `:app:assembleDebugAndroidTest` — PASS
- Gradle reported `BUILD SUCCESSFUL` with 65 actionable tasks executed.

The Termux AAPT2 environment emitted repeated `No package ID 7f found` diagnostics, but resource processing, Java/Kotlin compilation, AndroidTest packaging and APK assembly all completed successfully. For this validated build these messages are non-fatal noise.

Newer realtime connector projection changes require a fresh compile gate before device installation.

## Safety rules

- no destructive migration;
- no deletion of existing Cortex data during Stage C;
- Evidence is immutable;
- identity is not semantic similarity;
- observed/inferred personal claims require provenance;
- suggestions never become historical facts automatically;
- capture callbacks never wait on migration/search/AI work;
- realtime projection may only materialize already-authoritatively-promoted durable items;
- current product surfaces remain authoritative until staged cut-over validation.
