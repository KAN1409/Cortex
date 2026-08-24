# Cortex v50 Hardening Review — 2026-08-24

Source: user-provided external code review, verified against current `main` before changes.

## Implemented

### Combined cloud evidence defaults
- Confirmed issue: `CloudEvidencePolicy` previously allowed unclassified evidence because an empty privacy group was treated as cloud-eligible.
- Fix: explicit allow-list. Unknown/unclassified evidence is now local-only by default.
- Intentional `manual` / `quick_capture` evidence remains explicitly classifiable and can follow the normal privacy policy.

### Thread model prompt safety
- The reported Java operator-precedence issue was not a behavioral bug: latest-message cues are intentionally enough on their own, while older-context cues require a multi-message thread.
- Parentheses were added so the intended semantics are explicit.
- Message text is now marked as untrusted data in both system and user prompt.
- Total message context passed to the 4B relevance model is bounded.
- JSON extraction now uses balanced-brace parsing that respects quoted strings rather than first/last-brace slicing.
- Trivial-message normalization uses precompiled regex patterns.
- Policy version bumped to `thread_model_adjudicator_006`.

### Intentional capture backfill
- Confirmed issue: the old backfill used `lexicalSearch("", limit*3)`, which is a recency-biased generic Vault query. Screenshot volume could push older manual captures out of the backfill window.
- Fix: direct indexed-ish query over analyzed sources `manual`, `manual_recording`, and `quick_capture`.
- Added explicit negation guard so phrases such as `don't need to`, `no need to`, `مش لازم`, `مش محتاج`, etc. are not promoted as strong Actions.
- Named the project-candidate confidence threshold instead of leaving an unexplained magic literal.

### People / Projects lifecycle
- Confirmed issue: raw `new Thread()` work could outlive the Activity and the Activity did not close its `VaultDb`.
- Fix: a single lifecycle-owned executor, generation guard, shutdown in `onDestroy()`, and explicit database close.

### Contact phone identity
- External review incorrectly claimed +20 was ignored; the previous code already normalized Egyptian mobile local/+20 variants.
- Improvement: use Android `PhoneNumberUtils.formatNumberToE164(..., "EG")` first so Egyptian mobiles and landlines get platform E.164 normalization without adding another dependency. Keep a digits-only fallback for unusual imported values.

## Reviewed but not treated as release blockers

### RelevanceEvaluationStore insert/update race
- The current deterministic insert-then-update sequence is not as dangerous as reported: the second update touches deterministic/final columns and does not erase model columns.
- SQLite serializes writes and the model adjudicator is downstream of deterministic evaluation.
- A transactional/upsert refactor remains a reasonable cleanup, but no observed corruption or failing audit currently justifies a large correctness-sensitive rewrite before runtime validation.

### Audit queue / thread-gap scans
- These are Advanced/evaluation paths, not hot ingestion paths, and current table sizes are small enough that a larger indexing/query rewrite is deferred until profiling shows a real cost.

### People list RecyclerView / LIKE query optimization
- Valid scalability ideas, but current caps are 100–120 rows and entity counts remain modest. Deferred in favor of correctness/privacy fixes.

### Semantic cue substring matching
- Can cause extra local-model adjudications on edge cases, but it gates expensive reasoning rather than directly creating a durable Action. Existing confidence/review policy remains the correctness guard. Further token-aware matching should be driven by evaluation data to avoid hurting Egyptian-Arabic recall.

## Runtime validation required
The above changes are code-complete only. They must be compiled and then checked on-device before being marked VERIFIED.
