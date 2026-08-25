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

for f in "$SUITE" "$EXPORT" "$COMPAT" "$FIX" "$ACCESS" "$DISPATCH" "$BRAIN"; do need_file "$f"; done

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
