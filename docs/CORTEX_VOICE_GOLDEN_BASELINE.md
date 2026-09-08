# Cortex Voice Golden Baseline

Status: protected regression contract
Branch: `v2/cortex-recovery-zero-api`

The purpose of this document is to prevent accidental regression while Cortex migrates from paid cloud ASR to a zero-paid-API architecture.

## Golden rule

The existing working mixed Egyptian-Arabic/English voice transcription is the quality reference until a replacement proves equal or better on the same saved audio.

The cloud implementation may remain available on the recovery branch as a lab/reference provider, but it must not be a production dependency in Cortex V2.

## Immutable source requirements

Every voice capture used for production or benchmarking must preserve:
- original WAV bytes;
- 16 kHz sample rate;
- mono channel layout;
- PCM 16-bit encoding;
- creation timestamp;
- file size;
- stable source id;
- SHA-256 of original WAV;
- capture duration when known.

No ASR engine may rewrite, resample in place, delete, replace or normalize the source WAV.

Derived temporary files may be created for decoding, but they must never become the source of truth.

## ASR run identity

Every benchmark or production transcription run should eventually persist:
- source audio id/hash;
- provider/engine id;
- model id;
- model quantization/version;
- runtime version;
- decoding profile id;
- language mode;
- prompt id/hash if any;
- start/end timestamps;
- latency;
- transcript;
- timed segments if supported;
- error/retry state;
- device model/build;
- memory/thermal/battery samples when benchmark mode is enabled.

This makes every result replayable and attributable.

## Golden corpus composition

The official corpus must contain independently labeled recordings across all of these classes:
- Egyptian Arabic only;
- English only;
- natural Arabic + English code switching;
- English technical terms inside Arabic sentences;
- Arabic names;
- English names;
- phone numbers;
- prices and currencies;
- dates and times;
- app/product names;
- addresses/locations;
- technical architecture vocabulary;
- short commands;
- long voice notes;
- fast speech;
- quiet speech;
- noisy environment;
- pauses/silence;
- repeated words/corrections in natural speech.

Do not construct the official corpus solely from phrases already used to tune prompts, heuristics or replacement rules.

## Data split

Use three disjoint sets:

### Development set
May be inspected while developing candidate pipelines.

### Validation set
Used to choose decoding configuration and thresholds.

### Locked test set
Must not be used to author lexical prompts, replacement dictionaries, normalization rules or rescue heuristics. This set decides promotion.

Any sample that influences a candidate-specific rule must be removed from the locked test set.

## Required metrics

At minimum record:
- overall WER;
- overall CER;
- Arabic WER/CER;
- English WER;
- code-switch boundary accuracy;
- proper-name accuracy;
- numeric token accuracy;
- dropped-word rate;
- duplicated-word/span rate;
- hallucinated-word rate;
- punctuation score where relevant;
- real-time factor / end-to-end latency;
- peak memory;
- battery delta;
- thermal delta/throttling indicator.

## Code-switch scoring

A transcript is not considered correct merely because the meaning is approximately preserved.

Penalize:
- Arabic words translated into English;
- English words translated into Arabic;
- Arabic transliterated into Latin when Arabic was spoken;
- English transliterated into Arabic when English was spoken;
- language boundary shifted to the wrong word;
- names changed into common words;
- numbers omitted or reformatted incorrectly when the value changes.

## Candidate fairness rules

Official scoring profiles must not contain corpus-specific leakage.

Not allowed in a promoted profile:
- hard-coded fixes for individual WAVs;
- special-case replacement of a known failed name from the benchmark;
- prompt phrases copied from locked test utterances;
- vocabulary lists authored from errors seen only in the locked test set;
- sample-specific timing windows.

General linguistic normalization is allowed only when documented, deterministic, and applied equally across the full corpus.

The current recovered Whisper code-switch pipeline should be treated as an **experimental tuned profile** until its lexical prompt/hinting is separated from the unbiased scoring profile.

## Reference and candidate modes

Each WAV should be replayable through multiple providers without changing the original knowledge item:

- `golden_cloud_reference`
- `whisper_recovered_tuned`
- `whisper_upstream_clean`
- `sherpa_onnx_<model>`
- `android_on_device_speech`
- future zero-cost candidate profiles

Providers must write separate ASR run records. A candidate must never overwrite the Golden Reference transcript.

## Promotion gate

A local/free candidate may become Cortex production ASR only when all are true:
- zero paid API cost;
- no hidden cloud fallback required for normal transcription;
- weighted quality is equal to or better than Golden Reference;
- no catastrophic regression in names, numbers or code-switch boundaries;
- long-note duplication and dropped-tail rates are acceptable;
- target Samsung device performance is acceptable on battery and thermals;
- 5G/Wi-Fi state does not affect local transcription availability once the required local model is installed;
- failure never deletes source audio;
- replay/idempotency tests pass.

## Initial weighted score proposal

Use this only as a starting point and keep raw metrics visible:
- 25% Arabic word/character accuracy;
- 20% English word accuracy;
- 20% code-switch boundary/script preservation;
- 15% names + numbers;
- 10% dropped/duplicated/hallucinated token penalties;
- 5% latency;
- 5% memory/battery/thermal behavior.

A candidate with a higher total weighted score still fails promotion if it has a severe regression in names, numbers, source-script preservation or long-note completeness.

## Protected behavior

Until promotion is explicitly approved:
- do not delete `CloudAudioTranscriber`;
- do not silently redirect `AudioAnalyzer` to an unproven local candidate;
- do not alter stored historical transcripts in place;
- do not delete raw WAVs after successful transcription;
- do not merge recovery work without explicit approval.
