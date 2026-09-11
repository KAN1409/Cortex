# NEXUS inside Cortex

NEXUS is no longer treated as a second personal-intelligence database or a second observation service.

## Product loop

Observe → Remember → Model interests → Discover → Rank → Prepare action → User approval → Execute → Learn

## Canonical migration mapping

- NEXUS observations → Cortex `raw_signals`, Universal Event observations, notification capture, intentional captures, and Knowledge V2 evidence.
- NEXUS memories/entities → Cortex `knowledge_items`, Knowledge V2, `entity_nodes`, aliases, and evidence-grounded relationships.
- NEXUS interests → `nx_interests`, a rebuildable projection computed from canonical Cortex evidence.
- NEXUS discoveries → `nx_discoveries`, a rebuildable ranked projection; they do not become Now cards automatically.
- NEXUS actions → existing Cortex `derived_items` plus `nx_action_state` for approval lifecycle.
- NEXUS feedback → Cortex `feedback_events`; approval/defer/reject/start/complete/fail remain learning signals.
- NEXUS notification listener/share ingestion/app-usage observation → Cortex already owns these capture paths; duplicate services are intentionally not copied.
- NEXUS AppSearch/Room store → not copied. Cortex semantic/lexical retrieval and SQLite cognitive store remain the source of truth.

## Runtime rules

- NEXUS refresh starts only after Cortex Safe Core releases startup quarantine.
- Refresh runs once at startup and periodically through WorkManager.
- Raw evidence is never deleted by NEXUS projection rebuilds.
- NEXUS may rank existing Cortex actions, but it must not fabricate an action merely because a topic is frequently observed.
- Dismissed discoveries remain dismissed across refreshes.
- Deferred actions can resurface after their defer window.
- Completion/rejection resolves the underlying Cortex derived item so Now and NEXUS stay consistent.

## UI

The NEXUS surface is launched from the `NEXUS` chip in Cortex Now. It exposes:

- observation count
- learned interests with affinity/momentum/confidence
- prepared actions with approval lifecycle
- grounded For You discoveries

The old standalone NEXUS navigation shell is intentionally not copied; Cortex remains the single application and visual system.
