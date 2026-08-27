#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
EXPECTED_BRANCH="${CORTEX_EXPECT_BRANCH:-}"

printf 'Cortex validation build\nrepo: %s\nbranch: %s\nhead: %s\n' "$ROOT" "$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)" "$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
if [[ -n "$EXPECTED_BRANCH" ]]; then current="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)"; [[ "$current" == "$EXPECTED_BRANCH" ]] || { printf 'error: expected branch %s, currently %s\n' "$EXPECTED_BRANCH" "$current" >&2; exit 2; }; fi

bash scripts/cortex-repo-audit.sh "$ROOT"
if [[ -x ./gradlew ]]; then GRADLE=(./gradlew); elif command -v gradle >/dev/null 2>&1; then GRADLE=(gradle); else printf 'error: Gradle not found.\n' >&2; exit 2; fi
printf 'running: %s :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace\n' "${GRADLE[*]}"
"${GRADLE[@]}" :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace --console=plain
APK="app/build/outputs/apk/debug/app-debug.apk"; TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -f "$APK" ]] || { printf 'error: APK not found at %s\n' "$APK" >&2; exit 3; }
[[ -f "$TEST_APK" ]] || { printf 'error: instrumentation APK not found at %s\n' "$TEST_APK" >&2; exit 4; }
printf '\nBUILD OK\nAPK: %s\nTEST APK: %s\n' "$ROOT/$APK" "$ROOT/$TEST_APK"
command -v sha256sum >/dev/null 2>&1 && sha256sum "$APK" "$TEST_APK" || true
