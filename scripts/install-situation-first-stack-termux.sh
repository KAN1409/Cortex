#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

CORTEX_ROOT="$HOME/Cortex"
CORTEX_BRANCH="fix/v54-relay-ingress-brain-primary"
RELAY_ROOT="$HOME/Second-Brain"
RELAY_BRANCH="fix/relay-mechanical-notification-noise"
RELAY_PKG="com.kareem.secondbrain"
CORTEX_PKG="com.kareem.cortex"
RELAY_KEYSTORE="$HOME/.cortex-signing/cortex-relay-release.jks"
RELAY_ALIAS="cortex-relay"
STAMP="$(date +%s)"
RELAY_WT="$HOME/Cortex-Relay-situation-first-$STAMP"
RELAY_LOG="$HOME/cortex-relay-situation-first-$STAMP.log"
SIGNED_RELAY="$HOME/Cortex-Relay-situation-first-$STAMP.apk"
OLD_RELAY="$HOME/.cortex-relay-installed-$STAMP.apk"

fail(){ echo "❌ $*"; exit 1; }
cleanup(){
  rm -f "$OLD_RELAY" >/dev/null 2>&1 || true
  git -C "$RELAY_ROOT" worktree remove --force "$RELAY_WT" >/dev/null 2>&1 || true
}
trap cleanup EXIT

for cmd in git rish apksigner; do command -v "$cmd" >/dev/null || fail "$cmd missing"; done
[ -d "$CORTEX_ROOT/.git" ] || fail "$CORTEX_ROOT is not a Git repository"
[ -d "$RELAY_ROOT/.git" ] || fail "$RELAY_ROOT is not a Git repository"
[ -f "$CORTEX_ROOT/app/cortex-debug.keystore" ] || fail "Persistent Cortex signer missing"
[ -f "$RELAY_KEYSTORE" ] || fail "Permanent Cortex Relay signer missing"

echo "===== 1/2 INSTALL CORTEX · SITUATION FIRST ====="
git -C "$CORTEX_ROOT" fetch origin "$CORTEX_BRANCH"
CORTEX_SHA="$(git -C "$CORTEX_ROOT" rev-parse FETCH_HEAD)"
git -C "$CORTEX_ROOT" show "$CORTEX_SHA:scripts/install-v54-relay-fix-termux.sh" | bash

echo
echo "===== 2/2 INSTALL CORTEX RELAY · NOISE FIX ====="
git -C "$RELAY_ROOT" fetch origin "$RELAY_BRANCH"
RELAY_SHA="$(git -C "$RELAY_ROOT" rev-parse FETCH_HEAD)"
echo "Relay commit=$RELAY_SHA"
git -C "$RELAY_ROOT" worktree add --detach "$RELAY_WT" "$RELAY_SHA"
cd "$RELAY_WT"

echo "Building signed production-compatible Relay update…"
./gradlew :app:assembleRelease --console=plain >"$RELAY_LOG" 2>&1 || {
  tail -n 80 "$RELAY_LOG"
  fail "Relay build failed"
}

UNSIGNED="$(find "$RELAY_WT/app/build/outputs/apk/release" -maxdepth 1 -type f -name '*.apk' | head -n 1)"
[ -n "$UNSIGNED" ] && [ -f "$UNSIGNED" ] || fail "Relay release APK not produced"
ALIGNED="$UNSIGNED"
if command -v zipalign >/dev/null; then
  ALIGNED="$HOME/.cortex-relay-aligned-$STAMP.apk"
  zipalign -f -p 4 "$UNSIGNED" "$ALIGNED"
fi

echo "Signing Relay with the existing permanent identity."
echo "You may be asked for the keystore password once; it is never printed or stored."
apksigner sign \
  --ks "$RELAY_KEYSTORE" \
  --ks-key-alias "$RELAY_ALIAS" \
  --out "$SIGNED_RELAY" \
  "$ALIGNED" </dev/tty

apksigner verify --verbose --print-certs "$SIGNED_RELAY" >/dev/null || fail "New Relay signature verification failed"

INSTALLED_PATH="$(rish -c "pm path $RELAY_PKG" | sed -n 's/^package://p' | head -n1)"
[ -n "$INSTALLED_PATH" ] || fail "Installed Cortex Relay package not found"
rish -c "cat '$INSTALLED_PATH'" > "$OLD_RELAY" || fail "Could not read installed Relay APK for signer verification"

OLD_CERT="$(apksigner verify --print-certs "$OLD_RELAY" 2>/dev/null | awk -F': ' '/Signer #1 certificate SHA-256 digest/{print $2;exit}')"
NEW_CERT="$(apksigner verify --print-certs "$SIGNED_RELAY" 2>/dev/null | awk -F': ' '/Signer #1 certificate SHA-256 digest/{print $2;exit}')"
[ -n "$OLD_CERT" ] && [ "$OLD_CERT" = "$NEW_CERT" ] || fail "Relay signer mismatch — install blocked"
echo "✅ Relay signer matches installed app"

cat "$SIGNED_RELAY" | rish -c '
cat > /data/local/tmp/Cortex-Relay-situation-first.apk &&
chmod 644 /data/local/tmp/Cortex-Relay-situation-first.apk &&
pm install -r /data/local/tmp/Cortex-Relay-situation-first.apk
' | grep -q 'Success' || fail "Relay update-in-place install failed"
echo "✅ Relay updated in place"

rm -f "$ALIGNED" 2>/dev/null || true

rish -c "am force-stop $RELAY_PKG; monkey -p $RELAY_PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true"
sleep 2
rish -c "am force-stop $CORTEX_PKG; am start -W -n $CORTEX_PKG/.CompactTodayActivity >/dev/null"
sleep 4

LISTENERS="$(rish -c 'settings get secure enabled_notification_listeners' 2>/dev/null || true)"
printf '%s\n' "$LISTENERS" | tr ':' '\n' | grep -q "$RELAY_PKG" || fail "Relay Notification Access is not enabled"

echo
echo "===== STACK READY ====="
echo "✅ Cortex: situation-first Now + living Atlas + V2 PRIMARY"
echo "✅ Relay: Local Bus + deterministic mechanical-progress suppression"
echo "✅ update-in-place; app data preserved"
echo "Cortex commit=$CORTEX_SHA"
echo "Relay commit=$RELAY_SHA"
echo "Relay build log=$RELAY_LOG"
