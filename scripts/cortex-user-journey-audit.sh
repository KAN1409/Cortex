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
AUTOTEST="app/src/main/java/com/kareem/cortex/CortexAutoTestSuite.java"
SYN_AUDIO="app/src/main/java/com/kareem/cortex/SyntheticAudioFixture.java"
AUDIO_ANALYZER="app/src/main/java/com/kareem/cortex/AudioAnalyzer.java"
ANALYSIS_QUEUE="app/src/main/java/com/kareem/cortex/AnalysisQueue.java"
CAPTURE="app/src/main/java/com/kareem/cortex/CaptureActivity.java"
VISUAL_UI="app/src/main/java/com/kareem/cortex/VisualIntelligenceActivity.java"
VISUAL_POLICY="app/src/main/java/com/kareem/cortex/VisualFailurePolicy.java"
VISUAL_RECOVERY="app/src/main/java/com/kareem/cortex/VisualRecoveryStore.java"
ACCESS="app/src/main/java/com/kareem/cortex/CortexScreenAccessibilityService.java"
ROBOT="app/src/main/java/com/kareem/cortex/CortexRobotUserTest.java"
SEMANTIC="app/src/main/java/com/kareem/cortex/CortexSemanticOperation.java"
BRAIN_UI="app/src/main/java/com/kareem/cortex/ProposalAskCortexActivity.java"
DISPATCH="app/src/main/java/com/kareem/cortex/CortexActionDispatcher.java"
BRAIN="app/src/main/java/com/kareem/cortex/BrainRouter.java"
ONBOARD="app/src/main/java/com/kareem/cortex/AccessOnboardingActivity.java"
HEALTH="app/src/main/java/com/kareem/cortex/HealthFollowupActivity.java"
TRANSCRIPT="app/src/main/java/com/kareem/cortex/TranscriptCorrectionStore.java"

for f in "$SUITE" "$EXPORT" "$COMPAT" "$FIX" "$AUTOTEST" "$SYN_AUDIO" "$AUDIO_ANALYZER" "$ANALYSIS_QUEUE" "$CAPTURE" "$VISUAL_UI" "$VISUAL_POLICY" "$VISUAL_RECOVERY" "$ACCESS" "$ROBOT" "$SEMANTIC" "$BRAIN_UI" "$DISPATCH" "$BRAIN" "$ONBOARD" "$HEALTH" "$TRANSCRIPT"; do need_file "$f"; done

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

# Brain is judged only after the semantic operation triggered by Ask reaches a terminal outcome.
# Progress text remains useful UI evidence, but it is never the success oracle.
need "$SUITE" 'journeyBrainTerminal' 'suite has a dedicated Brain terminal-answer journey'
need "$SUITE" 'CortexSemanticOperation\.cursor\(\)' 'Brain journey captures a pre-action semantic operation cursor'
need "$SUITE" 'waitSemantic\(cursor,32_000L\)' 'Brain journey has an explicit bounded semantic terminal wait'
need "$SUITE" 'op\.terminal\(\)' 'Brain journey requires a terminal semantic operation'
need "$SUITE" 'op\.success\(\)' 'Brain journey distinguishes successful terminal completion from failure/timeout'
need "$SUITE" 'remained RUNNING past its terminal budget' 'Brain latency/stuck state is reported separately'
need "$SUITE" 'semantic operation ended' 'Brain journey recognizes explicit terminal failure'
need "$SUITE" 'visible Brain surface still shows a busy/progress state' 'Brain journey detects UI that remains busy after semantic completion'
need "$SUITE" 'ignored known ACTION/WAITING fixture state' 'terminal but ungrounded Brain answer is a confirmed functional bug'
need "$SEMANTIC" 'RUNNING="RUNNING",COMPLETED="COMPLETED",FAILED="FAILED",TIMEOUT="TIMEOUT",CANCELLED="CANCELLED"' 'semantic operation ledger defines explicit terminal states'
need "$BRAIN_UI" 'CortexSemanticOperation\.begin\("BRAIN_ANSWER"' 'visible Brain surface registers BRAIN_ANSWER work'
need "$BRAIN_UI" 'CortexSemanticOperation\.complete\(op,"ANSWER_READY' 'Brain surface marks answer-ready only after result rendering'
need "$BRAIN_UI" 'CortexSemanticOperation\.fail' 'Brain surface records terminal failures explicitly'
need "$ROBOT" 'schema_version",3' 'low-level Robot report schema records semantic-terminal generation'
need "$ROBOT" 'observeSemantic\(semanticCursor,deadline\)' 'Robot waits for semantic completion caused by the clicked action'
need "$ROBOT" 'FAILED_RESULT|terminalState|terminal_kind' 'Robot distinguishes click success from functional terminal failure'

# Capture/ASR terminal semantics: generic automation must never open the real microphone just to test Cortex.
# A real WAV is generated in the sandbox, then the normal AUDIO persistence/queue/post-analysis path is used.
# Only the external provider response is deterministic, explicitly marked as NOT testing live ASR quality.
need "$SYN_AUDIO" 'CortexExperimentalTestMode\.active' 'synthetic WAV generation is hard-gated to explicit experimental mode'
need "$SYN_AUDIO" 'robot_synthetic_voice\.wav' 'generic voice fixture creates a real WAV file'
need "$SYN_AUDIO" 'live_provider_tested",false' 'synthetic fixture cannot masquerade as live-provider validation'
need "$SYN_AUDIO" 'synthetic_asr_fixture' 'synthetic audio carries an explicit provider-bypass marker'
need "$AUDIO_ANALYZER" 'CortexExperimentalTestMode\.active\(ctx\).*SyntheticAudioFixture\.matches\(item\)' 'AudioAnalyzer accepts deterministic provider output only behind both sandbox guards'
need "$AUDIO_ANALYZER" 'cortex_deterministic_asr_fixture' 'deterministic ASR result is explicitly identifiable in diagnostics'
need "$AUDIO_ANALYZER" 'live_provider_tested",false' 'deterministic ASR result declares that provider quality was not tested'
need "$CAPTURE" '"synthetic_voice"\.equals\(mode\).*beginSyntheticVoiceFixture' 'capture exposes a hidden test-only synthetic voice route'
need "$CAPTURE" 'CortexSemanticOperation\.begin\("CAPTURE_ASR"' 'real/synthetic voice capture registers semantic ASR work'
need "$CAPTURE" 'AnalysisQueue\.trackSemantic\(itemId' 'voice evidence token is attached before analysis is kicked'
need "$ANALYSIS_QUEUE" 'trackSemantic\(long itemId,long token\)' 'analysis queue owns item-to-operation terminal linkage'
need "$ANALYSIS_QUEUE" 'TRANSCRIPT_READY' 'audio completion is emitted only after transcript persistence'
need "$ANALYSIS_QUEUE" 'semanticTimeout\(item\.id' 'audio/visual watchdog timeout closes semantic work explicitly'
need "$SEMANTIC" 'contains\("ASR"\).*245_000L' 'ASR semantic budget sits above the 240-second queue watchdog'
need "$AUTOTEST" 'audio\.synthetic_contract' 'deterministic AutoTest verifies real-WAV post-provider contract without inflating user-journey PASS counts'
need "$AUTOTEST" 'live_provider_tested' 'AutoTest explicitly verifies live ASR provider quality was not claimed'

# Manual Visual understanding and background Visual recovery must share the same failure truth.
need "$VISUAL_POLICY" 'MAX_TRANSIENT_ATTEMPTS=3' 'visual transient retries are bounded'
need "$VISUAL_RECOVERY" 'record\(' 'visual recovery state is persisted'
need "$VISUAL_UI" 'CortexSemanticOperation\.begin\("VISUAL_UNDERSTAND"' 'manual visual understanding starts semantic work'
need "$VISUAL_UI" 'VisualRecoveryStore\.record' 'manual visual failures enter the shared recovery ledger'
need "$VISUAL_UI" 'VisualFailurePolicy\.classify' 'manual visual failures use the shared classification policy'
need "$VISUAL_UI" 'retry_wait' 'recoverable manual visual failure is distinct from terminal failure'
need "$VISUAL_UI" 'VisualIntelligenceScheduler\.continueChain' 'recoverable visual failure schedules bounded retry work'
need "$VISUAL_UI" 'VisualRecoveryStore\.clear.*CortexSemanticOperation\.complete|CortexSemanticOperation\.complete.*VISUAL_READY' 'visual success reaches terminal only around persisted ready state'
need "$VISUAL_UI" 'VISUAL_FAILED' 'manual visual terminal failure is explicit'
need "$SEMANTIC" 'contains\("VISUAL"\).*155_000L' 'visual semantic budget sits above the 150-second queue/manual watchdog'

# Combined state questions use authoritative local Cortex state rather than discarding derived state in a cloud prompt.
need "$BRAIN" 'AskOperationalEngine\.tryAnswer' 'Combined Brain checks authoritative operational state first'
need "$BRAIN" 'cortex-operational-combined' 'Combined operational state has an explicit local provider result'
need "$BRAIN" 'no operational private state sent to cloud' 'operational fast path documents the privacy boundary'
need "$BRAIN" 'operational_fast_path' 'operational fast path is visible in job diagnostics'
need "$BRAIN" 'actions_deferred.*true' 'Brain first answer does not wait for next-move generation'

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
need "$HEALTH" 'registerForActivityResult' 'Health UI delegates permission ownership through a lifecycle-safe Activity Result contract'
need "$HEALTH" 'HealthPermissionsRequestContract\(HealthConnectBridge\.PROVIDER\)' 'Health UI uses the Health Connect permission contract'
need "$HEALTH" 'healthPermissionLauncher\.launch\(HealthConnectBridge\.requiredReadPermissions\(\)\)' 'Health UI requests the exact required read scopes'
need "$HEALTH" 'CortexSemanticOperation\.begin\("HEALTH_SYNC"' 'manual Health sync has semantic terminal tracking'

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
need "$ACCESS" 'pruneDerivedAncestors' 'robot accessibility removes synthesized whole-container actions'
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
