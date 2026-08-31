# Cortex Prime V0 Architecture

## Modules

- `app`: Android UI and composition root
- `core-evidence`: immutable raw evidence, revisions, provenance, attachments
- `capture-relay`: extracted proven notification Relay code, without Local Bus / IPC transport
- `capture-voice`: recording, playback, ASR adapter
- `capture-vision`: image capture, OCR / vision adapter
- `model-extractor`: structured extraction
- `model-linker`: embeddings, related-evidence matching, dedupe
- `model-organizer`: constrained high-level organization proposals
- `core-validator`: grounding, schema, confidence and conflict checks
- `core-state`: deterministic canonical state reducer
- `capabilities`: explicit user-approved Android actions

## Hard invariants

1. Raw evidence is immutable.
2. Corrections create revisions rather than rewriting raw evidence.
3. Every derived field references one or more evidence IDs.
4. Models return proposals only. They never write canonical state.
5. Canonical state changes only through `core-state` after `core-validator` passes.
6. Relay remains conceptually observation-only even though it is now inside the same APK.
7. No IPC or Local Bus is used between Relay and Cortex Prime.
8. Provider/model implementation details are hidden behind interfaces.
9. Failure preserves the original capture and remains retryable.
10. Android actions require explicit user approval.

## Flow

```text
Notification ----> capture-relay ----\
Voice ----------> capture-voice ------> core-evidence
Image / OCR ----> capture-vision -----/
                                      |
                                      v
                              model-extractor
                                      |
                              model-linker
                                      |
                             model-organizer
                                      |
                              core-validator
                                      |
                                core-state
                                      |
                              UI / capabilities
```

## First implementation milestone

Before choosing final on-device models, implement the interfaces, evidence schema and deterministic test harness. Models are selected by benchmark against fixed Cortex scenarios rather than by model reputation.
