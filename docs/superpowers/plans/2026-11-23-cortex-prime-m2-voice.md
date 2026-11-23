# Cortex Prime — Milestone 2 (Voice) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** One tap records a voice note. It's saved as an original WAV and transcribed on the phone by Whisper (Arabic, English or mixed), cleaned up, and shown in an "Understood." sheet and in Memory.

**Architecture:** `AudioRecorder` (AudioRecord, 16 kHz mono PCM16) streams to a WAV file, which `EvidenceRepository` stores as AUDIO evidence. The `perceive` stage gets a second handler, `TranscribeHandler`, which reads the WAV, runs a `SpeechEngine` (whisper.cpp through JNI), cleans the text, and writes `Perception(kind="transcript")`. `ModelManager` downloads the Whisper model once over Wi-Fi with a pinned SHA-256. Until the model is present, AUDIO jobs are **parked** with reason `model_missing`. They are unparked when the download finishes.

**Tech Stack:** M1 stack plus whisper.cpp **v1.8.7** (MIT; fetched by CMake `FetchContent` at build time), the Android NDK through AGP `externalNativeBuild`, and `DownloadManager`.

**Spec:** `docs/superpowers/specs/2026-11-23-cortex-prime-design.md` (§5.2, §6.2, §6.3)

## Global Constraints
- M1's constraints still apply: zero cost, no paid APIs, honest status.
- Add the `INTERNET` permission (model download only) and `RECORD_AUDIO` (only while the record screen is open).
- ABI: `arm64-v8a` only.
- **Model:** `ggml-small-q5_1.bin` (multilingual), downloaded from `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/<file>` over **unmetered networks only**. Size and SHA-256 are pinned in `ModelSpec`, with the values taken from the Hugging Face API in CI.
- **Time limit:** for an audio job, 4× the audio duration + 60 s.

## Review Focus
1. **Recording while the model isn't downloaded yet:** the note is saved, shows "Waiting for the voice model", and is transcribed automatically after the download. Test in Task 3.
2. **Download corrupted, or replaced by a captive-portal page:** a SHA-256 mismatch deletes the file and shows "Download failed — will retry on Wi-Fi". Test in Task 2.
3. **A silent or zero-length recording:** under 0.5 s it isn't saved, and the user sees "Too short to save". Test in Task 1.
4. **Whisper returns only artefacts** ("[Music]", "(موسيقى)"): the transcript is empty, and the label becomes Done with "No speech found", never a fake transcript. Test in Task 4.
5. **The app is killed mid-transcription:** the lease expires and the job runs again. This is covered by the M1 job-engine tests and needs no new test.

## Tasks
1. **WAV and recorder.** `WavCodec.writeHeader(out, sampleRate, channels, dataBytes)` and `WavCodec.readPcm16(file): ShortArray`, plus `pcmToFloat(ShortArray): FloatArray`. `AudioRecorder` exposes `start()`, `amplitude(): Float` and `stop(): RecordingResult(file, durationMs)`, and returns `TooShort` under 500 ms. Tests: header round-trip, PCM → float range in [-1, 1], and the too-short rule.
2. **ModelManager.** `ModelSpec(fileName, url, sizeBytes, sha256)`. `ModelManager.status(): ModelStatus` (Missing / Downloading(progress) / Ready / Failed(reason)), `start()` (DownloadManager, Wi-Fi only), and `verify(file): Boolean` (size plus SHA-256). On a mismatch, delete the file. Tests: `verify` accepts the right hash and rejects a wrong hash and a wrong size.
3. **TranscribeHandler and parking.** Interface `SpeechEngine { suspend fun transcribe(pcm: FloatArray): EngineResult(text, language) }`. The handler parks with `model_missing` when the model isn't Ready. Add `JobQueue.unpark(reason: String)`, which sets every job parked with that reason back to due now; it's called when the model becomes Ready. `processingLabel` gains `MODEL("Waiting for the voice model")`. Tests: parked while missing, unparked after Ready, transcript written with a fake engine, and the time limit computed from the duration.
4. **TranscriptCleaner.** `clean(raw: String): String`. It removes `[...]` and `(...)` artefacts made only of non-speech words (Music, موسيقى, Applause, تصفيق, silence) and fillers as whole words (يعني, امم, اه, um, uh, erm), and collapses whitespace. It keeps Arabic-Indic digits as spoken. An empty result is "No speech found". Tests use Arabic, English and mixed fixtures.
5. **Native whisper.** `src/main/cpp/CMakeLists.txt` uses FetchContent for whisper.cpp v1.8.7 and builds `libcortexwhisper.so` with `jni.cpp`, exposing `nativeInit(path, threads): Long`, `nativeTranscribe(ctx, FloatArray): String` (the language code, then `\n`, then the text) and `nativeFree(ctx)`. `WhisperEngine` implements `SpeechEngine`, with threads = min(4, cores) and language `auto`. Verified when CI compiles the native library and packages it in the APK.
6. **Record UI and Quick Settings tile.** `RecordScreen`: a full-screen overlay with the fast brain (`brain_fast.webp`, 80 ms per frame), a live amplitude waveform, the elapsed time, and Stop. Asks for the microphone permission first. On stop it saves, then shows `UnderstoodSheet` with the transcript when ready, or the honest state otherwise. Tapping the nav button now records; long-press is still a text idea. `RecordTileService` opens `RecordActivity`. Memory rows for AUDIO show the transcript as the title.
