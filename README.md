# Cortex

Cortex is a local-first Android memory and context system. It captures user-approved phone signals, turns them into evidence and memories, maintains situations and open loops, and exposes grounded reasoning and review surfaces without automatically executing model suggestions.

## Project identity

- Android package: `com.kareem.cortex`
- Minimum Android version: 8.0 (API 26)
- Target Android version: Android 15 (API 35)
- Java/Kotlin target: 17
- Current development generation: v63

## Product flow

`Evidence -> Episode -> Memory -> Facts/Worlds -> Situation -> Attention/Reasoning -> Pulse`

The SQLite schema is migrated additively. Existing installations must remain update-compatible; do not clear app data or replace signing material during development.

## Build

The project requires JDK 17, Android SDK 35, and Gradle 8.9.

```bash
gradle :app:assembleDebug --stacktrace
```

GitHub Actions builds the same target on pushes and pull requests and publishes the APK as a workflow artifact. Generated APKs are not committed to source control.

## Source layout

- `app/src/main/java/com/kareem/cortex/` — application, storage, reasoning, capture, and verification code
- `app/src/main/res/` — Android resources
- `docs/` — architecture, design constraints, hardening notes, and roadmap material
- `.github/workflows/build-apk.yml` — reproducible debug build

## Safety boundaries

- Preserve additive database migrations and update-in-place compatibility.
- Keep observations, model proposals, and user-authorized actions separate.
- Require grounded identifiers for reasoning outputs.
- Keep protected or external actions confirmation-gated.
- Never commit generated APKs, local build output, API keys, or replacement signing material.
