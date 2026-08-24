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

## Rolling rule
When a new user observation arrives:
1. Record it here.
2. Classify it into the current PRIME phase.
3. Fix it immediately only if it blocks correctness/safety/current surface work; otherwise fold it into the nearest planned pass.
4. Do not derail the overall completion sequence.
5. Mark VERIFIED only after runtime validation on the installed build.
