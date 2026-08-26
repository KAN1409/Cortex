#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$ROOT"
FAIL=0
ok(){ printf 'EXPERIMENTAL REGRESSION AUDIT PASS: %s\n' "$*"; }
bad(){ FAIL=$((FAIL+1)); printf 'EXPERIMENTAL REGRESSION AUDIT FAIL: %s\n' "$*" >&2; }
need(){ local f="$1" p="$2" label="$3"; grep -Eq "$p" "$f" 2>/dev/null && ok "$label" || bad "$label"; }
forbid(){ local f="$1" p="$2" label="$3"; if grep -Eq "$p" "$f" 2>/dev/null; then bad "$label"; else ok "$label"; fi; }

BUILD="app/build.gradle"
PUI="app/src/main/java/com/kareem/cortex/ProposalUi.java"
HEALTH="app/src/main/java/com/kareem/cortex/HealthFollowupActivity.java"
CAPTURE="app/src/main/java/com/kareem/cortex/CaptureActivity.java"
SUITE="app/src/main/java/com/kareem/cortex/CortexExperimentalUserSuite.java"

for f in "$BUILD" "$PUI" "$HEALTH" "$CAPTURE" "$SUITE"; do
  [ -f "$f" ] && ok "$f present" || bad "$f missing"
done

# J04: BRAIN_ANSWER terminal state must not be visually replaced by deferred proposal loading.
need "$PUI" 'holder\.setVisibility\(View\.GONE\)' 'proposal strip is absent while deferred enrichment is running'
need "$PUI" 'Fast Answer First invariant' 'proposal UI documents answer-first ownership boundary'
need "$PUI" 'renderRecoverableState.*holder\.setVisibility\(View\.VISIBLE\)|holder\.setVisibility\(View\.VISIBLE\).*renderRecoverableState' 'proposal terminal recovery can reveal its own surface'
need "$PUI" 'renderProposals' 'proposal terminal success renders separately from Brain answer readiness'
forbid "$PUI" 'CortexUi\.plain\(activity,"Generating useful next moves…"' 'deferred proposal generation cannot paint a visible busy label after answer readiness'
need "$SUITE" 'Semantic operation completed but the visible Brain surface still shows a busy/progress state' 'J04 continues to detect terminal-answer UI regressions'

# J10: Health permissions must use the Health Connect ActivityResultContract, not a raw legacy intent.
need "$BUILD" "androidx\.activity:activity:1\.10\.1" 'Activity Result runtime dependency is explicit'
need "$HEALTH" 'extends ComponentActivity' 'Health surface owns a lifecycle-aware ActivityResultRegistry'
need "$HEALTH" 'registerForActivityResult' 'Health permission launcher is registered with lifecycle ownership'
need "$HEALTH" 'HealthPermissionsRequestContract\(HealthConnectBridge\.PROVIDER\)' 'Health Connect permission contract targets the configured provider'
need "$HEALTH" 'healthPermissionLauncher\.launch\(HealthConnectBridge\.requiredReadPermissions\(\)\)' 'Health permission request launches the exact required read scopes'
forbid "$HEALTH" 'startActivityForResult' 'Health permission handoff cannot regress to legacy startActivityForResult'
need "$SUITE" 'external=.*back=.*returned=.*sandbox metrics' 'J10 still verifies external handoff, safe return and unchanged sandbox evidence'

# J11: Android document/photo pickers must return through registered contracts and handle cancel explicitly.
need "$CAPTURE" 'extends ComponentActivity' 'Capture surface owns a lifecycle-aware ActivityResultRegistry'
need "$CAPTURE" 'ActivityResultContracts\.OpenDocument' 'file/photo entry points use the Android OpenDocument contract'
need "$CAPTURE" 'filePickerLauncher\.launch\(new String\[\]\{"\*/\*"\}\)' 'file picker launches through registered contract'
need "$CAPTURE" 'photoPickerLauncher\.launch\(new String\[\]\{"image/\*"\}\)' 'photo picker launches through registered contract'
need "$CAPTURE" 'if\(uri==null\)\{setImporting\(false,""\);return;\}' 'picker cancellation explicitly restores the capture surface'
need "$CAPTURE" 'takePersistableUriPermission' 'selected document access remains safely retained when provider permits it'
forbid "$CAPTURE" 'startActivityForResult\(i,REQ_(FILE|PHOTO)\)' 'file/photo handoff cannot regress to legacy request-code lifecycle'
need "$SUITE" 'capture=.*picker=.*returned=.*image persisted=.*pdf persisted' 'J11 still verifies picker return plus both persistence paths'

if [ "$FAIL" -ne 0 ]; then
  printf '\nCORTEX_EXPERIMENTAL_REGRESSION_AUDIT=FAIL (%s)\n' "$FAIL" >&2
  exit 1
fi
printf '\nCORTEX_EXPERIMENTAL_REGRESSION_AUDIT=PASS\n'
