#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "Cortex full instrumented self-user test"
echo "repo: $ROOT"
echo "branch: $(git branch --show-current 2>/dev/null || true)"
echo "head: $(git rev-parse --short HEAD 2>/dev/null || true)"

if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: gradle is not installed in Termux"
  exit 2
fi
if ! command -v rish >/dev/null 2>&1; then
  echo "ERROR: rish is required so the test can install APKs and run am instrument on this phone."
  echo "Start Shizuku, install/configure rish, then rerun this script."
  exit 3
fi
if [[ ! -d "$HOME/storage/downloads" ]]; then
  echo "ERROR: Termux storage link is missing. Run termux-setup-storage once, then rerun."
  exit 3
fi

echo
echo "[1/4] Building app + instrumentation APK..."
gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace

APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -f "$APP_APK" ]] || { echo "Missing $APP_APK"; exit 4; }
[[ -f "$TEST_APK" ]] || { echo "Missing $TEST_APK"; exit 5; }

# rish runs as Android shell and cannot read Termux's private tree. First put
# APKs in shared storage, then copy them as shell into /data/local/tmp. Package
# Manager can reliably install from /data/local/tmp even on newer Android/SELinux.
STAGE_TERMUX="$HOME/storage/downloads/CortexSelfUserTestStage"
STAGE_ANDROID="/sdcard/Download/CortexSelfUserTestStage"
SHELL_STAGE="/data/local/tmp/cortex-self-user-test"
mkdir -p "$STAGE_TERMUX"
cp -f "$APP_APK" "$STAGE_TERMUX/cortex-app-debug.apk"
cp -f "$TEST_APK" "$STAGE_TERMUX/cortex-test-debug.apk"

echo
echo "[2/4] Installing update-in-place app and test APK..."
echo "staging APKs for Android shell..."
rish -c "rm -rf '$SHELL_STAGE'; mkdir -p '$SHELL_STAGE'; cp '$STAGE_ANDROID/cortex-app-debug.apk' '$SHELL_STAGE/cortex-app-debug.apk'; cp '$STAGE_ANDROID/cortex-test-debug.apk' '$SHELL_STAGE/cortex-test-debug.apk'; chmod 644 '$SHELL_STAGE/'*.apk; ls -lh '$SHELL_STAGE/'*.apk" || {
  echo "ERROR: Android shell could not stage the APKs into /data/local/tmp"
  echo "Shared-stage visibility:"
  rish -c "ls -lh '$STAGE_ANDROID'" || true
  exit 7
}

# -r preserves app data; -t allows test/debug packages; -d permits a debug
# downgrade if the phone happens to have a higher versionCode from another build.
set +e
APP_INSTALL="$(rish -c "pm install -r -t -d '$SHELL_STAGE/cortex-app-debug.apk'" 2>&1 | tr -d '\r')"
APP_STATUS=$?
set -e
echo "app install output:"
echo "${APP_INSTALL:-<no output>}"
if [[ $APP_STATUS -ne 0 || "$APP_INSTALL" != *"Success"* ]]; then
  echo "ERROR: app APK install failed (exit=$APP_STATUS)"
  echo "Installed Cortex package summary:"
  rish -c "dumpsys package com.kareem.cortex | grep -E 'versionCode=|versionName=|signatures=|Package \\[' | head -n 20" || true
  exit 8
fi

set +e
TEST_INSTALL="$(rish -c "pm install -r -t -d '$SHELL_STAGE/cortex-test-debug.apk'" 2>&1 | tr -d '\r')"
TEST_STATUS=$?
set -e
echo "test install output:"
echo "${TEST_INSTALL:-<no output>}"
if [[ $TEST_STATUS -ne 0 || "$TEST_INSTALL" != *"Success"* ]]; then
  echo "ERROR: instrumentation APK install failed (exit=$TEST_STATUS)"
  exit 9
fi

INSTRUMENTATION="$(rish -c "pm list instrumentation" 2>&1 | tr -d '\r')"
echo "$INSTRUMENTATION" | grep -F "com.kareem.cortex.test/androidx.test.runner.AndroidJUnitRunner" >/dev/null || {
  echo "ERROR: AndroidJUnitRunner was not registered after install."
  echo "Installed instrumentation entries:"
  echo "$INSTRUMENTATION"
  echo "Test package info:"
  rish -c "dumpsys package com.kareem.cortex.test | head -n 100" || true
  exit 10
}
echo "instrumentation: registered"

echo
echo "[3/4] Running full Android instrumentation..."
mkdir -p "$ROOT/build"
set +e
rish -c "am instrument -w -r -e class com.kareem.cortex.FullApplicationSelfUserTest com.kareem.cortex.test/androidx.test.runner.AndroidJUnitRunner" | tee "$ROOT/build/instrumented-self-user-test-console.txt"
STATUS=${PIPESTATUS[0]}
set -e

echo
echo "[4/4] Exporting evidence package..."
LATEST="$(rish -c "ls -td /sdcard/Android/data/com.kareem.cortex/files/self-user-test/FullCortexSelfUserTest_* 2>/dev/null | head -n 1" | tr -d '\r')"
if [[ -z "$LATEST" ]]; then
  echo "ERROR: no self-user-test report directory was produced. Instrumentation exit=$STATUS"
  echo "Instrumentation console: $ROOT/build/instrumented-self-user-test-console.txt"
  exit 6
fi

NAME="$(basename "$LATEST")"
rish -c "rm -rf '/sdcard/Download/$NAME'; cp -R '$LATEST' '/sdcard/Download/$NAME'; rm -rf '$SHELL_STAGE'"

if ! command -v zip >/dev/null 2>&1; then
  echo "zip is not installed; installing it in Termux..."
  pkg install -y zip >/dev/null
fi

cd "$HOME/storage/downloads"
rm -f "$NAME.zip"
zip -qr "$NAME.zip" "$NAME"
rm -rf "$STAGE_TERMUX"

echo
echo "DONE"
echo "Report folder: Downloads/$NAME"
echo "ZIP: Downloads/$NAME.zip"
echo "Instrumentation exit: $STATUS"
if [[ -f "$NAME/report.md" ]]; then
  echo
  sed -n '1,35p' "$NAME/report.md"
fi

echo
echo "Send $NAME.zip back to ChatGPT for full product critique, bug diagnosis, and brainstorming."
exit "$STATUS"
