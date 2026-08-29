#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PACKAGE="com.kareem.cortex"
RELAY_BRANCH="integration/cognitive-relay-v2-v54"
EXPECTED_VERSION_CODE=54
EXPECTED_VERSION_NAME="2.0.1-cognitive-relay-v2-candidate"
EXPECTED_CERT_SHA256="e869f4390c3508738758ed2d94d391e86e728a300c760afb8bc50add185987a8"
SRC="${CORTEX_LOCAL_SOURCE:-$HOME/Cortex}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="${CORTEX_SNAPSHOT_TARGET:-$HOME/Cortex-Local-v53-Relay-v54-$STAMP}"
KEYSTORE="${CORTEX_SIGNING_KEYSTORE:-$HOME/.config/mnemo/mnemo.keystore}"
KEY_ALIAS="${CORTEX_SIGNING_ALIAS:-mnemo}"
SIGNED_APK="${CORTEX_DEVICE_APK:-/sdcard/Download/Cortex-v54-cognitive-relay-v2-local-snapshot.apk}"
INSTALLED_COPY="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/cortex-installed-v53.apk"

fail(){ echo; echo "ERROR: $*" >&2; exit 1; }
need(){ command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"; }

need git
need tar
need rish
need sed
need grep
need sha256sum
[ -d "$SRC/.git" ] || fail "Source repo not found at $SRC"
[ ! -e "$TARGET" ] || fail "Snapshot target already exists: $TARGET"
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

cert_of_apk(){
  "$APKSIGNER" verify --print-certs "$1" 2>/dev/null \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
    | head -n1 | tr -d ':' | tr '[:upper:]' '[:lower:]'
}

installed_version_code(){
  rish -c "dumpsys package $PACKAGE" 2>/dev/null \
    | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n1
}

installed_version_name(){
  rish -c "dumpsys package $PACKAGE" 2>/dev/null \
    | sed -n 's/.*versionName=\([^[:space:]]*\).*/\1/p' | head -n1
}

copy_installed_base(){
  local pkg_path
  pkg_path="$(rish -c "pm path $PACKAGE" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
  [ -n "$pkg_path" ] || fail "$PACKAGE is not visible to PackageManager"
  rm -f "$INSTALLED_COPY"
  rish -c "cat '$pkg_path'" > "$INSTALLED_COPY" || fail "Could not read installed Cortex APK"
  [ -s "$INSTALLED_COPY" ] || fail "Installed APK copy is empty"
}

extract_remote(){
  local path="$1"
  mkdir -p "$TARGET/$(dirname "$path")"
  git -C "$SRC" show "origin/$RELAY_BRANCH:$path" > "$TARGET/$path" \
    || fail "Could not extract $path from origin/$RELAY_BRANCH"
}

echo "===== 1. VERIFY INSTALLED CORTEX ====="
CURRENT_CODE="$(installed_version_code)"
CURRENT_NAME="$(installed_version_name)"
[ -n "$CURRENT_CODE" ] || fail "Could not resolve installed Cortex versionCode"
[ "$CURRENT_CODE" -le "$EXPECTED_VERSION_CODE" ] || fail "Installed versionCode=$CURRENT_CODE is newer than candidate=$EXPECTED_VERSION_CODE"
copy_installed_base
CURRENT_CERT="$(cert_of_apk "$INSTALLED_COPY")"
[ "$CURRENT_CERT" = "$EXPECTED_CERT_SHA256" ] \
  || fail "Installed signer mismatch. Expected $EXPECTED_CERT_SHA256, got ${CURRENT_CERT:-<missing>}"
echo "installed_versionCode=$CURRENT_CODE"
echo "installed_versionName=${CURRENT_NAME:-<unknown>}"
echo "installed_signer=$CURRENT_CERT"

echo
echo "===== 2. VERIFY LOCAL SOURCE IS PRESERVED ====="
echo "source=$SRC"
echo "source_head=$(git -C "$SRC" rev-parse HEAD)"
echo "source_branch=$(git -C "$SRC" rev-parse --abbrev-ref HEAD)"
echo "-- source status (informational; it is NOT modified) --"
git -C "$SRC" status --short || true

# These two files were clean in the audited local tree. Refuse rather than silently replacing an
# unreported local edit. app/build.gradle is intentionally allowed to be dirty and is patched only
# inside the snapshot so local Compose/Gradle work survives.
if ! git -C "$SRC" diff --quiet -- app/src/main/AndroidManifest.xml app/src/main/java/com/kareem/cortex/AdvancedSettingsActivity.java; then
  fail "Manifest or AdvancedSettings has local edits. Refusing to overwrite them in the snapshot without a reviewed merge."
fi

echo
echo "===== 3. FETCH RELAY V54 WITHOUT TOUCHING WORKTREE ====="
git -C "$SRC" fetch origin "$RELAY_BRANCH"
REMOTE_HEAD="$(git -C "$SRC" rev-parse "origin/$RELAY_BRANCH")"
echo "relay_ref=$RELAY_BRANCH"
echo "relay_head=$REMOTE_HEAD"

echo
echo "===== 4. CREATE ISOLATED SNAPSHOT ====="
mkdir -p "$TARGET"
(
  cd "$SRC"
  tar \
    --exclude='./.git' \
    --exclude='./app/build' \
    --exclude='./build' \
    --exclude='./.gradle' \
    --exclude='./.kotlin' \
    -cf - .
) | (cd "$TARGET" && tar -xf -)
echo "snapshot=$TARGET"

# Add only Relay/Local-Bus implementation files from the reviewed integration branch.
for path in \
  app/src/main/java/com/kareem/cortex/CortexLocalBusProtocolV1.java \
  app/src/main/java/com/kareem/cortex/CortexLocalBusProtocolV2.java \
  app/src/main/java/com/kareem/cortex/CortexConnectorRegistryV1.java \
  app/src/main/java/com/kareem/cortex/CortexLocalBusStoreV1.java \
  app/src/main/java/com/kareem/cortex/CortexConnectorIngestV1.java \
  app/src/main/java/com/kareem/cortex/CortexLocalBusService.java \
  app/src/main/java/com/kareem/cortex/CortexRelayBridgeV2.java \
  app/src/main/java/com/kareem/cortex/CortexRelayV2DiagnosticsActivity.java \
  app/src/androidTest/java/com/kareem/cortex/CortexLocalBusV1RegressionTest.java \
  app/src/androidTest/java/com/kareem/cortex/CortexLocalBusV2RegressionTest.java; do
  extract_remote "$path"
done

# The audited source had no local changes in these two files, so use the reviewed component/UI
# declarations verbatim rather than trying a fragile text insertion.
extract_remote app/src/main/AndroidManifest.xml
extract_remote app/src/main/java/com/kareem/cortex/AdvancedSettingsActivity.java

BUILD_FILE="$TARGET/app/build.gradle"
[ -f "$BUILD_FILE" ] || fail "Snapshot app/build.gradle missing"
# Preserve every local Gradle edit except candidate identity. The audited local source was v53/2.0.
grep -Eq '^[[:space:]]*versionCode[[:space:]]+53[[:space:]]*$' "$BUILD_FILE" \
  || fail "Local snapshot is not versionCode 53; refusing automatic version patch"
grep -Eq "^[[:space:]]*versionName[[:space:]]+'2\.0'[[:space:]]*$" "$BUILD_FILE" \
  || fail "Local snapshot is not versionName 2.0; refusing automatic version patch"
sed -i -E 's/^([[:space:]]*versionCode[[:space:]]+)53[[:space:]]*$/\154/' "$BUILD_FILE"
sed -i -E "s/^([[:space:]]*versionName[[:space:]]+)'2\.0'[[:space:]]*$/\1'2.0.1-cognitive-relay-v2-candidate'/" "$BUILD_FILE"

grep -Eq '^[[:space:]]*versionCode[[:space:]]+54[[:space:]]*$' "$BUILD_FILE" || fail "versionCode patch failed"
grep -Eq "versionName[[:space:]]+'2\.0\.1-cognitive-relay-v2-candidate'" "$BUILD_FILE" || fail "versionName patch failed"
grep -q 'CortexLocalBusService' "$TARGET/app/src/main/AndroidManifest.xml" || fail "Local Bus service missing from snapshot manifest"
grep -q 'CortexRelayV2DiagnosticsActivity' "$TARGET/app/src/main/AndroidManifest.xml" || fail "Relay diagnostics activity missing from snapshot manifest"

echo "Snapshot identity:"
grep -nE 'versionCode|versionName' "$BUILD_FILE" | head -n4

echo
echo "===== 5. COMPILE LOCAL SNAPSHOT ====="
cd "$TARGET"
if [ -x ./gradlew ]; then GRADLE=./gradlew; else need gradle; GRADLE=gradle; fi
"$GRADLE" \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:compileDebugAndroidTestJavaWithJavac \
  --stacktrace \
  --console=plain

UNSIGNED_APK="$TARGET/app/build/outputs/apk/release/app-release-unsigned.apk"
if [ ! -f "$UNSIGNED_APK" ]; then UNSIGNED_APK="$TARGET/app/build/outputs/apk/release/app-release.apk"; fi
[ -f "$UNSIGNED_APK" ] || fail "Release APK not found after successful snapshot build"

echo
echo "===== 6. SIGN WITH EXISTING PERMANENT CORTEX IDENTITY ====="
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
[ "$SIGNED_CERT" = "$EXPECTED_CERT_SHA256" ] || fail "Candidate signer verification failed: ${SIGNED_CERT:-<missing>}"
"$APKSIGNER" verify --verbose "$SIGNED_APK" >/dev/null || fail "Candidate APK signature verification failed"
echo "candidate_signer=$SIGNED_CERT"

echo
echo "===== 7. UPDATE IN PLACE ====="
TMP_REMOTE="/data/local/tmp/Cortex-v54-cognitive-relay-v2-local-snapshot.apk"
cat "$SIGNED_APK" | rish -c "cat > '$TMP_REMOTE'" || fail "Failed to stage candidate APK"
INSTALL_OUT="$(rish -c "chmod 644 '$TMP_REMOTE'; pm install -r '$TMP_REMOTE'; rc=\$?; rm -f '$TMP_REMOTE'; exit \$rc" 2>&1)" || {
  printf '%s\n' "$INSTALL_OUT"
  fail "Update-in-place failed. Existing Cortex installation was left intact."
}
printf '%s\n' "$INSTALL_OUT"
printf '%s\n' "$INSTALL_OUT" | grep -q 'Success' || fail "PackageManager did not report Success"

echo
echo "===== 8. VERIFY INSTALLED V54 + LOCAL BUS ====="
FINAL_CODE="$(installed_version_code)"
FINAL_NAME="$(installed_version_name)"
[ "$FINAL_CODE" = "$EXPECTED_VERSION_CODE" ] || fail "Installed versionCode mismatch: $FINAL_CODE"
[ "$FINAL_NAME" = "$EXPECTED_VERSION_NAME" ] || fail "Installed versionName mismatch: $FINAL_NAME"
copy_installed_base
FINAL_CERT="$(cert_of_apk "$INSTALLED_COPY")"
[ "$FINAL_CERT" = "$EXPECTED_CERT_SHA256" ] || fail "Post-install signer mismatch"
rm -f "$INSTALLED_COPY"

SERVICE_LINES="$(rish -c "dumpsys package $PACKAGE" 2>/dev/null | grep -A8 -B4 -E 'CortexLocalBusService|com.kareem.cortex.LOCAL_BUS_V1' || true)"
[ -n "$SERVICE_LINES" ] || fail "CortexLocalBusService is not visible in installed package"
printf '%s\n' "$SERVICE_LINES"

echo
echo "===== 9. START CORTEX THEN RELAY ====="
rish -c "am force-stop $PACKAGE; monkey -p $PACKAGE -c android.intent.category.LAUNCHER 1 >/dev/null" >/dev/null 2>&1 || true
sleep 2
rish -c "am force-stop com.kareem.secondbrain; monkey -p com.kareem.secondbrain -c android.intent.category.LAUNCHER 1 >/dev/null" >/dev/null 2>&1 || true
sleep 5

echo "versionCode=$FINAL_CODE"
echo "versionName=$FINAL_NAME"
echo "installed_signer=$FINAL_CERT"
echo "snapshot=$TARGET"
echo "APK=$SIGNED_APK"
echo "APK_SHA256=$(sha256sum "$SIGNED_APK" | awk '{print $1}')"
echo
echo "CORTEX_LOCAL_V53_RELAY_V54_SNAPSHOT_UPDATE_SUCCESS"
