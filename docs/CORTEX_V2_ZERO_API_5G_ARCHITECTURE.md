# Cortex V2 — Zero-API, 5G-first Architecture

Research date: 2026-09-08

## Conclusions

### 1. Shizuku cannot be the foundation of Cortex

Non-root Shizuku starts from an ADB shell. Wireless Debugging is the normal phone-only bootstrap path, and vendor behavior can terminate the service when Wi-Fi / wireless debugging disappears. Samsung reports exist for this exact failure mode. Starting Shizuku from USB ADB can be more persistent until reboot on some devices, but this is not a portable Android guarantee.

Therefore Cortex must treat Shizuku as an optional privileged executor. Loss of Shizuku may remove individual privileged actions, but must not remove voice, memory, search, Ask, cellular networking, normal Android intents/providers, or accessibility-based capabilities.

LADB is not a better foundation: it embeds an ADB server but still depends on Android Wireless Debugging and has coexistence limitations with Shizuku.

Dhizuku exposes Device Owner powers, not shell/ADB equivalence. Device Owner provisioning is intrusive for a personal already-provisioned phone and is not an acceptable default Cortex requirement.

### 2. Gemini Nano is useful on the target Samsung — but for the brain, not our primary Arabic ASR

Google's current ML Kit device list includes Galaxy S26 / S26+ / S26 Ultra under Prompt API support (Nano v3 family). Prompt inference is on-device and incurs no per-call server cost.

This makes Prompt API a strong candidate for:
- intent classification;
- structured command extraction;
- local summarization;
- context compression;
- deciding whether Cortex can answer/act locally;
- generating a typed action proposal which a deterministic executor validates.

It is **not** the current primary plan for Egyptian-Arabic/English voice-note transcription. ML Kit GenAI Speech Recognition Advanced mode is presently limited to Pixel 10/11-class devices; Basic mode is broadly available but its documented locale set does not provide the Arabic coverage we need. Device support and model availability must always be probed at runtime before showing a Nano capability as ready.

### 3. Voice ASR should be local and benchmark-driven

Candidate order for the Samsung target:
1. Cortex's recovered Whisper.cpp pipeline, because we already have code-switch-specific VAD/rescue logic and historical test cases.
2. A refreshed upstream whisper.cpp integration using a measured multilingual model/quantization.
3. Moonshine Arabic as a challenger: Android support and Arabic models exist, but Egyptian-Arabic + English code-switch quality must be measured on our corpus before adoption.
4. Platform/ML Kit on-device speech only as a narrow fallback where supported; never silently replace the primary Arabic path.

No provider wins by reputation. The promotion decision is based on the same labeled WAV corpus.

## Runtime routing

```text
VOICE / TEXT / SCREEN / SHARE
        |
        v
Raw immutable input store
        |
        +--> Local ASR (voice)
        |
        +--> Local OCR / parser
        |
        v
Canonical transcript / extracted text
        |
        v
Local Brain
Gemini Nano when AVAILABLE
Deterministic fallback when unavailable
        |
        v
Typed Action Proposal
        |
        v
Capability Router
  1. normal Android API / Intent / provider
  2. durable permission/AppOp already granted
  3. Accessibility executor
  4. Shizuku executor if alive
  5. explicit unavailable result
```

## Connectivity contract

- `CELLULAR` is a first-class expected transport, not a degraded mode.
- Cortex must work on 5G with Wi-Fi off.
- Local features must also work with no internet at all.
- Network access is used only for ordinary web/user-requested internet work, downloads/updates, or explicitly allowed services — never paid inference APIs.
- Shizuku health is independent of network health in the app model.

## Privilege strategy

A privileged command is described by capability, not by implementation. Example:

```text
Change protected setting
  -> public Android API available? use it
  -> already-granted secure AppOp/permission? use it
  -> accessibility can perform the same visible user action safely? use it
  -> Shizuku alive and action supported? use it
  -> otherwise unavailable with a precise reason
```

This avoids building Cortex around one escalation mechanism.

## Voice promotion metrics

Every candidate ASR engine is scored on:
- WER / CER overall;
- Egyptian Arabic-only WER/CER;
- English-only WER;
- mixed Arabic-English WER;
- code-switch boundary accuracy;
- proper-name accuracy;
- numeric-token accuracy;
- dropped-word rate;
- duplicated-word/span rate;
- hallucinated-tail rate;
- real-time factor / latency;
- peak memory;
- battery/thermal behavior for short and long notes.

The original WAV and human ground truth are retained. Engine output is versioned, never overwrites the source audio, and can be replayed.

## Non-negotiable release tests

1. Boot phone, leave Wi-Fi off, use 5G only: record -> transcribe -> save -> search -> Ask must work.
2. Turn Wi-Fi on, start Shizuku, then turn Wi-Fi off: core Cortex remains fully operational whether Shizuku survives or not.
3. Kill Shizuku: only Shizuku-backed actions become unavailable.
4. Disable internet entirely: local voice/memory/search/local-brain paths still work.
5. Reboot: app restarts without requiring Shizuku; privileged capabilities honestly report their state.
6. No OpenAI/Google Cloud/Azure paid inference call exists in the V2 production path.

## Sources reviewed

- Android ADB / wireless debugging documentation
- Android `SpeechRecognizer` on-device API documentation
- Google ML Kit GenAI overview and Prompt API device support
- Google ML Kit GenAI Speech Recognition documentation
- Shizuku discussions/issues covering Wi-Fi-disconnect behavior, including Samsung reports
- LADB project documentation
- Dhizuku project documentation
- upstream whisper.cpp Android example
- Moonshine Android / Arabic support documentation
