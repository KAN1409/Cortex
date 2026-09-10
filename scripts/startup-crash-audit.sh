#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"
REPORT="app/build/reports/startup-crash-audit.txt"
mkdir -p "$(dirname "$REPORT")"
: > "$REPORT"

say(){ printf '%s\n' "$*" | tee -a "$REPORT"; }
fail(){ say "FAIL: $*"; exit 1; }

say "CORTEX_STARTUP_AUDIT_V1"
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

test -s "$MANIFEST" || fail "source manifest missing"
test -s "$APP" || fail "CortexApp missing"
test -s "$GATE" || fail "StartupSafetyGate missing"
test -s "$INPUT" || fail "InputActivity missing"

grep -q 'android:name=".CortexApp"' "$MANIFEST" || fail "manifest application is not CortexApp"
if grep -q 'androidx.work.WorkManagerInitializer' "$MANIFEST"; then
  fail "source manifest overrides WorkManagerInitializer; use AndroidX default provider bootstrap"
fi
if grep -q 'WorkManager.initialize' "$APP"; then
  fail "CortexApp manually initializes WorkManager"
fi
if grep -q 'Configuration.Provider' "$APP"; then
  fail "CortexApp implements custom WorkManager Configuration.Provider while default initializer is required"
fi
grep -q 'CrashRecorder.install(this)' "$APP" || fail "CrashRecorder startup hook missing"
if grep -Eq 'VaultDb|LocalLlm|Llama|Tess|WorkManager|getWritableDatabase|getReadableDatabase|ProcessExitRecorder' "$APP"; then
  fail "CortexApp contains heavyweight/database/native/background bootstrap"
fi

grep -q 'private static final boolean ACTIVE = true' "$GATE" || fail "recovery startup quarantine is not active"
grep -q 'if(!StartupSafetyGate.active())' "$INPUT" || fail "launcher does not quarantine post-resume schedulers"

# Every app scheduler that can touch WorkManager must honor the recovery gate.
SCHEDULER_HITS=0
while IFS= read -r f; do
  SCHEDULER_HITS=$((SCHEDULER_HITS+1))
  if ! grep -q 'StartupSafetyGate.active()' "$f"; then
    fail "WorkManager scheduler lacks StartupSafetyGate: $f"
  fi
done < <(grep -rl --include='*.java' --include='*.kt' 'WorkManager.getInstance' app/src/main/java/com/kareem/cortex | sort)
say "workmanager_callsite_files=$SCHEDULER_HITS"

# Inventory all startup-sensitive patterns across every source/config text file.
for pattern in \
  'WorkManager.getInstance' \
  'WorkManager.initialize' \
  'System.loadLibrary' \
  'System.load(' \
  'Llama.' \
  'TessBaseAPI' \
  'new VaultDb' \
  'getWritableDatabase' \
  'getReadableDatabase' \
  'extends ContentProvider' \
  'extends Application' \
  'static {'; do
  count=0
  while IFS= read -r f; do
    count=$((count+1))
    printf 'RISK\t%s\t%s\n' "$pattern" "$f" >> "$REPORT"
  done < <(grep -Fl "$pattern" "${TEXT_FILES[@]}" 2>/dev/null | sort || true)
  say "risk_pattern[$pattern]=$count"
done

# Inspect every Worker source. Persisted jobs bypass scheduler call sites, so record whether each
# worker has the recovery gate. This is reported exhaustively and blocks only workers that directly
# reference native runtimes.
WORKERS=0
while IFS= read -r f; do
  WORKERS=$((WORKERS+1))
  gated=no
  grep -q 'StartupSafetyGate.active()' "$f" && gated=yes
  native=no
  grep -Eq 'LocalLlm|Llama|Tess|Whisper|Ocr|AudioRecord|System\.load' "$f" && native=yes
  printf 'WORKER\t%s\tgated=%s\tnative=%s\n' "$f" "$gated" "$native" >> "$REPORT"
  if [[ "$native" == yes && "$gated" != yes ]]; then
    fail "native-capable persisted Worker lacks StartupSafetyGate: $f"
  fi
done < <(grep -rlE --include='*.java' --include='*.kt' 'extends[[:space:]]+(Worker|CoroutineWorker)|:[[:space:]]*(Worker|CoroutineWorker)\(' app/src/main/java/com/kareem/cortex | sort || true)
say "worker_files=$WORKERS"

# Build-time truth: the merged manifest must retain AndroidX's WorkManager initializer.
merged_found=0
initializer_found=0
while IFS= read -r m; do
  merged_found=1
  if grep -q 'androidx.work.WorkManagerInitializer' "$m"; then
    initializer_found=1
    printf 'MERGED_MANIFEST\t%s\tworkmanager_initializer=present\n' "$m" >> "$REPORT"
  else
    printf 'MERGED_MANIFEST\t%s\tworkmanager_initializer=absent\n' "$m" >> "$REPORT"
  fi
done < <(find app/build/intermediates -type f -name AndroidManifest.xml \( -path '*merged_manifest*' -o -path '*merged_manifests*' \) 2>/dev/null | sort || true)

if [[ "$merged_found" == 1 && "$initializer_found" != 1 ]]; then
  fail "WorkManagerInitializer absent from all merged manifests"
fi

say "merged_manifest_checked=$merged_found"
say "workmanager_initializer_present=$initializer_found"
say "RESULT=PASS"
