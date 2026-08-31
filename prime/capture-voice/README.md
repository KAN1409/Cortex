# capture-voice

Deterministic voice capture for Cortex Prime.

- foreground microphone service
- 16 kHz, mono, PCM16 WAV
- durable pending staging file before any model runs
- WAV header repair and recovery after interrupted captures
- content-addressed immutable audio asset
- immutable `EvidenceRecord` with duration/sample-rate/channel provenance
- no ASR dependency in the capture path

ASR is intentionally a later adapter: recording and source persistence must succeed even when every model provider is unavailable.
