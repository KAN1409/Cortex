# Master Fix execution status

## v159 continuation (supersedes the earlier checkpoint below)
- Final local verification: assembleDebug succeeded and 18 focused tests passed (4 publisher/recovery, 2 file-lock, 12 existing policy tests), zero failures/errors. This is not whole-app acceptance. APK is app/build/outputs/apk/debug/app-debug.apk; v159 is NOT installed on the phone at this checkpoint.
- GitHub handoff requested: continue from feature/truth-pipeline-foundation-v159. Do not treat migration to GitHub as product completion.
- Version prepared: 159 / 2.45.0-truth-pipeline-foundation.
- Manual Brains now queues the same unique worker that persists results; it no longer owns an analysis executor/database lifetime.
- Kernel file-lock ownership prevents overlapping council inference across processes. Recovery obtains that lock before changing orphaned running rows, so a live slow run is not interrupted merely for age. The lock is released on normal exit, setup exceptions, and process death. Durable heartbeat/session columns are still not implemented.
- SafeCoreRuntime cancels the legacy periodic council schedule and recovers orphaned runs after safe startup.
- Worker handles missing models and missing eligible evidence as explicit failed WorkManager output. Brains polls and displays queued/running/completion/failure output. Heavy execution is one situation per request; automatic retries removed.
- Rule and council findings now share CortexInsightPublisher: source existence checks, finite score checks, existing policy gates, transactionally stored findings/evidence, deterministic issue-key merge, and preserved dismissed/wrong/resolved states.
- Council final JSON must identify actual evidence IDs from its supplied pack. This checks provenance membership, not semantic correctness of every claim.
- Not useful/Wrong/Already knew feedback hides the finding persistently. Ranking adaptation remains outstanding.
- NOW detail exposes every linked source, not just the first.
- Procurement identifiers require numeric identity; alphabetic prose cannot be promoted as a PR/PO reference.
- Focused tests passed before final build: lock overlap/release, live-owner recovery protection and idempotent abandoned recovery, missing/no-source quarantine, duplicate merge, dismissal preservation, existing DiscoveryV3 policy tests. Final build/test results recorded below when complete.
- Phone v158 observation before update: NOW empty; audit remained Checking; process CPU sampled at 237%. Root cause of CPU/audit stall not established.

### Still incomplete
The full master fix remains incomplete: typed candidate/evidence contracts across all engines, complete Life/Work navigation and archive, reasoning-tier routing, source-quality/claim validation, end-to-end price scenario, action ledger/read-back, bounded feedback ranking, interruption budgets, all-engine scheduler consolidation, native cancellation, comprehensive phone acceptance and a real evidence-to-result demonstration. Existing legacy attention projection remains alongside DiscoveryV3.

No broad production data rewrite or external action was performed. No push or merge. Existing uncommitted changes preserved.

## Current checkpoint
Partial Phase A source changes only. No new APK installed. Phone package inspection confirms build 158, 2.44.0-council-recovery. Existing local changes preserved. User last reported 2% remaining quota; this checkpoint preserves outstanding acceptance work.

## Dependency map
- Capture: VaultDb.applyAnalysis persists extracted information, indexes it, then invokes DiscoveryV3Engine.
- Scheduling: DiscoveryV3DeepScheduler queues DiscoveryV3DeepWorker; EnvironmentActivity exposes a manual trigger.
- Manual reasoning: CognitiveCouncilActivity directly calls CognitiveCouncilOrchestrator.
- Publication: DiscoveryV3DeepWorker owns the council publication implementation; manual activity integration remains incomplete.
- UI: NowActivity reads judged brief and DiscoveryV3Feed. Its read helpers still need auditing for writes.
- Actions and feedback: existing implementations require inspection before consolidation.

## Changes in this checkpoint
- Removed the previously unremoved heavy council kick from VaultDb.applyAnalysis.
- Removed backfill/research scheduling from NowActivity.build.
- Manual scheduler requests now include user_requested=true.
- Deep worker rejects legacy unmarked requests before model checks and cancels its legacy periodic work.
- Scheduler enable no longer registers recurring heavy work; it cancels the identified periodic name.
- Moved AtomicBoolean ownership around the entire orchestrator call so setup exceptions release it.

## Limitations
- Verification: :app:compileDebugJavaWithJavac --offline succeeded. This verifies compilation only; no runtime acceptance tests passed in this checkpoint.
- Periodic cancellation occurs when old work next executes (or enable is called), not yet through a startup migration.
- All changes above require focused behavioral tests.
- Run recovery still uses age alone; replace with persisted ownership/liveness before release.
- Process-local AtomicBoolean is not the required durable concurrency mechanism.
- Activity-owned database/executor and manual publication remain unresolved.
- Unified evidence/candidate/judgment contract, action truth, feedback, Life/Work presentation, and golden acceptance scenario remain outstanding.
- No new phone UI acceptance or real model completion was verified.

## Resume
Complete Phase A lifecycle, persistent ownership, and common manual/worker publication first. Then follow the supplied execution instructions for Phases B-E. Do not declare completion or install this partial checkpoint as the master fix. Preserve version/signing/data; increment version only for the final tested release.
