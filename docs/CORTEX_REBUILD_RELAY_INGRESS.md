# Cortex Rebuild — Relay Ingress Contract

Cortex Rebuild is a new Android application. It does not reuse the old Cortex package identity or database.

## Endpoint

- Application package: `com.kareem.cortex.rebuild`
- Service: `com.kareem.cortex.rebuild.CortexLocalBusService`
- Accepted bind actions:
  - `com.kareem.cortex.LOCAL_BUS_V1` (wire compatibility)
  - `com.kareem.cortex.rebuild.LOCAL_BUS_V1`
- Expected Relay Android package: `com.kareem.secondbrain`
- Connector id: `second_brain`

Relay should explicitly bind the new package/service. The previous endpoint `com.kareem.cortex / com.kareem.cortex.CortexLocalBusService` belongs to the abandoned application and must not be used for the rebuild.

## Local Bus messages

| Message | `what` | Direction |
| --- | ---: | --- |
| HELLO | 1 | Relay → Cortex |
| INGEST V1 | 2 | Relay → Cortex |
| INGEST V2 | 20 | Relay → Cortex |
| ACK | 100 | Cortex → Relay |
| ERROR | 101 | Cortex → Relay |

HELLO preserves V1 capability compatibility. If `relay_capabilities_json` advertises `CORTEX_SIGNAL_V2`, Cortex selects V2 in the ACK using `selected_protocol=CORTEX_SIGNAL_V2`; otherwise it selects `CORTEX_INGEST_V1`.

Supported signal protocols:

- `CORTEX_INGEST_V1`
- `CORTEX_SIGNAL_V2` with schema `CORTEX_RELAY_SIGNAL_V2`

## Identity boundary

Cortex checks the Binder sender UID, requires that UID to own `com.kareem.secondbrain`, and pins the installed Relay APK signer SHA-256 on the first authenticated HELLO. Later sessions must match the pinned signer.

No signer material is transmitted over the wire.

## Delivery semantics

- `event_id` is the idempotency key.
- Exact incoming JSON is persisted as evidence.
- Duplicate events receive `DUPLICATE_ACCEPTED`.
- Fresh events receive `ACCEPTED`.
- ACK returns the persisted Cortex evidence id in the legacy `signal_id` field so existing Relay delivery tracking remains compatible.
- Invalid payloads receive `INVALID_EVENT`; identity failures receive `IDENTITY_MISMATCH`.

## Product boundary

Relay evidence entering this service does **not** automatically create:

- a task,
- a priority,
- a memory,
- a situation,
- a world entity,
- or a visible Now card.

The receiver only answers: **what evidence arrived, from where, when, and with what Relay-supplied provenance/quality.**

Cortex owns interpretation and personal meaning after intake:

`Relay → Evidence → Brain Intake → Interpretation → Episode → Situation → Attention → UI`

Relay may provide semantic structure, conversation continuity, deltas, entity candidates, field confidence, provenance, outcomes and capabilities. Cortex remains the sole authority for personal relevance, priority, memory, situation state and suggested action.
