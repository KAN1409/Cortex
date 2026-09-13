# Cortex <-> ChatGPT Gmail Bridge v1

## Purpose
Use Gmail as a durable transport between Cortex and ChatGPT for two phases:

1. **Adjudication** — one independent verdict per test case.
2. **Teaching** — bounded recommendations that may later be converted into temporary policy packs by Cortex. Teaching is disabled by default.

## Gmail routing

Subject prefix:

`[CORTEX-BRIDGE][<TYPE>][v1][<runId>][<testId>]`

Recommended Gmail label:

`Cortex/ChatGPT-Bridge`

Each message body is exactly one JSON object using `ChatGptBridgeEnvelope` schema version 1.

## Request contract

A `TEST_REQUEST` payload contains:

- `testIntent`
- `originalPipelineInput` — exact input presented to the Cortex pipeline.
- `cortexOutput` — exact output produced by Cortex for that input.
- `expectedOrReference` — optional deterministic expected result or trusted reference.
- `evidence` — authoritative evidence collected by the test harness.
- `allowTeachingRecommendations` — false in phase 1.
- `judgingRules`

The judge must never add evidence not included in the request. Missing data is missing data, not permission to infer it as fact.

## Independent ChatGPT comparison

For every `TEST_REQUEST`, ChatGPT must:

1. Read the original pipeline input and authoritative evidence.
2. Independently solve/judge the same grounded case without using Cortex output as the answer key.
3. Compare Cortex output with the original input/evidence.
4. Compare Cortex output with ChatGPT's independent grounded result.
5. Return exactly one verdict for that test.

## Verdict contract

A `TEST_VERDICT` payload must contain:

```json
{
  "status": "PASS|WARN|FAIL",
  "confidence": 0.0,
  "summary": "short grounded explanation",
  "independentResult": {},
  "mismatches": [],
  "recommendedFixes": [],
  "evidenceUsed": [],
  "evidenceMissing": []
}
```

`status`, `confidence`, `summary`, `mismatches`, and `recommendedFixes` are parsed by `ChatGptTestVerdict`. Extra fields are retained by the bridge log for audit.

## File tests

File-related cases must include explicit evidence for each stage that exists in the test harness:

- receive/import result
- URI/path identity
- MIME type
- size/hash when available
- permission/grant result
- open/preview result
- extraction result
- parsed semantic result
- final Cortex answer/action

A file test is not PASS merely because the final text looks plausible. The bridge must compare the reported intermediate evidence with the original file/test fixture.

## Correlation and replay safety

A response is accepted only when:

- `schemaVersion == 1`
- `type` is the expected response type
- `runId` matches the outstanding request
- `testId` matches the outstanding request
- `requestId` matches the outstanding request
- the request has not already been finalized

Duplicate or mismatched replies are logged and ignored.

## Teaching phase

Teaching is a separate message type and must never silently mutate canonical facts or evidence.

`TEACH_RESPONSE` may propose:

- temporary threshold changes
- feature-weight changes
- bounded boosts/penalties
- rule clarifications
- regression cases to add

Cortex owns whether to accept, reject, expire, or roll back a teaching recommendation.

## Security / credentials

Do not store Gmail passwords, app passwords, OAuth access tokens, or refresh tokens in source control.

The current branch establishes the protocol and audit contract. A fully unattended Cortex-side Gmail sender/reader still requires a real Google OAuth/Gmail API authorization for the Android app (or another explicitly configured credentialed transport). The ChatGPT-side Gmail connection does not expose its OAuth token to Cortex and must not be treated as an app credential.
