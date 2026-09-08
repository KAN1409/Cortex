# Cortex V2 — Zero-API, 5G-first Architecture

Research date: 2026-09-08

## Conclusions

### 1. Shizuku cannot be the foundation of Cortex

Non-root Shizuku starts from an ADB shell. Wireless Debugging is the normal phone-only bootstrap path, and vendor behavior can terminate the service when Wi-Fi / wireless debugging disappears. Samsung reports exist for this exact failure mode. Starting Shizuku from USB ADB can be more persistent until reboot on some devices, but this is not a portable Android guarantee.

Therefore Cortex must treat Shizuku as an optional privileged executor. Loss of Shizuku may remove individual privileged actions, but must not remove voice, memory, search, Ask, cellular networking, normal Android intents/providers, Assistant-role context, notification-listener context or accessibility-based capabilities.

LADB is not a better foundation: it embeds an ADB server but still depends on Android Wireless Debugging and has coexistence limitations with Shizuku.

Dhizuku exposes Device Owner powers, not shell/ADB equivalence. Device Owner provisioning is intrusive for a personal already-provisioned phone and is not an acceptable default Cortex requirement.

### 2. The Android Assistant role is a better core integration than ADB-derived privilege

Android exposes `RoleManager.ROLE_ASSISTANT` and the `VoiceInteractionService` / `VoiceInteractionSession` stack. When the user explicitly selects Cortex as the assistant, Android keeps the selected voice interaction service available as a system assistant surface; this does not depend on Wi-Fi, Shizuku or ADB.

A `VoiceInteractionSession` can receive foreground-app assist structure/content and, where the foreground app and policy allow it, a screenshot. That gives Cortex a legitimate system-level invocation/context lane for many assistant scenarios.

Important limits:
- Cortex must never silently make itself the default assistant. Role selection is an explicit user choice.
- Assist data can be absent or blocked, including secure content. Assistant role is therefore not a replacement for Accessibility.
- The always-running `VoiceInteractionService` should stay lightweight; heavier session work belongs in the session service/process.

This makes the preferred context order:

1. ordinary Android API / Intent / provider;
2. user-selected Android Assistant role for invocation/assist context;
3. NotificationListener for notification context;
4. Accessibility for broader user-authorized screen context and visible UI interaction;
5. durable permission/AppOp already granted;
6. Shizuku only when alive and genuinely necessary;
7. explicit unavailable state.

### 3. Gemini Nano is useful on the target Samsung — but for the brain, not our primary Arabic ASR

Google's current ML Kit device list includes Galaxy S26 / S26+ / S26 Ultra under Prompt API support (Nano v3 family). Prompt inference is on-device and incurs no per-call server cost.

This makes Prompt API a strong candidate for:
- intent classification;
- structured command extraction;
- local summarization;
- context compression;
- deciding whether Cortex can answer/act locally;
- generating a typed action proposal which a deterministic executor validates.

It is **not** the current primary plan for Egyptian-Arabic/English voice-note transcription. ML Kit GenAI Speech Recognition Advanced mode is presently limited to Pixel 10/11-class devices; Basic mode is broadly available but its documented locale set does not provide the Arabic coverage we need. Device support and model availability must always be probed at runtime before showing a Nano capability as ready.

### 4. Voice ASR should be local and benchmark-driven

Candidate order for the Samsung target:
1. Cortex's recovered Whisper.cpp pipeline, because we already have code-switch-specific VAD/rescue logic and historical test cases.
2. A refreshed upstream whisper.cpp integration using a measured multilingual model/quantization.
3. sherpa-onnx with multilingual Whisper as an independent fully-offline runtime challenger.
4. Moonshine Arabic as another challenger only after current Android/Arabic support is verified and measured.
5. Platform on-device `SpeechRecognizer` only when the device actually reports an on-device recognizer and our corpus proves the required Arabic/English behavior.
6. ML Kit speech only where its documented device/language support is sufficient; never silently substitute an unsupported Arabic path.

No provider wins by reputation. The promotion decision is based on the same labeled WAV corpus.

## Runtime routing

```text
VOICE / TEXT / SCREEN / SHARE / ASSIST INVOCATION
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
  2. Android Assistant role / assist context
  3. NotificationListener
  4. Accessibility
  5. durable permission/AppOp already granted
  6. Shizuku executor if alive
  7. explicit unavailable result
```

The production router intentionally contains **no paid/cloud inference lane**.

## Connectivity contract

- `CELLULAR` is a first-class expected transport, not a degraded mode.
- Cortex must work on 5G with Wi-Fi off.
- Local features must also work with no internet at all.
- Network access is used only for ordinary web/user-requested internet work, downloads/updates, or explicitly allowed services — never paid inference APIs.
- Shizuku health is independent of network health in the app model.
- The Assistant role, NotificationListener and Accessibility are Android system integrations, not Wi-Fi dependencies.

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

Assistant context is similarly capability-based:

```text
Understand current foreground context
  -> Assistant role held and assist data available? use it
  -> Accessibility granted? use its source-neutral screen tree
  -> otherwise explicitly report that foreground context is unavailable
```

This avoids building Cortex around one escalation mechanism.

## Runtime capability truth

Cortex must probe rather than assume:
- current transport (`CELLULAR`, `WIFI`, `VPN`, `OFFLINE`, etc.);
- validated internet and metered state;
- whether Android exposes any speech recognizer;
- whether Android exposes an on-device speech recognizer;
- whether the Assistant role exists;
- whether Cortex currently holds the Assistant role;
- whether Accessibility / NotificationListener grants are actually active;
- whether Shizuku is actually alive;
- whether a local ASR model is actually present and loadable;
- whether Gemini Nano Prompt API is actually available/ready.

A device-model allowlist is never enough to display a capability as operational.

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

## Protected voice baseline

The cloud transcription path that previously produced acceptable voice-note transcripts is retained temporarily as a **golden quality reference**, not as the final V2 production architecture. The raw 16 kHz mono PCM16 WAV remains the immutable source.

A local candidate may not replace the user's visible transcript until it meets or beats that reference on the fixed benchmark. Shadow/local runs must be stored separately so changing engines never destroys the known result or the original audio.

## Non-negotiable release tests

1. Boot phone, leave Wi-Fi off, use 5G only: record -> transcribe -> save -> search -> Ask must work.
2. Turn Wi-Fi on, start Shizuku, then turn Wi-Fi off: core Cortex remains fully operational whether Shizuku survives or not.
3. Kill Shizuku: only Shizuku-backed actions become unavailable.
4. Disable internet entirely: local voice/memory/search/local-brain paths still work.
5. Reboot: app restarts without requiring Shizuku; privileged capabilities honestly report their state.
6. Select Cortex as Assistant: system assist invocation/context works without Shizuku; deselect it and Cortex degrades gracefully.
7. Block assist data/secure foreground content: Cortex does not fabricate screen context and uses Accessibility only if actually granted.
8. No OpenAI/Google Cloud/Azure paid inference call exists in the V2 production path.
9. Existing Cortex update installation preserves the user's database and raw voice files.

## Sources reviewed

- Android ADB / wireless debugging documentation
- Android `RoleManager.ROLE_ASSISTANT` documentation
- Android `VoiceInteractionService` and `VoiceInteractionSession` documentation
- Android `SpeechRecognizer` on-device API documentation
- Android AccessibilityService and NotificationListenerService documentation
- Google ML Kit GenAI overview and Prompt API device support
- Google ML Kit GenAI Speech Recognition documentation
- Shizuku setup documentation and Wi-Fi-disconnect discussions, including Samsung reports
- LADB project documentation
- Dhizuku project documentation and Device Owner limitation discussions
- upstream whisper.cpp Android example
- sherpa-onnx Android/offline ASR + multilingual Whisper documentation
- Moonshine Android/Arabic documentation as a benchmark candidate only
