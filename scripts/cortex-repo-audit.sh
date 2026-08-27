#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"; cd "$ROOT"
FAIL=0; WARN=0; SCANNED=0
ok(){ printf 'AUDIT PASS: %s\n' "$*"; }
bad(){ FAIL=$((FAIL+1)); printf 'AUDIT FAIL: %s\n' "$*" >&2; }
warn(){ WARN=$((WARN+1)); printf 'AUDIT WARN: %s\n' "$*" >&2; }
req(){ [ -f "$1" ] && ok "required $1" || bad "missing $1"; }
has(){ grep -Eq "$2" "$1" 2>/dev/null && ok "$3" || bad "$3"; }
none(){ if git grep -nEi "$1" -- ':!downloads/**' >/dev/null 2>&1; then bad "$2"; else ok "$2"; fi; }
printf '\n================ CORTEX REPO AUDIT ================\n'
printf 'Commit: %s\nBranch: %s\n' "$(git rev-parse --short=12 HEAD 2>/dev/null||echo unknown)" "$(git branch --show-current 2>/dev/null||echo detached)"
while IFS= read -r f; do case "$f" in *.java|*.kt|*.xml|*.gradle|*.properties|*.sh|*.yml|*.yaml|*.json|*.md) SCANNED=$((SCANNED+1)); grep -nE '^(<<<<<<< |=======|>>>>>>> )' "$f" >/dev/null 2>&1 && bad "merge marker in $f";; esac; done < <(git ls-files)
ok "scanned $SCANNED tracked source/config files"

MAN=app/src/main/AndroidManifest.xml; UI=app/src/main/java/com/kareem/cortex/CortexUi.java; CAP=app/src/main/java/com/kareem/cortex/CortexCapabilityRegistry.java
for f in "$MAN" "$UI" "$CAP" app/src/main/java/com/kareem/cortex/BackupImporter.java app/src/main/java/com/kareem/cortex/CompactTodayActivity.java app/src/main/java/com/kareem/cortex/PrimeBriefStore.java app/src/main/java/com/kareem/cortex/AttentionLearning.java app/src/main/java/com/kareem/cortex/CortexActionExecutor.java app/src/main/java/com/kareem/cortex/CortexActionDispatcher.java app/src/main/java/com/kareem/cortex/CloudEvidencePolicy.java; do req "$f"; done

[ "$(grep -o 'android.intent.action.MAIN' "$MAN"|wc -l|tr -d ' ')" = 1 ] && ok 'exactly one launcher' || bad 'launcher count is not 1'
[ "$(grep -o 'android.intent.action.SEND"' "$MAN"|wc -l|tr -d ' ')" = 1 ] && ok 'exactly one ACTION_SEND owner' || bad 'ACTION_SEND count is not 1'
[ "$(grep -o 'android.intent.action.SEND_MULTIPLE' "$MAN"|wc -l|tr -d ' ')" = 1 ] && ok 'exactly one ACTION_SEND_MULTIPLE owner' || bad 'ACTION_SEND_MULTIPLE count is not 1'
has "$MAN" 'android:allowBackup="false"' 'platform backup disabled for private Vault'
has "$MAN" 'activity-alias android:name="\.PremiumHomeActivity" android:targetActivity="\.CompactTodayActivity"' 'legacy Home alias targets current Now surface'

BI=app/src/main/java/com/kareem/cortex/BackupImporter.java
has "$BI" 'class Inspection' 'backup inspection model present'; has "$BI" 'Inspection inspect\(' 'backup read-only inspect path present'; has "$BI" 'int restore\(' 'explicit restore path present'; has "$BI" 'validatedName\(' 'archive path validation present'

COUNT="$(grep -oE 'c\([0-9]+,"' "$CAP"|wc -l|tr -d ' ')"; [ "$COUNT" = 43 ] && ok 'authoritative registry has 43 capabilities' || bad "capability count=$COUNT"
for n in $(seq 1 43); do grep -Fq "c($n," "$CAP" || bad "capability #$n missing"; done

has "$UI" 'BRAND=Color\.rgb\(137,217,74\)' 'green #89D94A centralized'; has "$UI" 'ORANGE=Color\.rgb\(229,169,59\)' 'orange #E5A93B centralized'; has "$UI" 'QUIET=Color\.rgb\(51,53,50\)' 'quiet #333532 centralized'; has "$UI" 'PURPLE=Color\.rgb\(155,81,224\)' 'project #9B51E0 centralized'; has "$UI" 'RED=Color\.rgb\(217,83,79\)' 'review #D9534F centralized'; has "$UI" 'BLUE=Color\.rgb\(74,144,226\)' 'recent #4A90E2 centralized'

has app/src/main/java/com/kareem/cortex/AttentionLearning.java 'actedAt>=item\.updatedAt' 'acted feedback is version-scoped'; has app/src/main/java/com/kareem/cortex/PrimeBriefStore.java 'attentionEmpty\(' 'attention-empty state is explicit'; has app/src/main/java/com/kareem/cortex/PrimeBriefStore.java "'ALERT','CHANGE'" 'alert/change enter Today read model'; has app/src/main/java/com/kareem/cortex/CompactTodayActivity.java 'removeView\(loadingView\)' 'Now loading state is one-shot'
has app/src/main/java/com/kareem/cortex/CortexActionExecutor.java 'Calendar app owns the final write' 'calendar actions remain drafts'; has app/src/main/java/com/kareem/cortex/CortexActionDispatcher.java 'Nothing will be sent or changed until you confirm' 'external actions require approval'; has app/src/main/java/com/kareem/cortex/BrainRouter.java 'CloudEvidencePolicy\.filter' 'Combined Brain filters cloud evidence locally'
none 'tmpfiles\.org|transfer\.sh|file\.io' 'no anonymous public APK upload endpoint tracked'

for wf in .github/workflows/android-build.yml .github/workflows/build-apk.yml; do req "$wf"; has "$wf" 'contents: read' "$wf is read-only"; has "$wf" ':app:assembleDebug :app:assembleDebugAndroidTest' "$wf compiles app + instrumentation APKs"; done
for t in CognitivePacketDifferentialV5Test.java CognitiveProductAdjudicationTest.java FullApplicationSelfUserTest.java TeacherStudentCognitiveDifferentialTest.java; do req "app/src/androidTest/java/com/kareem/cortex/$t"; done

HITS="$(git grep -nEi '\b(TODO|FIXME|temporary stub|placeholder implementation)\b' -- '*.java' '*.kt' '*.xml' '*.gradle' '*.sh' 2>/dev/null|wc -l|tr -d ' '||true)"; [ "$HITS" = 0 ] && ok 'no TODO/FIXME placeholder markers' || warn "$HITS TODO/FIXME/placeholder marker(s)"
printf '%s\nFiles scanned: %s  Warnings: %s  Failures: %s\n' '-----------------------------------------------------' "$SCANNED" "$WARN" "$FAIL"
[ "$FAIL" = 0 ] || { printf 'CORTEX_REPO_AUDIT=FAIL\n' >&2; exit 2; }; printf 'CORTEX_REPO_AUDIT=PASS\n=====================================================\n\n'
