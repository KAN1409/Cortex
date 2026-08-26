#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$ROOT"
FAIL=0
ok(){ printf 'HEALTH BRANCH AUDIT PASS: %s\n' "$*"; }
bad(){ FAIL=$((FAIL+1)); printf 'HEALTH BRANCH AUDIT FAIL: %s\n' "$*" >&2; }
need_file(){ [ -f "$1" ] && ok "$1 present" || bad "$1 missing"; }
need(){ local f="$1" p="$2" label="$3"; grep -Eq "$p" "$f" 2>/dev/null && ok "$label" || bad "$label"; }
forbid(){ local f="$1" p="$2" label="$3"; if grep -Eq "$p" "$f" 2>/dev/null; then bad "$label"; else ok "$label"; fi; }

MAN="app/src/main/AndroidManifest.xml"
INPUT="app/src/main/java/com/kareem/cortex/InputActivity.java"
ONBOARD="app/src/main/java/com/kareem/cortex/AccessOnboardingActivity.java"
ACCESS="app/src/main/java/com/kareem/cortex/AccessGateRegistry.java"
PROVIDER="app/src/main/java/com/kareem/cortex/ExternalBrainProvider.java"
BRAIN="app/src/main/java/com/kareem/cortex/BrainRouter.java"
PUI="app/src/main/java/com/kareem/cortex/ProposalUi.java"
RDEBUG="app/src/main/java/com/kareem/cortex/ReliableDebugExporter.java"
ENV="app/src/main/java/com/kareem/cortex/EnvironmentActivity.java"
TEXTUI="app/src/main/java/com/kareem/cortex/CortexTextUi.java"
BIDI="app/src/main/java/com/kareem/cortex/MixedBidiText.java"
AUTOTEST="app/src/main/java/com/kareem/cortex/CortexAutoTestSuite.java"
AUTOEXPORT="app/src/main/java/com/kareem/cortex/CortexAutoTestExporter.java"
SYN_AUDIO="app/src/main/java/com/kareem/cortex/SyntheticAudioFixture.java"
AUDIO_ANALYZER="app/src/main/java/com/kareem/cortex/AudioAnalyzer.java"
HEALTHUI="app/src/main/java/com/kareem/cortex/HealthFollowupActivity.java"
HEALTHBRIDGE="app/src/main/java/com/kareem/cortex/HealthConnectBridge.kt"
HEALTHRESULT="app/src/main/java/com/kareem/cortex/HealthSyncResult.java"
SEMANTIC="app/src/main/java/com/kareem/cortex/CortexSemanticOperation.java"
ROBOTMODE="app/src/main/java/com/kareem/cortex/CortexExperimentalTestMode.java"
ROBOTFIX="app/src/main/java/com/kareem/cortex/CortexRobotFixtures.java"
ROBOT="app/src/main/java/com/kareem/cortex/CortexRobotUserTest.java"
ROBOTEXPORT="app/src/main/java/com/kareem/cortex/CortexRobotTestExporter.java"
ACCESSIBILITY="app/src/main/java/com/kareem/cortex/CortexScreenAccessibilityService.java"
VAULT="app/src/main/java/com/kareem/cortex/VaultDb.java"

for f in "$ONBOARD" "$ACCESS" "$PROVIDER" "$BRAIN" "$PUI" "$RDEBUG" "$ENV" "$TEXTUI" "$BIDI" "$AUTOTEST" "$AUTOEXPORT" "$SYN_AUDIO" "$AUDIO_ANALYZER" "$HEALTHUI" "$HEALTHBRIDGE" "$HEALTHRESULT" "$SEMANTIC" "$ROBOTMODE" "$ROBOTFIX" "$ROBOT" "$ROBOTEXPORT" "$ACCESSIBILITY" "$VAULT"; do need_file "$f"; done

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

# Provider rate-limit recovery must avoid repeatedly hitting the same upstream shared pool.
need "$PROVIDER" 'OPENROUTER_RATE_LIMIT_COOLDOWN_MS' 'OpenRouter rate-limit cooldown is defined'
need "$PROVIDER" 'rateLimited\(\).*markOpenRouterCooldown' 'HTTP 429 marks OpenRouter cooling down'
need "$PROVIDER" 'GeminiKeyStore.*openRouterCoolingDown\(context\)|openRouterCoolingDown\(context\).*GeminiKeyStore' 'cooldown can route directly to Gemini fallback'
need "$PROVIDER" 'clearOpenRouterCooldown' 'successful OpenRouter recovery clears cooldown'
need "$RDEBUG" 'brain_health_primary' 'debug export retains primary provider health result'
need "$RDEBUG" 'brain_failover_active' 'debug export reports effective Gemini failover state'

# Fast Answer First: attached captures avoid unnecessary broad retrieval; generic external questions do too.
# The first provider call answers only the question. ProposalUi owns useful next moves after answer render.
need "$BRAIN" 'fastFocal=true' 'Brain has explicit fast focal route'
need "$BRAIN" '!needsBroadContext\(question\)' 'fast focal route is limited to direct capture questions'
need "$BRAIN" 'fastGeneral=true' 'Combined has a generic fast route when broad Cortex history is unnecessary'
need "$BRAIN" 'broadRetrieval=combined&&needsBroadContext\(question\)' 'broad Cortex retrieval is intent-gated'
need "$BRAIN" 'String modelQuestion=question' 'first provider request answers the actual question only'
forbid "$BRAIN" 'String modelQuestion=.*BrainActionStore\.request' 'structured-action JSON cannot block first-answer readiness'
need "$BRAIN" 'actions_deferred.*true' 'Brain diagnostics state that next moves are deferred'
need "$BRAIN" 'broad retrieval and next-move generation do not block answer readiness' 'fast focal route records independent enrichment explicitly'
need "$PUI" 'target\.sourceItemId>0' 'attached proposal opens Brain with authoritative source item id'
need "$PUI" 'Do not duplicate its OCR/transcript' 'attached evidence is not duplicated into the visible Brain prompt'

# Health Connect sync has a real terminal semantic contract; installed/pressed is not success.
need "$HEALTHRESULT" 'SUCCESS.*NEEDS_ACCESS.*UNAVAILABLE.*UPDATE_REQUIRED.*ERROR' 'Health sync result has structured terminal states'
need "$HEALTHBRIDGE" 'syncRecentDetailed' 'Health Connect exposes detailed structured sync result'
need "$HEALTHUI" 'CortexSemanticOperation\.begin\("HEALTH_SYNC"' 'visible Health sync starts a semantic operation'
need "$HEALTHUI" 'result\.success\(\).*CortexSemanticOperation\.complete' 'Health sync success closes only from structured successful result'
need "$HEALTHUI" 'CortexSemanticOperation\.fail' 'Health sync failures close explicitly'
need "$HEALTHUI" 'Partial read:.*run not marked successful' 'partial Health reads cannot be presented as full success'
need "$SEMANTIC" 'contains\("HEALTH"\).*50_000L' 'Health semantic operation has bounded terminal budget'

# Arabic-dominant content must stay RTL even when a line begins with an English drug/model/dose token.
need "$TEXTUI" 'TEXT_DIRECTION_RTL' 'Arabic-dominant TextViews use forced RTL paragraph direction'
need "$TEXTUI" 'TEXT_ALIGNMENT_VIEW_END' 'Arabic-dominant TextViews align to the right/end edge'
need "$BIDI" 'documentRtl=isArabicDominant\(clean\)' 'mixed bidi formatting derives a document-level Arabic base'
need "$BIDI" 'baseRtl=documentRtl\|\|lineHasArabic' 'Arabic document/line forces RTL while embedded Latin runs remain isolated'

# Useful Next Moves must always resolve to proposals, a no-op, or a recoverable error; never infinite loading.
need "$PUI" 'UI_TIMEOUT_MS=45_000L' 'proposal UI has bounded loading watchdog'
need "$PUI" 'Suggestions are taking too long' 'proposal timeout has explicit recovery state'
need "$PUI" 'Retry suggestions' 'proposal failures expose retry'
need "$PUI" 'ResultProposalEngine\.invalidate' 'proposal retry invalidates stale cache before fresh request'

# Debug export must create an artifact even when exhaustive diagnostics partially fail or are unsafe for the current heap.
need "$RDEBUG" 'DebugExporter\.build' 'reliable exporter can attempt exhaustive package'
need "$RDEBUG" 'exhaustiveRisk' 'debug exporter performs memory/database preflight'
need "$RDEBUG" 'MAX_DB_FOR_EXHAUSTIVE' 'debug exporter bounds exhaustive DB size'
need "$RDEBUG" 'MIN_HEAP_HEADROOM_FOR_EXHAUSTIVE' 'debug exporter requires safe heap headroom'
need "$RDEBUG" 'FullExportSkipped' 'unsafe exhaustive export falls back without OOM'
need "$RDEBUG" 'buildRecovery' 'reliable exporter has recovery package fallback'
need "$RDEBUG" 'MediaStore\.Downloads' 'debug package is copied to Downloads on modern Android'
need "$RDEBUG" 'Environment\.DIRECTORY_DOWNLOADS\+"/Cortex"' 'debug package has known Downloads/Cortex destination'
need "$RDEBUG" 'ClipData\.newRawUri' 'debug share carries ClipData URI grant'
need "$RDEBUG" 'grantUriPermission' 'debug share explicitly grants target apps read access'
need "$ENV" 'ReliableDebugExporter\.exportAndShare' 'Advanced diagnostics uses reliable debug exporter'

# Complete automatic verification must cover the authoritative matrix plus deterministic fixtures and readable reports.
need "$AUTOTEST" 'CortexCapabilityRegistry\.all' 'automatic verification walks all authoritative capabilities'
need "$AUTOTEST" 'health\.metric_roundtrip' 'automatic verification includes synthetic health metric round-trip'
need "$AUTOTEST" 'transcript\.manual_override' 'automatic verification includes transcript correction authority test'
need "$AUTOTEST" 'audio\.synthetic_contract' 'automatic verification includes deterministic real-WAV ASR pipeline contract'
need "$AUTOTEST" 'live_provider_tested' 'synthetic audio verification cannot masquerade as provider-quality validation'
need "$SYN_AUDIO" 'robot_synthetic_voice\.wav' 'synthetic audio fixture creates a real WAV'
need "$SYN_AUDIO" 'live_provider_tested",false' 'synthetic audio metadata labels provider quality as untested'
need "$AUDIO_ANALYZER" 'CortexExperimentalTestMode\.active\(ctx\).*SyntheticAudioFixture\.matches\(item\)' 'deterministic ASR bypass requires both sandbox mode and exact fixture marker'
need "$AUTOTEST" 'proposal\.parser' 'automatic verification tests structured proposal parsing'
need "$AUTOTEST" 'rtl\.arabic_dominant' 'automatic verification tests Arabic-dominant mixed bidi behavior'
need "$AUTOEXPORT" 'CortexAutoTest_.*\.md' 'automatic verification exports Markdown report'
need "$AUTOEXPORT" 'CortexAutoTest_.*\.json' 'automatic verification exports JSON report'
need "$ENV" 'CortexAutoTestExporter\.runAndShare' 'Advanced diagnostics exposes complete automatic verification'

# Legacy recursive crawler remains available only as low-level infrastructure while the primary goal-driven suite is audited separately.
need "$VAULT" 'cortex_robot_test\.db' 'experimental journeys use a dedicated sandbox Vault DB'
need "$ROBOTMODE" 'guardedLabel' 'experimental test mode retains side-effect guard classifier'
need "$ROBOTFIX" 'robot_fixture' 'experimental tests seed disposable synthetic fixtures'
need "$ROBOTFIX" 'deleteDatabase\(VaultDb\.robotDbName\(\)\)' 'experimental sandbox is deleted after/before test'
need "$ACCESSIBILITY" 'robotClickableNodes' 'Accessibility service exposes test-only clickable node inventory'
need "$ACCESSIBILITY" 'robotEditableNodes' 'Accessibility service exposes test-only editable fields'
need "$ACCESSIBILITY" 'robotSetText' 'experimental journeys can enter synthetic form data'
need "$ACCESSIBILITY" 'robotBack' 'test hand can backtrack across dialogs/system surfaces'
need "$ROBOT" 'MAX_STEPS=700' 'legacy crawler remains bounded when used for low-level exploration'
need "$ROBOT" 'MAX_SCREENS=240' 'legacy crawler retains bounded unique-screen budget'
need "$ROBOT" 'PRESSED_EXTERNAL' 'legacy crawler records external/system transitions without interacting there'
need "$ROBOT" 'GUARDED_PRIVACY' 'legacy crawler distinguishes privacy-sensitive guarded controls'
need "$ROBOTEXPORT" 'CortexUserJourneyTestExporter\.runAndShare' 'legacy diagnostics compatibility entry delegates to goal-driven user journeys'
need "$ENV" 'CortexRobotTestExporter\.runAndShare' 'Advanced diagnostics compatibility entry reaches goal-driven suite'

# Existing manual transcript correction behavior is part of the same runtime recovery contract.
need_file app/src/main/java/com/kareem/cortex/TranscriptCorrectionStore.java
need app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'Edit transcript' 'capture result exposes Edit transcript'
need app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'TranscriptCorrectionStore\.save' 'manual transcript correction is persisted'

if [ "$FAIL" -ne 0 ]; then
  printf 'CORTEX_HEALTH_FOLLOWUP_AUDIT=FAIL (%s failure(s))\n' "$FAIL" >&2
  exit 2
fi
printf 'CORTEX_HEALTH_FOLLOWUP_AUDIT=PASS\n'
