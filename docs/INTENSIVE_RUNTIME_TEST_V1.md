# Cortex Intensive Runtime Test V1

This is the release-blocking real-device validation for the current Cortex + Cortex Relay architecture.

The existing regression suite is intentionally retained. Regression tests prevent known bugs from returning; this runtime campaign adds end-to-end evidence for paths that unit/instrumented-source compilation cannot prove on a real phone.

## Scope

Validate the live chain:

`Capture / Cortex Relay → Cortex Local Bus → Raw Signal / Evidence → Memory → Situation → Pulse → autonomous Gemini → grounded priority/action`

and the independent intentional voice chain:

`Voice recording → AnalysisQueue → ASR → intentional V4 projection → Situation → Pulse → autonomous Gemini when policy warrants`

## Gate 0 — Build and identity

Cortex must pass:

- repository audit,
- `:app:assembleDebug`,
- `:app:compileDebugAndroidTestJavaWithJavac`,
- permanent Cortex signer verification,
- update-in-place installation only.

Cortex Relay must pass its Android CI or an equivalent local Gradle build, permanent Relay signer verification, and update-in-place installation only.

No uninstall is part of this test.

## Gate 1 — Voice / ASR / intentional projection

Record one explicit future voice note such as:

`CORTEX_VOICE_E2E_001 — بكرة الساعة عشرة أأكل البغبغان.`

Then use NOW/Inbox/Runtime Pipeline refresh.

Pass requires:

1. the newest AUDIO item does not remain permanently `queued`, `analyzing`, or `failed_retryable`,
2. the diagnostic shows an ASR attempt and then `analyzed`,
3. `extracted_text_chars > 0`,
4. an Evidence + Memory mapping exists,
5. an `UPCOMING_EVENT` or equivalent grounded Situation exists with tomorrow at 10:00,
6. the Situation is eligible for Pulse.

If the test fails, `runtime_pipeline_v1.analysis` and `recent_internal_transitions` must identify whether the blocker is attachment, privacy, missing provider, retryable provider failure, timeout, or projection.

## Gate 2 — Cortex Relay tunnel

Receive one unique WhatsApp notification while Relay capture is running, for example:

`CORTEX_RELAY_E2E_001 — ابعت ملف الاختبار بكرة الساعة 5.`

Pass requires the same captured event to progress in Relay:

`CAPTURED → WAITING → SENT/AWAITING ACK → DELIVERED`

and Cortex to contain the matching wire id:

`sb_<relay event id>`

with:

- connector id `second_brain`,
- source package `com.whatsapp`,
- ingest state `ACCEPTED` or `DUPLICATE_ACCEPTED`,
- a non-zero canonical `signal_id` for an accepted event.

Relay must not dequeue or report delivery merely because Android binding or `Messenger.send()` succeeded.

On timeout/transient failure the same event remains queued for retry. A terminal Cortex rejection must be shown as `REJECTED`, not silently lost.

## Gate 3 — canonical cognition

For meaningful accepted evidence, inspect Cortex Runtime Pipeline.

Pass requires traceability across the available canonical stages:

- raw signal / Evidence,
- relevance-gated Memory where appropriate,
- Situation,
- Pulse eligibility/current score.

The test does not require every notification to become a Memory or Situation; conservative relevance filtering is part of the product. It does require the diagnostic to make that distinction visible rather than making data silently disappear.

## Gate 4 — autonomous Gemini

For a fresh meaningful Situation, `runtime_pipeline_v1.autonomous_gemini` must explain the current decision.

Expected diagnostic fields include:

- `enabled`,
- `gemini_key_configured`,
- `effective_model`,
- `policy_should_run_now`,
- `policy_reason`,
- `runtime_gate_allowed`,
- `runtime_gate_reason`,
- cooldown/backoff/budget state,
- latest provider/model/run state/error when a run exists.

If policy and runtime gate both allow execution, the normal path must use Gemini internally. ChatGPT sharing is not part of the pass criteria and must not be required.

A successful autonomous result must remain grounded in supplied Cortex IDs and pass the existing Deep Brain apply boundary.

## Gate 5 — intensive diagnostics

From Cortex:

`Settings → Advanced → Runtime pipeline → Export intensive JSON`

This file contains the existing exhaustive DebugExporter output plus `runtime_pipeline_v1` interpretation for ASR, Local Bus, V4/Pulse and Gemini.

From Cortex Relay:

`Cortex Relay → Export Relay diagnostic JSON`

Relay export intentionally omits notification title/body text and records delivery mechanics only.

Join the two files by:

`Relay wire_event_id == Cortex connector_ingest_events.event_id`

This gives deterministic cross-app proof without expanding the Local Bus V1 wire contract or granting either app direct access to the other app's sandbox.

## Pass decision

Do not mark the architecture validated from UI appearance alone. A release pass requires:

- both apps compile,
- update-in-place install succeeds with the permanent signer,
- voice E2E passes,
- one Relay WhatsApp E2E passes with correlated Cortex ACK,
- canonical progression is traceable,
- Gemini gate/run behavior is explained and, when warranted, executed,
- diagnostic exports contain enough evidence to locate any failed stage.

PRs remain Draft / Unmerged until these gates pass and the user explicitly authorizes merge.
