#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$ROOT"
FAIL=0
ok(){ printf 'CONTEXT CORE AUDIT PASS: %s\n' "$*"; }
bad(){ FAIL=$((FAIL+1)); printf 'CONTEXT CORE AUDIT FAIL: %s\n' "$*" >&2; }
need_file(){ [ -f "$1" ] && ok "$1 present" || bad "$1 missing"; }
need(){ local f="$1" p="$2" label="$3"; grep -Eq "$p" "$f" 2>/dev/null && ok "$label" || bad "$label"; }

SCHEMA="app/src/main/java/com/kareem/cortex/ContextSchema.java"
STATE="app/src/main/java/com/kareem/cortex/ContextStateStore.java"
RESOLVER="app/src/main/java/com/kareem/cortex/ContextResolver.java"
PACKET="app/src/main/java/com/kareem/cortex/ContextPacketBuilder.java"
ASK="app/src/main/java/com/kareem/cortex/ContextAskEngine.java"
OPS="app/src/main/java/com/kareem/cortex/AskOperationalEngine.java"

for f in "$SCHEMA" "$STATE" "$RESOLVER" "$PACKET" "$ASK" "$OPS"; do need_file "$f"; done
need "$SCHEMA" 'CREATE TABLE IF NOT EXISTS contexts' 'first-class context identity exists'
need "$SCHEMA" 'context_episodes' 'context episodes are persisted'
need "$SCHEMA" 'context_stack_state' 'primary/background context stack exists'
need "$SCHEMA" 'context_snapshots' 'resume snapshots exist'
need "$SCHEMA" 'context_fingerprint_features' 'learnable context fingerprints exist'
need "$SCHEMA" 'context_feedback' 'context corrections/feedback have a durable home'

need "$STATE" 'ROLE_PRIMARY="PRIMARY".*ROLE_BACKGROUND="BACKGROUND".*ROLE_AMBIENT="AMBIENT"' 'stack role is separate from lifecycle'
need "$STATE" 'LIFE_ACTIVE="ACTIVE".*LIFE_SUSPENDED="SUSPENDED".*LIFE_COMPLETED="COMPLETED"' 'context lifecycle is explicit'
need "$STATE" 'SWITCH_MIN=0\.78,SWITCH_MARGIN=0\.12' 'context switch hysteresis is conservative'
need "$STATE" 'PRIMARY_HOLD_MS=10L\*60L\*1000L' 'fresh primary context has a hold window'
need "$STATE" 'offerPrimary' 'resolver cannot bypass stack hysteresis'
need "$STATE" 'recordSnapshot' 'context can be resumed from event snapshots'
need "$STATE" 'linkEvidence' 'context retains provenance links'

need "$RESOLVER" 'signal_threads' 'explicit conversation/thread evidence is considered'
need "$RESOLVER" 'intentional Cortex capture' 'intentional capture evidence is considered'
need "$RESOLVER" 'group\.size\(\)>=3' 'phone activity needs repeated evidence before becoming a context candidate'
need "$RESOLVER" 'phone_context' 'phone activity remains an evidence source, not the context model itself'
need "$RESOLVER" 'shouldSnapshot' 'snapshots are event-driven with periodic safety refresh'

need "$PACKET" 'CURRENT CORTEX CONTEXT' 'Brain/Brief receive a compact context passport'
need "$PACKET" 'cloudText' 'context packet separates local and cloud representations'
need "$PACKET" 'exports no derived context summary to cloud' 'v1 prevents privacy laundering through derived summaries'
need "$ASK" 'where did i leave off|اكمل منين' 'Brain recognizes resume/current-context questions'
need "$OPS" 'ContextAskEngine\.tryAnswer' 'operational Brain routes context questions locally first'

if [ "$FAIL" -ne 0 ]; then
  printf 'CORTEX_CONTEXT_CORE_AUDIT=FAIL (%s failure(s))\n' "$FAIL" >&2
  exit 2
fi
printf 'CORTEX_CONTEXT_CORE_AUDIT=PASS\n'
