#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$ROOT"
FAIL=0
ok(){ printf 'HEALTH BRANCH AUDIT PASS: %s\n' "$*"; }
bad(){ FAIL=$((FAIL+1)); printf 'HEALTH BRANCH AUDIT FAIL: %s\n' "$*" >&2; }
need_file(){ [ -f "$1" ] && ok "$1 present" || bad "$1 missing"; }
need(){ local f="$1" p="$2" label="$3"; grep -Eq "$p" "$f" 2>/dev/null && ok "$label" || bad "$label"; }

MAN="app/src/main/AndroidManifest.xml"
INPUT="app/src/main/java/com/kareem/cortex/InputActivity.java"
ONBOARD="app/src/main/java/com/kareem/cortex/AccessOnboardingActivity.java"
ACCESS="app/src/main/java/com/kareem/cortex/AccessGateRegistry.java"
PROVIDER="app/src/main/java/com/kareem/cortex/ExternalBrainProvider.java"
PUI="app/src/main/java/com/kareem/cortex/ProposalUi.java"
RDEBUG="app/src/main/java/com/kareem/cortex/ReliableDebugExporter.java"
ENV="app/src/main/java/com/kareem/cortex/EnvironmentActivity.java"

for f in "$ONBOARD" "$ACCESS" "$PROVIDER" "$PUI" "$RDEBUG" "$ENV"; do need_file "$f"; done

# First-run Android access walkthrough must stay wired to the same authoritative gate inventory.
need "$MAN" 'activity android:name="\.AccessOnboardingActivity"' 'first-run Access onboarding is registered'
need "$INPUT" 'AccessOnboardingActivity\.PREFS' 'launcher checks first-run onboarding state'
need "$INPUT" 'AccessOnboardingActivity\.class' 'launcher can open access onboarding'
need "$ONBOARD" 'AccessGateRegistry\.snapshot' 'onboarding consumes authoritative AccessGateRegistry'
need "$ONBOARD" 'notification_listener.*accessibility.*usage' 'onboarding covers notification, accessibility and usage special access'
need "$ONBOARD" 'requestPermissions' 'onboarding requests runtime permissions normally'
need "$ONBOARD" 'ACTION_NOTIFICATION_LISTENER_SETTINGS' 'onboarding opens Notification Access settings'
need "$ONBOARD" 'ACTION_ACCESSIBILITY_SETTINGS' 'onboarding opens Accessibility settings'
need "$ONBOARD" 'PhoneUsageAccess\.openSettings' 'onboarding opens Usage Access settings'

# OX Alpha must not spend its user-visible token budget on hidden reasoning or surface JSON null.
need "$PROVIDER" 'isOxAlpha\(context\).*reasoning' 'OX Alpha normal request carries explicit reasoning configuration'
need "$PROVIDER" 'content==null\|\|content==JSONObject\.NULL' 'OpenRouter JSON null content is rejected as empty'
need "$PROVIDER" 'isNullLike' 'provider rejects literal null/undefined model text'
need "$PROVIDER" 'effort","low".*exclude",true' 'OX reasoning remains low and excluded from returned content'

# Useful Next Moves must always resolve to proposals, a no-op, or a recoverable error; never infinite loading.
need "$PUI" 'UI_TIMEOUT_MS=45_000L' 'proposal UI has bounded loading watchdog'
need "$PUI" 'Suggestions are taking too long' 'proposal timeout has explicit recovery state'
need "$PUI" 'Retry suggestions' 'proposal failures expose retry'
need "$PUI" 'ResultProposalEngine\.invalidate' 'proposal retry invalidates stale cache before fresh request'

# Debug export must create an artifact even when exhaustive diagnostics partially fail.
need "$RDEBUG" 'DebugExporter\.build' 'reliable exporter attempts exhaustive package first'
need "$RDEBUG" 'buildRecovery' 'reliable exporter has recovery package fallback'
need "$RDEBUG" 'MediaStore\.Downloads' 'debug package is copied to Downloads on modern Android'
need "$RDEBUG" 'Environment\.DIRECTORY_DOWNLOADS\+"/Cortex"' 'debug package has known Downloads/Cortex destination'
need "$RDEBUG" 'ClipData\.newRawUri' 'debug share carries ClipData URI grant'
need "$RDEBUG" 'grantUriPermission' 'debug share explicitly grants target apps read access'
need "$ENV" 'ReliableDebugExporter\.exportAndShare' 'Advanced diagnostics uses reliable debug exporter'

# Existing manual transcript correction behavior is part of the same runtime recovery contract.
need_file app/src/main/java/com/kareem/cortex/TranscriptCorrectionStore.java
need app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'Edit transcript' 'capture result exposes Edit transcript'
need app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'TranscriptCorrectionStore\.save' 'manual transcript correction is persisted'

if [ "$FAIL" -ne 0 ]; then
  printf 'CORTEX_HEALTH_FOLLOWUP_AUDIT=FAIL (%s failure(s))\n' "$FAIL" >&2
  exit 2
fi
printf 'CORTEX_HEALTH_FOLLOWUP_AUDIT=PASS\n'
