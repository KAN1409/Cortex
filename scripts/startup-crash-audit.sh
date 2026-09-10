#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"
REPORT="app/build/reports/startup-crash-audit.txt"
mkdir -p "$(dirname "$REPORT")"
: > "$REPORT"

say(){ printf '%s\n' "$*" | tee -a "$REPORT"; }
fail(){ say "FAIL: $*"; exit 1; }
record_error(){ AUDIT_ERRORS=$((AUDIT_ERRORS+1)); say "ERROR: $*"; }
AUDIT_ERRORS=0

say "CORTEX_STARTUP_AUDIT_V2"
say "commit=$(git rev-parse HEAD 2>/dev/null || echo unknown)"
say "timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"

mapfile -t ALL_FILES < <(find . -type f \
  ! -path './.git/*' \
  ! -path './app/build/*' \
  ! -path './.gradle/*' \
  -print | sort)
say "files_scanned=${#ALL_FILES[@]}"

TEXT_FILES=()
for f in "${ALL_FILES[@]}"; do
  case "$f" in
    *.java|*.kt|*.kts|*.xml|*.gradle|*.properties|*.yml|*.yaml|*.md|*.sh|*.txt) TEXT_FILES+=("$f");;
  esac
done
say "text_files_scanned=${#TEXT_FILES[@]}"

MANIFEST=app/src/main/AndroidManifest.xml
APP=app/src/main/java/com/kareem/cortex/CortexApp.java
GATE=app/src/main/java/com/kareem/cortex/StartupSafetyGate.java
INPUT=app/src/main/java/com/kareem/cortex/InputActivity.java
SUPERVISOR=app/src/main/java/com/kareem/cortex/CapabilitySupervisor.java
SAFE_CORE=app/src/main/java/com/kareem/cortex/SafeCoreRuntime.java
SAFE_LIFECYCLE=app/src/main/java/com/kareem/cortex/SafeCoreLifecycle.java
STATEFUL_SCHEDULER=app/src/main/java/com/kareem/cortex/StatefulMeaningScheduler.java
STATEFUL_WORKER=app/src/main/java/com/kareem/cortex/StatefulMeaningWorker.java
SEMANTIC_SCHEDULER=app/src/main/java/com/kareem/cortex/UniversalSemanticScheduler.java
NOTIFICATION_SERVICE=app/src/main/java/com/kareem/cortex/NotificationCaptureService.java

for required in "$MANIFEST" "$APP" "$GATE" "$INPUT" "$SUPERVISOR" "$SAFE_CORE" "$SAFE_LIFECYCLE" "$STATEFUL_SCHEDULER" "$STATEFUL_WORKER" "$SEMANTIC_SCHEDULER" "$NOTIFICATION_SERVICE"; do
  test -s "$required" || fail "required startup/recovery file missing: $required"
done

grep -q 'android:name=".CortexApp"' "$MANIFEST" || record_error "manifest application is not CortexApp"
if grep -q 'androidx.work.WorkManagerInitializer' "$MANIFEST"; then
  record_error "source manifest overrides WorkManagerInitializer; use AndroidX default provider bootstrap"
fi
if grep -Eq 'WorkManager[[:space:]]*\.[[:space:]]*initialize[[:space:]]*\(' "$APP"; then
  record_error "CortexApp manually initializes WorkManager"
fi
if grep -Eq 'implements[^{]*Configuration\.Provider' "$APP"; then
  record_error "CortexApp implements a custom WorkManager provider while default initialization is required"
fi
grep -q 'CrashRecorder.install(this)' "$APP" || record_error "CrashRecorder startup hook missing"
grep -q 'SafeCoreLifecycle.install(this)' "$APP" || record_error "safe-core lifecycle hook missing"
if grep -Eq 'new[[:space:]]+VaultDb|LocalLlm(Runtime|Bridge)|Llama\.|TessBaseAPI|getWritableDatabase[[:space:]]*\(|getReadableDatabase[[:space:]]*\(|WorkManager\.getInstance|ProcessExitRecorder\.' "$APP"; then
  record_error "CortexApp contains heavyweight/database/native/scheduling bootstrap code"
fi

grep -q 'private static final boolean ACTIVE = true' "$GATE" || record_error "emergency native/startup quarantine is not compile-time active"
grep -q 'if(!StartupSafetyGate.active())' "$INPUT" || record_error "legacy launcher startup schedulers are not quarantined"

# Staged safe-core contract: activation only after InputActivity resume, then a delayed off-main DB probe.
grep -q 'activity instanceof InputActivity' "$SAFE_LIFECYCLE" || record_error "safe core is not tied to launcher resume"
grep -q 'armAfterLauncherResume' "$SAFE_LIFECYCLE" || record_error "safe-core lifecycle does not arm runtime"
grep -q 'POST_RESUME_SETTLE_MS' "$SAFE_CORE" || record_error "safe core lacks post-resume settle window"
grep -q 'new Thread' "$SAFE_CORE" || record_error "safe-core DB probe is not moved off main thread"
grep -q 'SELECT 1' "$SAFE_CORE" || record_error "safe core lacks explicit DB health probe"
grep -q 'CORE_READY' "$SAFE_CORE" || record_error "safe core lacks ready state"
grep -q 'CORE_FAILED' "$SAFE_CORE" || record_error "safe core lacks failed state"

# The emergency gate must continue to quarantine native/proactive capabilities even after safe core readiness.
grep -q 'StartupSafetyGate.active() && !SafeCoreRuntime.safeCapability(capability)' "$SUPERVISOR" || record_error "native capabilities are not independently quarantined"
for cap in OCR_NATIVE ASR_NATIVE LOCAL_LLM_NATIVE PROACTIVE_ACTIONS; do
  grep -q "$cap" "$SUPERVISOR" || record_error "capability supervisor missing $cap"
done
for cap in DATABASE RAW_NOTIFICATION_CAPTURE DETERMINISTIC_COGNITION BACKGROUND_SCHEDULING; do
  grep -q "$cap" "$SAFE_CORE" || record_error "safe-core allow-list missing $cap"
done

# Raw notification capture may resume only through explicit DB + raw-capture capability checks.
grep -q 'CapabilitySupervisor.Capability.DATABASE' "$NOTIFICATION_SERVICE" || record_error "notification capture lacks DATABASE capability gate"
grep -q 'CapabilitySupervisor.Capability.RAW_NOTIFICATION_CAPTURE' "$NOTIFICATION_SERVICE" || record_error "notification capture lacks RAW_NOTIFICATION_CAPTURE gate"

# Deterministic projection/shadow drain is the only WorkManager path allowed while emergency gate stays active.
for cap in DATABASE BACKGROUND_SCHEDULING DETERMINISTIC_COGNITION; do
  grep -q "CapabilitySupervisor.Capability.$cap" "$STATEFUL_SCHEDULER" || record_error "stateful scheduler missing $cap capability gate"
done
grep -q 'CapabilitySupervisor.Capability.DATABASE' "$STATEFUL_WORKER" || record_error "stateful worker missing DATABASE gate"
grep -q 'CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION' "$STATEFUL_WORKER" || record_error "stateful worker missing deterministic cognition gate"

# Local semantic refinement is NOT safe-core: it directly reaches the local model and must retain both gates.
grep -Eq 'if[[:space:]]*\([[:space:]]*StartupSafetyGate\.active\(\)' "$SEMANTIC_SCHEDULER" || record_error "local semantic scheduler lost emergency startup gate"
grep -q 'CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE' "$SEMANTIC_SCHEDULER" || record_error "local semantic scheduler lacks LOCAL_LLM_NATIVE gate"

# Every WorkManager callsite must use either the emergency gate or the narrow deterministic safe-core contract.
SCHEDULER_HITS=0
while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  SCHEDULER_HITS=$((SCHEDULER_HITS+1))
  if grep -q 'StartupSafetyGate.active()' "$f"; then
    continue
  fi
  if grep -q 'CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING' "$f" \
      && grep -q 'CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION' "$f" \
      && grep -q 'CapabilitySupervisor.Capability.DATABASE' "$f"; then
    continue
  fi
  record_error "WorkManager callsite lacks emergency gate or full deterministic safe-core contract: $f"
done < <(grep -rl --include='*.java' --include='*.kt' 'WorkManager.getInstance' app/src/main/java/com/kareem/cortex | sort || true)
say "workmanager_callsite_files=$SCHEDULER_HITS"

# Inventory startup-sensitive patterns across every source/config text file.
for pattern in \
  'WorkManager.getInstance' \
  'WorkManager.initialize' \
  'System.loadLibrary' \
  'System.load(' \
  'Llama.' \
  'TessBaseAPI' \
  'Whisper.loadModel' \
  'new VaultDb' \
  'getWritableDatabase' \
  'getReadableDatabase' \
  'extends ContentProvider' \
  'extends Application' \
  'static {'; do
  count=0
  while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    count=$((count+1))
    printf 'RISK\t%s\t%s\n' "$pattern" "$f" >> "$REPORT"
  done < <(grep -Fl "$pattern" "${TEXT_FILES[@]}" 2>/dev/null | sort || true)
  say "risk_pattern[$pattern]=$count"
done

# Persisted jobs bypass scheduler callsites. Direct native workers must retain emergency/native gates.
WORKERS=0
while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  WORKERS=$((WORKERS+1))
  startup_gated=no
  native_cap_gated=no
  grep -q 'StartupSafetyGate.active()' "$f" && startup_gated=yes
  grep -Eq 'CapabilitySupervisor\.Capability\.(OCR_NATIVE|ASR_NATIVE|LOCAL_LLM_NATIVE)' "$f" && native_cap_gated=yes
  native=no
  grep -Eq 'LocalLlmBridge|Llama\.|TessBaseAPI|Whisper|AudioRecord|System\.load' "$f" && native=yes
  printf 'WORKER\t%s\tstartup_gated=%s\tnative_cap_gated=%s\tnative=%s\n' "$f" "$startup_gated" "$native_cap_gated" "$native" >> "$REPORT"
  if [[ "$native" == yes && "$startup_gated" != yes && "$native_cap_gated" != yes ]]; then
    record_error "native-capable persisted Worker lacks emergency/native capability gate: $f"
  fi
done < <(grep -rlE --include='*.java' --include='*.kt' 'extends[[:space:]]+(Worker|CoroutineWorker)|:[[:space:]]*(Worker|CoroutineWorker)\(' app/src/main/java/com/kareem/cortex | sort || true)
say "worker_files=$WORKERS"

# LocalLlmRuntime itself remains the choke point for indirect local-model access.
grep -Eq 'if[[:space:]]*\([[:space:]]*StartupSafetyGate\.active\(\)' app/src/main/java/com/kareem/cortex/LocalLlmRuntime.java || record_error "LocalLlmRuntime is not recovery-gated"
grep -q 'CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE' app/src/main/java/com/kareem/cortex/LocalLlmRuntime.java || record_error "LocalLlmRuntime lacks local-LLM capability breaker"

# Direct OCR/Whisper classes are deliberately still protected by the compile-time emergency gate at callers.
# They must never be referenced by SafeCoreRuntime or its lifecycle hook.
if grep -Eq 'ArabicOcr|TessBaseAPI|Whisper|CleanWhisper|LocalLlm|LocalLlmBridge|Llama\.' "$SAFE_CORE" "$SAFE_LIFECYCLE"; then
  record_error "safe-core activation path references native OCR/ASR/LLM code"
fi

# Build-time truth: once merged manifests exist, AndroidX's initializer must be present.
merged_found=0
initializer_found=0
while IFS= read -r m; do
  [[ -z "$m" ]] && continue
  merged_found=1
  if grep -q 'androidx.work.WorkManagerInitializer' "$m"; then
    initializer_found=1
    printf 'MERGED_MANIFEST\t%s\tworkmanager_initializer=present\n' "$m" >> "$REPORT"
  else
    printf 'MERGED_MANIFEST\t%s\tworkmanager_initializer=absent\n' "$m" >> "$REPORT"
  fi
done < <(find app/build/intermediates -type f -name AndroidManifest.xml \( -path '*merged_manifest*' -o -path '*merged_manifests*' \) 2>/dev/null | sort || true)

if [[ "$merged_found" == 1 && "$initializer_found" != 1 ]]; then
  record_error "WorkManagerInitializer absent from all merged manifests"
fi

say "merged_manifest_checked=$merged_found"
say "workmanager_initializer_present=$initializer_found"
say "audit_errors=$AUDIT_ERRORS"
if [[ "$AUDIT_ERRORS" -ne 0 ]]; then
  say "RESULT=FAIL"
  exit 1
fi
say "RESULT=PASS"
