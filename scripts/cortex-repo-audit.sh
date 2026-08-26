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

printf '\n================ CORTEX REPO AUDIT ================\n'
printf 'Commit: %s\n' "$(git rev-parse --short=12 HEAD 2>/dev/null || echo unknown)"
printf 'Branch: %s\n' "$(git branch --show-current 2>/dev/null || echo detached)"

while IFS= read -r f; do
  case "$f" in
    *.java|*.kt|*.kts|*.xml|*.gradle|*.properties|*.sh|*.yml|*.yaml|*.json|*.md)
      SCANNED=$((SCANNED+1))
      if grep -nE '^(<<<<<<< |>>>>>>> )' "$f" >/dev/null 2>&1; then bad "merge-conflict marker in $f"; fi
      ;;
  esac
done < <(git ls-files)
ok "scanned $SCANNED tracked source/config files"

BI="app/src/main/java/com/kareem/cortex/BackupImporter.java"
require_file "$BI"
require_text "$BI" 'public static final class Inspection' 'BackupImporter has real Inspection preflight model'
require_text "$BI" 'public static Inspection inspect\(Context [A-Za-z]+,Uri [A-Za-z]+\)' 'BackupImporter exposes read-only inspect API'
require_text "$BI" 'public static int restore\(Context [A-Za-z]+,VaultDb [A-Za-z]+,Uri [A-Za-z]+\)' 'BackupImporter exposes validated restore API'
if grep -Fq 'Valid Cortex backup archive verified.' "$BI" 2>/dev/null; then bad "BackupImporter emergency stub text detected"; fi
require_text "$BI" 'memories\.jsonl' 'BackupImporter validates Cortex memory payload'
require_text "$BI" 'validatedName\(' 'BackupImporter validates archive entry paths'

for f in \
  app/src/main/java/com/kareem/cortex/ResultProposalEngine.java \
  app/src/main/java/com/kareem/cortex/ProposalUi.java \
  app/src/main/java/com/kareem/cortex/ProposalCaptureActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalBriefActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalPeopleProjectsActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalAskCortexActivity.java \
  app/src/main/java/com/kareem/cortex/CortexGlyphView.java \
  app/src/main/java/com/kareem/cortex/CortexTruthPolicy.java; do require_file "$f"; done

require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'ExternalBrainProvider\.ask' 'proposal engine can use configured external reasoning model'
require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'LocalLlmBridge\.completeCached' 'proposal engine has local/private model fallback'
require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'Zero proposals is allowed' 'proposal prompt permits no-op instead of fake suggestions'
require_text app/src/main/java/com/kareem/cortex/ProposalBriefActivity.java 'consequence-first' 'NOW is consequence-first rather than proposal-first'
if grep -Eq 'ProposalUi\.attach' app/src/main/java/com/kareem/cortex/ProposalBriefActivity.java; then bad "NOW must not generate per-card micro proposals"; else ok "NOW does not generate per-card micro proposals"; fi
require_text app/src/main/java/com/kareem/cortex/ProposalPeopleProjectsActivity.java 'ProposalUi\.attach' 'legacy People/Projects detail view retains proposal support outside primary navigation'
require_text app/src/main/java/com/kareem/cortex/ProposalAskCortexActivity.java 'ProposalUi\.attach' 'Brain answers are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'ProposalUi\.attach' 'Capture results are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'hideLegacySuggestions' 'fixed legacy pseudo-suggestions are removed from final capture result'
require_text app/src/main/java/com/kareem/cortex/CortexTruthPolicy.java 'confirmedDecision' 'product reset has a central user-decision truth gate'
require_text app/src/main/java/com/kareem/cortex/CortexTruthPolicy.java 'ambientContext' 'product reset has a central ambient-context truth gate'

# Capture correction + recoverable proposal contract.
TC="app/src/main/java/com/kareem/cortex/TranscriptCorrectionStore.java"
require_file "$TC"
require_text "$TC" 'item_text_corrections' 'manual transcript corrections preserve correction history'
require_text "$TC" 'keep_manual_transcript_correction' 'manual transcript override survives later analysis writes'
require_text "$TC" 'SemanticIndex\.indexItem' 'corrected transcript is re-indexed for retrieval'
require_text app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'Edit transcript' 'audio result exposes direct transcript editing'
require_text app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'TranscriptCorrectionStore\.save' 'transcript editor persists corrected evidence'
require_text app/src/main/java/com/kareem/cortex/ProposalUi.java 'Retry suggestions' 'proposal failures expose a retry action instead of a blank card'
require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'proposal-v3' 'proposal cache generation invalidates stale empty results'
require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'invalidateSource' 'proposal cache can be invalidated after corrected evidence'
require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'mo!=null&&recognized&&db!=null' 'malformed model responses are not cached as empty proposals'

MAN="app/src/main/AndroidManifest.xml"
require_file "$MAN"
launcher_count="$(grep -o 'android.intent.action.MAIN' "$MAN" | wc -l | tr -d ' ')"
[ "$launcher_count" = "1" ] && ok "exactly one launcher intent" || bad "expected 1 launcher intent, found $launcher_count"
send_count="$(grep -o 'android.intent.action.SEND"' "$MAN" | wc -l | tr -d ' ')"
send_multi_count="$(grep -o 'android.intent.action.SEND_MULTIPLE' "$MAN" | wc -l | tr -d ' ')"
[ "$send_count" = "1" ] && ok "exactly one ACTION_SEND owner" || bad "expected 1 ACTION_SEND owner, found $send_count"
[ "$send_multi_count" = "1" ] && ok "exactly one ACTION_SEND_MULTIPLE owner" || bad "expected 1 ACTION_SEND_MULTIPLE owner, found $send_multi_count"
require_text "$MAN" 'activity android:name="\.ProposalCaptureActivity" android:exported="true"' 'proposal-aware capture owns external share entry'
require_text "$MAN" 'activity-alias android:name="\.PremiumHomeActivity" android:targetActivity="\.ProposalBriefActivity"' 'legacy Brief alias routes to final NOW surface'

require_text app/src/main/java/com/kareem/cortex/InputActivity.java 'ProposalCaptureActivity\.class' 'Input capture routes to proposal-aware capture'
require_text app/src/main/java/com/kareem/cortex/CortexQuickTileService.java 'ProposalCaptureActivity\.class' 'voice Quick Tile routes to proposal-aware capture'
require_text app/src/main/java/com/kareem/cortex/UnderstandScreenTileService.java 'ProposalCaptureResultActivity\.class' 'Understand Screen opens proposal-aware result'
require_text app/src/main/java/com/kareem/cortex/CortexRecordWidget.java 'ProposalCaptureActivity\.class' 'record widget setup routes to proposal-aware capture'

UI="app/src/main/java/com/kareem/cortex/CortexUi.java"
require_text "$UI" '"brief","Now".*ProposalBriefActivity\.class' 'bottom nav routes to NOW'
require_text "$UI" '"input","Capture".*InputActivity\.class' 'bottom nav routes to Capture'
require_text "$UI" '"brain","Ask".*ProposalAskCortexActivity\.class' 'bottom nav routes to Ask'
require_text "$UI" '"history","History".*VaultActivity\.class' 'bottom nav routes to History instead of People/Projects'

CAP="app/src/main/java/com/kareem/cortex/CortexCapabilityRegistry.java"
require_file "$CAP"
cap_count="$(grep -oE 'c\([0-9]+,"' "$CAP" | wc -l | tr -d ' ')"
[ "$cap_count" = "43" ] && ok "authoritative capability registry still has exactly 43 entries" || bad "capability registry expected 43 entries, found $cap_count"
for n in 1 43; do grep -Fq "c($n," "$CAP" && : || bad "capability #$n missing"; done

require_text app/src/main/java/com/kareem/cortex/ProposalUi.java 'CloudEvidencePolicy\.canSend' 'proposal cloud routing respects source privacy policy'
require_text app/src/main/java/com/kareem/cortex/CortexActionDispatcher.java 'CALENDAR_RESCHEDULE' 'dispatcher handles calendar reschedule explicitly'
require_text app/src/main/java/com/kareem/cortex/CortexActionExecutor.java 'Calendar app owns the final write' 'external calendar mutation remains user-confirmed draft'
require_text app/src/main/java/com/kareem/cortex/BrainRouter.java 'CloudEvidencePolicy\.filter' 'Combined Brain still filters cloud evidence locally'

# Locked approved preview: matte graphite + red/orange/yellow/green, no purple/blue drift.
require_text "$UI" 'RED = Color\.rgb\(255,72,62\)' 'approved red signal is centralized'
require_text "$UI" 'ORANGE = Color\.rgb\(255,146,42\)' 'approved orange interaction color is centralized'
require_text "$UI" 'YELLOW = Color\.rgb\(241,188,52\)' 'approved yellow decision/attention color is centralized'
require_text "$UI" 'GREEN = Color\.rgb\(105,194,82\)' 'approved green useful/confirmed color is centralized'
require_text "$UI" 'VIOLET = YELLOW' 'legacy violet semantic cannot render purple'
require_text "$UI" 'public static GradientDrawable matte' 'matte surface helper is centralized'
require_text "$UI" 'public static GradientDrawable velvet' 'low-reflection depth helper is centralized'
require_text "$UI" 'public static <T extends View> T raised' 'raised elevation helper is centralized'
require_text app/src/main/java/com/kareem/cortex/CortexGlyphView.java 'monoline white glyph' 'custom raised monoline icon language is present'
require_text app/src/main/java/com/kareem/cortex/ProposalBriefActivity.java 'CortexUi\.glyph' 'NOW uses shared custom icon plates'
require_text app/src/main/java/com/kareem/cortex/InputActivity.java 'CortexUi\.glyph' 'Input uses shared custom icon plates'
require_text app/src/main/java/com/kareem/cortex/ProposalPeopleProjectsActivity.java 'CortexUi\.glyph' 'People/Projects uses shared custom icon plates'
require_text app/src/main/java/com/kareem/cortex/ProposalAskCortexActivity.java 'CortexUi\.glyph' 'Brain uses shared custom icon plates'
require_text app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'CortexUi\.glyph' 'Capture result uses shared custom icon plates'
require_text app/src/main/java/com/kareem/cortex/SatinCaptureActivity.java 'setAccent\(CortexUi\.SIGNAL\)' 'recording STOP ring uses red signal color'
require_text app/src/main/java/com/kareem/cortex/CortexScrubberView.java 'CortexUi\.RED' 'waveform playback uses approved red signal family'
require_text app/src/main/java/com/kareem/cortex/CortexScrubberView.java 'Color\.rgb\(244,243,239\)' 'waveform playhead resolves toward neutral white, not purple'

if git grep -nEi '126,158,255|182,137,255|178,103,255|#7E9EFF|#B689FF|#B267FF' -- 'app/src/main/**' >/dev/null 2>&1; then bad "legacy blue/purple visual token detected in final app sources"; else ok "no legacy blue/purple visual token in final app sources"; fi

# Tactile interaction contract.
require_file app/src/main/java/com/kareem/cortex/CortexHaptics.java
require_text app/src/main/java/com/kareem/cortex/CortexHaptics.java 'performHapticFeedback' 'central haptic helper respects Android haptic feedback system'
require_text "$UI" 'CortexHaptics\.press' 'shared pressable/navigation layer emits tactile feedback'
require_text app/src/main/java/com/kareem/cortex/CortexRingButton.java 'CortexHaptics\.(confirm|press)' 'Cortex ring/orb controls emit tactile feedback'

# Health Follow-up branch contract: read-only Health Connect + evidence provenance + no fake Huawei connection.
for f in \
  app/src/main/java/com/kareem/cortex/HealthSchema.java \
  app/src/main/java/com/kareem/cortex/HealthStore.java \
  app/src/main/java/com/kareem/cortex/HealthConnectBridge.kt \
  app/src/main/java/com/kareem/cortex/HealthFollowupActivity.java \
  app/src/main/java/com/kareem/cortex/HealthPermissionsRationaleActivity.java; do require_file "$f"; done
require_text app/build.gradle 'androidx\.health\.connect:connect-client:1\.1\.0-alpha10' 'Health Connect compatibility client is pinned for SDK 35'
require_text app/build.gradle 'com\.google\.guava:guava:31\.1-android' 'WorkManager ListenableFuture API is present on Java compile classpath'
require_text "$MAN" 'android\.permission\.health\.READ_STEPS' 'Health Connect steps read scope declared'
require_text "$MAN" 'android\.permission\.health\.READ_HEART_RATE' 'Health Connect heart-rate read scope declared'
require_text "$MAN" 'android\.permission\.health\.READ_RESTING_HEART_RATE' 'Health Connect resting-heart-rate read scope declared'
require_text "$MAN" 'android\.permission\.health\.READ_SLEEP' 'Health Connect sleep read scope declared'
require_text "$MAN" 'android\.permission\.health\.READ_OXYGEN_SATURATION' 'Health Connect oxygen read scope declared'
require_text "$MAN" 'android\.permission\.health\.READ_WEIGHT' 'Health Connect weight read scope declared'
require_text "$MAN" 'HealthPermissionsRationaleActivity' 'Health Connect privacy rationale route is declared'
require_text app/src/main/java/com/kareem/cortex/HealthConnectBridge.kt 'dataOrigin\.packageName' 'Health metric provenance retains Health Connect data origin'
require_text app/src/main/java/com/kareem/cortex/HealthConnectBridge.kt 'syncRecent' 'Health Connect user-triggered recent sync is implemented'
require_text app/src/main/java/com/kareem/cortex/HealthStore.java 'linkKnowledgeEvidence' 'health imports link back to original Cortex evidence'
require_text app/src/main/java/com/kareem/cortex/CaptureActivity.java 'health_context' 'capture pipeline preserves health import context'
require_text app/src/main/java/com/kareem/cortex/HealthFollowupActivity.java 'HEALTH KIT SETUP|Health Kit' 'Huawei Health is represented as an explicit setup gate, not fake active data'
if grep -Fq 'READ_HEALTH_DATA_IN_BACKGROUND' "$MAN" || grep -Fq 'READ_HEALTH_DATA_HISTORY' "$MAN"; then bad "background/history Health Connect permission declared before corresponding behavior"; else ok "Health Connect keeps least privilege: no background/history read scope yet"; fi

# Unified phone environment access center and least-privilege permission boundary.
require_file app/src/main/java/com/kareem/cortex/AccessGateRegistry.java
require_text app/src/main/java/com/kareem/cortex/PhoneContextAccessActivity.java 'AccessGateRegistry\.snapshot' 'Access Center renders authoritative gate inventory'
require_text app/src/main/java/com/kareem/cortex/AccessGateRegistry.java 'notification_listener' 'notification-listener gate is inventoried'
require_text app/src/main/java/com/kareem/cortex/AccessGateRegistry.java 'accessibility' 'Accessibility gate is inventoried'
require_text app/src/main/java/com/kareem/cortex/AccessGateRegistry.java 'usage' 'Usage Access gate is inventoried'
require_text app/src/main/java/com/kareem/cortex/AccessGateRegistry.java 'microphone' 'microphone runtime gate is inventoried'
require_text app/src/main/java/com/kareem/cortex/AccessGateRegistry.java 'contacts' 'contacts read gate is inventoried'
require_text app/src/main/java/com/kareem/cortex/AccessGateRegistry.java 'calendar' 'calendar read gate is inventoried'
require_text app/src/main/java/com/kareem/cortex/AccessGateRegistry.java 'battery' 'background reliability gate is inventoried'
require_text app/src/main/java/com/kareem/cortex/AccessGateRegistry.java 'shizuku' 'optional Shizuku gate is inventoried'
if grep -Fq 'android.permission.WRITE_CALENDAR' "$MAN"; then bad "direct WRITE_CALENDAR permission conflicts with approval-first external draft architecture"; else ok "calendar access is read-only; external writes remain owning-app drafts"; fi
for p in 'android.permission.CAMERA' 'android.permission.MANAGE_EXTERNAL_STORAGE' 'android.permission.SYSTEM_ALERT_WINDOW' 'android.permission.SCHEDULE_EXACT_ALARM'; do if grep -Fq "$p" "$MAN"; then bad "unneeded privileged permission declared: $p"; else ok "unneeded privileged permission not declared: $p"; fi; done

placeholder_hits="$(git grep -nEi '\b(TODO|FIXME|temporary stub|placeholder implementation)\b' -- '*.java' '*.kt' '*.xml' '*.gradle' '*.sh' 2>/dev/null | wc -l | tr -d ' ' || true)"
[ "$placeholder_hits" = "0" ] || warn "$placeholder_hits TODO/FIXME/placeholder source hit(s) require human context review"

printf '%s\n' '-----------------------------------------------------'
printf 'Files scanned: %s  Warnings: %s  Failures: %s\n' "$SCANNED" "$WARN" "$FAIL"
if [ "$FAIL" -ne 0 ]; then printf 'CORTEX_REPO_AUDIT=FAIL\n' >&2;exit 2;fi
printf 'CORTEX_REPO_AUDIT=PASS\n'
printf '%s\n\n' '====================================================='
