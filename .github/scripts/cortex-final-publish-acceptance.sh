#!/usr/bin/env sh
set -eu

ARTIFACTS="publish-artifacts"
APP="app/build/outputs/apk/debug/app-debug.apk"
TEST="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
PKG="com.kareem.cortex"
TEST_PKG="com.kareem.cortex.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.kareem.cortex.CortexPublishAcceptanceTest"

mkdir -p "$ARTIFACTS"

stage() {
  echo "RUNTIME_STAGE|$1"
  printf '%s\n' "$1" >> "$ARTIFACTS/runtime_stages.txt"
}

collect_diagnostics() {
  adb get-state > "$ARTIFACTS/adb_state.txt" 2>&1 || true
  adb shell getprop sys.boot_completed > "$ARTIFACTS/boot_completed.txt" 2>&1 || true
  adb shell pm list instrumentation > "$ARTIFACTS/instrumentations.txt" 2>&1 || true
  adb shell dumpsys package "$PKG" > "$ARTIFACTS/package.txt" 2>&1 || true
  adb shell dumpsys meminfo "$PKG" > "$ARTIFACTS/meminfo.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$ARTIFACTS/activities.txt" 2>&1 || true
  adb shell run-as "$PKG" sh -c 'ls -la databases files shared_prefs 2>/dev/null' > "$ARTIFACTS/app_storage.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$ARTIFACTS/logcat.txt" 2>&1 || true
}
trap collect_diagnostics EXIT

stage DEVICE_READY
adb wait-for-device
test "$(adb get-state)" = "device"
test "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1"
adb shell input keyevent 82 >/dev/null 2>&1 || true
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c || true

stage FRESH_INSTALL
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb uninstall "$TEST_PKG" >/dev/null 2>&1 || true
adb install "$APP" | tee "$ARTIFACTS/install_app.txt"
adb install -r -t "$TEST" | tee "$ARTIFACTS/install_test.txt"
adb shell pm path "$PKG" | tee "$ARTIFACTS/app_package_path.txt"
adb shell pm path "$TEST_PKG" | tee "$ARTIFACTS/test_package_path.txt"
grep -q '^package:' "$ARTIFACTS/app_package_path.txt"
grep -q '^package:' "$ARTIFACTS/test_package_path.txt"
adb shell pm list instrumentation | tee "$ARTIFACTS/instrumentations_after_install.txt"
grep -Fq "instrumentation:${TEST_PKG}/${RUNNER} (target=${PKG})" "$ARTIFACTS/instrumentations_after_install.txt"

stage FRESH_LAUNCH
adb shell am start -W -S -n "$PKG/.NowActivity" > "$ARTIFACTS/launch_timing.txt"
cat "$ARTIFACTS/launch_timing.txt"
grep -Eq 'Status: ok|Complete' "$ARTIFACTS/launch_timing.txt"
adb shell pidof "$PKG" > "$ARTIFACTS/pid_after_launch.txt"
test -s "$ARTIFACTS/pid_after_launch.txt"
adb exec-out screencap -p > "$ARTIFACTS/fresh_launch.png" || true

stage INSTRUMENTATION_DISCOVERED
echo "INSTRUMENTATION_TARGET|${TEST_PKG}/${RUNNER}|class=${TEST_CLASS}" | tee "$ARTIFACTS/instrumentation_target.txt"

stage INSTRUMENTATION_RUNNING
set +e
adb shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  "${TEST_PKG}/${RUNNER}" \
  > "$ARTIFACTS/instrumentation.txt" 2>&1
TEST_EXIT=$?
set -e
cat "$ARTIFACTS/instrumentation.txt"
echo "INSTRUMENTATION_EXIT|$TEST_EXIT" | tee "$ARTIFACTS/instrumentation_exit.txt"

if [ "$TEST_EXIT" -ne 0 ]; then
  echo "Instrumentation failed with exit $TEST_EXIT"
  exit "$TEST_EXIT"
fi

grep -q 'INSTRUMENTATION_CODE: -1' "$ARTIFACTS/instrumentation.txt"
grep -q 'PUBLISH_SIM|PASS|fresh_runtime_launcher' "$ARTIFACTS/instrumentation.txt"
grep -q 'PUBLISH_SIM|PASS|ui_surface_matrix' "$ARTIFACTS/instrumentation.txt"
grep -q 'PUBLISH_SIM|PASS|office_document_extractors' "$ARTIFACTS/instrumentation.txt"
grep -q 'PUBLISH_SIM|PASS|database_quick_check_and_production_self_test' "$ARTIFACTS/instrumentation.txt"
stage INSTRUMENTATION_PASS

stage PERSISTENCE_BEFORE_UPDATE
adb shell run-as "$PKG" cat shared_prefs/cortex_publish_acceptance.xml > "$ARTIFACTS/persistence_before.xml"
cat "$ARTIFACTS/persistence_before.xml"
grep -q 'PERSIST_ME_ACROSS_REINSTALL' "$ARTIFACTS/persistence_before.xml"

stage UPDATE_IN_PLACE
adb install -r "$APP" > "$ARTIFACTS/reinstall.txt"
cat "$ARTIFACTS/reinstall.txt"
grep -q 'Success' "$ARTIFACTS/reinstall.txt"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.NowActivity" > "$ARTIFACTS/relaunch_after_update.txt"
cat "$ARTIFACTS/relaunch_after_update.txt"
adb shell pidof "$PKG" > "$ARTIFACTS/pid_after_update.txt"
test -s "$ARTIFACTS/pid_after_update.txt"
adb shell run-as "$PKG" cat shared_prefs/cortex_publish_acceptance.xml > "$ARTIFACTS/persistence_after.xml"
cat "$ARTIFACTS/persistence_after.xml"
grep -q 'PERSIST_ME_ACROSS_REINSTALL' "$ARTIFACTS/persistence_after.xml"
cmp "$ARTIFACTS/persistence_before.xml" "$ARTIFACTS/persistence_after.xml"
adb exec-out screencap -p > "$ARTIFACTS/after_update.png" || true
stage UPDATE_PERSISTENCE_PASS

stage EVIDENCE_COLLECTION
adb pull "/sdcard/Android/data/${PKG}/files/publish-simulation" "$ARTIFACTS/publish-simulation" || true
collect_diagnostics

grep -E 'FATAL EXCEPTION|ANR in com\.kareem\.cortex|Process: com\.kareem\.cortex.*AndroidRuntime' "$ARTIFACTS/logcat.txt" > "$ARTIFACTS/fatal_scan.txt" || true
if [ -s "$ARTIFACTS/fatal_scan.txt" ]; then
  echo 'Cortex fatal runtime event detected:'
  cat "$ARTIFACTS/fatal_scan.txt"
  exit 91
fi
stage NO_FATAL_ANR

printf 'PUBLISH_ACCEPTANCE|PASS|head=%s\n' "${GITHUB_SHA:-unknown}" > "$ARTIFACTS/final_result.txt"
cat "$ARTIFACTS/final_result.txt"
stage FINAL_PASS
