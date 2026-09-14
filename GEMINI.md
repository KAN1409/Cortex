# Cortex full-code repair contract

You are operating on the Cortex Android repository for a bounded, evidence-driven full-code audit and repair.

## Primary target

Find and repair real defects across the entire tracked source/config surface while preserving Cortex behavior, data, package identity, signing identity, and update compatibility.

## Hard boundaries

- Never merge to `main`.
- Never uninstall Cortex or change `applicationId` / package identity.
- Never modify, replace, regenerate, expose, or delete signing keys, signing lineage, keystores, or credentials.
- Never change `versionCode` or `versionName` as part of audit repair.
- Do not produce or upload intermediate APKs.
- Do not weaken, delete, skip, mute, baseline, or blanket-suppress tests or analyzers merely to obtain green CI.
- Do not delete functioning product behavior to silence a finding.
- Do not add telemetry, tracking, or new cloud dependencies unless the existing code already requires them and the fix is necessary.
- Keep user data/database migrations backwards compatible. Treat destructive migration or state loss as critical.

## Required review surface

Use `audit/manifest.tsv` as the coverage ledger. Review every listed tracked file that contains executable code, build/config logic, Android resources, tests, workflows, scripts, or bridge code. Do not claim full coverage unless every manifest entry has been considered by at least one deterministic gate or by direct semantic review.

Focus especially on:

- Java/Kotlin correctness and Java/Kotlin interop
- Android lifecycle, services, receivers, activities, providers, foreground/background restrictions
- WorkManager initialization and job scheduling
- threading, coroutines, races, deadlocks, cancellation, stale state, main-thread I/O
- SQLite/Room transactions, cursors, migrations, data integrity and state persistence
- nullability, bounds, parsing, overflow, resource leaks, file/URI handling
- permission and exported-component security
- intents, PendingIntent mutability/identity, notifications and Android 16 behavior
- OCR/ASR/native resource lifecycle and failure isolation
- network/timeouts/retries and bounded resource use
- archive/document/media parsing, zip-slip/path traversal, decompression/resource exhaustion
- privacy boundaries and accidental cloud evidence leakage
- TypeScript bridge type/runtime errors
- Gradle, GitHub Actions, shell, JSON/XML/YAML config defects
- duplicate or contradictory implementation paths that can create inconsistent behavior

## Deterministic authority

The following are authoritative gates when available:

1. `scripts/cortex-repo-audit.sh`
2. source/config parse and syntax checks
3. TypeScript `npm run check`
4. Semgrep
5. Android Lint
6. JVM unit tests
7. debug Java/Kotlin compilation
8. CodeQL

A change is not accepted because it looks correct. Re-run the relevant gates after every repair batch.

## Repair discipline

- Prefer root-cause fixes over local patches.
- Preserve public behavior unless a failing invariant proves that behavior is defective.
- Add or strengthen a focused regression test when fixing a reproducible logic defect.
- Avoid broad rewrites when a smaller safe correction exists.
- Do not invent missing evidence.
- Treat analyzer output as evidence, not infallible truth; if a finding is genuinely false-positive, document exact reasoning instead of blanket suppression.
- Keep changes reviewable and cohesive.

## Loop guard

The automation permits at most 3 repair rounds. If the same failure or same diff state repeats, stop rather than cycling. Never keep changing code without new evidence.

## Completion condition

Only report complete when all mandatory deterministic gates invoked by the full-code workflow are green and no high-severity CodeQL/Semgrep/Android Lint defect remains unresolved. Otherwise report the remaining blockers exactly.