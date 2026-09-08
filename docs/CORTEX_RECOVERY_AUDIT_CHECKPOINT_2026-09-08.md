# Cortex Recovery Audit — 2026-09-08 Checkpoint

Branch: `v2/cortex-recovery-zero-api`
Observed HEAD at audit start: `14952165ab1763ba1e2f5c834a14da9fb7eae1ab`
Baseline: `8baa00a6ea76234b6dc3a1264541d621daaf7133`

This checkpoint is intentionally evidence-first. It does not promote any replacement ASR and does not remove the working cloud reference path.

## Executive result

The recovery branch is directionally correct and substantially ahead of the original baseline, but it is **not yet ready for a zero-paid-API Cortex V2 release**.

The strongest current assets are:
- protected raw 16 kHz mono PCM16 WAV capture;
- immutable attachment path retained before analysis;
- redirect-safe cloud reference ASR still intact;
- local Whisper candidate restored in shadow/recovery form;
- pure execution-lane router that treats Shizuku as optional;
- explicit Android Assistant role work;
- recovery CI and code-switch regression tests.

The largest blockers are:
1. production voice still routes through `CloudAudioTranscriber`;
2. the current local Whisper candidate contains benchmark-specific lexical prompting and text heuristics and therefore cannot yet be treated as an unbiased production replacement;
3. there is no formal versioned Golden Voice corpus/manifest in the repository yet;
4. no measured real-device retained-data / cellular-only release gate exists yet;
5. the current recovery manifest does not yet contain notification-listener, accessibility, Shizuku or other privileged integration components, which is acceptable for now but means the capability router is ahead of concrete adapters;
6. Gemini Nano/AICore has not yet been probed at runtime on the target device.

## Subsystem classification

| Subsystem | Decision | Evidence / next condition |
|---|---|---|
| Audio capture | KEEP + HARDEN | `AudioCapture` records 16 kHz mono PCM16 WAV and handles runtime revocation at start. Add process-death and storage-failure tests. |
| Raw voice persistence | KEEP + HARDEN | Original WAV remains the source of truth. Add explicit integrity/hash metadata and lifecycle state. |
| Cloud ASR | KEEP AS GOLDEN REFERENCE ONLY | Working mixed-language reference, but violates zero-paid-API production constraint. Must not be deleted before parity benchmark. |
| Local Whisper ASR | EXPERIMENTAL | Real local candidate exists, but includes fixed Egyptian/English prompt examples and heuristic rescue logic. Must be benchmarked without leakage. |
| Code-switch selector | EXPERIMENTAL / REPLACE OR GENERALIZE | Contains hand-maintained English hint vocabulary, Arabicized-English patterns and two dialect spelling rewrites. Useful lab code, not evidence of general ASR quality. |
| Transcript storage | KEEP + HARDEN | `TranscriptResult`/`AnalysisResult` preserve verbatim text and segments. Add ASR-run identity, source hash and replay metadata. |
| Retry | KEEP PATTERN | WorkManager retry semantics are useful, but cloud-specific retry ownership must migrate behind an ASR provider interface. |
| OCR | AUDIT / BENCHMARK | ML Kit + Tesseract exist; quality, Arabic layout and image-source persistence still need corpus tests. |
| Share/import | KEEP + HARDEN | Preserve source object before interpretation; verify URI permission persistence and duplicate imports. |
| Vault database | KEEP + HARDEN | Core local memory should remain. Add explicit capture/transcription/understanding lifecycle state rather than conflating interpretation with source. |
| Search / semantic recall | KEEP CONCEPT + AUDIT | Must be tested for truth, scale, stale derived data and deterministic reindex. |
| Ask | KEEP CONCEPT | Grounded answer path belongs in Cortex, but local reasoning should not fabricate actions or unsupported context. |
| Proactive | KEEP + HARDEN | Must surface only durable evidence-backed signals and outcomes. |
| Execution router | KEEP + HARDEN | Correct least-privilege ordering and Shizuku-optional philosophy. Needs concrete adapter capability tests. |
| Android Assistant role | EXPERIMENTAL / KEEP | Correct strategic direction for assistant identity/context, but must be tested on the actual Samsung build and cannot be assumed available. |
| Accessibility | EXPERIMENTAL | Add only for real user-visible actions/context that cannot be achieved through standard APIs. |
| Notification listener | EXPERIMENTAL | Useful durable context source; must have explicit user grant and privacy controls. |
| Shizuku | OPTIONAL EXPERIMENTAL | Never a core dependency. Use only where live binder privilege is genuinely required. |
| Persistent ADB-granted permissions/AppOps | RESEARCH + CAPABILITY-SPECIFIC | Catalog exact grants that survive Shizuku death/reboot and separately catalog operations requiring live Shizuku binder. |
| Gemini Nano / AICore | HIGH-PRIORITY EXPERIMENTAL LOCAL BRAIN | Target Galaxy S26 Ultra is listed for ML Kit Prompt API nano-v3 as of 2026-09-01. Probe runtime feature status/model/version; never make core capture depend on it. |
| Android on-device SpeechRecognizer | BENCHMARK CANDIDATE | API 31+ supports explicit on-device recognizer discovery. Arabic/English code-switch quality must be proven on device; availability alone is not quality. |
| ML Kit GenAI Speech Recognition | NOT PRIMARY ASR ON TARGET YET | Basic mode is broad-device but current published Basic locales omit Arabic; Advanced mode is currently Pixel 10/11 only. |
| sherpa-onnx | ADD BENCHMARK CANDIDATE | Current Android stack is fully local and has 2026 multilingual models including Arabic; candidate must be measured for Egyptian Arabic + English code-switching and target-device thermals. |
| whisper.cpp upstream | KEEP AS BENCHMARK CANDIDATE | Android is officially supported; compare current upstream runtime/models against the recovered custom AAR rather than assuming the old AAR is optimal. |
| 5G-first behavior | RELEASE GATE | Internet itself is allowed; no normal flow may require Wi-Fi. Test with Wi-Fi disabled from app launch through capture/search/action. |
| Security/privacy | HARDEN | Keep raw/derived state local by default; explicit network boundary; no hidden paid-provider fallback in V2 production. |
| UI | KEEP CORTEX IDENTITY | Do not redesign into LifeOS. Every surfaced capability must have a real backing source/action/outcome. |
| Diagnostics | EXPAND | Add capability matrix, ASR run metadata, source hash, model id, latency, thermal/battery sampling and retry reason. |
| Testing/CI | KEEP + EXPAND | Current recovery CI is green at the observed HEAD. Add benchmark-schema validation, database migration tests, process death, permission revoke, and retained-data update tests. |
| Signing/update compatibility | PROTECTED RELEASE GATE | No uninstall workflow; package id and signing lineage must remain update-compatible. |

## 2026 research decisions

### Gemini Nano / AICore

Use as a **Local Brain candidate**, not as an assumed ASR solution.

Required target-device probe:
- AICore package/service present;
- Prompt API feature status;
- `getBaseModelName()` / Nano generation;
- model download state;
- text-only structured command extraction quality for Arabic + English mixed prompts;
- latency and per-app quota behavior;
- behavior with mobile data only;
- behavior offline after model availability;
- graceful fallback when BUSY / battery quota / feature unavailable is returned.

No deterministic Android action should be executed from free-form LLM text. Nano should emit a typed Cortex command that is validated by deterministic code.

### Voice ASR candidate order

Benchmark, do not guess:
1. recovered local Whisper pipeline;
2. current upstream whisper.cpp runtime with clean multilingual decoding and no corpus-specific lexical prompt;
3. sherpa-onnx multilingual Arabic-capable models;
4. Android `SpeechRecognizer.createOnDeviceSpeechRecognizer()` where present;
5. ML Kit GenAI Speech Recognition only where the requested Arabic/English modes are actually supported on the target device.

A candidate cannot win by WER alone. Required dimensions include Arabic WER/CER, English WER, switch-boundary accuracy, proper names, numbers, dropped words, duplication, hallucination, punctuation, latency, peak RSS, battery delta and thermal behavior.

### Shizuku / privilege architecture

Keep the current policy: Shizuku is a privilege accelerator, not Cortex infrastructure.

Official Shizuku guidance still states that non-root Android 11+ startup uses Wireless Debugging and must be repeated after reboot. Therefore V2 must distinguish:
- durable permission/AppOp granted once and usable by Cortex directly later;
- settings/intents/accessibility flows that need no Shizuku;
- operations that truly require a live Shizuku binder.

Do not market an operation as persistent merely because the grant command was issued through Shizuku once.

## Immediate implementation order after this checkpoint

1. Add a formal Golden Voice Baseline manifest/spec and benchmark scoring contract.
2. Add an ASR provider abstraction so cloud reference and local candidates can run side-by-side without changing stored source state.
3. Remove benchmark leakage from any candidate configuration used for official scoring; preserve the current tuned pipeline as a named experimental profile for comparison.
4. Add a target-device capability probe for AICore/Prompt API and Android on-device SpeechRecognizer.
5. Add sherpa-onnx as a lab candidate only after benchmark harness/provider abstraction exists.
6. Build persistent-privilege capability inventory before adding Shizuku integration.
7. Only after measured evidence, route production transcription away from paid cloud.

## Non-negotiable release gates

- zero paid API calls in V2 production runtime;
- raw WAV never lost because ASR/understanding fails;
- no corpus-specific patching in the promoted ASR profile;
- local/free ASR equals or beats the Golden Reference on agreed weighted score;
- cellular-only core journey passes on the target Samsung device;
- Shizuku stopped/unavailable does not break core Cortex;
- update install preserves existing data;
- no merge without explicit approval.
