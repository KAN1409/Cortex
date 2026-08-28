# Cortex Cognitive Architecture V4 — Stage E: Situations + Pulse

## Purpose

Stage E turns grounded Memory into a live cognitive state without making local heuristics the final brain.

Canonical flow:

`Evidence -> Memory -> Situation detection -> ChatGPT Deep Brain ranking -> Pulse`

Worlds/Facts enrich this flow as they mature, but a Situation may be created directly from a grounded Memory when the actionable signal is explicit.

## Local Situation Engine

`CognitiveSituationEngineV4` is deliberately conservative. It detects explicit shapes such as:

- account/security risk
- upcoming event with explicit clock time
- explicit deadline
- missed-call follow-up
- explicit waiting state
- explicit future commitment

The local engine does **not** decide the user's final priority order. Initial attention/interruption values are low-confidence baseline signals only.

Every Situation is deterministic/idempotent and is grounded to its source Memory through V4 provenance.

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

## Pulse projection

`CognitivePulseProjectionV4` reads the canonical Situation state and overlays active Deep Brain ranking/reason/action proposals.

Ordering policy:

1. current ChatGPT-ranked Situations, by Deep Brain rank;
2. remaining local Situations, by canonical attention score and time relevance.

The current launcher displays `PULSE · CANONICAL` above the legacy attention sections during real-device validation. The legacy sections remain temporarily for side-by-side quality comparison; they are not a second source of V4 truth.

## Why ChatGPT data matters after import

A Deep Brain response creates durable downstream value:

- **priority memory** — Cortex remembers which open Situation ChatGPT judged most important;
- **state update** — attention/relevance follows that judgement until newer reasoning or user feedback supersedes it;
- **action continuity** — suggested actions remain attached to the Situation instead of disappearing with the chat response;
- **future context** — the next Deep Brain packet contains the updated Situations, so ChatGPT reasons over the previous cognitive state rather than starting from raw Memory every time;
- **Pulse UX** — the home surface can immediately reflect Deep Brain ranking without re-running a local imitation of the model;
- **auditability** — the original response/request and the provenance link remain available.

This makes the loop cumulative:

`Cortex state -> ChatGPT reasoning -> validated update -> richer Cortex state -> next ChatGPT reasoning`.

## Current validation gate

On real data, after installation/startup we expect:

- non-zero grounded `v4_situations` for explicit actionable recent Memories;
- existing Deep Brain priority items linked to matching Situations where their cited Memory IDs match;
- matching proposed actions linked to those Situations;
- `PULSE · CANONICAL` visible above legacy sections;
- the next compact Deep Brain packet to contain non-empty `situations`;
- a subsequent ChatGPT apply to report non-zero `situations updated` when it returns `priority_updates` against those Situation IDs.
