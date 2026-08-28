# Cortex Cognitive Architecture V4 — Stage E: Situations + Pulse

## Purpose

Stage E turns grounded Memory into a live cognitive state without making local heuristics the final brain.

Canonical flow:

`Evidence -> Memory -> Situation detection -> live Now policy -> ChatGPT Deep Brain ranking -> Pulse`

Worlds/Facts enrich this flow as they mature, but a Situation may be created directly from a grounded Memory when the actionable signal is explicit.

## Local Situation Engine

`CognitiveSituationEngineV4` is deliberately conservative. It detects explicit shapes such as:

- account/security risk
- upcoming event with explicit clock time
- explicit deadline
- missed-call follow-up
- explicit waiting state
- explicit future commitment

The local engine does **not** decide the user's final priority order. Initial attention/interruption values are baseline signals only.

Every Situation is deterministic/idempotent and is grounded to its source Memory through V4 provenance.

## Live Now policy

Canonical Situation state is durable history. "What matters now" is a read-time projection, so Cortex must not rewrite historical attention merely because time passed.

`CognitiveNowPolicyV4` derives a current score from:

- canonical attention
- confidence and interruption
- Situation kind
- state (`RELEVANT`, `WAITING`, `DEFERRED`, etc.)
- deadline/event temporal proximity
- whether an action is currently proposed
- freshness of the last Deep Brain judgement.

Important consequences:

- near deadlines gain urgency without mutating their stored baseline;
- an old event falls out of Now instead of living forever as an urgent card;
- `DEFERRED` does not reappear because of an old ChatGPT rank;
- Deep Brain rank decays with age;
- a ChatGPT judgement becomes stale immediately if the canonical Situation changes after that judgement, even if the model response itself is only minutes old.

## Reasoning freshness

`CognitiveReasoningFreshnessV4` uses the timestamp of the last successfully **applied** Deep Brain response as the reasoning boundary.

A Situation is new to the model when its canonical `updated_at` is later than that boundary. `updated_at`, rather than only `created_at`, is intentional: new Evidence can materially change an existing stable Situation.

Pulse exposes this as `newSinceDeepBrain`, and the compact ChatGPT packet includes:

```json
{
  "reasoning_freshness": {
    "latest_applied_at": 0,
    "new_open_situations": 1,
    "newest_situation_change_at": 0
  }
}
```

Per Situation the packet may also include:

- `attention_score` — live current Now score;
- `canonical_attention_score` — durable baseline;
- `changed_at`;
- `new_since_deep_brain`;
- `connector_enriched` when trusted Second Brain evidence enriched the supporting Evidence.

ChatGPT is instructed to reconsider `new_since_deep_brain=true` rather than preserving an older rank by inertia. `connector_enriched` is provenance, not an urgency boost by itself.

## Deep Brain reconciliation

ChatGPT output is useful cognitive state, not just display text.

`CognitiveDeepBrainReconcilerV4` connects previously-applied `CORTEX_RESPONSE_V1` data to canonical Situations using the Memory IDs that ChatGPT cited.

A stored Deep Brain priority may:

1. link itself to the matching open Situation;
2. raise that Situation's `attention_score`;
3. move a locally `DETECTED` Situation to `RELEVANT` when the Deep Brain ranking is strong;
4. attach an already-stored proposed action to the Situation;
5. add audit provenance showing that the ranking came from a Deep Brain priority item.

It may **not**:

- modify Evidence;
- rewrite Memory;
- rewrite Facts;
- mark a Situation RESOLVED/CANCELLED/DISMISSED;
- execute an external action.

User deferral/dismissal is not overridden by reconciliation.

## Refresh replacement semantics

Deep Brain ranking and suggested actions are projections of a reasoning pass, not historical facts.

When a newer validated response is applied:

- an explicit empty `priority_items: []` retires the previous active model ranking;
- a non-empty priority list must contain at least one grounded valid item before it may replace the last known-good ranking;
- an entirely hallucinated/invalid ranking is skipped safely and cannot erase the previous grounded ranking;
- the same rule applies to ChatGPT `suggested_actions`;
- only still-`PROPOSED` ChatGPT-origin actions are superseded; local/user actions and already-executed actions are untouched;
- replay of an already-applied request remains idempotent.

This prevents both kinds of stale state: old model decisions lingering forever and malformed new model output destroying a useful previous result.

## Pulse projection

`CognitivePulseProjectionV4` reads canonical Situation state and overlays only **current** Deep Brain ranking/reason/action proposals.

Current ordering uses dynamic Now score first, then freshness/model/time tie-breaks. A Deep Brain cluster may suppress a sibling local card only while that model cluster is still newer than the linked canonical Situation.

The launcher displays `PULSE · CANONICAL` above the legacy attention sections during real-device validation. It can visibly label:

- `ChatGPT` — a current model-ranked Situation;
- `NEW` / `NEW CONTEXT` — a Situation changed after the last applied ChatGPT pass;
- `SECOND BRAIN` — the supporting Memory is grounded in Evidence with `CONNECTOR_ENRICHMENT`.

When new context exists, the CTA becomes `Refresh ChatGPT · N new`. That action builds a fresh grounded packet and opens the ChatGPT share flow in one tap.

## Deep Brain packet selection

The packet builder uses the same dynamic Now policy as Pulse rather than simply sorting by stored `attention_score`. This matters because the model must not receive a stale-biased context before reasoning even starts.

Selected Situations' supporting Memories are guaranteed packet space before generic recent-memory fill. A high-priority deadline/risk therefore cannot be sent to ChatGPT without the actual grounded message that produced it merely because unrelated recent Memories filled the packet limit.

Cloud/privacy constraints remain hard filters.

## Why ChatGPT data matters after import

A Deep Brain response creates durable downstream value:

- **current prioritisation** — Cortex can show which open Situation the latest valid reasoning pass judged most important;
- **state update** — attention/relevance follows that judgement until newer reasoning, newer Evidence, or user feedback supersedes it;
- **action continuity** — current suggested actions stay attached to their Situation while stale proposals retire on refresh;
- **future context** — the next packet contains current Situations and freshness markers rather than starting from raw notifications;
- **Pulse UX** — the home surface reflects current model ranking without a local imitation of the model;
- **auditability** — request, response, grounded IDs and situation-update audit remain available.

This makes the loop cumulative but not sticky:

`Cortex state -> ChatGPT reasoning -> validated update -> new Evidence invalidates stale judgement -> next ChatGPT reasoning`.

## Real E2E fixture

The current Second Brain + Cortex + ChatGPT validation fixture is:

```text
CORTEX_E2E_001 — يا كريم، محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده. لو مش هتلحق ابعتلي وقولي عشان أتصرف.
```

Regression coverage verifies:

`weak native WhatsApp preview -> trusted Second Brain enrichment -> ACTION relevance -> realtime V4 Memory -> DEADLINE Situation -> 17:00 same-day deadline`.

The remaining release gate is device validation of the latest build so the visible Pulse can be checked for the expected `SECOND BRAIN · NEW CONTEXT · DEADLINE` card, followed by one ChatGPT refresh/apply pass.
