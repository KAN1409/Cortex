# Cortex Prime Model Contracts V0

All model outputs are proposals. Every derived item must cite source evidence IDs.

## 1. ASR
Input: audio evidence ID + audio bytes
Output:
```json
{
  "evidence_id": "ev_...",
  "transcript": "...",
  "language_segments": [{"lang":"ar","text":"..."}],
  "confidence": 0.0
}
```

## 2. Vision / OCR
Input: image evidence ID + image bytes
Output:
```json
{
  "evidence_id": "ev_...",
  "visible_text": ["..."],
  "visual_facts": ["..."],
  "urls": [],
  "barcodes": [],
  "uncertainties": []
}
```

## 3. Extractor
Input: one or more normalized evidence records
Output:
```json
{
  "source_evidence_ids": ["ev_..."],
  "people": [],
  "dates": [],
  "tasks": [],
  "events": [],
  "facts": [],
  "uncertainties": []
}
```

## 4. Linker
Input: evidence + extracted structures + embeddings
Output:
```json
{
  "links": [
    {
      "left_evidence_id": "ev_1",
      "right_evidence_id": "ev_2",
      "relation": "same_thread|same_event|updates|duplicates|related",
      "confidence": 0.0,
      "reason_codes": []
    }
  ]
}
```

## 5. Organizer
Input: grounded evidence, extraction and links
Output:
```json
{
  "source_evidence_ids": ["ev_..."],
  "summary": "...",
  "task_candidates": [],
  "calendar_candidates": [],
  "saved_context_candidates": [],
  "suggested_actions": [],
  "conflicts": [],
  "unresolved_questions": []
}
```

## Validator rules
- reject unknown evidence IDs
- reject unsupported dates, people or facts
- reject model-created source text
- retain conflicts rather than silently choosing when evidence does not establish an update
- prefer explicit newer update evidence only when the update relation is grounded
- never execute suggested actions automatically
