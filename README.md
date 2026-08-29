# Cortex Rebuild V1

Fresh Android application. No production source code from the previous Cortex app is part of this tree.

## Identity
- Display name: Cortex
- applicationId: `com.kareem.cortex.rebuild`
- version: `0.1-rebuild` (`versionCode 1`)
- Installs side-by-side with the previous Cortex app.

## Product contract
- Evidence is not UI.
- Now shows current situations only.
- Memory shows durable user saves only.
- World shows entities only after Cortex has enough grounded evidence to maintain a real model.
- Ask refuses to fabricate unsupported answers.
- The + button is intentional capture.

## Fresh data model
`evidence -> understanding -> situations/world/memory -> attention -> product`

The first APK intentionally starts with an empty world and zero inherited priorities, contacts, cards or memories.
