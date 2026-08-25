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

# Scan every tracked source/configuration file. Gradle remains the authoritative syntax/type/resource compiler.
while IFS= read -r f; do
  case "$f" in
    *.java|*.kt|*.kts|*.xml|*.gradle|*.properties|*.sh|*.yml|*.yaml|*.json|*.md)
      SCANNED=$((SCANNED+1))
      # Start/end markers are sufficient to prove a real unresolved merge and avoid false positives from Markdown separators.
      if grep -nE '^(<<<<<<< |>>>>>>> )' "$f" >/dev/null 2>&1; then bad "merge-conflict marker in $f"; fi
      ;;
  esac
done < <(git ls-files)
ok "scanned $SCANNED tracked source/config files"

# Known high-risk regression: emergency restore stub must never return to release source.
BI="app/src/main/java/com/kareem/cortex/BackupImporter.java"
require_file "$BI"
require_text "$BI" 'public static final class Inspection' 'BackupImporter has real Inspection preflight model'
require_text "$BI" 'public static Inspection inspect\(Context [A-Za-z]+,Uri [A-Za-z]+\)' 'BackupImporter exposes read-only inspect API'
require_text "$BI" 'public static int restore\(Context [A-Za-z]+,VaultDb [A-Za-z]+,Uri [A-Za-z]+\)' 'BackupImporter exposes validated restore API'
if grep -Fq 'Valid Cortex backup archive verified.' "$BI" 2>/dev/null; then bad "BackupImporter emergency stub text detected"; fi
require_text "$BI" 'memories\.jsonl' 'BackupImporter validates Cortex memory payload'
require_text "$BI" 'validatedName\(' 'BackupImporter validates archive entry paths'

# Final PRIME result/proposal fabric must be present as actual routable surfaces.
for f in \
  app/src/main/java/com/kareem/cortex/ResultProposalEngine.java \
  app/src/main/java/com/kareem/cortex/ProposalUi.java \
  app/src/main/java/com/kareem/cortex/ProposalCaptureActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalBriefActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalPeopleProjectsActivity.java \
  app/src/main/java/com/kareem/cortex/ProposalAskCortexActivity.java; do require_file "$f"; done

require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'ExternalBrainProvider\.ask' 'proposal engine can use configured external reasoning model'
require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'LocalLlmBridge\.completeCached' 'proposal engine has local/private model fallback'
require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'Zero proposals is allowed' 'proposal prompt permits no-op instead of fake suggestions'
require_text app/src/main/java/com/kareem/cortex/ProposalBriefActivity.java 'ProposalUi\.attach' 'Brief results are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalPeopleProjectsActivity.java 'ProposalUi\.attach' 'People/Projects results are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalAskCortexActivity.java 'ProposalUi\.attach' 'Brain answers are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'ProposalUi\.attach' 'Capture results are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'hideLegacySuggestions' 'fixed legacy pseudo-suggestions are removed from final capture result'

# Manifest routing: exactly one launcher and one Android share owner.
MAN="app/src/main/AndroidManifest.xml"
require_file "$MAN"
launcher_count="$(grep -o 'android.intent.action.MAIN' "$MAN" | wc -l | tr -d ' ')"
[ "$launcher_count" = "1" ] && ok "exactly one launcher intent" || bad "expected 1 launcher intent, found $launcher_count"
send_count="$(grep -o 'android.intent.action.SEND"' "$MAN" | wc -l | tr -d ' ')"
send_multi_count="$(grep -o 'android.intent.action.SEND_MULTIPLE' "$MAN" | wc -l | tr -d ' ')"
[ "$send_count" = "1" ] && ok "exactly one ACTION_SEND owner" || bad "expected 1 ACTION_SEND owner, found $send_count"
[ "$send_multi_count" = "1" ] && ok "exactly one ACTION_SEND_MULTIPLE owner" || bad "expected 1 ACTION_SEND_MULTIPLE owner, found $send_multi_count"
require_text "$MAN" 'activity android:name="\.ProposalCaptureActivity" android:exported="true"' 'proposal-aware capture owns external share entry'
require_text "$MAN" 'activity-alias android:name="\.PremiumHomeActivity" android:targetActivity="\.ProposalBriefActivity"' 'legacy Brief alias routes to final proposal Brief'

# Every capture entry point must converge on the same proposal-aware pipeline.
require_text app/src/main/java/com/kareem/cortex/InputActivity.java 'ProposalCaptureActivity\.class' 'Input capture routes to proposal-aware capture'
require_text app/src/main/java/com/kareem/cortex/CortexQuickTileService.java 'ProposalCaptureActivity\.class' 'voice Quick Tile routes to proposal-aware capture'
require_text app/src/main/java/com/kareem/cortex/UnderstandScreenTileService.java 'ProposalCaptureResultActivity\.class' 'Understand Screen opens proposal-aware result'
require_text app/src/main/java/com/kareem/cortex/CortexRecordWidget.java 'ProposalCaptureActivity\.class' 'record widget setup routes to proposal-aware capture'

# PRIME navigation must point to proposal-aware result surfaces.
UI="app/src/main/java/com/kareem/cortex/CortexUi.java"
require_text "$UI" 'ProposalBriefActivity\.class' 'bottom nav routes to proposal Brief'
require_text "$UI" 'ProposalPeopleProjectsActivity\.class' 'bottom nav routes to proposal People/Projects'
require_text "$UI" 'ProposalAskCortexActivity\.class' 'bottom nav routes to proposal Brain'

# The capability contract is intentionally exact: 1..43 once each in the authoritative all() list.
CAP="app/src/main/java/com/kareem/cortex/CortexCapabilityRegistry.java"
require_file "$CAP"
cap_count="$(grep -oE 'c\([0-9]+,"' "$CAP" | wc -l | tr -d ' ')"
[ "$cap_count" = "43" ] && ok "authoritative capability registry still has exactly 43 entries" || bad "capability registry expected 43 entries, found $cap_count"
for n in 1 43; do grep -Fq "c($n," "$CAP" && : || bad "capability #$n missing"; done

# Privacy / execution trust invariants.
require_text app/src/main/java/com/kareem/cortex/ProposalUi.java 'CloudEvidencePolicy\.canSend' 'proposal cloud routing respects source privacy policy'
require_text app/src/main/java/com/kareem/cortex/CortexActionDispatcher.java 'CALENDAR_RESCHEDULE' 'dispatcher handles calendar reschedule explicitly'
require_text app/src/main/java/com/kareem/cortex/CortexActionExecutor.java 'Calendar app owns the final write' 'external calendar mutation remains user-confirmed draft'
require_text app/src/main/java/com/kareem/cortex/BrainRouter.java 'CloudEvidencePolicy\.filter' 'Combined Brain still filters cloud evidence locally'

# UI semantic color invariants: primary != recording signal.
require_text "$UI" 'ACCENT = Color\.rgb\(126,158,255\)' 'icy-blue primary accent is centralized'
require_text "$UI" 'SIGNAL = Color\.rgb\(255,82,76\)' 'coral signal color is centralized separately'
require_text app/src/main/java/com/kareem/cortex/SatinCaptureActivity.java 'setAccent\(CortexUi\.SIGNAL\)' 'recording STOP ring uses signal color'

# Non-fatal hygiene report. These may be intentional diagnostics or historical code paths.
placeholder_hits="$(git grep -nEi '\b(TODO|FIXME|temporary stub|placeholder implementation)\b' -- '*.java' '*.kt' '*.xml' '*.gradle' '*.sh' 2>/dev/null | wc -l | tr -d ' ' || true)"
[ "$placeholder_hits" = "0" ] || warn "$placeholder_hits TODO/FIXME/placeholder source hit(s) require human context review"

printf '%s\n' '-----------------------------------------------------'
printf 'Files scanned: %s  Warnings: %s  Failures: %s\n' "$SCANNED" "$WARN" "$FAIL"
if [ "$FAIL" -ne 0 ]; then
  printf 'CORTEX_REPO_AUDIT=FAIL\n' >&2
  exit 2
fi
printf 'CORTEX_REPO_AUDIT=PASS\n'
printf '%s\n\n' '====================================================='
