# Cortex V4 — Persistence & Identity

Status: Stage B contract for `architecture/pulse-memory-worlds-think-v1`.

This document defines how the V4 cognitive hierarchy is persisted without turning semantic similarity into identity and without destructively replacing current Cortex data.

## 1. Storage rule

The canonical hierarchy remains:

```text
Evidence → Episode → Memory → Facts / Worlds / Relations → Situation
                                                    ↓
                                                  Pulse

All canonical layers → Think
```

Persistence tables are namespaced `v4_*` during migration. Legacy tables remain readable until device equivalence is proven.

`CognitiveSchemaV4.ensure()` is idempotent and additive.

## 2. Identity is not similarity

Three concepts must never be conflated:

```text
Identity   = these records are the same canonical thing.
Similarity = these records look related enough to retrieve together.
Relation   = these distinct things are connected.
```

Embedding similarity never authorizes a destructive merge.

A low/medium identity hypothesis can improve retrieval without changing canonical IDs.

## 3. Stable IDs

V4 domain IDs are strings with a type prefix and a deterministic SHA-256-derived suffix where identity is deterministic.

Examples:

```text
ev_ab12...  Evidence revision
si_82fe...  Situation
```

Objects whose grouping can evolve (Episodes, emerging Worlds) receive an opaque persisted ID once and retain that ID while their membership changes.

### Evidence

With a platform external ID:

```text
source type + package + external ID + content hash
```

This means repeated callbacks for one unchanged notification revision produce one Evidence object.

If the notification body changes while the notification key stays the same, the content hash changes and Cortex creates a new Evidence revision.

Without an external ID, bounded temporal identity is used:

```text
SCREEN       10-second bucket
APP_ACTIVITY 30-second bucket
NOTIFICATION 60-second fallback bucket
SYSTEM       60-second bucket
other        1-second fallback bucket
```

Image/file/voice evidence is content-addressable by asset hash when no explicit capture ID is available.

### Episode

Episode identity is allocated once. A deterministic candidate key is used only to find/reopen a likely current grouping:

```text
kind + source package + durable context key + bounded start bucket
```

Changing Episode membership never changes its persisted ID.

### Memory

Memory identity uses stable supporting Evidence/Episode identity, not generated title text.

Changing a summary or title therefore does not create a new historical Memory.

### Fact

Facts have two identities:

```text
slot key    = subject world + predicate
version key = slot key + value + valid-from
```

Example:

```text
slot: Cortex / current_branch
v1:   main
v2:   cleanup/repo-consolidation
```

The slot is stable while fact versions remain auditable.

### Relation

```text
source object + relation type + target object
```

The edge identity is stable; confidence/grounding may be revised without inventing duplicate edges.

### Situation

Situation identity represents the unresolved real-world condition, not the source notification.

Long-lived kinds:

```text
COMMITMENT
WAITING
UNRESOLVED_QUESTION
FOLLOW_UP
```

use:

```text
kind + primary world + semantic anchor
```

Repeated messages about `send revised plan` therefore update one Situation.

Event-shaped kinds such as RISK/DEADLINE/MEANINGFUL_CHANGE require an occurrence discriminator:

```text
transaction ID
due-date bucket
appointment ID
event occurrence window
```

If an event-shaped Situation has no stable provider event ID, the detector must create a bounded deterministic occurrence bucket before persistence. Omitting occurrence identity is rejected rather than risking permanent over-merge.

## 4. World identity

Worlds use explicit identity claims rather than name matching.

Claim types include:

```text
USER_KEY
CONTACT_ID
PHONE_E164
EMAIL
ACCOUNT_ID
PACKAGE_NAME
DOMAIN
CANONICAL_URL
EXTERNAL_ID
EXACT_NAME
MODEL_ALIAS
```

Claim strength:

```text
STRONG
MEDIUM
WEAK
```

### Automatic merge

Automatic merge is allowed only when `CognitiveIdentityV4.Match.canAutoMerge()` is true.

A shared claim can authorize automatic merge only when the matching claim on both Worlds is `STRONG` or user-confirmed.

Examples of durable anchors:

- same explicit user identity key,
- same strong contact ID for a person,
- same strong normalized phone identity for a person,
- same strong account/package identity where appropriate,
- same strong canonical external identity.

Exact display name alone is **never** enough for auto-merge. Weak matching phone/name/model claims also remain non-destructive retrieval hints.

`Ahmed` + `Ahmed` may be jointly retrieved as possible matches while remaining separate Worlds.

### Reversible merges

Merges are represented by `v4_world_merges`.

A merge does not rewrite source Evidence, Memory, or identity claims.

```text
child World --MERGED_INTO--> parent World
```

Reverting the merge restores the child World identity and its original claims.

User-confirmed merge/correction outranks inferred matching.

## 5. Provenance

`v4_provenance` is the generic grounding ledger.

Every derived canonical object can point back to its support:

```text
MEMORY    ← EVIDENCE
FACT      ← EVIDENCE / MEMORY
SITUATION ← EVIDENCE / MEMORY / FACT
WORLD     ← EVIDENCE / MEMORY
RELATION  ← EVIDENCE
```

Observed and inferred personal claims without provenance are invalid.

`CognitiveGroundingV4` enforces that boundary for Facts, Relations, Situations and Think reasoning blocks. `SUGGESTED` Think blocks may be uncited recommendations, but they are never historical Facts.

## 6. Evidence immutability

`v4_evidence.original_text`, source identity, original asset reference and occurrence time are immutable after insertion.

Repeated callbacks may update observation metadata such as `updated_at`, but may not replace original captured content.

OCR/transcription/model outputs are appended to `v4_evidence_analysis` with engine/version/content hash.

A newer OCR model therefore creates a new analysis row rather than rewriting Evidence.

## 7. Retention

Normal Evidence/Memory defaults to `EPISODIC_90_DAY`.

Pinned and promoted long-term knowledge may outlive source expiry according to policy, but provenance behavior must remain explicit:

- if detailed source evidence is deleted by retention, retained long-term Facts keep a retention/consolidation provenance record,
- user-pinned source Evidence is not expired,
- shared assets are garbage-collected only when no retained object references them.

Retention implementation follows after canonical write/read paths are validated.

## 8. Legacy bridge

`v4_legacy_map` records migration without overwriting legacy IDs.

Example:

```text
raw_signals / 442 → EVIDENCE / ev_...
derived_items / 98 → SITUATION / si_...
entity_nodes / 17 → WORLD / world_...
```

Backfill is idempotent. A legacy row may be mapped, validated, rejected, or remapped without deleting the source row.

## 9. Tables

Stage B adds:

```text
v4_evidence
v4_evidence_analysis
v4_episodes
v4_episode_evidence
v4_memories
v4_memory_evidence
v4_worlds
v4_world_aliases
v4_world_identity_claims
v4_world_merges
v4_facts
v4_relations
v4_situations
v4_action_proposals
v4_provenance
v4_legacy_map
```

These are canonical persistence, not separate product-surface stores.

There is deliberately no `pulse_cards`, `memory_screen_rows`, `world_dashboard`, or `think_answers` truth table.

## 10. Read model rules

### Pulse

Queries unresolved/relevant Situations, then joins supporting Worlds/Facts/Memories.

### Memory

Queries Memories/Episodes with Evidence drill-down.

### Worlds

Resolves reversible World merges and composes Facts/Relations/Memories/Situations.

### Think

Retrieves across canonical layers. Reasoning output is not truth until an explicit correction/action/consolidation path writes a valid canonical object.

## 11. Release-blocking identity tests

The architecture must preserve these properties:

1. Same notification key + same content hash = one Evidence revision.
2. Same notification key + changed content = new revision.
3. Same strong contact ID can auto-merge a Person World.
4. Same display name or weak identity hint alone cannot auto-merge Worlds.
5. Repeated signals about one commitment keep one Situation identity.
6. Event-shaped Situations require occurrence identity and distinct occurrences do not collapse forever.
7. Fact slot stays stable while values become separate versions.
8. World merge is reversible without source deletion.
9. No unsupported observed/inferred Fact is accepted by the grounding boundary.
10. No suggestion becomes personal history merely because Think generated it.
