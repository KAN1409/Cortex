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
strict JSON / request_id / contract validation
        ↓
CognitiveDeepBrainApplyV4
        ↓
allowed-ID/state/action validation
        ↓
Pulse + suggested actions
```

ChatGPT remains an optional user-triggered **Deep review** provider through the existing share/import protocol. Local Qwen remains the grounded on-device Ask/refinement path; it is not yet trusted to emit the structured autonomous priority contract.

## Trigger policy

Autonomous cloud reasoning is not called for every notification. Only fresh Situations that are semantically meaningful or high-attention qualify, including risks, deadlines, upcoming events, commitments and waiting states. Low-attention follow-up/noise remains local.

A realtime projection schedules Deep Brain only when the Situation Engine reports a **material canonical change**: a newly-created Situation or an existing Situation whose evidence-derived timing was enriched/corrected. Merely rediscovering an old candidate does not spend cloud work.

Native Cortex notification capture and the trusted Second Brain Local Bus both feed the same realtime V4 projection boundary.

## Safety / authority

- Evidence remains immutable.
- The model never writes the database directly.
- The packet contains bounded IDs and cloud-policy-filtered context.
- Gemini must return the exact request_id and the complete structured response contract.
- Every ranked priority must ground to an allowed Situation, Memory or World ID.
- A non-empty ranking with zero valid grounded items fails closed and does **not** mark the request applied/freshness-covered.
- External actions remain proposals and require the existing action-risk rules.
- Terminal user state (RESOLVED/CANCELLED/DISMISSED) is not reopened by richer grounding.
- Richer evidence may update derived timing on an open Situation without resetting DEFERRED/WAITING/model/user state.

## Scheduling / cost control

- WorkManager survives process death and requires network connectivity.
- New triggers append behind in-flight work instead of cancelling a running Gemini request.
- Every Worker re-evaluates current freshness before spending a provider call, so stale queued work becomes a no-op.
- Duplicate context fingerprints are suppressed.
- Normal and urgent cooldowns, a daily call budget, and exponential failure backoff limit cost/battery churn.

## Observability

`v4_reasoning_runs` records provider, model, trigger, context fingerprint, state, latency and errors. The Now surface exposes a COGNITIVE LOOP card showing Second Brain tunnel contribution, current Deep Brain state and latest autonomous Gemini run.

## Validation gate

Regression coverage includes:

- urgent deadline trigger vs unchanged/low-value context,
- complete Gemini response contract,
- hallucinated non-empty ranking fail-closed behavior,
- realtime wake only on material Situation changes,
- richer timing updates preserving durable Situation state,
- per-Situation freshness and stale-model reconciliation rules.

A real compile + instrumented-source build remains required before device installation. GitHub Actions is currently failing before any job step starts, so those failures are infrastructure signals, not compile results.
