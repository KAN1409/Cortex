#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
echo "Cortex instrumented review V1 + V2 + V3 Teacher/Student Differential"
echo "repo: $ROOT"; echo "branch: $(git branch --show-current 2>/dev/null || true)"; echo "head: $(git rev-parse --short HEAD 2>/dev/null || true)"
command -v gradle >/dev/null 2>&1 || { echo "ERROR: gradle is not installed in Termux"; exit 2; }
command -v rish >/dev/null 2>&1 || { echo "ERROR: rish/Shizuku is required"; exit 3; }
[[ -d "$HOME/storage/downloads" ]] || { echo "ERROR: Run termux-setup-storage once"; exit 3; }

echo; echo "[1/6] Building app + instrumentation APK..."
gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"; TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -f "$APP_APK" ]] || { echo "Missing $APP_APK"; exit 4; }; [[ -f "$TEST_APK" ]] || { echo "Missing $TEST_APK"; exit 5; }
STAGE_TERMUX="$HOME/storage/downloads/CortexSelfUserTestStage"; STAGE_ANDROID="/sdcard/Download/CortexSelfUserTestStage"; SHELL_STAGE="/data/local/tmp/cortex-self-user-test"
mkdir -p "$STAGE_TERMUX"; cp -f "$APP_APK" "$STAGE_TERMUX/cortex-app-debug.apk"; cp -f "$TEST_APK" "$STAGE_TERMUX/cortex-test-debug.apk"

echo; echo "[2/6] Installing update-in-place app and test APK..."
rish -c "rm -rf '$SHELL_STAGE'; mkdir -p '$SHELL_STAGE'; cp '$STAGE_ANDROID/cortex-app-debug.apk' '$SHELL_STAGE/cortex-app-debug.apk'; cp '$STAGE_ANDROID/cortex-test-debug.apk' '$SHELL_STAGE/cortex-test-debug.apk'; chmod 644 '$SHELL_STAGE/'*.apk; ls -lh '$SHELL_STAGE/'*.apk"
set +e
APP_INSTALL="$(rish -c "pm install -r -t -d '$SHELL_STAGE/cortex-app-debug.apk'" 2>&1 | tr -d '\r')"; APP_STATUS=$?
TEST_INSTALL="$(rish -c "pm install -r -t -d '$SHELL_STAGE/cortex-test-debug.apk'" 2>&1 | tr -d '\r')"; TEST_STATUS=$?
set -e
echo "app install output:"; echo "${APP_INSTALL:-<no output>}"; [[ $APP_STATUS -eq 0 && "$APP_INSTALL" == *Success* ]] || { echo "ERROR: app APK install failed"; exit 8; }
echo "test install output:"; echo "${TEST_INSTALL:-<no output>}"; [[ $TEST_STATUS -eq 0 && "$TEST_INSTALL" == *Success* ]] || { echo "ERROR: instrumentation APK install failed"; exit 9; }
INSTRUMENTATION="$(rish -c "pm list instrumentation" 2>&1 | tr -d '\r')"; echo "$INSTRUMENTATION" | grep -F "com.kareem.cortex.test/androidx.test.runner.AndroidJUnitRunner" >/dev/null || { echo "ERROR: AndroidJUnitRunner not registered"; echo "$INSTRUMENTATION"; exit 10; }
echo "instrumentation: registered"; mkdir -p "$ROOT/build"

run_test(){ local label="$1" cls="$2" log="$3"; echo; echo "$label"; set +e; rish -c "am instrument -w -r -e class $cls com.kareem.cortex.test/androidx.test.runner.AndroidJUnitRunner" | tee "$log"; local s=${PIPESTATUS[0]}; set -e; return $s; }
V1=0; V2=0; V3=0
run_test "[3/6] Running V1 runtime/navigation/surface health..." "com.kareem.cortex.FullApplicationSelfUserTest" "$ROOT/build/instrumented-self-user-test-v1-console.txt" || V1=$?
run_test "[4/6] Running V2 Cognitive/Product Adjudication..." "com.kareem.cortex.CognitiveProductAdjudicationTest" "$ROOT/build/instrumented-self-user-test-v2-console.txt" || V2=$?
run_test "[5/6] Running V3 Teacher/Student Cognitive Differential..." "com.kareem.cortex.TeacherStudentCognitiveDifferentialTest" "$ROOT/build/instrumented-self-user-test-v3-console.txt" || V3=$?

echo; echo "[6/6] Exporting combined evidence package..."
LATEST_V1="$(rish -c "ls -td /sdcard/Android/data/com.kareem.cortex/files/self-user-test/FullCortexSelfUserTest_* 2>/dev/null | head -n 1" | tr -d '\r')"
LATEST_V2="$(rish -c "ls -td /sdcard/Android/data/com.kareem.cortex/files/self-user-test/CognitiveProductAdjudication_* 2>/dev/null | head -n 1" | tr -d '\r')"
LATEST_V3="$(rish -c "ls -td /sdcard/Android/data/com.kareem.cortex/files/self-user-test/TeacherStudentCognitiveDifferential_* 2>/dev/null | head -n 1" | tr -d '\r')"
[[ -n "$LATEST_V1" && -n "$LATEST_V2" && -n "$LATEST_V3" ]] || { echo "ERROR: report directory missing. V1=$LATEST_V1 V2=$LATEST_V2 V3=$LATEST_V3"; exit 6; }
STAMP="$(date +%Y%m%d_%H%M%S)"; BUNDLE="FullCortexInstrumentedReview_${STAMP}"; ANDROID_BUNDLE="/sdcard/Download/$BUNDLE"
rish -c "rm -rf '$ANDROID_BUNDLE'; mkdir -p '$ANDROID_BUNDLE'; cp -R '$LATEST_V1' '$ANDROID_BUNDLE/'; cp -R '$LATEST_V2' '$ANDROID_BUNDLE/'; cp -R '$LATEST_V3' '$ANDROID_BUNDLE/'; rm -rf '$SHELL_STAGE'"
cat > "$HOME/storage/downloads/$BUNDLE/README.txt" <<EOF
Cortex Instrumented Review Bundle
V1 = Android/runtime/navigation/surface health
V2 = Cognitive/Product Adjudication
V3 = Full-fidelity Teacher/Student Cognitive Differential
V3 redaction = NONE
V3 database_pre = teacher evidence world before student probes
V3 student_cases.json = Cortex retrieval + answer trace
V3 database_post = resulting AI job/model state
V1 exit: $V1
V2 exit: $V2
V3 exit: $V3
Git head: $(git rev-parse HEAD 2>/dev/null || true)
EOF
command -v zip >/dev/null 2>&1 || pkg install -y zip >/dev/null
cd "$HOME/storage/downloads"; rm -f "$BUNDLE.zip"; zip -qr "$BUNDLE.zip" "$BUNDLE"; rm -rf "$STAGE_TERMUX"
echo; echo "DONE"; echo "Bundle folder: Downloads/$BUNDLE"; echo "ZIP: Downloads/$BUNDLE.zip"; echo "V1=$V1 V2=$V2 V3=$V3"; echo; echo "Send $BUNDLE.zip back to ChatGPT. I will build the teacher answer from database_pre first, then reveal Cortex student_cases and compute the cognitive differential."
[[ $V1 -eq 0 ]] || exit "$V1"; [[ $V2 -eq 0 ]] || exit "$V2"; exit "$V3"
