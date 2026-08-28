#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_DIR="${CORTEX_REPO_DIR:-$HOME/Cortex}"
BUILD_LOG="$REPO_DIR/termux-build-last.log"
BUILD_LOG_OUT="/sdcard/Download/Cortex-build-last.log"
CLEAN_BUILD="${CORTEX_CLEAN_BUILD:-0}"
AUTO_INSTALL="${CORTEX_AUTO_INSTALL:-0}"
EXPECTED_CERT_SHA256="5c6550a070abe477dcad5f23f3f437e183bff8aeaeb6ac52e1beaa8243ee69a7"
AUTO_STASH_NAME="cortex-prebuild-$(date +%Y%m%d-%H%M%S)"
AUTO_STASHED=0

log(){ printf '\n==> %s\n' "$*"; }
fail(){ printf '\nERROR: %s\n' "$*" >&2; exit 1; }

log "Preparing Termux build environment"
command -v pkg >/dev/null || fail "Run this inside Termux"
MISSING=()
command -v git >/dev/null || MISSING+=(git)
command -v java >/dev/null || MISSING+=(openjdk-17)
command -v gradle >/dev/null || MISSING+=(gradle)
command -v aapt2 >/dev/null || MISSING+=(aapt2)
if [ ${#MISSING[@]} -gt 0 ]; then log "Installing: ${MISSING[*]}"; pkg update -y; pkg install -y "${MISSING[@]}"; fi

if [ ! -d "$REPO_DIR/.git" ]; then
  if [ -d "$HOME/Cortex/.git" ]; then REPO_DIR="$HOME/Cortex";
  elif [ -d "$HOME/cortex/.git" ]; then REPO_DIR="$HOME/cortex";
  else fail "Cortex repo not found. Set CORTEX_REPO_DIR or clone it to $HOME/Cortex first."; fi
fi
cd "$REPO_DIR"
TARGET_REF="${CORTEX_BUILD_REF:-main}"
log "Updating Cortex source: $TARGET_REF"
git fetch origin "$TARGET_REF"

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  log "Preserving local tracked edits before sync"
  git stash push -m "$AUTO_STASH_NAME" >/dev/null
  AUTO_STASHED=1
  printf 'Saved local edits as git stash: %s\n' "$AUTO_STASH_NAME"
fi

git checkout "$TARGET_REF"
git pull --ff-only origin "$TARGET_REF"

log "Running Cortex repository audit"
[ -f "$REPO_DIR/scripts/cortex-repo-audit.sh" ] || fail "Repository audit script is missing"
if ! bash "$REPO_DIR/scripts/cortex-repo-audit.sh" "$REPO_DIR"; then
  printf '\nCORTEX BUILD STOPPED: repository audit failed before Gradle.\n' >&2
  if [ "$AUTO_STASHED" = "1" ]; then printf 'Local pre-build edits remain safely stashed as: %s\n' "$AUTO_STASH_NAME" >&2; fi
  exit 2
fi

if [ -z "${ANDROID_HOME:-}" ]; then for d in "$HOME/android-sdk" "$PREFIX/share/android-sdk" "$HOME/Android/Sdk"; do if [ -d "$d" ]; then export ANDROID_HOME="$d"; break; fi; done; fi
[ -n "${ANDROID_HOME:-}" ] || fail "ANDROID_HOME is not set and Android SDK was not found."
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ -z "${JAVA_HOME:-}" ]; then JBIN="$(readlink -f "$(command -v java)")"; export JAVA_HOME="$(dirname "$(dirname "$JBIN")")"; fi
mkdir -p "$HOME/.gradle"
AAPT_PROP="android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
touch "$HOME/.gradle/gradle.properties"
if grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties"; then sed -i "s|^android.aapt2FromMavenOverride=.*|$AAPT_PROP|" "$HOME/.gradle/gradle.properties"; else printf '%s\n' "$AAPT_PROP" >> "$HOME/.gradle/gradle.properties"; fi

APKSIGNER="${APKSIGNER:-}"
if [ -z "$APKSIGNER" ]; then APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner 2>/dev/null | sort -V | tail -n1 || true)"; fi
if [ -z "$APKSIGNER" ]; then APKSIGNER="$(command -v apksigner 2>/dev/null || true)"; fi
[ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ] || fail "apksigner not found; signer verification is mandatory"

VERSION_NAME="$(sed -nE "s/^[[:space:]]*versionName[[:space:]]+['\"]([^'\"]+)['\"].*/\1/p" app/build.gradle | head -n 1)"
[ -n "$VERSION_NAME" ] || VERSION_NAME="debug"
APK_SRC="$REPO_DIR/app/build/outputs/apk/debug/app-debug.apk"
APK_OUT="/sdcard/Download/Cortex-${VERSION_NAME}-debug.apk"
INSTALL_TMP="/data/local/tmp/Cortex-debug.apk"

log "Java: $(java -version 2>&1 | head -n 1)"
log "Android SDK: $ANDROID_HOME"
log "AAPT2: $(aapt2 version 2>&1 | head -n 1)"
if [ "$CLEAN_BUILD" = "1" ]; then
  log "Removing previous build outputs for a clean rebuild"
  rm -rf "$REPO_DIR/app/build" "$REPO_DIR/build"
fi
log "Building Cortex ${VERSION_NAME} from $TARGET_REF"
log "Compile gate: debug APK + instrumented-test Java sources"
rm -f "$BUILD_LOG"
set +e
if [ -x ./gradlew ]; then
  if [ "$CLEAN_BUILD" = "1" ]; then ./gradlew :app:clean :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac --stacktrace --console=plain 2>&1 | tee "$BUILD_LOG"; else ./gradlew :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac --stacktrace --console=plain 2>&1 | tee "$BUILD_LOG"; fi
  BUILD_RC=${PIPESTATUS[0]}
else
  if [ "$CLEAN_BUILD" = "1" ]; then gradle :app:clean :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac --stacktrace --console=plain 2>&1 | tee "$BUILD_LOG"; else gradle :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac --stacktrace --console=plain 2>&1 | tee "$BUILD_LOG"; fi
  BUILD_RC=${PIPESTATUS[0]}
fi
set -e
mkdir -p /sdcard/Download
cp -f "$BUILD_LOG" "$BUILD_LOG_OUT" 2>/dev/null || true
if [ "$BUILD_RC" -ne 0 ]; then
  printf '\n================ CORTEX BUILD ERROR SUMMARY ================\n' >&2
  grep -n -E '(^|[[:space:]])error:|FAILURE:|Execution failed for task|What went wrong:|Caused by:|cannot find symbol|method .* cannot be applied|incompatible types|package .* does not exist' "$BUILD_LOG" 2>/dev/null | tail -n 100 >&2 || true
  printf '=============================================================\n' >&2
  printf 'Full log: %s\n' "$BUILD_LOG_OUT" >&2
  if [ "$AUTO_STASHED" = "1" ]; then printf 'Local pre-build edits remain safely stashed as: %s\n' "$AUTO_STASH_NAME" >&2; fi
  exit "$BUILD_RC"
fi

[ -f "$APK_SRC" ] || fail "Build finished but APK was not found at $APK_SRC"
log "Verifying permanent Cortex signer"
VERIFY_OUTPUT="$($APKSIGNER verify --verbose --print-certs "$APK_SRC")"
printf '%s\n' "$VERIFY_OUTPUT"
ACTUAL_CERT_SHA256="$(printf '%s\n' "$VERIFY_OUTPUT" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
[ "$ACTUAL_CERT_SHA256" = "$EXPECTED_CERT_SHA256" ] || fail "Unexpected Cortex signer. Expected $EXPECTED_CERT_SHA256, got ${ACTUAL_CERT_SHA256:-<missing>}. No install attempted."
printf 'Permanent Cortex signer verified: %s\n' "$ACTUAL_CERT_SHA256"

log "Copying APK to Downloads"
cp -f "$APK_SRC" "$APK_OUT"
sha256sum "$APK_OUT" | tee "$APK_OUT.sha256"
log "Build complete"
printf 'APK: %s\n' "$APK_OUT"

if [ "$AUTO_INSTALL" = "1" ]; then
  if command -v rish >/dev/null 2>&1; then
    log "Installing APK update-in-place through Shizuku/rish"
    INSTALL_OUT="$(rish -c "cp '$APK_OUT' '$INSTALL_TMP' && chmod 644 '$INSTALL_TMP' && pm install -r '$INSTALL_TMP'; rc=\$?; rm -f '$INSTALL_TMP'; exit \$rc" 2>&1)" || { printf '%s\n' "$INSTALL_OUT"; fail "Cortex update-in-place failed; existing app was not uninstalled"; }
    printf '%s\n' "$INSTALL_OUT"
    printf '%s\n' "$INSTALL_OUT" | grep -q 'Success' || fail "PackageManager did not report Success"
    printf 'CORTEX_UPDATE_SUCCESS\n'
  else
    printf 'CORTEX_AUTO_INSTALL=1 requested, but rish is not available. APK is ready in Downloads.\n' >&2
  fi
else
  printf 'Install over the existing Cortex build to preserve app data.\n'
fi
if [ "$AUTO_STASHED" = "1" ]; then printf 'Local pre-build edits were preserved in git stash: %s\n' "$AUTO_STASH_NAME"; fi
