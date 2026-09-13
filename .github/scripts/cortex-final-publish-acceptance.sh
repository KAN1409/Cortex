#!/usr/bin/env sh
set -eu

mkdir -p publish-artifacts
adb wait-for-device
adb shell input keyevent 82 || true
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

APP="app/build/outputs/apk/debug/app-debug.apk"
TEST="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

echo '=== FRESH INSTALL ==='
adb uninstall com.kareem.cortex >/dev/null 2>&1 || true
adb install "$APP"
adb shell pm clear com.kareem.cortex
adb install -r "$APP"
adb install -r "$TEST"

echo '=== LAUNCH TIMING ==='
adb shell am start -W -S -n com.kareem.cortex/.NowActivity > publish-artifacts/launch_timing.txt
cat publish-artifacts/launch_timing.txt

echo '=== INSTRUMENTED FINAL PUBLISH SUITE ==='
set +e
adb shell am instrument -w -r \
  -e class com.kareem.cortex.CortexPublishAcceptanceTest \
  com.kareem.cortex.test/androidx.test.runner.AndroidJUnitRunner \
  > publish-artifacts/instrumentation.txt 2>&1
TEST_EXIT=$?
set -e
cat publish-artifacts/instrumentation.txt

echo '=== PERSISTENCE BEFORE REPLACE INSTALL ==='
adb shell run-as com.kareem.cortex cat shared_prefs/cortex_publish_acceptance.xml \
  > publish-artifacts/persistence_before.xml
cat publish-artifacts/persistence_before.xml
grep -q 'PERSIST_ME_ACROSS_REINSTALL' publish-artifacts/persistence_before.xml

echo '=== UPDATE-IN-PLACE / SAME SIGNING IDENTITY ==='
adb install -r "$APP" > publish-artifacts/reinstall.txt
cat publish-artifacts/reinstall.txt
adb shell am force-stop com.kareem.cortex
adb shell am start -W -n com.kareem.cortex/.NowActivity > publish-artifacts/relaunch_after_update.txt
cat publish-artifacts/relaunch_after_update.txt
adb shell run-as com.kareem.cortex cat shared_prefs/cortex_publish_acceptance.xml \
  > publish-artifacts/persistence_after.xml
cat publish-artifacts/persistence_after.xml
grep -q 'PERSIST_ME_ACROSS_REINSTALL' publish-artifacts/persistence_after.xml
cmp publish-artifacts/persistence_before.xml publish-artifacts/persistence_after.xml

echo '=== PACKAGE / DB / MEMORY DIAGNOSTICS ==='
adb shell dumpsys package com.kareem.cortex > publish-artifacts/package.txt
adb shell dumpsys meminfo com.kareem.cortex > publish-artifacts/meminfo.txt || true
adb shell dumpsys activity activities > publish-artifacts/activities.txt || true
adb shell run-as com.kareem.cortex sh -c 'ls -la databases files shared_prefs 2>/dev/null' > publish-artifacts/app_storage.txt || true

echo '=== SCREENSHOTS + FIXTURES ==='
adb pull /sdcard/Android/data/com.kareem.cortex/files/publish-simulation publish-artifacts/publish-simulation || true

echo '=== LOGCAT SAFETY GATE ==='
adb logcat -d -v threadtime > publish-artifacts/logcat.txt
grep -E 'FATAL EXCEPTION|ANR in com\.kareem\.cortex|Process: com\.kareem\.cortex.*AndroidRuntime' publish-artifacts/logcat.txt \
  > publish-artifacts/fatal_scan.txt || true
if [ -s publish-artifacts/fatal_scan.txt ]; then
  echo 'Cortex fatal runtime event detected:'
  cat publish-artifacts/fatal_scan.txt
  exit 91
fi

if [ "$TEST_EXIT" -ne 0 ]; then
  echo "Instrumentation failed with exit $TEST_EXIT"
  exit "$TEST_EXIT"
fi

grep -q 'PUBLISH_SIM|PASS|fresh_runtime_launcher' publish-artifacts/instrumentation.txt
grep -q 'PUBLISH_SIM|PASS|ui_surface_matrix' publish-artifacts/instrumentation.txt
grep -q 'PUBLISH_SIM|PASS|office_document_extractors' publish-artifacts/instrumentation.txt
grep -q 'PUBLISH_SIM|PASS|database_quick_check_and_production_self_test' publish-artifacts/instrumentation.txt
echo "PUBLISH_ACCEPTANCE|PASS|head=${GITHUB_SHA}" > publish-artifacts/final_result.txt
cat publish-artifacts/final_result.txt
