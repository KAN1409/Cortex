# Cortex Autonomous Deep Brain V4

Status: additive / Draft PR #65 / no merge

## Goal

Make Cortex reason internally about meaningful fresh context without requiring the ChatGPT share round-trip, while keeping Cortex—not the model—as the authority over canonical state.

## Runtime loop

```text
Phone / Second Brain
        ↓
Raw Signal → immutable Evidence
        ↓
relevance-gated Memory
        ↓
Situation Engine
        ↓
material fresh Situation?
        ↓ yes
WorkManager (network constrained)
        ↓
local deterministic policy + budget/backoff/fingerprint gate
        ↓
CORTEX_CONTEXT_V2 packet
        ↓
GeminiCognitiveReasoningProviderV4
        ↓
structured JSON + request_id + contract validation
        ↓
CognitiveDeepBrainApplyV4
        ↓
allowed-ID/state/action validation
        ↓
Pulse + suggested actions
```

ChatGPT remains an optional user-triggered **Deep review** provider through the existing share/import protocol. Local Qwen remains the grounded on-device Ask/refinement path; it is not yet trusted to emit the structured autonomous priority contract.

## Gemini provider contract

- Cortex uses the configured Gemini generation model; the default for an unset preference is `gemini-3.7-flash`. Existing explicit model preferences are preserved across update-in-place installs.
- The Gemini API key stays encrypted with Android Keystore and is sent in the `x-goog-api-key` request header, not embedded in the request URL.
- GenerateContent uses the current `generationConfig.responseFormat.text` structured-output contract with `application/json` and a bounded JSON schema.
- The schema requires the complete Cortex reasoning response surface and constrains priority/state/action fields, array sizes and numeric score ranges.
- Every suggested action is schema-guided to reference a supplied `situation_id` or `world_id`; Cortex independently re-validates that grounding before persisting anything.
- Structured output constrains syntax and shape only. It never replaces Cortex semantic/authority validation.

## Trigger policy

Autonomous cloud reasoning is not called for every notification. Only fresh Situations that are semantically meaningful or high-attention qualify, including risks, deadlines, upcoming events, commitments and waiting states. Low-attention follow-up/noise remains local.

A realtime projection schedules Deep Brain only when the Situation Engine reports a **material canonical change**: a newly-created Situation or an existing Situation whose evidence-derived timing was enriched/corrected. Merely rediscovering an old candidate does not spend cloud work.

Native Cortex notification capture and the trusted Second Brain Local Bus both feed the same realtime V4 projection boundary.

Deadlines within two hours, recent overdue deadlines (up to one day), risks and very-high-attention fresh changes may use the urgent lane. Far-overdue deadlines remain meaningful but no longer remain on the 45-second urgent cooldown lane indefinitely.

## Packet grounding

- Situation selection uses the same dynamic Now policy as Pulse and gives fresh canonical changes packet space first.
- Supporting Memories for selected Situations are added before generic recent Memories, protecting richer connector text needed to understand a selected deadline/risk.
- Reasoning freshness is tracked per Situation. A bounded applied request cannot make an omitted open Situation look already reviewed.
- Cloud/privacy policy is enforced before a Situation, Memory, World, Fact or phone context is included.

## Safety / authority

- Evidence remains immutable.
- The model never writes the database directly.
- The packet contains bounded IDs and cloud-policy-filtered context.
- Gemini must return the exact request_id and the complete structured response contract.
- Every ranked priority must ground to an allowed Situation, Memory or World ID.
- A non-empty ranking with zero valid grounded items fails closed and does **not** mark the request applied/freshness-covered.
- Suggested Actions must ground to an allowed Situation or World; free-floating or invalid-grounding model actions are skipped and cannot erase the last known-good model action set.
- External actions remain proposals and require the existing action-risk rules.
- Terminal user state (RESOLVED/CANCELLED/DISMISSED) cannot be reopened by richer grounding or delayed model output, and terminal Situations cannot receive delayed model actions.
- Richer evidence may update derived timing on an open Situation without resetting DEFERRED/WAITING/model/user state.

## Scheduling / cost control

- WorkManager survives process death and requires network connectivity.
- New triggers append behind in-flight work instead of cancelling a running Gemini request.
- Every Worker re-evaluates current freshness before spending a provider call, so stale queued work becomes a no-op.
- If canonical context changes while Gemini is already running, the returned response is rejected as `STALE_CONTEXT`; this is audited separately from provider failure, does not add exponential failure backoff, clears the obsolete pass's transient cooldown, and schedules a fresh-context re-evaluation.
- The stale check runs inside the same database write transaction used to apply the model response, eliminating the check-then-write race window.
- A terminal user transition after packet creation also invalidates the old global priority judgement.
- The stale cloud call still counts against the daily call budget because provider work was actually spent.
- Duplicate context fingerprints are suppressed.
- Normal and urgent cooldowns, a daily call budget, and exponential failure backoff limit cost/battery churn.

## Observability

`v4_reasoning_runs` records provider, model, trigger, context fingerprint, state, latency and errors. The Now surface exposes a COGNITIVE LOOP card showing Second Brain tunnel contribution, current Deep Brain state and latest autonomous Gemini run.

## Validation gate

Regression coverage includes:

- urgent deadline trigger vs unchanged/low-value/far-overdue context,
- current Gemini structured-output response contract,
- hallucinated non-empty ranking fail-closed behavior,
- grounded Suggested Action enforcement and preservation of known-good proposals,
- realtime wake only on material Situation changes,
- richer timing updates preserving durable Situation state,
- terminal-state preservation against delayed reasoning,
- stale-response rejection and stale-vs-provider-failure classification,
- per-Situation freshness and stale-model reconciliation rules.

A real compile + instrumented-source build remains required before device installation. GitHub Actions is currently failing before any job step starts, so those failures are infrastructure signals, not compile results.
