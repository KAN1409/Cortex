# capture-relay

Notification capture extracted from the proven `KAN1409/Second-Brain` Relay behavior into Cortex Prime.

## Preserved semantics
- Android `NotificationListenerService`
- title, body, expanded text and conversation title
- MessagingStyle sender/text/timestamp extraction
- notification key, id, tag, group key, group/ongoing state, category and channel provenance
- self-notification exclusion

## Deliberately removed
- Hilt and Relay repository wiring
- Local Bus / Binder / broadcast transport
- cross-app delivery
- personal importance or prioritization logic

Each notification version becomes immutable `EvidenceRecord` data. Exact repeats produce the same evidence ID; content changes produce a new evidence ID under the same `sourceRef`, preserving notification revision history without rewriting raw evidence.
