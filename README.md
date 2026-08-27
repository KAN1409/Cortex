# Cortex — Personal Cognitive Intelligence System

Cortex is an Android personal cognitive system. Capture and sensing are evidence layers; the product goal is to understand context, connect evidence, remember durable state, reason about what remains unresolved, surface what deserves attention, propose executable next steps, and learn from outcomes.

## Current product shell

- **Now** — evidence-ranked attention, waiting items, decisions, meaningful changes and relevant context.
- **Inbox** — incoming/captured material and review flows.
- **Capture (+)** — intentional text, voice, file, image and Android share entry.
- **Atlas** — people, projects and linked evidence.
- **Ask** — grounded local/external reasoning with privacy-aware evidence routing.

## Core systems

- Persistent Vault with provenance, search, semantic index and bounded analysis queues.
- Notification, phone-context, accessibility/screen and intentional-capture evidence.
- Thread relevance, cognitive adjudication, attention scoring, AI adjudication and personal attention learning.
- Approval-first action handoff: Cortex may plan/draft external actions but does not silently send messages, email or calendar mutations.
- Local model fallback plus privacy-gated external reasoning.
- OCR, visual intelligence, audio transcription, prompt library, backup/restore and diagnostic/self-review tooling.
- Authoritative 43-capability registry and functional self-test.

## Build and validation

Use Java 17 and the Android SDK. The release gate compiles both the app APK and the instrumentation APK:

```bash
gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
```

On Termux, the repository build script also runs the structural regression audit first:

```bash
CORTEX_CLEAN_BUILD=1 bash termux-build-cortex.sh
```

Generated APKs are artifacts, not source. They are ignored under `downloads/` and should not be committed. GitHub workflows keep private builds inside authenticated GitHub artifacts; they do not upload private APKs to anonymous public file hosts.

## Validation layers

1. `scripts/cortex-repo-audit.sh` — structural/security/regression invariants.
2. Debug + `androidTest` compilation — catches production/test API drift.
3. `CortexFunctionalSelfTest` — on-device capability and real-path functional checks.
4. `tools/run-instrumented-self-user-test.sh` — navigation/surface health plus cognitive differential tests.
5. `tools/run-phone-only-self-review.sh` — phone-only build/install/self-review workflow.

Cortex updates are intended to install over the existing package so the user's Vault remains intact. Do not uninstall as part of a normal update workflow.
