# Cortex Donor Matrix V1

Status: source-backed donor audit. This document turns the donor blueprint into concrete dispositions.

## Source reality

A full Mnemo 3.0.0 build script was recovered from the user's file library. It contains the generated Java source for the production-like Mnemo build, including notification capture, passive Outlook capture, native periodic capture, local memory, attention extraction and reminders.

The original PhoneBridge/The Bridge source tree is still not directly available as a clean archive/repository. However, Cortex debug archives contain build traces and source-fragment evidence proving the existence of the PhoneBridge project and classes such as `WhatsAppMessagingStyleCapture`, `BridgeSender`, and a retrying worker path. These are enough to define what to look for, but not enough to safely copy code verbatim.

MeMessage is treated as a product/UX donor only; no proprietary implementation should be copied.

## Executive disposition

| Donor piece | Disposition | Cortex target | Why |
| --- | --- | --- | --- |
| Mnemo `MnemoNotificationListener` | ADAPT | `NotificationCaptureService` / evidence normalization | Strong message extraction and duplicate suppression ideas; Cortex already has the stronger canonical pipeline |
| Mnemo `WorkMemoryStore` | ADAPT | new communication normalization helper + project/thread hints | Useful subject normalization, thread fingerprints, actionable-language heuristic, passive work intake |
| Mnemo `OutlookAccessibilityService` | REIMPLEMENT_IDEA | optional app-scoped passive context reader | Useful passive-only open-message capture pattern, but must feed Cortex raw evidence rather than a parallel store |
| Mnemo `NativeCaptureScheduler` / `NativeCaptureJob` | REIMPLEMENT_IDEA | existing background/context capture infrastructure | Persisted periodic capture pattern is useful; Cortex should not import a second scheduler architecture blindly |
| Mnemo `MemoryReminderScheduler` / `MemoryReminderJob` | REJECT_AS_AUTHORITY / ADAPT_HEURISTIC | Cortex attention/lifecycle engine | Good reminder dedupe/signature concept; bad fit if it bypasses Cortex truth and prioritization |
| Mnemo `MnemoMemoryDb` | REJECT_DB / ADAPT_HEURISTICS | `VaultDb`, `SemanticIndex`, situation reasoning | Cortex already has richer persistence and provenance. Reuse only intent words, type/source mapping, project hints and lightweight action candidates |
| Mnemo `MnemoHealth` | ADAPT | Cortex status/capability surfaces | Clear source-health checks and explicit source limitations are valuable |
| Mnemo OTP redaction | ADAPT | ingestion privacy/sensitive-text guard | Simple pre-ingestion protection worth keeping as one layer |
| Bridge `WhatsAppMessagingStyleCapture` | ADAPT_WHEN_SOURCE_FOUND | notification/message normalization | MessagingStyle-specific extraction likely improves sender/message fidelity |
| Bridge `BridgeSender` | REIMPLEMENT_IDEA | internal event bus / durable queue only | External relay transport is not Cortex architecture; retry and delivery semantics may be useful |
| Bridge retrying worker | ADAPT_WHEN_SOURCE_FOUND | durable enrichment/context worker | Latch/timeout/retry behavior is relevant to background reliability |
| MeMessage self-chat timeline | REIMPLEMENT_IDEA | new `Inbox` surface | Excellent intentional-capture mental model |
| MeMessage composer/share-first capture | REIMPLEMENT_IDEA | `ShareImporter` + Inbox composer | Cortex already has the ingest path; needs better first-class UX around it |

## Mnemo findings worth moving

### 1. MessagingStyle-aware notification extraction

Mnemo reads normal title/text/big-text and then prefers the latest entry from `Notification.EXTRA_MESSAGES`. It also performs a short-window fingerprint dedupe before persistence.

Cortex already captures notifications into `PhoneContextStore`, `RawSignalStore`, enrichment and thread adjudication, so the donor value is narrow:
- add a robust `EXTRA_MESSAGES` parser;
- preserve sender and latest message separately from display title;
- maintain notification-key/group-key provenance;
- dedupe only at the evidence-normalizer layer, never by deleting state.

Do not copy Mnemo's direct `remember(...)` architecture.

### 2. Work-thread subject normalization

`WorkMemoryStore` strips repeated `Re:`, `Fw:`, `Fwd:`, `AW:`, `WG:`, `SV:` prefixes before hashing a mail thread. It uses a deterministic `outlook:<sha>` thread key and filters obvious Outlook chrome noise.

Adapt into Cortex as a general communication-thread normalizer:
- normalized subject;
- provider conversation/thread id when available;
- sender/recipient identity refs;
- stable fallback hash;
- explicit source/provenance.

Never use normalized subject alone as identity proof.

### 3. Lightweight open-loop candidate heuristic

Mnemo marks text as review-worthy when it contains question marks or verbs/phrases such as:
`please`, `confirm`, `approval`, `review`, `reply`, `send`, `provide`, `urgent`, `deadline`, `follow up`, plus Arabic equivalents such as `مطلوب`, `برجاء`, `يرجى`, `تأكيد`, `اعتماد`, `راجع`, `رد`, `ابعت`, `محتاج`, `عاجل`, `متابعة`.

This is valuable only as a cheap candidate generator.

Target contract:
```text
Evidence -> CandidateObligationExtractor -> candidate_obligation
          -> Situation/Lifecycle reconciliation
          -> NOW ranking
          -> action proposal only if still live
```

No keyword is allowed to create a task directly.

### 4. Source/type mapping

Mnemo has pragmatic package-to-source/type mapping for WhatsApp, Messenger, Instagram, Telegram, Snapchat, Gmail, Outlook, Calendar, screenshot/files and calls.

Cortex should keep its richer notification metadata but can use the donor mapping as a fallback classifier when Android category/template metadata is missing.

### 5. Project hints

Mnemo recognizes `#project` and `PO <id>` patterns. Keep them as weak project hints, not canonical project creation.

### 6. Reminder dedupe signature

Mnemo hashes the current attention summary and suppresses an identical reminder until the underlying summary changes.

Adapt the concept after Cortex has produced a canonical attention decision:
- notification fingerprint must be based on canonical situation ids + state/version;
- do not base it only on rendered text;
- resolved/dismissed situations must invalidate previous reminder state.

### 7. Passive app-specific Accessibility pattern

Mnemo's Outlook reader is intentionally passive: package-scoped, no gestures, no crawler, no historical backfill. It captures only the currently open mail view.

This pattern is acceptable for Cortex only where Android APIs cannot provide equivalent evidence and only with explicit user permission. Any implementation must be source-specific, passive, provenance-preserving and removable without breaking the brain.

### 8. Health/source truth

Mnemo explicitly reports which capture sources are active and what each source can and cannot know. Cortex should adopt this clarity in its status UI.

Example source truth:
- notification listener active/inactive;
- calls permission active/inactive;
- calendar active/inactive;
- accessibility source active/inactive;
- last successful evidence timestamp;
- exact limitations such as "notification text only" or "call metadata only".

## Bridge donor targets once clean source is recovered

Debug evidence confirms a PhoneBridge project under `~/phonebridge-app`, a `WhatsAppMessagingStyleCapture.java` class and a `BridgeSender.send(...)` path used from background work.

When the source tree is available, inspect in this order:
1. `WhatsAppMessagingStyleCapture.java`
2. notification listener/service classes
3. message normalization models
4. `BridgeSender`
5. WorkManager/worker classes using retry/timeout
6. Gmail/email collection or batching code
7. call/SMS/context collectors
8. dedupe/fingerprint/state files

Expected dispositions:
- capture parsers: ADAPT;
- durable retry semantics: ADAPT;
- external email relay transport: REJECT as core architecture;
- any local JSON spool that improves crash recovery: ADAPT into Cortex DB queue;
- UI: REJECT;
- parallel memory/truth DB: REJECT.

## MeMessage-style Inbox target

The implementation should be native Cortex, using the existing `knowledge_items` and `ShareImporter` path.

### Required item states
- `saved`
- `pending_analysis`
- `understood`
- `fetch_failed` / `analysis_failed_retryable`
- `connected` where relationship/situation linking exists

### Card anatomy
Each Inbox card should show:
- source/app/domain;
- timestamp;
- primary content or preview;
- processing state;
- summary when understood;
- related person/project/situation when known;
- compact actions: Ask / Connect / Remind / Archive.

### Composer
The composer writes directly into normal Cortex evidence. It is not a separate notes database.

### Navigation
Target only after the surface works:
```text
Now | Inbox | World | Ask
```
Capture becomes an action, not a destination.

## What Cortex already does better and must remain authoritative

- canonical `VaultDb` / evidence persistence;
- `RawSignalStore` and provenance;
- notification enrichment and thread adjudication;
- `AnalysisQueue` with watchdogs and retryable states;
- semantic retrieval/indexing;
- temporal resolution;
- lifecycle reconciliation;
- cognitive packet/decision contract;
- safe structured action proposals;
- strict phone-only product review.

Therefore donor code must feed these layers rather than bypass them.

## First collective implementation tranche

Do not ship each item independently. Implement and test as one branch-level tranche:

1. `CommunicationEvidenceNormalizer`
   - MessagingStyle extraction
   - subject normalization
   - provider/thread fallback key
   - source/type fallback mapping
   - sender identity hints

2. `CandidateObligationExtractor`
   - Mnemo action-language heuristics
   - outputs candidates only
   - lifecycle gate required before surfacing

3. `InboxActivity` + `InboxStore` query layer
   - intentional shares/manual notes only for V1
   - existing `ShareImporter` persistence
   - rich state-aware cards
   - composer
   - search/filter

4. `SourceHealthSnapshot`
   - capture source status + last evidence + limitations

5. Extend phone-only review with hard gates:
   - Inbox save-before-analysis
   - duplicate share idempotency
   - MessagingStyle sender/text preservation
   - project query excludes generic screenshot/system noise
   - candidate obligation cannot bypass lifecycle
   - same situation cannot be live and closed
   - shared URL cannot be `understood` without extracted content

6. Only after these pass, switch bottom nav to `Now | Inbox | World | Ask`.

## Stop conditions

Reject or rewrite any donor part that:
- creates a second truth database;
- turns keyword detection directly into a task;
- treats sender display-name equality as person identity;
- crawls social apps via gestures;
- makes Accessibility a required dependency for core Cortex;
- emails/relays private evidence merely to make the brain work;
- loses provenance while deduping;
- marks content understood when only a URL/title is available.
