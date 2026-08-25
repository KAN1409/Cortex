#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$ROOT"
FAIL=0
ok(){ printf 'USER JOURNEY AUDIT PASS: %s\n' "$*"; }
bad(){ FAIL=$((FAIL+1)); printf 'USER JOURNEY AUDIT FAIL: %s\n' "$*" >&2; }
need_file(){ [ -f "$1" ] && ok "$1 present" || bad "$1 missing"; }
need(){ local f="$1" p="$2" label="$3"; grep -Eq "$p" "$f" 2>/dev/null && ok "$label" || bad "$label"; }

SUITE="app/src/main/java/com/kareem/cortex/CortexExperimentalUserSuite.java"
EXPORT="app/src/main/java/com/kareem/cortex/CortexUserJourneyTestExporter.java"
COMPAT="app/src/main/java/com/kareem/cortex/CortexRobotTestExporter.java"
FIX="app/src/main/java/com/kareem/cortex/CortexRobotFixtures.java"
ACCESS="app/src/main/java/com/kareem/cortex/CortexScreenAccessibilityService.java"
DISPATCH="app/src/main/java/com/kareem/cortex/CortexActionDispatcher.java"
BRAIN="app/src/main/java/com/kareem/cortex/BrainRouter.java"
ONBOARD="app/src/main/java/com/kareem/cortex/AccessOnboardingActivity.java"
HEALTH="app/src/main/java/com/kareem/cortex/HealthFollowupActivity.java"
TRANSCRIPT="app/src/main/java/com/kareem/cortex/TranscriptCorrectionStore.java"

for f in "$SUITE" "$EXPORT" "$COMPAT" "$FIX" "$ACCESS" "$DISPATCH" "$BRAIN" "$ONBOARD" "$HEALTH" "$TRANSCRIPT"; do need_file "$f"; done

# The primary test contract is user goals + expected terminal outcome, not click/screen counts.
need "$SUITE" 'enum Status \{ PASS, CONFIRMED_APP_BUG, QUALITY_PROBLEM, TEST_GAP, HARNESS_ISSUE \}' 'suite separates product bugs, quality issues, gaps and harness issues'
need "$SUITE" 'goal=.*expected=.*actual=' 'journey records goal, expected result and actual result'
need "$SUITE" 'CONFIRMED CORTEX PROBLEMS' 'report puts confirmed Cortex problems first'
need "$SUITE" 'QUALITY PROBLEMS' 'report has quality-problem section'
need "$SUITE" 'TEST GAPS' 'report distinguishes untested coverage from app bugs'
need "$SUITE" 'PASSED JOURNEYS' 'report records passed real journeys'
need "$SUITE" 'Test harness exception:.*HARNESS_ISSUE' 'test-harness exceptions cannot be mislabeled as Cortex app bugs'

# Real product outcomes are asserted after the UI action.
need "$SUITE" 'knowledge_items WHERE raw_text=\?' 'text-capture journey verifies exact persisted evidence'
need "$SUITE" 'derived_items WHERE kind=.NOTE.' 'approved local proposal is verified in Cortex DB'
need "$SUITE" 'prompt_library_items WHERE pinned=1' 'Prompt Library journey verifies persistence'
need "$SUITE" 'health_metrics' 'Health journey checks grounded metric data'
need "$SUITE" 'click\("Images"\).*click\("Voice"\).*click\("Text"\)' 'Vault journey checks evidence-type filters'
need "$SUITE" "source='robot_fixture' AND type='AUDIO'" 'Vault journey validates the voice fixture uses the production AUDIO contract'
need "$SUITE" 'Robot Test Person' 'People journey uses a known confirmed identity fixture'
need "$SUITE" 'Robot Test Project' 'Projects journey uses a known confirmed project fixture'

# Brain is judged only after a terminal state, and grounding failure is distinct from slow progress.
need "$SUITE" 'journeyBrainTerminal' 'suite has a dedicated Brain terminal-answer journey'
need "$SUITE" '30000' 'Brain journey has an explicit bounded terminal wait'
need "$SUITE" '!busy\(x\)' 'Brain journey waits for busy/progress state to end'
need "$SUITE" 'Brain did not reach a stable terminal UI state' 'Brain latency/stuck state is reported separately'
need "$SUITE" 'ignored known ACTION/WAITING fixture state' 'terminal but ungrounded Brain answer is a confirmed functional bug'
need "$SUITE" 'Brain stopped safely' 'Brain journey recognizes explicit terminal failure'

# Combined state questions use authoritative local Cortex state rather than discarding derived state in a cloud prompt.
need "$BRAIN" 'AskOperationalEngine\.tryAnswer' 'Combined Brain checks authoritative operational state first'
need "$BRAIN" 'cortex-operational-combined' 'Combined operational state has an explicit local provider result'
need "$BRAIN" 'no operational private state sent to cloud' 'operational fast path documents the privacy boundary'
need "$BRAIN" 'operational_fast_path' 'operational fast path is visible in job diagnostics'

# First-run is tested through its visible UI while restoring the real onboarding preference afterward.
need "$SUITE" 'journeyOnboardingRoundTrip' 'suite exercises first-run onboarding as a user journey'
need "$SUITE" 'AccessOnboardingActivity\.PREFS' 'onboarding journey uses the real first-run preference'
need "$SUITE" 'Skip optional access' 'onboarding journey safely traverses optional gates without granting them'
need "$SUITE" 'ed\.remove\(AccessOnboardingActivity\.KEY_SEEN\)|ed\.putBoolean\(AccessOnboardingActivity\.KEY_SEEN,old\)' 'onboarding journey restores the pre-test preference state'
need "$ONBOARD" 'AccessGateRegistry\.snapshot' 'onboarding itself remains grounded in authoritative access gates'

# Health permissions are exercised only as a safe Android-owned round-trip; real health records are never pulled into an automated report.
need "$SUITE" 'journeyHealthPermissionRoundTrip' 'suite exercises Health Connect permission hand-off'
need "$SUITE" 'Grant health read access' 'Health journey uses the visible permission control'
need "$SUITE" 'snapshotPackage' 'external permission surface is detected by active package'
need "$SUITE" 'sandbox metrics .*→' 'Health permission round-trip verifies no silent sandbox data mutation'
need "$SUITE" 'Real Health Connect provider sync after user grants scopes' 'real provider sync remains an explicit manual privacy boundary'
need "$HEALTH" 'HealthConnectBridge\.permissionIntent' 'Health UI delegates permission ownership to Health Connect'

# Known photo/PDF evidence is imported through the same ACTION_SEND entry the real Android share sheet uses.
need "$FIX" 'PdfDocument' 'fixtures include a valid synthetic PDF'
need "$FIX" 'sharedImageFile' 'fixtures expose a synthetic image inside the FileProvider-safe test directory'
need "$FIX" 'sharedPdfFile' 'fixtures expose a synthetic PDF inside the FileProvider-safe test directory'
need "$SUITE" 'FileProvider\.getUriForFile' 'share-import journey uses a granted content URI'
need "$SUITE" 'Intent\.ACTION_SEND' 'share-import journey exercises the real Android share entry'
need "$SUITE" 'application/pdf' 'share-import journey includes a PDF payload'
need "$SUITE" "source='android_share' AND type='SCREENSHOT'" 'image import is verified in Cortex DB'
need "$SUITE" "source='android_share' AND type='FILE'" 'PDF import is verified in Cortex DB'

# Manual transcript editing must persist through the visible result editor and remain authoritative over later ASR writes.
need "$SUITE" 'journeyTranscriptCorrection' 'suite exercises Edit Transcript end to end'
need "$SUITE" 'item_text_corrections' 'transcript journey verifies durable correction history'
need "$SUITE" 'stale ASR overwrite' 'transcript journey simulates a later ASR write'
need "$SUITE" 'survives later extracted_text overwrite' 'transcript journey verifies manual correction authority'
need "$TRANSCRIPT" 'keep_manual_transcript_correction' 'production transcript store keeps manual correction authoritative'

# Synthetic test data stays isolated and mirrors production type contracts; local approved mutations execute in sandbox while external writes remain blocked.
need "$FIX" 'cortex_robot_test|robot_fixture' 'journeys use disposable synthetic fixtures'
need "$FIX" 'memory\(db,"AUDIO","robot_fixture","Voice note' 'voice fixture uses production AUDIO type'
need "$FIX" 'Robot Test Person' 'fixture seeds known person'
need "$FIX" 'Robot Test Project' 'fixture seeds known project'
need "$FIX" 'HealthStore\.addMetric' 'fixture seeds known health measurements'
need "$DISPATCH" 'CortexExperimentalTestMode\.active' 'dispatcher has explicit experimental sandbox behavior'
need "$DISPATCH" 'localType\(x\.type\).*createLocal' 'approved local Cortex actions execute in sandbox'
need "$DISPATCH" 'Experimental test intercepted external action safely' 'external mutations remain intercepted'

# Accessibility is only the test hand. The report/export naming must make the new purpose unmistakable.
need "$ACCESS" 'robotClick' 'suite can operate the same visible controls as a user'
need "$ACCESS" 'robotSetText' 'suite can enter known test data through visible fields'
need "$EXPORT" 'CortexExperimentalUserTest_.*\.md' 'goal-driven suite exports Markdown problem report'
need "$EXPORT" 'CortexExperimentalUserTest_.*\.json' 'goal-driven suite exports JSON evidence'
need "$EXPORT" 'CortexExperimentalUserTest_.*\.zip' 'goal-driven suite exports ZIP bundle'
need "$EXPORT" 'Downloads/Cortex/AutoTests/UserJourneys' 'reports have a dedicated UserJourneys destination'
need "$EXPORT" 'CortexExperimentalUserSuite\.run' 'exporter runs goal-driven suite directly'
need "$COMPAT" 'CortexUserJourneyTestExporter\.runAndShare' 'legacy diagnostics compatibility entry delegates to goal-driven suite'

if [ "$FAIL" -ne 0 ]; then
  printf 'CORTEX_USER_JOURNEY_AUDIT=FAIL (%s failure(s))\n' "$FAIL" >&2
  exit 2
fi
printf 'CORTEX_USER_JOURNEY_AUDIT=PASS\n'
