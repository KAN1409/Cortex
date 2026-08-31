# capture-vision

Deterministic image intake for Cortex Prime.

- accepts one or many `image/*` items from Android Share
- immediately copies bytes into app-owned storage before any model runs
- content-addresses the immutable image asset with SHA-256
- records image dimensions, MIME type, file name, byte size and asset provenance
- appends immutable `EvidenceRecord` rows with `analysis_status=PENDING`
- never requires OCR or a vision provider in the capture path

OCR and semantic vision remain model adapters. A failed or replaced model can always re-run against the original preserved image.
