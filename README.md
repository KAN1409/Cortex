# Cortex v50 — Intelligence Layer

Cortex is a local-first Android knowledge vault and second-brain foundation.

## v0.2 capabilities

- Share text and images into Cortex from Android.
- On-device screenshot/image OCR using ML Kit Latin-script text recognition.
- Local automatic title, category and tag generation.
- Local entity extraction for URLs, emails, dates, phone-like numbers, money and hashtags.
- Local action extraction including common Arabic action phrasing.
- Prompt + Input + Example Result bundles with ratings and linked detail display.
- SHA-256 duplicate fingerprints.
- Persistent analysis queue/state and manual re-analysis.
- Versioned analysis JSON while preserving the original source.
- CSV/TSV detection and local tabular summaries including rows, columns, min, max and average for numeric columns.
- Search across original text, OCR text, summaries, entities and actions.
- v0.1 to v0.2 SQLite migration path.

## Build

The GitHub Actions workflow in `.github/workflows/build-apk.yml` builds a debug APK automatically on pushes to `main` and on manual workflow dispatch.

Expected artifact: `Cortex-v50-debug`

The repository's own regression gate (`scripts/cortex-repo-audit.sh`) runs before every Gradle build; `termux-build-cortex.sh` uses it as a local release gate as well.

## Install

Install over the existing Cortex build to preserve app data:

1. Download `Cortex-latest-debug.apk` from `downloads/` (or the `Cortex-v50-debug` workflow artifact) together with `Cortex-latest-debug.sha256`.
2. Verify the checksum: `sha256sum -c Cortex-latest-debug.sha256`.
3. Open the APK on the phone and allow the install when prompted.

## Architecture direction

Cortex is intended to become a shared Memory Core for Cortex Vault and Cortex Voice, with future context packs, semantic relationships, timeline recall and proactive resurfacing.
