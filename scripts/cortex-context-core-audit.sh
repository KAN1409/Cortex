#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$ROOT"
FAIL=0
ok(){ printf 'CONTEXT CORE AUDIT PASS: %s\n' "$*"; }
bad(){ FAIL=$((FAIL+1)); printf 'CONTEXT CORE AUDIT FAIL: %s\n' "$*" >&2; }
need_file(){ [ -f "$1" ] && ok "$1 present" || bad "$1 missing"; }
need(){ local f="$1" p="$2" label="$3"; grep -Eq "$p" "$f" 2>/dev/null && ok "$label" || bad "$label"; }
forbid(){ local f="$1" p="$2" label="$3"; if grep -Eq "$p" "$f" 2>/dev/null; then bad "$label"; else ok "$label"; fi; }

SCHEMA="app/src/main/java/com/kareem/cortex/ContextSchema.java"
STATE="app/src/main/java/com/kareem/cortex/ContextStateStore.java"
RESOLVER="app/src/main/java/com/kareem/cortex/ContextResolver.java"
BOUNDARY="app/src/main/java/com/kareem/cortex/ContextBoundaryDetector.java"
OPENLOOP="app/src/main/java/com/kareem/cortex/ContextOpenLoopResolver.java"
ACTION="app/src/main/java/com/kareem/cortex/ContextActionEngine.java"
CONTROL="app/src/main/java/com/kareem/cortex/ContextControls.java"
LEARN="app/src/main/java/com/kareem/cortex/ContextFingerprintLearner.java"
DIAG="app/src/main/java/com/kareem/cortex/ContextDiagnostics.java"
DIAGUI="app/src/main/java/com/kareem/cortex/ContextDiagnosticsActivity.java"
PACKET="app/src/main/java/com/kareem/cortex/ContextPacketBuilder.java"
ASK="app/src/main/java/com/kareem/cortex/ContextAskEngine.java"
OPS="app/src/main/java/com/kareem/cortex/AskOperationalEngine.java"
AWARE="app/src/main/java/com/kareem/cortex/ContextAwarenessScheduler.java"
PHONE="app/src/main/java/com/kareem/cortex/PhoneContextCollector.java"
BRAIN="app/src/main/java/com/kareem/cortex/BrainRouter.java"
BUDGET="app/src/main/java/com/kareem/cortex/BrainAnswerBudget.java"
BRAINUI="app/src/main/java/com/kareem/cortex/ProposalAskCortexActivity.java"
CONTEXTUI="app/src/main/java/com/kareem/cortex/ContextNowActivity.java"
BRIEFUI="app/src/main/java/com/kareem/cortex/ProposalBriefActivity.java"
DEBUG="app/src/main/java/com/kareem/cortex/ReliableDebugExporter.java"
MAN="app/src/main/AndroidManifest.xml"

for f in "$SCHEMA" "$STATE" "$RESOLVER" "$BOUNDARY" "$OPENLOOP" "$ACTION" "$CONTROL" "$LEARN" "$DIAG" "$DIAGUI" "$PACKET" "$ASK" "$OPS" "$AWARE" "$PHONE" "$BRAIN" "$BUDGET" "$BRAINUI" "$CONTEXTUI" "$BRIEFUI" "$DEBUG" "$MAN"; do need_file "$f"; done
need "$SCHEMA" 'CREATE TABLE IF NOT EXISTS contexts' 'first-class context identity exists'
need "$SCHEMA" 'context_episodes' 'context episodes are persisted'
need "$SCHEMA" 'context_stack_state' 'primary/background context stack exists'
need "$SCHEMA" 'context_snapshots' 'resume snapshots exist'
need "$SCHEMA" 'context_fingerprint_features' 'learnable context fingerprints exist'
need "$SCHEMA" 'context_feedback' 'context corrections/feedback have a durable home'

need "$STATE" 'ROLE_PRIMARY="PRIMARY".*ROLE_BACKGROUND="BACKGROUND".*ROLE_AMBIENT="AMBIENT"' 'stack role is separate from lifecycle'
need "$STATE" 'LIFE_ACTIVE="ACTIVE".*LIFE_SUSPENDED="SUSPENDED".*LIFE_COMPLETED="COMPLETED"' 'context lifecycle is explicit'
need "$STATE" 'SWITCH_MIN=0\.78,SWITCH_MARGIN=0\.12' 'context switch hysteresis is conservative'
need "$STATE" 'PRIMARY_HOLD_MS=10L\*60L\*1000L' 'fresh primary context has a hold window'
need "$STATE" 'ContextBoundaryDetector\.strong' 'explicit interruption/resume can override ordinary score hysteresis'
need "$STATE" 'recordSnapshot' 'context can be resumed from event snapshots'
need "$STATE" 'linkEvidence' 'context retains provenance links'
need "$STATE" 'public static void suspend\(' 'explicit wrong-context correction can non-destructively suspend inferred context'
need "$STATE" 'USER_REJECT' 'explicit suspension is distinguishable in transition diagnostics'

need "$BOUNDARY" 'BOUNDARY_INTERRUPT' 'interruption is a first-class transition'
need "$BOUNDARY" 'BOUNDARY_RESUME' 'resume is a first-class transition'
need "$RESOLVER" 'project_entity' 'confirmed project identity is a preferred context anchor'
need "$RESOLVER" 'signal_threads' 'explicit conversation/thread evidence is considered'
need "$RESOLVER" 'ContextBoundaryDetector\.interrupt' 'new communication can interrupt prior work'
need "$RESOLVER" 'ContextBoundaryDetector\.resume' 'quiet interruptions can resume suspended work'
need "$RESOLVER" 'intentional Cortex capture' 'intentional capture evidence is considered'
need "$RESOLVER" 'group\.size\(\)>=3' 'phone activity needs repeated evidence before becoming a context candidate'
need "$RESOLVER" 'phone_context' 'phone activity remains an evidence source, not the context model itself'
need "$RESOLVER" 'shouldSnapshot' 'snapshots are event-driven with periodic safety refresh'
need "$RESOLVER" 'ContextOpenLoopResolver\.resolve' 'resolver snapshots use exact-context obligation grounding'
need "$RESOLVER" 'obligation_provenance.*context_bound_only' 'snapshot records that obligations were context-bound'
forbid "$RESOLVER" 'ORDER BY importance DESC,updated_at DESC LIMIT 1.*derived_items' 'resolver cannot borrow a global derived obligation while snapshotting'

need "$AWARE" '2200' 'raw phone evidence is debounced before contextualization'
need "$PHONE" 'ContextAwarenessScheduler\.request' 'phone evidence continuously feeds the Context Engine'

# Resume/open-loop state must belong to the exact Context. No global obligation may be borrowed as a fallback.
need "$OPENLOOP" 'latest snapshot recorded for this exact context' 'resume authority starts with the exact Context snapshot'
need "$OPENLOOP" "l\.from_type='derived'.*l\.to_type='context'" 'direct derived-to-context provenance is accepted'
need "$OPENLOOP" "l\.from_type='raw_signal'.*d\.anchor_signal_id.*l\.to_type='context'" 'anchor raw-signal provenance can ground a Context obligation'
need "$OPENLOOP" 'No title similarity and no global-open-loop fallback' 'resolver explicitly forbids similarity/global obligation adoption'
need "$OPENLOOP" 'linkedOpenCount' 'Context completion can inspect unresolved linked obligations'
need "$ACTION" 'never calls a model and never manufactures an external action' 'Context next-move layer is deterministic and local'
need "$ACTION" 'Resume my work' 'Context next-move button routes through the local Context question contract'
need "$CONTEXTUI" 'linked open obligation.*will NOT silently resolve' 'Done UI preserves unresolved obligation truth'
need "$CONTEXTUI" 'GROUNDED NEXT MOVE' 'Current Context exposes a grounded next move when one exists'
need "$CONTEXTUI" 'no model inference' 'Current Context distinguishes deterministic resume state from model inference'

# Explicit corrections must override old inference without erasing unfinished work.
need "$CONTROL" 'REJECT_CURRENT' 'wrong-context correction is persisted as explicit feedback'
need "$CONTROL" 'ContextFingerprintLearner\.reinforceRejection' 'wrong-context correction teaches the fingerprint learner'
need "$CONTROL" 'ContextStateStore\.suspend' 'wrong-context correction suspends instead of completing work'
need "$LEARN" 'reinforceRejection' 'fingerprint learner has explicit negative learning path'
need "$RESOLVER" 'REJECT_HOLD_MS=20L\*60L\*1000L' 'wrong-context evidence has a bounded rejection hold'
need "$RESOLVER" 'rejected>=evidenceAt' 'newer evidence can legitimately reactivate a previously rejected context'
need "$CONTEXTUI" 'Not this' 'Current Context exposes an explicit wrong-context correction control'
need "$CONTEXTUI" 'does not mark the work Done or close linked actions' 'wrong-context UI preserves action/open-loop truth'

# Context replay must be SQLite-read-only. Diagnostics are not allowed to initialize schema or rerun inference.
need "$DIAG" 'SQLite reads only' 'Context replay declares strict SQLite-read-only behavior'
need "$DIAG" 'cortex_context_replay_v2' 'Context replay has the strict read-only diagnostic schema version'
need "$DIAG" 'sqlite_reads_only.*true' 'Context replay machine output records the read-only boundary'
need "$DIAG" 'hasTables' 'Context replay checks ledger existence instead of initializing it'
need "$DIAG" 'context_episodes' 'Context replay includes transition episodes'
need "$DIAG" 'context_snapshots' 'Context replay includes resume snapshots'
need "$DIAG" 'context_feedback' 'Context replay includes explicit corrections'
need "$DIAG" "to_type='context'" 'Context replay includes evidence provenance'
forbid "$DIAG" 'ContextSchema\.ensure|CognitiveStore\.ensure|ContextResolver\.refresh|ContextOpenLoopResolver\.resolve|ContextStateStore\.primary|ContextStateStore\.stack|offerPrimary|recordSnapshot|feedback\(' 'read-only replay cannot initialize schema, mutate or rerun Context inference'
need "$DIAGUI" 'READ ONLY' 'Context replay UI visibly states read-only behavior'
need "$DIAGUI" 'Copy replay' 'Context replay can be copied for diagnosis'
need "$CONTEXTUI" 'ContextDiagnosticsActivity\.class' 'Current Context exposes replay diagnostics'
need "$MAN" 'activity android:name="\.ContextDiagnosticsActivity"' 'Context replay activity is registered'
need "$DEBUG" 'context_replay' 'recovery debug export preserves Context replay even when exhaustive export is skipped'

need "$PACKET" 'CURRENT CORTEX CONTEXT' 'Brain/Brief receive a compact context passport'
need "$PACKET" 'ContextOpenLoopResolver\.resolve' 'Context Passport uses provenance-aware resume state'
need "$PACKET" 'cloudText' 'context packet separates local and cloud representations'
need "$PACKET" 'exports no derived context summary to cloud' 'v1 prevents privacy laundering through derived summaries'
forbid "$PACKET" 'globalOpenLoops|globalNextStep' 'Context Passport cannot borrow unrelated global obligations'
need "$ASK" 'where did i leave off|اكمل منين' 'Brain recognizes resume/current-context questions'
need "$ASK" 'will not borrow one from another context' 'Brain states when no grounded Context next step exists'
need "$ASK" 'local only; no model inference' 'Brain exposes local provenance for Context resume state'
need "$OPS" 'ContextAskEngine\.tryAnswer' 'operational Brain routes context questions locally first'

# Fast Answer First: operational state stays local; generic external questions skip broad retrieval;
# next-move generation is separate from BRAIN_ANSWER and must not block the first visible answer.
need "$BRAIN" 'context_cloud_sent.*false' 'Brain diagnostics prove the live Context Passport stays local in v1'
need "$BRAIN" 'fastGeneral' 'Combined has a fast route for questions that do not need broad Cortex retrieval'
need "$BRAIN" 'broadRetrieval=combined&&needsBroadContext' 'broad retrieval is explicitly budgeted by question intent'
need "$BRAIN" 'actions_deferred.*true' 'Brain first-answer diagnostics record deferred next moves'
need "$BRAIN" 'String modelQuestion=question' 'first external provider call answers the user question only'
forbid "$BRAIN" 'String modelQuestion=.*BrainActionStore\.request' 'structured action JSON cannot block the first Brain answer'
need "$BRAIN" '\(fastFocal\|\|fastGeneral\)\?12_000L:18_000L' 'external Brain has explicit fast/general answer budgets'
need "$BUDGET" 'ArrayBlockingQueue' 'stalled provider calls have bounded concurrency and queueing'
need "$BRAINUI" 'ANSWER_READY' 'Brain answer semantic terminal occurs at rendered answer readiness'
need "$BRAINUI" 'ProposalUi\.attach' 'useful next moves start only after the answer card exists'
need "$BRAINUI" 'LOCAL CONTEXT' 'Brain visibly exposes the live local context'
need "$BRAINUI" 'ATTACHED' 'attached capture remains visible beside live context'
need "$BRIEFUI" 'CURRENT CONTEXT' 'Brief is context-first instead of modality-first'

if [ "$FAIL" -ne 0 ]; then
  printf 'CORTEX_CONTEXT_CORE_AUDIT=FAIL (%s failure(s))\n' "$FAIL" >&2
  exit 2
fi
printf 'CORTEX_CONTEXT_CORE_AUDIT=PASS\n'
