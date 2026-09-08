# Cortex V2 Recovery Audit

Branch: `v2/cortex-recovery-zero-api`
Baseline commit: `8baa00a6ea76234b6dc3a1264541d621daaf7133` (`1.0.22`)

## Product constraints

1. Cortex remains Cortex: an active assistant/second brain, not LifeOS renamed.
2. Normal operation must work on cellular data (5G) and must not require Wi-Fi.
3. Shizuku is optional privilege acceleration, never a core dependency or single point of failure.
4. Production Cortex must require **zero paid API usage**. Free-tier cloud quotas are not a production dependency.
5. Voice notes must continue to be captured durably and transcribed. The raw recording is the immutable source of truth.
6. Existing mixed Egyptian-Arabic/English transcription behavior is a quality baseline to beat, not code that may be casually deleted.
7. No UI capability is considered real unless the end-to-end path exists and is testable.

## What exists in the current baseline

| Area | Current state | Decision |
|---|---|---|
| Voice capture | Real 16 kHz mono PCM16 WAV capture in app-private storage | **KEEP / PROTECT** |
| Raw audio retention | Audio path is stored with the knowledge item before analysis | **KEEP / HARDEN** |
| Current voice ASR | `AudioAnalyzer` routes to `CloudAudioTranscriber` | **REPLACE BEFORE V2 SHIP** |
| Current ASR backend | Server chain: OpenAI transcription -> Google Chirp 3 -> Azure Speech | **LEGACY REFERENCE ONLY**; incompatible with zero-paid-API rule |
| Retry | WorkManager retries retryable cloud failures | **REUSE PATTERN**, provider must become local-first |
| Transcript model | `TranscriptResult` supports verbatim text, language, duration, timed segments | **KEEP / EXTEND** |
| Local ASR history | A previous branch had Whisper.cpp, VAD, code-switch rescue and model selection | **RECOVER AS CANDIDATE**, benchmark before promotion |
| OCR | ML Kit Latin + Tesseract Arabic paths exist | **AUDIT / BENCHMARK** |
| Memory DB | SQLite `VaultDb`, knowledge items, actions, examples | **KEEP**, then audit schema/lifecycle/indexing |
| Search | Lexical + local semantic recall | **AUDIT FOR SCALE AND TRUTH** |
| Ask | Grounded retrieval from saved memory | **KEEP CONCEPT**, improve reasoning locally |
| Proactive | Local rules/scheduler exist | **AUDIT**, no fabricated urgency |
| UI | Cortex Prime identity and workflows exist | **KEEP PRODUCT IDENTITY**, simplify only after functional audit |
| Shizuku | No current production integration in this baseline | **ADD AS OPTIONAL ADAPTER ONLY** |
| Gemini Nano | Not currently integrated | **ADD FOR LOCAL NLU/REASONING AFTER DEVICE PROBE** |

## Protected voice baseline

The following behavior is frozen as a regression contract while V2 is rebuilt:

- microphone permission -> recording starts;
- recording is 16 kHz / mono / PCM16 WAV;
- stopping with Save stores the original file before transcription;
- cancel deletes only the cancelled recording;
- analysis failure must never delete the WAV;
- verbatim transcript is stored separately from summaries/metadata;
- English words inside Arabic speech must not be translated or transliterated into Arabic;
- Arabic words must not be replaced by an English translation;
- numbers, names and code-switch boundaries require explicit benchmark scoring;
- retries must be idempotent and must not duplicate transcript text;
- changing ASR engines must be replayable against the same saved WAV.

The previous local code-switch algorithm (`CodeSwitchCandidateSelector` + `WavSpeechChunker`) is restored on this branch as **benchmark/recovery code**, not yet promoted as the production ASR engine.

## Recovery order

### Phase A — make the project measurable
- restore pure local code-switch/VAD regression code and tests;
- add V2 CI on this branch;
- add a runtime capability probe (network/on-device speech availability);
- preserve old cloud ASR only as a quality reference while no V2 APK is released;
- build a labeled WAV benchmark corpus.

### Phase B — zero-cost voice
Evaluate on the target Samsung device:
1. restored Whisper.cpp pipeline;
2. current upstream whisper.cpp integration;
3. Moonshine Arabic candidate;
4. Android/ML Kit on-device speech only where language/device support is actually sufficient.

Promotion requires measured parity or improvement on Egyptian Arabic + English code switching, names, numbers, dropped words, duplication and latency.

### Phase C — local brain
- Gemini Nano / ML Kit Prompt API for local intent extraction, routing, summarization and structured action planning when available;
- deterministic Cortex action router owns execution and safety;
- Nano being unavailable must degrade gracefully, never break core capture/search/voice.

### Phase D — 5G-first actions
Order of execution:
1. normal Android API / Intent / provider;
2. granted durable Android permission/AppOp;
3. Accessibility for user-visible UI actions where appropriate;
4. Shizuku only when its service is alive;
5. explicit unavailable state if the requested privileged operation genuinely cannot be performed.

No normal user flow may require connecting to Wi-Fi merely to keep Cortex alive.

## Release gates

V2 is not releasable until all are true:
- no production request to paid OpenAI/Google Cloud/Azure APIs;
- raw voice survives every transcription failure;
- local voice benchmark passes agreed thresholds;
- cellular-only core-flow device test passes;
- Wi-Fi loss while using Cortex does not disable non-Shizuku features;
- Shizuku loss is surfaced as capability loss only, not app failure;
- update install preserves existing Cortex data;
- full QA covers capture, transcription, memory, search, Ask, actions, lifecycle, permissions, process death and background behavior.
