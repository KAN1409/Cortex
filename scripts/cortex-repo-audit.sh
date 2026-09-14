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
    *.java|*.kt|*.kts|*.xml|*.gradle|*.properties|*.sh|*.py|*.yml|*.yaml|*.json|*.md)
      SCANNED=$((SCANNED+1))
      if grep -nE '^(<<<<<<< |>>>>>>> )' "$f" >/dev/null 2>&1; then bad "merge-conflict marker in $f"; fi
      ;;
  esac
done < <(git ls-files)
ok "scanned $SCANNED tracked source/config files"

# Product identity, canonical navigation, release identity, signing, legacy aliases and
# production-vs-diagnostic surface boundaries have one canonical verifier. Do not duplicate
# those contracts here; duplicated grep contracts previously drifted behind the product shell.
if python3 tools/verify_product_contracts.py; then
  ok "canonical product contracts"
else
  bad "canonical product contracts"
fi

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
  app/src/main/java/com/kareem/cortex/CortexDestinationRegistry.java; do require_file "$f"; done

require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'ExternalBrainProvider\.ask' 'proposal engine can use configured external reasoning model'
require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'LocalLlmBridge\.completeCached' 'proposal engine has local/private model fallback'
require_text app/src/main/java/com/kareem/cortex/ResultProposalEngine.java 'Zero proposals is allowed' 'proposal prompt permits no-op instead of fake suggestions'
require_text app/src/main/java/com/kareem/cortex/ProposalBriefActivity.java 'ProposalUi\.attach' 'Brief results are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalPeopleProjectsActivity.java 'ProposalUi\.attach' 'People/Projects results are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalAskCortexActivity.java 'ProposalUi\.attach' 'Brain answers are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'ProposalUi\.attach' 'Capture results are wired to micro proposals'
require_text app/src/main/java/com/kareem/cortex/ProposalCaptureResultActivity.java 'hideLegacySuggestions' 'fixed legacy pseudo-suggestions are removed from final capture result'

REG="app/src/main/java/com/kareem/cortex/CortexDestinationRegistry.java"
require_text "$REG" 'public static final String NOW = "NOW"' 'canonical NOW destination exists'
require_text "$REG" 'public static final String WORK = "WORK"' 'canonical WORK destination exists'
require_text "$REG" 'public static final String MEMORY = "MEMORY"' 'canonical MEMORY destination exists'
require_text "$REG" 'public static final String CAPTURE = "CAPTURE"' 'canonical CAPTURE destination exists'
require_text "$REG" 'primary\.size\(\) != 4' 'registry enforces exactly four primary destinations'
UI="app/src/main/java/com/kareem/cortex/CortexUi.java"
require_text "$UI" 'CortexDestinationRegistry\.NOW' 'bottom nav uses canonical NOW destination'
require_text "$UI" 'CortexDestinationRegistry\.MEMORY' 'bottom nav uses canonical MEMORY destination'
require_text "$UI" 'CortexDestinationRegistry\.WORK' 'bottom nav uses canonical WORK destination'
require_text "$UI" 'CortexDestinationRegistry\.CAPTURE' 'bottom nav uses canonical CAPTURE destination'

CAP="app/src/main/java/com/kareem/cortex/CortexCapabilityRegistry.java"
require_file "$CAP"
cap_count="$(grep -oE 'c\([0-9]+,"' "$CAP" | wc -l | tr -d ' ')"
[ "$cap_count" = "43" ] && ok "authoritative capability registry still has exactly 43 entries" || bad "capability registry expected 43 entries, found $cap_count"
for n in $(seq 1 43); do grep -Fq "c($n," "$CAP" || bad "capability #$n missing"; done

require_text app/src/main/java/com/kareem/cortex/ProposalUi.java 'CloudEvidencePolicy\.canSend' 'proposal cloud routing respects source privacy policy'
require_text app/src/main/java/com/kareem/cortex/CortexActionDispatcher.java 'CALENDAR_RESCHEDULE' 'dispatcher handles calendar reschedule explicitly'
require_text app/src/main/java/com/kareem/cortex/CortexActionExecutor.java 'Calendar app owns the final write' 'external calendar mutation remains user-confirmed draft'
require_text app/src/main/java/com/kareem/cortex/BrainRouter.java 'CloudEvidencePolicy\.filter' 'Combined Brain still filters cloud evidence locally'

# Current design-system invariants are semantic, not frozen RGB snapshots.
require_text "$UI" 'LIME=Color\.rgb' 'primary lime semantic is centralized'
require_text "$UI" 'GREEN=Color\.rgb' 'green success semantic is centralized'
require_text "$UI" 'YELLOW=Color\.rgb' 'yellow attention semantic is centralized'
require_text "$UI" 'ORANGE=Color\.rgb' 'orange interaction semantic is centralized'
require_text "$UI" 'RED=Color\.rgb' 'red risk semantic is centralized'
require_text "$UI" 'VIOLET=OLIVE' 'legacy violet semantic resolves to non-purple olive'
require_text "$UI" 'public static GradientDrawable matte' 'matte surface helper is centralized'
require_text "$UI" 'public static GradientDrawable velvet' 'low-reflection depth helper is centralized'
require_text "$UI" 'public static <T extends View>T raised' 'raised elevation helper is centralized'
require_text app/src/main/java/com/kareem/cortex/CortexGlyphView.java 'monoline white glyph' 'custom raised monoline icon language is present'
require_text app/src/main/java/com/kareem/cortex/SatinCaptureActivity.java 'setAccent\(CortexUi\.SIGNAL\)' 'recording STOP ring uses semantic signal color'
require_text app/src/main/java/com/kareem/cortex/CortexScrubberView.java 'CortexUi\.RED' 'waveform playback uses approved red signal family'

if git grep -nEi '126,158,255|182,137,255|178,103,255|#7E9EFF|#B689FF|#B267FF' -- 'app/src/main/**' >/dev/null 2>&1; then bad "legacy blue/purple visual token detected in final app sources"; else ok "no legacy blue/purple visual token in final app sources"; fi

placeholder_hits="$(git grep -nEi '\b(TODO|FIXME|temporary stub|placeholder implementation)\b' -- '*.java' '*.kt' '*.xml' '*.gradle' '*.sh' '*.py' 2>/dev/null | wc -l | tr -d ' ' || true)"
[ "$placeholder_hits" = "0" ] || warn "$placeholder_hits TODO/FIXME/placeholder source hit(s) require human context review"

printf '%s\n' '-----------------------------------------------------'
printf 'Files scanned: %s  Warnings: %s  Failures: %s\n' "$SCANNED" "$WARN" "$FAIL"
if [ "$FAIL" -ne 0 ]; then printf 'CORTEX_REPO_AUDIT=FAIL\n' >&2;exit 2;fi
printf 'CORTEX_REPO_AUDIT=PASS\n'
printf '%s\n\n' '====================================================='
