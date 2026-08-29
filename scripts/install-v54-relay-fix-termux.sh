#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="${HOME}/Cortex"
BRANCH="fix/v54-relay-ingress-brain-primary"
PKG="com.kareem.cortex"
STAMP="$(date +%s)"
WT="${HOME}/Cortex-v54-relay-fix-${STAMP}"
LOG="${HOME}/cortex-v54-relay-install-${STAMP}.log"

fail() { echo "❌ $*"; exit 1; }

command -v git >/dev/null || fail "git missing"
command -v gradle >/dev/null || fail "gradle missing"
command -v rish >/dev/null || fail "rish missing"
[ -d "${ROOT}/.git" ] || fail "${ROOT} is not a Git repository"
[ -f "${ROOT}/app/cortex-debug.keystore" ] || fail "Persistent Cortex signer missing"

echo "===== FETCH FIX ====="
git -C "$ROOT" fetch origin "$BRANCH"
HEAD_SHA="$(git -C "$ROOT" rev-parse FETCH_HEAD)"
echo "HEAD=$HEAD_SHA"

echo "===== CREATE CLEAN WORKTREE ====="
git -C "$ROOT" worktree add --detach "$WT" "$HEAD_SHA"
trap 'git -C "$ROOT" worktree remove --force "$WT" >/dev/null 2>&1 || true' EXIT
cp -p "$ROOT/app/cortex-debug.keystore" "$WT/app/cortex-debug.keystore"
cd "$WT"

echo "===== VERIFY SOURCE ====="
grep -q 'DEFAULT_AUTHORITY_MODE=CognitiveAuthorityMode.V2_PRIMARY' app/src/main/java/com/kareem/cortex/CognitiveFeatureFlags.java || fail "V2_PRIMARY default missing"
grep -q 'REVISION = "cognitive_004"' app/src/main/java/com/kareem/cortex/CognitiveSchema.java || fail "cognitive_004 missing"
grep -q 'versionCode 54' app/build.gradle || fail "versionCode 54 missing"
grep -q 'CortexLocalBusService' app/src/main/AndroidManifest.xml || fail "CortexLocalBusService missing from manifest"

echo "===== BUILD ====="
mkdir -p "$HOME/.gradle"
PROP="android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
if grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties" 2>/dev/null; then
  sed -i "s|^android.aapt2FromMavenOverride=.*|$PROP|" "$HOME/.gradle/gradle.properties"
else
  echo "$PROP" >> "$HOME/.gradle/gradle.properties"
fi

gradle :app:assembleDebug --console=plain >"$LOG" 2>&1 || { tail -n 100 "$LOG"; fail "BUILD FAILED"; }
tail -n 20 "$LOG"

APK="$WT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || fail "APK not produced"

echo "===== INSTALL UPDATE-IN-PLACE ====="
cat "$APK" | rish -c 'cat > /data/local/tmp/Cortex-v54-relay-fix.apk && chmod 644 /data/local/tmp/Cortex-v54-relay-fix.apk && pm install -r /data/local/tmp/Cortex-v54-relay-fix.apk'

echo "===== START CORTEX ====="
rish -c "logcat -b crash -c; am force-stop $PKG; am start -W -n $PKG/.CompactTodayActivity" >/dev/null
sleep 5

echo "===== VERIFY INSTALLED ====="
VERSION="$(rish -c "dumpsys package $PKG" | grep -E 'versionCode=|versionName=' | head -n 2)"
printf '%s\n' "$VERSION"
echo "$VERSION" | grep -q 'versionCode=54' || fail "Installed Cortex is not versionCode 54"

SERVICE="$(rish -c "dumpsys package $PKG" | grep -m1 'CortexLocalBusService' || true)"
[ -n "$SERVICE" ] || fail "CortexLocalBusService not visible in installed package"
echo "✅ CortexLocalBusService present"

echo "===== RELAY NOTIFICATION LISTENER ====="
LISTENERS="$(rish -c 'settings get secure enabled_notification_listeners' 2>/dev/null || true)"
printf '%s\n' "$LISTENERS" | tr ':' '\n' | grep 'com.kareem.secondbrain' || echo "⚠️ Cortex Relay notification listener is not enabled"

echo "===== MODEL ====="
rish -c "run-as $PKG cat shared_prefs/cortex_local_runtime.xml" 2>/dev/null | grep -E 'state|model_sha' || true

echo "===== CRASH BUFFER ====="
rish -c 'logcat -b crash -d -v threadtime' | tail -n 40

echo "===== RESULT ====="
echo "✅ CORTEX_V54_RELAY_FIX_INSTALLED"
echo "build_log=$LOG"
echo "commit=$HEAD_SHA"
