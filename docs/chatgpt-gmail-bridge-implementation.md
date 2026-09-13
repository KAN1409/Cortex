# Cortex ↔ ChatGPT Gmail Bridge — Implementation Contract

This document is intentionally implementation-facing. The bridge transports test adjudication and later bounded teaching messages. Gmail is transport only; Cortex remains owner of evidence, canonical state, actions, and execution.

## Message directions

- `CORTEX_TEST_REQUEST` — Cortex → ChatGPT
- `CHATGPT_TEST_VERDICT` — ChatGPT → Cortex
- `CORTEX_TEACH_REQUEST` — Cortex → ChatGPT (phase 2)
- `CHATGPT_TEACH_RESPONSE` — ChatGPT → Cortex (phase 2)

Every message MUST carry:

- `schemaVersion`
- `messageType`
- `requestId`
- `runId`
- `testId`
- `createdAtEpochMs`
- `payloadSha256`

A verdict is accepted only when `requestId`, `runId`, `testId`, and `payloadSha256` match a locally persisted pending request.

## Test adjudication payload

The request carries three separately named sections:

1. `originalInput` — the exact grounded input entering the tested Cortex pipeline stage(s).
2. `cortexOutput` — Cortex result before external judging.
3. `referenceEvidence` — expected facts, fixture truth, file metadata/hash, or other admissible reference evidence for the scenario.

The ChatGPT judge must solve/evaluate the case from `originalInput + referenceEvidence` independently before comparing with `cortexOutput`.

## Verdict

A verdict contains:

- `status`: `PASS | WARN | FAIL`
- `severity`: `NONE | P3 | P2 | P1 | P0`
- `independentAnswer`
- `cortexAssessment`
- `groundingAssessment`
- `decisionAssessment`
- `mismatches[]`
- `unsupportedClaims[]`
- `missingExpectedFacts[]`
- `teachingCandidate` (nullable and non-applying in phase 1)

## File scenarios

A file case must report and judge the full chain when applicable:

`receive → persist/reference → MIME/type → hash/size → open/FileProvider → extract → parse → canonicalization boundary → retrieval → decision/action proposal`

Synthetic fixture data must never be committed to canonical Cortex evidence stores.

## Safety / authority

- Gmail transport never becomes evidence authority.
- ChatGPT verdicts never create canonical facts.
- ChatGPT teaching never executes actions.
- Teaching candidates are inert until explicitly accepted by Cortex policy-management logic.
- Unknown, stale, duplicated, mismatched, or malformed responses are rejected and retained as diagnostic evidence only.
