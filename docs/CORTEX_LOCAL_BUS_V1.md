# Cortex Local Bus V1

Cortex is the cognitive hub. Sensor/integration apps may contribute grounded events without becoming owners of canonical Cortex truth.

## V1 topology

```text
Second Brain notification capture
        |
        | Android Messenger / explicit bound service
        v
CortexLocalBusService
        |
        +-- Android sender UID -> connector package allowlist
        +-- CORTEX_INGEST_V1 validation
        +-- idempotent connector event audit
        +-- cross-sensor notification-key dedupe
        v
RawSignalStore
        |
        +-- immutable V4 Evidence
        +-- additive CONNECTOR_ENRICHMENT
        +-- trusted enrichment re-evaluation when native preview was weaker
        v
Realtime Memory -> Situations -> Pulse / Think
```

V1 admits `NOTIFICATION` events from `com.kareem.secondbrain` (`connector_id=second_brain`). The protocol is deliberately capability-based so Bridge, Mnemo, or other owned apps can be added without giving them direct database access.

## Trust boundary

Payload `connector_id` is never trusted by itself. `CortexLocalBusService` authenticates each Messenger message from Android's `Message.sendingUid`, resolves the packages installed for that UID, and maps an allowed package to its Cortex connector identity. Unknown packages are rejected before JSON is ingested.

This avoids requiring all currently-installed apps to be re-signed with a shared certificate during the first migration. A future hardening stage may additionally pin signing certificates / require a signature-level permission.

## Failure isolation

Second Brain stores the notification locally first. Only `CaptureResult.Stored` events are offered to Cortex. Failure to bind to Cortex never rolls back or blocks Second Brain capture.

Cortex also isolates connector failure from its own native listener and canonical persistence.

## Exact physical-notification dedupe

Cortex native notification capture and Second Brain can temporarily run side-by-side. `NotificationSignalIngressV1` treats the same:

- source package
- Android notification key
- exact `postTime`

as one physical notification Raw Signal.

If Second Brain carries richer structure (MessagingStyle messages, conversation title, expanded text), Cortex preserves the connector payload as additive `CONNECTOR_ENRICHMENT` on the mapped V4 Evidence rather than rewriting historical Evidence.

### Semantic recovery after dedupe

Exact dedupe must not mean semantic loss. A native listener may win the race with a short preview such as a sender name while Second Brain subsequently supplies the full request/deadline.

For an already-deduped, still-unpromoted Raw Signal, `RawSignalStore.promoteTrustedEnrichment(...)` re-runs Cortex's relevance boundary against the trusted richer connector text. It does **not** replace the original Raw Signal body or Evidence text. If the richer grounded text crosses the durable boundary, Cortex promotes the same canonical signal to Memory and runs the realtime Situation projection.

Communication/action semantics are restricted to communication/email threads (or explicit message/email metadata). An arbitrary app cannot become an ACTION merely because promotional copy contains request-like words. Security/payment/etc. can still cross the normal fast durable rules when the richer connector text proves them.

Connector-origin promotion is auditable as `trusted_connector_enrichment_v1`; it is not mislabeled as native/thread authority.

## Realtime cognitive projection

A connector event that the Cortex relevance governor has already accepted as durable no longer waits for bounded startup/backfill. `CognitiveRealtimeProjectionV4` asynchronously projects:

```text
Raw Signal
  -> existing immutable Evidence
  -> connector-enriched Memory body when richer
  -> Situation refresh
  -> Pulse
```

This keeps capture callbacks local/fast while making meaningful new evidence visible to the live cognitive layer quickly.

## CORTEX_INGEST_V1 notification shape

```json
{
  "protocol": "CORTEX_INGEST_V1",
  "event_id": "sb_<second-brain-event-id>",
  "connector_id": "second_brain",
  "source_type": "NOTIFICATION",
  "source_package": "com.whatsapp",
  "occurred_at": 1787900000000,
  "notification_key": "...",
  "title": "Ahmed",
  "text": "message preview",
  "expanded_text": "...",
  "conversation_title": "...",
  "ongoing": false,
  "messages": [],
  "metadata": {}
}
```

## Real-device validation — 28 Aug 2026

The V1 notification tunnel was validated with real WhatsApp traffic on-device:

```text
WhatsApp
 -> Second Brain local capture
 -> Cortex Local Bus ACCEPTED
 -> Raw Signal
 -> V4 Evidence
 -> CONNECTOR_ENRICHMENT (engine=local_bus:second_brain, version=1)
```

Both native-first and connector-created Raw Signals were observed. For native-first signals, the same Evidence received the Second Brain enrichment instead of creating a second exact physical-notification Raw Signal. A duplicate audit grouped by source + occurred_at + notification_key returned zero duplicate rows.

The follow-up product E2E fixture is:

```text
CORTEX_E2E_001 — يا كريم، محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده. لو مش هتلحق ابعتلي وقولي عشان أتصرف.
```

Regression coverage now verifies the semantic path `weak native preview -> richer Second Brain enrichment -> ACTION relevance -> connector-preferred Memory text -> DEADLINE Situation at 17:00`.

## Ownership rules

Connector apps contribute grounded Evidence/context. They do **not** directly create or mutate Cortex Facts, Worlds, Situations, Pulse rank, or Deep Brain state. Any durable promotion caused by richer connector text is still a Cortex relevance decision, and any Situation is still produced by the Cortex Situation Engine.

Canonical reasoning remains:

```text
connector Evidence -> Cortex relevance/Memory/Worlds/Situations -> ChatGPT Deep Brain -> suggested cognitive state -> Pulse
```

No connector may mark a canonical Situation resolved or rewrite historical Evidence.
