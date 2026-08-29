#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PACKAGE="com.kareem.cortex"
EXPECTED_VERSION_CODE=51
EXPECTED_VERSION_NAME="1.0.0-v51-cognitive-relay-v2-candidate"
EXPECTED_CERT_SHA256="e869f4390c3508738758ed2d94d391e86e728a300c760afb8bc50add185987a8"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="${CORTEX_SIGNING_KEYSTORE:-$HOME/.config/mnemo/mnemo.keystore}"
KEY_ALIAS="${CORTEX_SIGNING_ALIAS:-mnemo}"
SIGNED_APK="${CORTEX_DEVICE_APK:-/sdcard/Download/Cortex-v51-cognitive-relay-v2-candidate.apk}"
INSTALLED_COPY="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/cortex-installed-base.apk"

fail() { echo "ERROR: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"; }

need rish
need sed
need grep
need bash
need sha256sum
[ -f "$KEYSTORE" ] || fail "Permanent Cortex keystore not found at $KEYSTORE"

APKSIGNER="${APKSIGNER:-}"
if [ -z "$APKSIGNER" ] && [ -n "${ANDROID_HOME:-}" ]; then
  APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner 2>/dev/null | sort -V | tail -n1 || true)"
fi
if [ -z "$APKSIGNER" ]; then
  APKSIGNER="$(find "$HOME/android-sdk/build-tools" -type f -name apksigner 2>/dev/null | sort -V | tail -n1 || true)"
fi
if [ -z "$APKSIGNER" ]; then APKSIGNER="$(command -v apksigner 2>/dev/null || true)"; fi
[ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ] || fail "apksigner not found"

cert_of_apk() {
  "$APKSIGNER" verify --print-certs "$1" 2>/dev/null \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
    | head -n1 | tr -d ':' | tr '[:upper:]' '[:lower:]'
}

resolve_installed_path() {
  local output="" pkg_path="" attempt
  for attempt in 1 2 3; do
    output="$(rish -c "pm path $PACKAGE" 2>/dev/null || true)"
    pkg_path="$(printf '%s\n' "$output" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
    [ -n "$pkg_path" ] && { printf '%s\n' "$pkg_path"; return 0; }
    sleep 1
  done
  return 1
}

copy_installed_base() {
  local pkg_path
  pkg_path="$(resolve_installed_path || true)"
  [ -n "$pkg_path" ] || fail "$PACKAGE is not visible to PackageManager"
  rm -f "$INSTALLED_COPY"
  rish -c "cat '$pkg_path'" > "$INSTALLED_COPY" || fail "Could not read installed Cortex APK"
  [ -s "$INSTALLED_COPY" ] || fail "Installed APK copy is empty"
}

installed_version_code() {
  rish -c "dumpsys package $PACKAGE" 2>/dev/null \
    | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n1
}

installed_version_name() {
  rish -c "dumpsys package $PACKAGE" 2>/dev/null \
    | sed -n 's/.*versionName=\([^[:space:]]*\).*/\1/p' | head -n1
}

echo "==> Guard 1/6: verify installed Cortex identity"
copy_installed_base
CURRENT_CERT="$(cert_of_apk "$INSTALLED_COPY")"
[ "$CURRENT_CERT" = "$EXPECTED_CERT_SHA256" ] \
  || fail "Installed Cortex signer mismatch. Expected $EXPECTED_CERT_SHA256, got ${CURRENT_CERT:-<missing>}. No install attempted."
CURRENT_CODE="$(installed_version_code)"
CURRENT_NAME="$(installed_version_name)"
[ -n "$CURRENT_CODE" ] || fail "Could not resolve installed Cortex versionCode"
if [ "$CURRENT_CODE" -gt "$EXPECTED_VERSION_CODE" ]; then
  fail "Installed Cortex versionCode=$CURRENT_CODE is newer than candidate=$EXPECTED_VERSION_CODE. No downgrade attempted."
fi
echo "Installed Cortex: versionCode=$CURRENT_CODE versionName=${CURRENT_NAME:-<unknown>}"
echo "Installed signer OK: $CURRENT_CERT"

echo
echo "==> Audit 2/6: repository safety audit"
cd "$ROOT_DIR"
bash scripts/cortex-repo-audit.sh

echo
echo "==> Build 3/6: combined cognitive + Relay V2 release"
if [ -x "$ROOT_DIR/gradlew" ]; then GRADLE="$ROOT_DIR/gradlew"; else need gradle; GRADLE="gradle"; fi
# Deliberately build-only: the separate cognitive major latency benchmark remains its own gate.
# This installer must not run connectedDebugAndroidTest or mutate that benchmark's status.
"$GRADLE" :app:assembleRelease --no-daemon --stacktrace
UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
if [ ! -f "$UNSIGNED_APK" ]; then UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"; fi
[ -f "$UNSIGNED_APK" ] || fail "Release APK not found after successful build"

echo
echo "==> Sign 4/6: existing permanent Cortex identity"
rm -f "$SIGNED_APK"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled false \
  --out "$SIGNED_APK" \
  "$UNSIGNED_APK"
SIGNED_CERT="$(cert_of_apk "$SIGNED_APK")"
[ "$SIGNED_CERT" = "$EXPECTED_CERT_SHA256" ] || fail "Candidate signer verification failed"
"$APKSIGNER" verify --verbose "$SIGNED_APK" >/dev/null || fail "Candidate APK signature verification failed"
echo "Candidate signer OK: $SIGNED_CERT"

echo
echo "==> Install 5/6: update-in-place only (NO UNINSTALL)"
TMP_REMOTE="/data/local/tmp/Cortex-v51-cognitive-relay-v2-candidate.apk"
cat "$SIGNED_APK" | rish -c "cat > '$TMP_REMOTE'" || fail "Failed to stage candidate APK"
INSTALL_OUT="$(rish -c "chmod 644 '$TMP_REMOTE'; pm install -r '$TMP_REMOTE'; rc=\$?; rm -f '$TMP_REMOTE'; exit \$rc" 2>&1)" || {
  printf '%s\n' "$INSTALL_OUT"
  fail "Update-in-place failed. Existing Cortex installation was left intact."
}
printf '%s\n' "$INSTALL_OUT"
printf '%s\n' "$INSTALL_OUT" | grep -q 'Success' || fail "PackageManager did not report Success"

echo
echo "==> Verify 6/6: installed combined candidate + permanent signer"
FINAL_CODE="$(installed_version_code)"
FINAL_NAME="$(installed_version_name)"
[ "$FINAL_CODE" = "$EXPECTED_VERSION_CODE" ] || fail "Installed versionCode mismatch: $FINAL_CODE"
[ "$FINAL_NAME" = "$EXPECTED_VERSION_NAME" ] || fail "Installed versionName mismatch: $FINAL_NAME"
copy_installed_base
FINAL_CERT="$(cert_of_apk "$INSTALLED_COPY")"
[ "$FINAL_CERT" = "$EXPECTED_CERT_SHA256" ] || fail "Post-install signer mismatch"
rm -f "$INSTALLED_COPY"

echo "versionCode=$FINAL_CODE"
echo "versionName=$FINAL_NAME"
echo "Installed signer OK: $FINAL_CERT"
echo "APK SHA-256: $(sha256sum "$SIGNED_APK" | awk '{print $1}')"
echo "APK: $SIGNED_APK"
echo
echo "CORTEX_COGNITIVE_RELAY_V2_CANDIDATE_UPDATE_SUCCESS"
