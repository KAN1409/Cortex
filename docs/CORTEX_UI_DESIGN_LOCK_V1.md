# CORTEX UI DESIGN LOCK V1

Status: APPROVED / LOCKED

This document freezes the visual and interaction direction approved on 2026-09-01 for the original Cortex Android app.

## Primary shell

Cortex is chat-first. The launcher surface is the Cortex conversation, not a dashboard, tool grid, card launcher, or bottom-tab home.

Approved primary surfaces:
1. Cortex Chat
2. Left navigation drawer
3. Evidence
4. Deep Review

Evidence and Deep Review are dedicated full-screen destinations. They must never be reintroduced as tabs, dashboard cards, or large sections inside the Chat/Input home.

## Navigation

- No bottom navigation bar on the four locked surfaces.
- Chat opens the drawer from the upper-left menu control.
- Drawer owns scalable navigation: chat/session entry, Projects, Evidence, Deep Review, Archive, Settings.
- Evidence and Deep Review use a back affordance and remain independent full-screen tools.

## Visual language

- AMOLED/deep-black base with graphite layered surfaces.
- Lime is the shell accent and active-state color.
- White/light graphite typography with restrained muted grays.
- Orange is status-only, primarily processing/warning. Red is error-only.
- No purple.
- No rainbow semantic dots across the shell.
- No oversized hero cards.
- No grid of capture cards.
- No glassy decorative effects.
- No visual drift back to the old matte-card dashboard language.

## Icons

The locked surfaces use one coherent geometric monoline icon family implemented by `CortexLineIconView`.

- Bare line icons by default.
- Icon plates only when the approved composition explicitly calls for a circular control.
- Consistent stroke weight and optical size.
- No emoji or mixed Android/system icon families.

## Cortex Chat

- Header: menu, Cortex mark + wordmark, search, overflow.
- Conversation-first vertical timeline.
- User messages use restrained graphite bubbles with a thin lime accent.
- Cortex answers may use structured graphite answer cards when structure adds value.
- Grounding appears inline as compact `Evidence linked` / source affordances.
- Composer is fixed at bottom: plus, text field, mic, primary send/voice control.
- No bottom tabs.

## Drawer

- Dark drawer overlays and dims the current chat.
- Cortex branding + search at top.
- Compact sections with clear hierarchy.
- Projects, Evidence, Deep Review, Archive, Settings are first-class destinations.
- Chat history may expand as persistence matures, but drawer layout and visual hierarchy remain locked.

## Evidence

- Dedicated full-screen immutable source ledger.
- Dense, professional list rather than dashboard cards.
- Search + compact filters.
- Rows show title, source, time, type/status and provenance cues.
- Expanded evidence appears inline with raw/extracted content and immutable/provenance metadata.
- Bottom summary may show totals/processing/review counts.

## Deep Review

- Dedicated full-screen controlled workflow.
- Vertical numbered flow: Build context -> Open in ChatGPT -> Return structured review -> Validate -> Preview grounded diff -> Apply.
- Structured response is visibly machine-readable (`CORTEX_REVIEW_V1`).
- Validation and evidence grounding are explicit.
- Apply remains user-controlled and never bypasses `DeepReviewContractV1` validation/apply boundaries.

## Product boundary

This is a UI shell redesign only. Existing canonical Cortex evidence, cognition, model routing, signing identity, package identity, persistence, and safety boundaries remain authoritative.

Do not merge this branch without explicit user instruction `merge`.
