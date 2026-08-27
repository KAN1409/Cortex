# Cortex Donor Blueprint

Status: planning artifact only. No donor code should be copied blindly.

## Product rule

Cortex remains the single authority for truth, persistence, provenance, lifecycle, prioritization and execution. Donor projects contribute implementation patterns or isolated modules only when they solve a confirmed Cortex gap.

## Donor roles

| Donor | Primary role | What Cortex should take | What Cortex should not take |
| --- | --- | --- | --- |
| The Bridge | Perception / connectivity | passive event intake, notification/email/call normalization, batching, retry, cross-app correlation, background capture | separate truth store, app-specific UI, hard-coded importance rules |
| Mnemo | Memory / extraction heuristics | open-loop extraction, lightweight classification, entity/topic hints, dedupe heuristics, memory resurfacing ideas | duplicate DB schema, duplicate search engine, autonomous task creation |
| MeMessage-style UX | Intentional capture / self-inbox | message-like capture stream, Android share target, rich cards, timestamped timeline, composer, fast search, save-first enrichment-later behavior | copying proprietary code/UI; treating capture as understanding |
| Cortex | Intelligence authority | canonical evidence graph, people/project/situation linking, lifecycle truth, state evolution, NOW ranking, safe action proposal/execution | n/a |

## Target architecture

```text
PASSIVE INPUT                       INTENTIONAL INPUT
The Bridge-derived intake           MeMessage-style Inbox
notifications / mail / calls        text / links / files / voice / images
          \                             /
           \                           /
            -> Evidence Normalizer <-
                     |
                     v
             Canonical Evidence Store
                     |
          +----------+-----------+
          |                      |
          v                      v
  Mnemo-derived extraction   Content enrichment
  entities / open loops      URL / OCR / ASR / metadata
          |                      |
          +----------+-----------+
                     |
                     v
         Cortex Relationship Resolver
 Person <-> Thread <-> Situation <-> Project
                     |
                     v
         Cortex Lifecycle / State Evolution
                     |
                     v
              Attention Ranking
                     |
                     v
        Proposed Action -> Safety Gate -> Execution
```

## Priority donor capabilities

### P0 — Self Inbox

New first-class product surface for intentional capture.

Required behavior:
- Accept Android ACTION_SEND / ACTION_SEND_MULTIPLE.
- Save immediately before any model/enrichment work.
- Render one chronological message-like stream.
- Support text, links, images/screenshots, audio, files and manual notes.
- Every card exposes provenance and processing state.
- Link cards use explicit states: `pending_content`, `understood`, `fetch_failed`.
- Search is scoped to things intentionally sent to Cortex.
- Composer creates a normal evidence item, not a special note silo.

Recommended IA:

```text
Now | Inbox | World | Ask
```

Capture becomes an action available from the Inbox composer / global plus button, not a bottom-nav destination.

### P0 — Communication-to-Person Resolution

Bridge-derived normalized communication should resolve to a canonical person entity before higher reasoning.

Inputs:
- notification sender
- phone number
- email address
- contact id
- conversation/thread id
- calendar attendee

Output:
- `person_id`
- confidence
- provenance refs
- unresolved identity state when ambiguous

Never merge identities from name similarity alone.

### P0 — Evidence-to-Situation Stitching

Multiple observations about one real-world episode must become evidence of one situation rather than independent memories.

Signals may include:
- WhatsApp/message
- email
- missed call
- screenshot
- calendar event
- shared link
- manual note

Grouping should use person/thread/time/topic plus semantic evidence, then pass through Cortex lifecycle reconciliation.

### P1 — Open-loop Candidate Extraction

Mnemo-style heuristics may generate candidates such as:
- follow up
- waiting on
- send/review/submit
- appointment/reminder
- unresolved decision

But extraction creates a `candidate_obligation`, never an authoritative task. Cortex must reconcile it with newer evidence, closed state, and temporal truth first.

### P1 — Background Reliability

Bridge patterns worth reusing/adapting:
- persistent queue
- retry with backoff
- idempotent ingestion
- duplicate suppression
- batch processing
- app-process-safe workers/services

This should also power link enrichment, OCR/ASR retries and deferred model work.

### P1 — State Evolution

Cortex should update facts instead of merely accumulating them.

Examples:
- bank amount changed -> same financial state, latest value + history
- appointment rescheduled -> one situation, new target time
- setup completed -> resolve prior open situation
- person replied -> waiting-on state changes

## MeMessage research conclusions

The identified app `MeMessage | Save It All` describes itself as a local-only app for saving text/messages/shared content. Its privacy policy says saved content stays on-device, no account is required for core use, and shared content is processed locally. The product value visible from the supplied screenshot is the low-friction self-chat mental model: share something, it appears immediately in a timestamped personal stream.

Cortex should adopt the interaction model, not copy implementation or branding.

Useful adjacent open implementations/patterns for engineering research:
- Note to Self / Phone Safe: chat-interface notes, attachments, voice, folders, search, local-first storage, backup/restore.
- MoeMemos: quick self-posting, local/offline-first capture, attachments, export/sync patterns.
- SelfMsg: topics/conversations, tags, search, dates, text/voice/images/files, share-sheet ingestion and offline-first storage.

## Confirmed source-access status

GitHub currently exposes two repositories under the connected account:
- `KAN1409/Cortex`
- `KAN1409/th-bridge-`

The visible `th-bridge-` main branch currently contains only `mnemo/autoupdate/...` artifacts rather than the actual Bridge or Mnemo application source tree. Therefore no class-level donor extraction should be claimed until the original source trees are provided or found in another branch/repository/archive.

## Donor evaluation matrix

Every donor class/module must be scored before integration:

| Criterion | Question |
| --- | --- |
| Solves confirmed gap | Does it improve a failure already observed in Cortex tests/product use? |
| Architectural fit | Can it feed Cortex canonical schemas rather than creating a parallel truth store? |
| Reliability | Is it idempotent, restart-safe and failure-observable? |
| Provenance | Can Cortex preserve where the evidence came from? |
| Lifecycle-safe | Can newer/closed evidence override it? |
| Model-safe | Does it avoid directly executing model output? |
| Maintainability | Is adaptation cheaper than clean reimplementation? |

Disposition for each donor piece:
- `TAKE_AS_IS`
- `ADAPT`
- `REIMPLEMENT_IDEA`
- `REJECT`

## First implementation tranche after source acquisition

1. Inventory actual Bridge and Mnemo source trees.
2. Produce a class-by-class donor matrix.
3. Extract only P0/P1 modules.
4. Build `Inbox` on Cortex's existing `knowledge_items` / evidence path, not a second database.
5. Add communication/person normalization before situation reasoning.
6. Add queue/retry infrastructure around enrichment.
7. Extend phone-only review with product gates for:
   - Inbox capture reliability
   - person resolution
   - cross-source situation stitching
   - link processing state
   - no duplicate obligations
8. Only then modify bottom navigation from `Now/World/Capture/Ask` toward `Now/Inbox/World/Ask` if the Inbox implementation passes the new gates.

## Non-negotiables

- Save first; enrich asynchronously.
- Evidence is not automatically an action.
- A notification is not automatically important.
- A shared URL is not `understood` until content extraction succeeds.
- A person name is not an identity without corroboration.
- Closed/resolved truth outranks similarity retrieval.
- No donor model response directly executes an action.
- No full-project merge; donor code is spare parts only.
