# Cortex Recovery Audit — Zero Paid API / 5G-first

Branch: `v2/cortex-recovery-zero-api`
Baseline commit: `8baa00a6ea76234b6dc3a1264541d621daaf7133`

## Non-negotiable product constraints

1. **Zero paid API cost.** No production Cortex feature may require OpenAI, Google Cloud Speech, Azure Speech, Groq, or any other metered API.
2. **5G-first.** Normal Cortex use must work away from home/work Wi-Fi. Wi-Fi may never be a prerequisite for core capture, transcription, memory, understanding, or user interaction.
3. **Shizuku is optional acceleration, never a single point of failure.** Normal Android APIs/Intents first, Accessibility where appropriate, Shizuku only for privileged operations while its service is alive.
4. **Preserve the voice-note behavior the user liked.** Do not delete or rewrite the working recording/transcript path until a replacement is measured against saved real voice notes.
5. **Raw evidence survives failure.** Original WAV stays durable even when transcription fails or is retried.
6. **No patch architecture.** Arabic/English, numbers, names, and code-switching quality are benchmarked; they are not fixed with user-specific string substitutions.

## What the current baseline really does

### Voice capture — KEEP

`AudioCapture` records 16 kHz, mono, PCM16 WAV into app-private storage. This is a good durable source format for both legacy and local ASR. Keep the raw WAV as the source of truth.

### Current voice transcription — PROTECT AS REFERENCE, THEN REPLACE FOR RELEASE

The current `AudioAnalyzer` is explicitly cloud-only and delegates to `CloudAudioTranscriber`.

`CloudAudioTranscriber` posts the original audio file to the Cortex backend. The backend currently tries paid/metered providers in this order:

1. OpenAI `gpt-transcribe`
2. Google Speech-to-Text Chirp 3
3. Azure Speech

That path is the known-good behavioral reference, but it violates the new zero-paid-API rule. It stays present temporarily so we do not accidentally destroy the behavior we are trying to match. A zero-API release must not depend on it.

### Local ASR history — RECOVER

Immediately before the cloud-only migration, Cortex already contained a real on-device Whisper stack:

- `MultilingualWhisperTranscriber.kt`
- `LocalAsrModelStore.java`
- `CodeSwitchCandidateSelector.java`
- `WavSpeechChunker.java`
- `WhisperGgmlModel.java`
- `WhisperRuntimeState.java`
- custom `whisper-android-cortex-prompt-1.0.0.aar`

The old local design used multilingual Whisper, Egyptian/English prompting, VAD trimming, a guarded English rescue pass for long notes, and guarded tail recovery. It was removed by the cloud migration; it was not lost from Git history.

**Recovery action:** restore that stack in parallel, compile it, and benchmark it. Do not wire it into the normal user path until it passes.

## Voice quality gate

The cloud service does **not** need to be called again to create a benchmark. Use previously accepted transcripts / saved voice-note text as the golden reference and compare new local output against the same source WAVs.

Required corpus categories:

- Egyptian Arabic only
- English only
- Arabic → English → Arabic within one sentence
- English technical terms inside Arabic
- people names
- app/product names
- integers / decimals / money
- phone-number-like digit sequences
- fast speech
- quiet speech
- short notes
- long notes
- noise / car / outdoor notes

Metrics:

- WER
- CER
- number-token accuracy
- proper-name accuracy
- code-switch boundary accuracy
- dropped-word rate
- duplicated-word/span rate
- hallucinated-span rate
- latency
- peak memory
- thermal impact for repeated notes

Promotion rule: local ASR becomes the production default only after the real-device corpus shows it is acceptable against the behavior the user already liked. Until then the recovery APK is a development/benchmark build, not the final zero-API release.

## 5G / Shizuku architecture

Target command routing:

```text
User request
  -> normal Android API / Intent available? -> execute
  -> Accessibility can perform the permitted UI operation? -> execute
  -> Shizuku service currently alive? -> privileged execute
  -> otherwise -> expose the exact unavailable capability; keep Cortex alive
```

Shizuku startup without root remains an environmental dependency of Android wireless debugging. Cortex therefore must not make core features depend on Shizuku availability. A Shizuku outage must degrade only privileged commands.

## Existing subsystems — first-pass classification

| Subsystem | Status | Direction |
|---|---|---|
| WAV recording | KEEP | Preserve 16 kHz mono PCM source and improve lifecycle/error reporting |
| Cloud ASR | REFERENCE / REMOVE FROM FINAL PATH | Keep source temporarily as golden behavior reference; zero-API production cannot depend on it |
| Local Whisper | RECOVER + BENCHMARK | Restore from pre-cloud commit in parallel |
| Transcript persistence / segments | KEEP + HARDEN | Stable engine/version/status; never overwrite raw transcript silently |
| VaultDb | KEEP + AUDIT | Migration, stable IDs, indexes, scale, corruption/recovery tests |
| LocalAnalyzer | AUDIT | Deterministic extraction only; separate transcript from derived summary/tags |
| SemanticIndex | AUDIT | Confirm entirely local, indexed, scalable, deterministic enough for retrieval |
| SecondBrainEngine | AUDIT | Ground every answer/open loop in durable evidence; remove fabricated certainty |
| ContextEngine | AUDIT | Verify grouping identity and no mega-context conflation |
| OCR | QUALITY GATE | Measure Arabic/English mixed OCR before product promises |
| Proactive engine | AUDIT | No false obligations/notifications; lifecycle and evidence required |
| Backup/restore | KEEP + TEST | Update-safe, corruption-safe, round-trip tests |
| DebugReview / exported debug entry | FIX | Development tooling must not be an exposed production launcher |
| Android backup | REVIEW | Baseline currently has `allowBackup=true`; private-memory threat model required |
| Shizuku | ADD AS OPTIONAL PROVIDER | Privileged command provider, not Cortex runtime dependency |
| Gemini Nano / AICore | INVESTIGATE ON DEVICE | Local understanding/routing candidate only; feature-detect, benchmark, no product dependency until proven available |

## Recovery phases

### R0 — baseline protection
- Freeze current working voice capture/transcript behavior in Git history.
- Restore local Whisper source/runtime beside it.
- Add CI that proves both the working legacy source and local recovery stack remain present and compile.

### R1 — voice benchmark harness
- Import/select local model.
- Run saved WAV corpus without modifying the saved original.
- Store engine/version/output/segments/latency separately per run.
- Compare against golden transcript and export a machine-readable report.

### R2 — zero-API transcription promotion
- Choose the best local configuration from measured results.
- Switch normal `AudioAnalyzer` to the local provider interface.
- Keep the old cloud code non-production/reference-only or remove it once historical comparison is no longer needed.
- Add a release CI guard that fails if a metered ASR endpoint is reachable from production code.

### R3 — Cortex capability router
- Normal Android APIs/Intents.
- Accessibility provider.
- Optional Shizuku provider.
- Explicit capability/status model and deterministic fallback.

### R4 — local brain
- Feature-detect Gemini Nano/AICore on the physical phone.
- Benchmark structured intent/entity extraction locally.
- Keep deterministic action execution outside the model.

### R5 — exhaustive QA
- Fresh install + in-place update.
- Process death / reboot / permission revoke.
- Wi-Fi off + 5G-only usage.
- Shizuku alive/dead transitions.
- Voice corpus, memory, search, context, actions, backup/restore.
- API-level emulator matrix plus Samsung physical-device evidence.

## Definition of done

Cortex is not considered recovered because a demo works. It is recovered when:

`voice/source -> durable WAV -> measured transcript -> durable memory -> grounded understanding -> supported action -> outcome`

is reproducible, testable, update-safe, works on 5G without Wi-Fi, incurs zero paid API cost, and does not collapse when Shizuku is unavailable.
