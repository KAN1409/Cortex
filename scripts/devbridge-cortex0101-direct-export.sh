#!/data/data/com.termux/files/usr/bin/bash
set -u

ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
CONTROL="$ROOT/control"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
SOURCE_REF="rebuild/cortex-v1"
TRIGGER_PATH=".devbridge/cortex0101-direct-export.json"
STATE="$ROOT/cortex0101-direct-exported.txt"
LOCK="$ROOT/cortex0101-direct-export.lock"
LOG="$ROOT/cortex0101-direct-export.log"
SIGNER="$LOCAL_REPO/app/cortex-debug.keystore"
EXPECTED_CERT="5c6550a070abe477dcad5f23f3f437e183bff8aeaeb6ac52e1beaa8243ee69a7"
JOB="job_20260830_cortex0101_direct_export_01"

[ -d "$CONTROL/.git" ] || exit 0
mkdir -p "$ROOT"
touch "$STATE"
grep -Fxq "$JOB" "$STATE" 2>/dev/null && exit 0
mkdir "$LOCK" 2>/dev/null || exit 0
trap 'rm -rf "$LOCK"' EXIT

git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || exit 0
TRIGGER="$ROOT/cortex0101-direct-export.json"
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$TRIGGER_PATH" > "$TRIGGER" 2>/dev/null || exit 0
[ "$(jq -r '.job_id // empty' "$TRIGGER" 2>/dev/null)" = "$JOB" ] || exit 0
[ "$(jq -r '.source_ref // empty' "$TRIGGER" 2>/dev/null)" = "$SOURCE_REF" ] || exit 0
[ "$(jq -r '.expected_signer_sha256 // empty' "$TRIGGER" 2>/dev/null)" = "$EXPECTED_CERT" ] || exit 0
[ -s "$SIGNER" ] || { printf '%s\n' 'signer_missing' > "$LOG"; exit 0; }

REMOTE="$(git -C "$CONTROL" remote get-url origin 2>/dev/null || true)"
[ -n "$REMOTE" ] || REMOTE="https://github.com/KAN1409/Cortex.git"
TMP="$(mktemp -d "$ROOT/cortex0101.direct.XXXXXX")" || exit 0
trap 'rm -rf "$TMP" "$LOCK"' EXIT

{
  echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  git clone --filter=blob:none --no-tags --single-branch --branch "$SOURCE_REF" "$REMOTE" "$TMP/repo"
  cp -f "$SIGNER" "$TMP/repo/app/cortex-debug.keystore"
  chmod 600 "$TMP/repo/app/cortex-debug.keystore" 2>/dev/null || true

  if [ -z "${ANDROID_HOME:-}" ]; then
    for d in "$HOME/android-sdk" "$PREFIX/share/android-sdk" "$HOME/Android/Sdk"; do
      if [ -d "$d" ]; then export ANDROID_HOME="$d"; break; fi
    done
  fi
  [ -n "${ANDROID_HOME:-}" ] || { echo ANDROID_HOME_NOT_FOUND; exit 51; }
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  if [ -z "${JAVA_HOME:-}" ] && command -v java >/dev/null 2>&1; then
    jbin="$(readlink -f "$(command -v java)")"
    export JAVA_HOME="$(dirname "$(dirname "$jbin")")"
  fi
  if command -v aapt2 >/dev/null 2>&1; then
    mkdir -p "$HOME/.gradle"; touch "$HOME/.gradle/gradle.properties"
    prop="android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
    if grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties"; then
      sed -i "s|^android.aapt2FromMavenOverride=.*|$prop|" "$HOME/.gradle/gradle.properties"
    else
      printf '%s\n' "$prop" >> "$HOME/.gradle/gradle.properties"
    fi
  fi

  cd "$TMP/repo"
  if [ -f ./gradlew ]; then chmod +x ./gradlew 2>/dev/null || true; ./gradlew --no-daemon --console=plain :app:exportCortex0101PermanentApk; else gradle --no-daemon --console=plain :app:exportCortex0101PermanentApk; fi
  echo "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$LOG" 2>&1
RC=$?
if [ $RC -eq 0 ] && grep -q 'CORTEX_SIGNED_APK_EXPORTED' "$LOG" && grep -q "permanent_signer_sha256=$EXPECTED_CERT" "$LOG"; then
  printf '%s\n' "$JOB" >> "$STATE"
fi
exit 0
