#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$HOME/Cortex"
BRANCH="major/cortex-brain-product-v5"
PKG="com.kareem.cortex"
STAMP="$(date +%s)"
WT="$HOME/Cortex-v55-major-$STAMP"
LOG="$HOME/cortex-v55-major-build-$STAMP.log"
APK_OUT="$HOME/Cortex-v55-major.apk"

fail(){ echo "❌ $*"; exit 1; }
cleanup(){ git -C "$ROOT" worktree remove --force "$WT" >/dev/null 2>&1 || true; }
trap cleanup EXIT

command -v git >/dev/null || fail "git missing"
command -v gradle >/dev/null || fail "gradle missing"
command -v rish >/dev/null || fail "rish missing"
[ -d "$ROOT/.git" ] || fail "$ROOT is not a Git repository"
[ -f "$ROOT/app/cortex-debug.keystore" ] || fail "Persistent Cortex signer missing"

echo "===== FETCH CORTEX V55 MAJOR ====="
git -C "$ROOT" fetch origin "$BRANCH"
HEAD_SHA="$(git -C "$ROOT" rev-parse FETCH_HEAD)"
echo "HEAD=$HEAD_SHA"

echo "===== CLEAN DETACHED BUILD TREE ====="
git -C "$ROOT" worktree add --detach "$WT" "$HEAD_SHA"
cp -p "$ROOT/app/cortex-debug.keystore" "$WT/app/cortex-debug.keystore"
cd "$WT"

grep -q "versionCode 55" app/build.gradle || fail "versionCode 55 missing"
grep -q "versionName '3.0-brain-v55'" app/build.gradle || fail "v55 versionName missing"
grep -Eq 'DEFAULT_AUTHORITY_MODE[[:space:]]*=[[:space:]]*CognitiveAuthorityMode\.V2_PRIMARY' app/src/main/java/com/kareem/cortex/CognitiveFeatureFlags.java || fail "V2_PRIMARY default missing"
grep -q 'android:name=".CortexLocalBusService"' app/src/main/AndroidManifest.xml || fail "Relay ingress service missing"
! grep -q 'android:name=".NotificationCaptureService"' app/src/main/AndroidManifest.xml || fail "Direct Cortex notification capture must stay disabled"
test -f app/src/main/java/com/kareem/cortex/BrainSituationStore.java || fail "BrainSituationStore missing"
test -f app/src/main/java/com/kareem/cortex/RelayPerceptionContext.java || fail "Relay perception adapter missing"

mkdir -p "$HOME/.gradle"
PROP="android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
if grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties" 2>/dev/null; then
  sed -i "s|^android.aapt2FromMavenOverride=.*|$PROP|" "$HOME/.gradle/gradle.properties"
else
  echo "$PROP" >> "$HOME/.gradle/gradle.properties"
fi

echo "===== BUILD SIGNED UPDATE ====="
gradle :app:assembleDebug --console=plain >"$LOG" 2>&1 || { tail -n 100 "$LOG"; fail "BUILD FAILED"; }
tail -n 20 "$LOG"

APK="$WT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || fail "APK not produced"
cp -f "$APK" "$APK_OUT"
echo "APK=$APK_OUT"
sha256sum "$APK_OUT"

echo "===== INSTALL UPDATE-IN-PLACE ====="
cat "$APK_OUT" | rish -c '
cat > /data/local/tmp/Cortex-v55-major.apk &&
chmod 644 /data/local/tmp/Cortex-v55-major.apk &&
pm install -r /data/local/tmp/Cortex-v55-major.apk
' | tee "$HOME/cortex-v55-install-result.txt"
grep -q 'Success' "$HOME/cortex-v55-install-result.txt" || fail "pm install -r did not return Success"

echo "===== START ====="
rish -c "logcat -b crash -c; am force-stop $PKG; am start -W -n $PKG/.CompactTodayActivity" >/dev/null
sleep 5

echo "===== VERIFY ====="
VERSION="$(rish -c "dumpsys package $PKG" | grep -E 'versionCode=|versionName=' | head -n2)"
printf '%s\n' "$VERSION"
echo "$VERSION" | grep -q 'versionCode=55' || fail "Installed Cortex is not v55"
echo "$VERSION" | grep -q 'versionName=3.0-brain-v55' || fail "Installed Cortex versionName mismatch"

rish -c "dumpsys package $PKG" | grep -m1 'CortexLocalBusService' >/dev/null || fail "CortexLocalBusService not installed"

CRASH="$(rish -c 'logcat -b crash -d -v threadtime' | tail -n 80)"
if [ -n "$(printf '%s' "$CRASH" | tr -d '[:space:]')" ]; then
  printf '%s\n' "$CRASH"
  fail "Crash buffer is not empty"
fi

echo "===== RESULT ====="
echo "✅ CORTEX_V55_MAJOR_INSTALLED"
echo "commit=$HEAD_SHA"
echo "apk=$APK_OUT"
echo "build_log=$LOG"
