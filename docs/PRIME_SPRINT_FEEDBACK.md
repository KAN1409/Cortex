# Cortex PRIME Sprint Feedback Ledger

This file is the persistent rolling ledger for user-observed product issues during the PRIME completion sprint.
Feedback is folded into the correct phase without stopping the active implementation sequence.

Status values: `NEW` · `PLANNED` · `IMPLEMENTED` · `VERIFIED`

## Product guard — No orphan data
**Status:** IMPLEMENTED

Any surfaced item must make its provenance understandable without requiring the user to remember the original notification or capture event.

For notification-derived memories, Cortex should expose when available:
- source application label and package
- exact notification/capture timestamp
- Android notification subtype/category/channel when available
- capture path (`Android notification`)
- original stored evidence
- Cortex relevance disposition and the reason it was retained

A notification must not appear merely as generic `TEXT / Memory` when Cortex knows it is a notification.

Implementation checkpoint:
- `MemoryProvenance` centralizes provenance interpretation.
- `NotificationCaptureService` records capture kind, channel, category/template and inferred subtype for new notifications.
- `VaultActivity` shows notification type, source app, exact time, Origin and `Why Cortex kept it`.
- Old promoted notification memories can recover package/time/relevance provenance from their existing metadata.

## OCR wrong-script / Arabic garbage entering evidence
**Status:** VERIFIED

Tesseract Arabic hallucinations must remain diagnostic evidence only when they fail the central OCR quality gate. They must never contaminate Cortex memory, entities or embeddings.

Verified by the user on OCR comparison #793: the Arabic candidate was rejected and the safe merged output retained the usable Latin OCR.

## Mixed Arabic / English voice presentation
**Status:** VERIFIED

Bidi formatting is display-only and centralized. Stored ASR evidence stays plain/verbatim. Voice titles are compact presentation text and redundant summary/evidence blocks collapse when effectively identical.

Verified by the user on the same historical mixed Arabic/English voice item after the Bidi/presentation pass.

## Android contacts falsely becoming tasks / attention
**Status:** IMPLEMENTED

Imported contacts are passive People/reference evidence. Contact names containing words such as `Fix` or `Book` must not create Actions, open loops or proactive resurfacing. Phone numbers are canonicalized before dedupe. Legacy false contact actions are removed by idempotent maintenance.

Runtime re-check remains part of the final release validation.

## Brain semantic self-echo
**Status:** IMPLEMENTED

Brain must not answer operational questions by retrieving screenshots of Cortex's own UI or prompt suggestions. State questions such as attention, waiting, decisions, goals and ideas route to the live cognitive ledger first. Ask-scoped semantic retrieval filters Cortex self-UI contamination while Vault search remains complete.

Runtime re-check remains part of the final release validation.

## PRIME navigation consolidation
**Status:** IMPLEMENTED

Normal navigation is `Input · Brief · People / Projects · Brain`. Legacy Home / Focus / Vault / Brain dashboard concepts are not top-level surfaces; Archive is secondary and diagnostic/test activities remain internal/Advanced.

## Brain identity
**Status:** IMPLEMENTED

Cortex remains the application/system name. `Brain` is the user-facing AI surface inside Cortex. Internal class/package names may retain historical Cortex names where renaming would add migration risk without user value.

## Brain source modes and cloud privacy
**Status:** IMPLEMENTED

Brain supports explicit modes:
- `Your data`: Cortex/local route; no cloud memory upload.
- `External`: external Gemini route with the user's question only; no Cortex memory is sent.
- `Combined`: explicit cloud route using only selected Cortex evidence that passes per-source cloud privacy policy.

Contacts and Calendar default to `Local only` for cloud use. `Never collect` is enforced for manual integration imports, not merely displayed as a setting.

## Intentional capture → cognitive ledger bridge
**Status:** IMPLEMENTED

Manual Text/Voice captures feed the unified derived graph rather than remaining only in legacy action tables. Strong explicit tasks can become Actions; ambiguous action-like clauses go through Review; explicit Waiting, Decision, Goal, Idea, Opportunity and Hypothesis language is preserved as derived intelligence. Project mentions become Project Candidates only and still require explicit confirmation. Historical intentional captures are backfilled with fingerprint dedupe.

## Visual Intelligence Gemini quota / request storm
**Status:** IMPLEMENTED

The 2026-08-24 diagnostic baseline showed 43 visual insight rows: 10 done, 7 skipped/protected and 26 failed, with repeated Gemini HTTP 429 quota errors. Rate limits must be treated as temporary provider state, never permanent image failure.

Implementation checkpoint:
- `VisionRateLimitGate` enforces a conservative rolling request budget plus provider-wide cooldown.
- HTTP 429 uses the provider retry hint when available and stops immediate in-call retry.
- Visual items enter `rate_limited`, keep their original evidence, and resume through delayed WorkManager work.
- Visual pipeline v50 gives pre-v50 failures one safe retry, allowing the historical 429 backlog to recover gradually.
- Manual Visual Intelligence shows `Waiting for Gemini quota` instead of `Visual analysis failed`.

Runtime validation remains required after the new build.

## Brain local refinement latency
**Status:** IMPLEMENTED

The same diagnostic baseline showed grounded retrieval completing in about 598 ms while optional local Qwen refinement took about 31.5 s. The default Brain `Your data` route remains grounded-fast. The optional `Improve wording` route now uses a compact prompt (top 4 sources with shorter excerpts, grounded draft included) and a 96-token output ceiling rather than the previous larger context / 180-token ceiling.

Runtime validation remains required; empirical device latency will determine whether local refinement remains worth exposing.

## Gemini external code-review recommendations
**Status:** IMPLEMENTED / DEFERRED-PERF

Review of the user-provided Gemini code suggestions against current `main`:
- Dynamic Brain model configuration: implemented with `GeminiModelConfig`; External/Combined model id is runtime configurable from Gemini settings without an app rebuild.
- Dynamic Combined evidence windows: implemented as a bounded 4,800-character evidence budget with 480–1,200 characters per source, preserving more context when few sources exist without uncontrolled prompt growth.
- OCR regex compilation: implemented in `OcrGarbageGate` using precompiled `Pattern` instances for token cleanup, whitespace splitting and line scoring hot paths.
- `ThreadModelAdjudicator` regex compilation: deliberately deferred. Its trivial-message regex work is tiny compared with 4B local-model inference, and changing a correctness-sensitive relevance file for microsecond-level savings is not justified until profiling shows it matters.

Runtime/compile validation remains required for the implemented changes.

## 2026-08-25 capture intelligence / Brief / People usability batch
**Status:** IMPLEMENTED / PLANNED

User testing showed that the installed product still felt like `transcription + OCR + storage` rather than a cognitive system. The following observations are now part of the PRIME contract:

- Capture Text keyboard/IME must never cover the Capture action. **IMPLEMENTED** with explicit IME/system-bar inset handling in `CaptureActivity`.
- Voice/Text/Photo/File capture must not end at `Saved to Cortex`. The user should immediately see processing and the resulting understanding. **IMPLEMENTED** through `CaptureResultActivity`, actual imported item ids, live polling, retry, `What Cortex understood`, optional extracted evidence, and contextual next actions.
- Text/code input must not be "transcribed" or copied back as its own summary. Technical command blocks are classified as code/commands, receive a functional summary, and are not auto-promoted as personal tasks merely because command words such as `send`, `fix`, or `review` appear. **IMPLEMENTED** in `LocalAnalyzer`.
- Brief must expose the user's latest intentional captures and their state (`Analyzing`, `Understood`, `Needs retry`) rather than hiding them while unrelated derived items appear. **IMPLEMENTED** in `PrimeBriefStore` / `PremiumHomeActivity`.
- Brief items must explain why they surfaced. **IMPLEMENTED (first pass)** with source, confidence, semantic reason and timing; deeper original-evidence linking remains **PLANNED**.
- People should prioritize useful real-world context over phone-normalization detail. **IMPLEMENTED (first pass)** by keeping identity hardening while surfacing recent non-contact-sync linked evidence.
- `Open archive` from People/Projects must deep-link to the relevant evidence, not drop the user into a generic list. **IMPLEMENTED** as direct `item_id` navigation to the latest grounded evidence.
- Brain Combined should not die when Gemini is unavailable if a safe local Cortex answer can still be produced. **IMPLEMENTED** with a Combined → local Cortex fallback while preserving the external failure in telemetry.
- Raw OCR/transcript are evidence for Cortex, not the primary user result. Extracted evidence is now treated as optional on the post-capture result surface. **IMPLEMENTED (surface-level)**.
- Visual Intelligence must move beyond OCR into image meaning, contextual interpretation and useful suggestions. OCR lexical accuracy for visually ambiguous media titles remains **PLANNED / requires visual-model validation**; a structural OCR score cannot prove that a plausible-looking word such as a song title was recognized correctly.
- Mixed Arabic/English presentation still needs a stronger visual layout than generic BiDi handling in some screens. **PLANNED** for a dedicated mixed-run presentation component; stored evidence remains unchanged.
- Product rule: **Never show extracted information when Cortex can show understanding instead; never stop at understanding when Cortex can offer a useful action.**

Runtime validation is required for all items marked IMPLEMENTED in this batch.

## Rolling rule
When a new user observation arrives:
1. Record it here.
2. Classify it into the current PRIME phase.
3. Fix it immediately only if it blocks correctness/safety/current surface work; otherwise fold it into the nearest planned pass.
4. Do not derail the overall completion sequence.
5. Mark VERIFIED only after runtime validation on the installed build.
