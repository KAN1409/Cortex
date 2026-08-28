#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$ROOT"
FAIL=0
WARN=0
SCANNED=0
ok(){ printf 'AUDIT PASS: %s\n' "$*"; }
warn(){ WARN=$((WARN+1)); printf 'AUDIT WARN: %s\n' "$*" >&2; }
bad(){ FAIL=$((FAIL+1)); printf 'AUDIT FAIL: %s\n' "$*" >&2; }
require_file(){ [ -f "$1" ] && ok "required file $1" || bad "missing required file $1"; }
require_text(){ local f="$1" p="$2" label="$3"; grep -Eq "$p" "$f" 2>/dev/null && ok "$label" || bad "$label"; }
forbid_text(){ local f="$1" p="$2" label="$3"; if grep -Eq "$p" "$f" 2>/dev/null; then bad "$label"; else ok "$label"; fi; }

printf '\n================ CORTEX REPO AUDIT ================\n'
printf 'Commit: %s\n' "$(git rev-parse --short=12 HEAD 2>/dev/null || echo unknown)"
printf 'Branch: %s\n' "$(git branch --show-current 2>/dev/null || echo detached)"

while IFS= read -r f; do
  case "$f" in
    *.java|*.kt|*.kts|*.xml|*.gradle|*.properties|*.sh|*.yml|*.yaml|*.json|*.md)
      SCANNED=$((SCANNED+1))
      grep -nE '^(<<<<<<< |=======|>>>>>>> )' "$f" >/dev/null 2>&1 && bad "merge-conflict marker in $f"
      ;;
  esac
done < <(git ls-files)
ok "scanned $SCANNED tracked source/config files"

for f in \
  app/build.gradle \
  app/src/main/AndroidManifest.xml \
  app/src/main/java/com/kareem/cortex/VaultDb.java \
  app/src/main/java/com/kareem/cortex/CognitiveSchema.java \
  app/src/main/java/com/kareem/cortex/CognitiveStore.java \
  app/src/main/java/com/kareem/cortex/RawSignalStore.java \
  app/src/main/java/com/kareem/cortex/MasterRelevanceFilter.java \
  app/src/main/java/com/kareem/cortex/AttentionEngine.java \
  app/src/main/java/com/kareem/cortex/AttentionLearning.java \
  app/src/main/java/com/kareem/cortex/CandidateConsolidator.java \
  app/src/main/java/com/kareem/cortex/PrimeBriefStore.java \
  app/src/main/java/com/kareem/cortex/BriefComposer.java \
  app/src/main/java/com/kareem/cortex/AskOperationalEngine.java \
  app/src/main/java/com/kareem/cortex/ProactiveEngine.java \
  app/src/main/java/com/kareem/cortex/BrainRouter.java \
  app/src/main/java/com/kareem/cortex/CortexActionDispatcher.java \
  app/src/main/java/com/kareem/cortex/BackupImporter.java \
  app/src/main/java/com/kareem/cortex/CompactTodayActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalPeopleProjectsActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalAskCortexActivity.java \
  app/src/androidTest/java/com/kareem/cortex/CognitiveProductAdjudicationTest.java; do
  require_file "$f"
done

MAN=app/src/main/AndroidManifest.xml
launcher_count="$(grep -o 'android.intent.action.MAIN' "$MAN" | wc -l | tr -d ' ')"
[ "$launcher_count" = "1" ] && ok "exactly one launcher intent" || bad "expected 1 launcher intent, found $launcher_count"
send_count="$(grep -o 'android.intent.action.SEND"' "$MAN" | wc -l | tr -d ' ')"
send_multi_count="$(grep -o 'android.intent.action.SEND_MULTIPLE' "$MAN" | wc -l | tr -d ' ')"
[ "$send_count" = "1" ] && ok "exactly one ACTION_SEND owner" || bad "expected 1 ACTION_SEND owner, found $send_count"
[ "$send_multi_count" = "1" ] && ok "exactly one ACTION_SEND_MULTIPLE owner" || bad "expected 1 ACTION_SEND_MULTIPLE owner, found $send_multi_count"
require_text "$MAN" 'activity android:name="\.CompactTodayActivity"[^>]*exported="true"' 'CompactTodayActivity is the current launcher surface'

# One cognitive truth path: raw evidence -> relevance/adjudication -> derived_items -> attention -> Today/Ask/Brief/proactive.
PRIME=app/src/main/java/com/kareem/cortex/PrimeBriefStore.java
ASK=app/src/main/java/com/kareem/cortex/AskOperationalEngine.java
BRIEF=app/src/main/java/com/kareem/cortex/BriefEngine.java
PROACTIVE=app/src/main/java/com/kareem/cortex/ProactiveEngine.java
require_text "$PRIME" "FROM derived_items WHERE state='open'" 'Today reads canonical derived_items state'
require_text "$PRIME" 'AttentionEngine\.evaluate' 'Today ranks canonical candidates through AttentionEngine'
require_text "$ASK" 'PrimeBriefStore\.load' 'Ask attention uses the same canonical Today snapshot'
forbid_text "$ASK" 'FROM actions|JOIN actions|actions a' 'Ask attention does not consult legacy actions table'
require_text "$BRIEF" 'BriefComposer\.compose' 'legacy BriefEngine is only a compatibility facade'
require_text "$PROACTIVE" 'PrimeBriefStore\.load' 'proactive digest uses the canonical Today snapshot'
forbid_text "$PROACTIVE" 'SecondBrainEngine\.openLoops|FROM actions|JOIN actions' 'proactive digest does not derive a parallel open-loop truth'

parallel_classes=(
  OpenLoopStore.java
  SemanticOpenLoopStore.java
  CortexAttentionOrchestrator.java
  CortexAttentionSchema.java
  AttentionFeedStore.java
  AttentionActionStore.java
  AttentionActionPlanner.java
  AttentionFeedbackStore.java
  AttentionModels.java
  AttentionSemantics.java
  DerivedAttentionBridge.java
  AttentionMaintenance.java
  AttentionMaintenanceWorker.java
)
for f in "${parallel_classes[@]}"; do
  p="app/src/main/java/com/kareem/cortex/$f"
  [ ! -e "$p" ] && ok "parallel attention class absent: $f" || bad "obsolete parallel attention architecture returned: $p"
done

if git grep -nE 'CREATE TABLE( IF NOT EXISTS)? (open_loops|attention_feed|attention_actions)|FROM (open_loops|attention_feed|attention_actions)|JOIN (open_loops|attention_feed|attention_actions)' -- 'app/src/main/**' >/dev/null 2>&1; then
  bad "parallel attention/open-loop SQL store detected; derived_items must remain authoritative"
else
  ok "no parallel attention/open-loop SQL truth store"
fi

workflow_count="$(git ls-files '.github/workflows/*.yml' '.github/workflows/*.yaml' | wc -l | tr -d ' ')"
[ "$workflow_count" = "1" ] && ok "exactly one GitHub Actions workflow" || bad "expected one workflow, found $workflow_count"

if git ls-files | grep -Eq '(^|/)(build[-_]?trigger|.*-trigger\.txt$|\.build-.*marker$)'; then
  bad "obsolete build-trigger marker files are still tracked"
else
  ok "no obsolete build-trigger marker files"
fi

if git ls-files | grep -Ei '\.apk$' >/dev/null 2>&1; then
  bad "compiled APK binaries must not be tracked in git"
else
  ok "no tracked APK binaries"
fi

if git ls-files | grep -Eq '^downloads/'; then
  bad "generated downloads/ artifacts must not be tracked"
else
  ok "no generated downloads artifacts"
fi

placeholder_hits="$(git grep -nEi '\b(TODO|FIXME|temporary stub|placeholder implementation)\b' -- '*.java' '*.kt' '*.xml' '*.gradle' '*.sh' 2>/dev/null | wc -l | tr -d ' ' || true)"
[ "$placeholder_hits" = "0" ] || warn "$placeholder_hits TODO/FIXME/placeholder source hit(s) require review"

printf '%s\n' '-----------------------------------------------------'
printf 'Files scanned: %s  Warnings: %s  Failures: %s\n' "$SCANNED" "$WARN" "$FAIL"
if [ "$FAIL" -ne 0 ]; then printf 'CORTEX_REPO_AUDIT=FAIL\n' >&2; exit 2; fi
printf 'CORTEX_REPO_AUDIT=PASS\n'
printf '%s\n\n' '====================================================='
