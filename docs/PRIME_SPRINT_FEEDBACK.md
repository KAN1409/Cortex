# Cortex PRIME Sprint Feedback Ledger

This file is the persistent rolling ledger for user-observed product issues during the PRIME completion sprint.
Feedback is folded into the correct phase without stopping the active implementation sequence.

Status values: `NEW` · `PLANNED` · `IMPLEMENTED` · `VERIFIED`

## Product guard — No orphan data
**Status:** IMPLEMENTED

Any surfaced item must make its provenance understandable without requiring the user to remember the original notification or capture event.

For notification-derived memories, Cortex should expose when available:
- source application label and package
- exact notification/capture timestamp
- Android notification subtype/category/channel when available
- capture path (`Android notification`)
- original stored evidence
- Cortex relevance disposition and the reason it was retained

A notification must not appear merely as generic `TEXT / Memory` when Cortex knows it is a notification.

Implementation checkpoint:
- `MemoryProvenance` centralizes provenance interpretation.
- `NotificationCaptureService` records capture kind, channel, category/template and inferred subtype for new notifications.
- `VaultActivity` shows `NOTIF`, source app, exact time, Origin and `Why Cortex kept it`.
- Old promoted notification memories can recover package/time/relevance provenance from their existing metadata.

## Rolling rule
When a new user observation arrives:
1. Record it here.
2. Classify it into the current PRIME phase.
3. Fix it immediately only if it blocks correctness/safety/current surface work; otherwise fold it into the nearest planned pass.
4. Do not derail the overall completion sequence.
5. Mark VERIFIED only after runtime validation on the installed build.
