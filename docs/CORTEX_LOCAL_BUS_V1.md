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
        v
V4 Evidence -> Memory -> Situations -> Pulse / Think
```

V1 admits `NOTIFICATION` events from `com.kareem.secondbrain` (`connector_id=second_brain`). The protocol is deliberately capability-based so Bridge, Mnemo, or other owned apps can be added without giving them direct database access.

## Trust boundary

Payload `connector_id` is never trusted by itself. `CortexLocalBusService` authenticates each Messenger message from Android's `Message.sendingUid`, resolves the packages installed for that UID, and maps an allowed package to its Cortex connector identity. Unknown packages are rejected before JSON is ingested.

This avoids requiring all currently-installed apps to be re-signed with a shared certificate during the first migration. A future hardening stage may additionally pin signing certificates / require a signature-level permission.

## Failure isolation

Second Brain stores the notification locally first. Only `CaptureResult.Stored` events are offered to Cortex. Failure to bind to Cortex never rolls back or blocks Second Brain capture.

Cortex also isolates connector failure from its own native listener and canonical persistence.

## Dedupe

Cortex native notification capture and Second Brain can temporarily run side-by-side. `NotificationSignalIngressV1` treats the same:

- source package
- Android notification key
- exact `postTime`

as one physical notification Raw Signal. This avoids duplicate Memory while the migration is evaluated.

If Second Brain carries richer structure (MessagingStyle messages, conversation title, expanded text), Cortex preserves the connector payload as additive `CONNECTOR_ENRICHMENT` on the mapped V4 Evidence rather than rewriting historical Evidence.

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

## Ownership rules

Connector apps may contribute Evidence/context. They do **not** directly create or mutate Cortex Facts, Worlds, Situations, Pulse rank, or Deep Brain state.

Canonical reasoning remains:

```text
connector Evidence -> Cortex Memory/Worlds/Situations -> ChatGPT Deep Brain -> suggested cognitive state -> Pulse
```

No connector may mark a canonical Situation resolved or rewrite historical Evidence.
