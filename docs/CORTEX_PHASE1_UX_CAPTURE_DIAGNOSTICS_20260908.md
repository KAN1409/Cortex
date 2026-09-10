# Cortex Phase 1 — UX, Capture, Diagnostics

## Product decisions

- Preserve the current Cortex visual language.
- Primary navigation represents top-level user destinations, not internal implementation concepts.
- Target shell: Now, Brief, Capture, Brain, with the center + remaining the primary input action.
- People / Projects is no longer a primary navigation destination; its knowledge remains part of Brain/context.
- Capture is user-facing observability, not a developer log.
- Capture owns subcategories: Notifications, Screen, Voice, Images, Files, Calls & SMS.
- Capture starts with Live Capture, showing what Cortex is receiving and processing now.
- Raw source capture is durable before interpretation. Processing failure must not erase source evidence.
- Settings must be shallow and understandable.
- User-facing Settings contain only core configuration groups.
- Testing is consolidated into one Full Cortex Diagnostic. Individual engineering tests remain implementation details, not normal-user menu items.
- Full diagnostic must expose progress, current phase, PASS/WARN/FAIL, evidence, timestamps, and exportable detail.

## Phase 1 acceptance gates

1. Bottom navigation no longer exposes People / Projects.
2. Capture is a top-level destination.
3. Capture provides live status plus source categories and recent evidence.
4. Notification capture proof includes app, timestamp, title/text where Android exposes it, and stored capture metadata.
5. Screen-understanding state is derived from the real Android accessibility-enabled state and refreshed on resume.
6. Settings do not present nested test menus.
7. One Full Cortex Diagnostic is the primary test entry point.
8. Existing production capture/voice paths are preserved unless a replacement is separately validated.
9. No merge without explicit authorization.
