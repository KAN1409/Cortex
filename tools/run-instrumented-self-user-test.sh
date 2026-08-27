#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "Cortex instrumented self-user test V1 + Cognitive/Product Adjudication V2"
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
echo "[1/5] Building app + instrumentation APK..."
gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace

APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -f "$APP_APK" ]] || { echo "Missing $APP_APK"; exit 4; }
[[ -f "$TEST_APK" ]] || { echo "Missing $TEST_APK"; exit 5; }

STAGE_TERMUX="$HOME/storage/downloads/CortexSelfUserTestStage"
STAGE_ANDROID="/sdcard/Download/CortexSelfUserTestStage"
SHELL_STAGE="/data/local/tmp/cortex-self-user-test"
mkdir -p "$STAGE_TERMUX"
cp -f "$APP_APK" "$STAGE_TERMUX/cortex-app-debug.apk"
cp -f "$TEST_APK" "$STAGE_TERMUX/cortex-test-debug.apk"

echo
echo "[2/5] Installing update-in-place app and test APK..."
echo "staging APKs for Android shell..."
rish -c "rm -rf '$SHELL_STAGE'; mkdir -p '$SHELL_STAGE'; cp '$STAGE_ANDROID/cortex-app-debug.apk' '$SHELL_STAGE/cortex-app-debug.apk'; cp '$STAGE_ANDROID/cortex-test-debug.apk' '$SHELL_STAGE/cortex-test-debug.apk'; chmod 644 '$SHELL_STAGE/'*.apk; ls -lh '$SHELL_STAGE/'*.apk" || {
  echo "ERROR: Android shell could not stage the APKs into /data/local/tmp"
  rish -c "ls -lh '$STAGE_ANDROID'" || true
  exit 7
}

set +e
APP_INSTALL="$(rish -c "pm install -r -t -d '$SHELL_STAGE/cortex-app-debug.apk'" 2>&1 | tr -d '\r')"
APP_STATUS=$?
set -e
echo "app install output:"
echo "${APP_INSTALL:-<no output>}"
if [[ $APP_STATUS -ne 0 || "$APP_INSTALL" != *"Success"* ]]; then
  echo "ERROR: app APK install failed (exit=$APP_STATUS)"
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
  echo "$INSTRUMENTATION"
  rish -c "dumpsys package com.kareem.cortex.test | head -n 100" || true
  exit 10
}
echo "instrumentation: registered"

mkdir -p "$ROOT/build"

echo
echo "[3/5] Running V1 runtime / navigation / surface health..."
set +e
rish -c "am instrument -w -r -e class com.kareem.cortex.FullApplicationSelfUserTest com.kareem.cortex.test/androidx.test.runner.AndroidJUnitRunner" | tee "$ROOT/build/instrumented-self-user-test-v1-console.txt"
V1_STATUS=${PIPESTATUS[0]}
set -e

echo
echo "[4/5] Running V2 Cognitive / Product Adjudication..."
set +e
rish -c "am instrument -w -r -e class com.kareem.cortex.CognitiveProductAdjudicationTest com.kareem.cortex.test/androidx.test.runner.AndroidJUnitRunner" | tee "$ROOT/build/instrumented-self-user-test-v2-console.txt"
V2_STATUS=${PIPESTATUS[0]}
set -e

echo
echo "[5/5] Exporting combined evidence package..."
LATEST_V1="$(rish -c "ls -td /sdcard/Android/data/com.kareem.cortex/files/self-user-test/FullCortexSelfUserTest_* 2>/dev/null | head -n 1" | tr -d '\r')"
LATEST_V2="$(rish -c "ls -td /sdcard/Android/data/com.kareem.cortex/files/self-user-test/CognitiveProductAdjudication_* 2>/dev/null | head -n 1" | tr -d '\r')"
if [[ -z "$LATEST_V1" || -z "$LATEST_V2" ]]; then
  echo "ERROR: expected report directories were not both produced. V1=$V1_STATUS V2=$V2_STATUS"
  echo "V1 dir: ${LATEST_V1:-missing}"
  echo "V2 dir: ${LATEST_V2:-missing}"
  exit 6
fi

STAMP="$(date +%Y%m%d_%H%M%S)"
BUNDLE="FullCortexInstrumentedReview_${STAMP}"
ANDROID_BUNDLE="/sdcard/Download/$BUNDLE"
rish -c "rm -rf '$ANDROID_BUNDLE'; mkdir -p '$ANDROID_BUNDLE'; cp -R '$LATEST_V1' '$ANDROID_BUNDLE/'; cp -R '$LATEST_V2' '$ANDROID_BUNDLE/'; rm -rf '$SHELL_STAGE'"

cat > "$HOME/storage/downloads/$BUNDLE/README.txt" <<EOF
Cortex Instrumented Review Bundle
V1 = Android/runtime/navigation/surface health
V2 = Cognitive/Product Adjudication
V1 instrumentation exit: $V1_STATUS
V2 instrumentation exit: $V2_STATUS
Git head: $(git rev-parse HEAD 2>/dev/null || true)
EOF

if ! command -v zip >/dev/null 2>&1; then
  echo "zip is not installed; installing it in Termux..."
  pkg install -y zip >/dev/null
fi

cd "$HOME/storage/downloads"
rm -f "$BUNDLE.zip"
zip -qr "$BUNDLE.zip" "$BUNDLE"
rm -rf "$STAGE_TERMUX"

echo
echo "DONE"
echo "Bundle folder: Downloads/$BUNDLE"
echo "ZIP: Downloads/$BUNDLE.zip"
echo "V1 instrumentation exit: $V1_STATUS"
echo "V2 instrumentation exit: $V2_STATUS"
echo
find "$BUNDLE" -name report.md -maxdepth 3 -print -exec sh -c 'echo; sed -n "1,28p" "$1"; echo' _ {} \;
echo "Send $BUNDLE.zip back to ChatGPT. V1 tells us whether Cortex works; V2 tells us whether Cortex thinks and behaves like the right product."

if [[ $V1_STATUS -ne 0 ]]; then exit "$V1_STATUS"; fi
exit "$V2_STATUS"
