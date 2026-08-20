# Cortex Memory Core — Data Model v0.2

## `knowledge_items`
Canonical captured source plus additive display fields.

- `id`
- `type`: TEXT | SCREENSHOT | IMAGE | AUDIO | DOCUMENT | LINK | DATASET | AI_PROMPT | AI_RESULT | EXAMPLE_INPUT
- `source`
- `title`
- `raw_text` — captured source text, never overwritten by analysis
- `extracted_text` — OCR / extraction output
- `summary`
- `category`
- `tags`
- `attachment_path`
- `status`: queued | analyzing | analyzed | analysis_failed | archived
- `fingerprint` — SHA-256 duplicate identity
- `analysis_error`
- `metadata_json`
- `created_at`
- `updated_at`

## `analyses`
Versioned analysis history.

- item id
- engine
- analysis version
- structured output JSON
- timestamp

## `entities`
Searchable structured entities derived from an item.

## `actions`
Searchable action items with a due-text hint and open/closed status.

## `relations`
Item → item edge with relationship type and confidence.

## `examples`
Prompt / optional input / result bundle, rating and notes.

## Core rule
Captured source is immutable. New OCR/AI models add a new analysis record rather than replacing the original source.
