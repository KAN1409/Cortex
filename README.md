# Cortex

Cortex is a local-first Android personal cognitive intelligence system.

Current product direction:

```text
Capture / Perceive
→ Understand
→ Connect
→ Remember
→ Reason
→ Suggest
→ Execute
→ Learn
```

The active fresh-start cognitive architecture is documented in:

- `docs/COGNITIVE_ARCHITECTURE_V4.md`
- `docs/COGNITIVE_PERSISTENCE_IDENTITY_V4.md`

Its target product surfaces are:

```text
Pulse | Memory | Worlds | Think
```

These are projections over one canonical truth hierarchy, not independent stores:

```text
Evidence → Episode → Memory → Facts / Worlds / Relations → Situation
```

The V4 work is additive while the existing Cortex product remains operational. Existing UI/read paths are not redirected until each replacement projection passes compile, regression, migration and real-device validation.

## Build

From the repository root:

```bash
gradle :app:assembleDebug :app:assembleDebugAndroidTest
```

For Termux, use the repository's `termux-build-cortex.sh` when building the configured target branch.
