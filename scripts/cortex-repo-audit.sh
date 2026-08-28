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
  app/src/main/java/com/kareem/cortex/RawSignalStore.java \
  app/src/main/java/com/kareem/cortex/MasterRelevanceFilter.java \
  app/src/main/java/com/kareem/cortex/AttentionEngine.java \
  app/src/main/java/com/kareem/cortex/CandidateConsolidator.java \
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
