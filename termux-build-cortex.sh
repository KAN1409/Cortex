#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_DIR="${CORTEX_REPO_DIR:-$HOME/Cortex}"
APK_SRC="$REPO_DIR/app/build/outputs/apk/debug/app-debug.apk"
APK_OUT="/sdcard/Download/Cortex-v50-debug.apk"
BUILD_LOG="$REPO_DIR/termux-build-last.log"
BUILD_LOG_OUT="/sdcard/Download/Cortex-build-last.log"
CLEAN_BUILD="${CORTEX_CLEAN_BUILD:-0}"
AUTO_INSTALL="${CORTEX_AUTO_INSTALL:-0}"
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
CURRENT_REF="$(git branch --show-current 2>/dev/null || true)"
TARGET_REF="${CORTEX_BUILD_REF:-${CURRENT_REF:-main}}"
[ -n "$TARGET_REF" ] || TARGET_REF=main
log "Updating Cortex source: $TARGET_REF"
git fetch origin "$TARGET_REF"

# Preserve local tracked edits instead of deleting them, then build exact origin/<target>.
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  log "Preserving local tracked edits before sync"
  git stash push -m "$AUTO_STASH_NAME" >/dev/null
  AUTO_STASHED=1
  printf 'Saved local edits as git stash: %s\n' "$AUTO_STASH_NAME"
fi

git checkout "$TARGET_REF"
git pull --ff-only origin "$TARGET_REF"

if [ -z "${ANDROID_HOME:-}" ]; then for d in "$HOME/android-sdk" "$PREFIX/share/android-sdk" "$HOME/Android/Sdk"; do if [ -d "$d" ]; then export ANDROID_HOME="$d"; break; fi; done; fi
[ -n "${ANDROID_HOME:-}" ] || fail "ANDROID_HOME is not set and Android SDK was not found."
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ -z "${JAVA_HOME:-}" ]; then JBIN="$(readlink -f "$(command -v java)")"; export JAVA_HOME="$(dirname "$(dirname "$JBIN")")"; fi
mkdir -p "$HOME/.gradle"
AAPT_PROP="android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
touch "$HOME/.gradle/gradle.properties"
if grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties"; then sed -i "s|^android.aapt2FromMavenOverride=.*|$AAPT_PROP|" "$HOME/.gradle/gradle.properties"; else printf '%s\n' "$AAPT_PROP" >> "$HOME/.gradle/gradle.properties"; fi

log "Java: $(java -version 2>&1 | head -n 1)"
log "Android SDK: $ANDROID_HOME"
log "AAPT2: $(aapt2 version 2>&1 | head -n 1)"
if [ "$CLEAN_BUILD" = "1" ]; then
  log "Removing previous build outputs for a clean rebuild"
  rm -rf "$REPO_DIR/app/build" "$REPO_DIR/build"
fi
log "Building Cortex v50 from $TARGET_REF"
rm -f "$BUILD_LOG"
set +e
if [ -x ./gradlew ]; then
  if [ "$CLEAN_BUILD" = "1" ]; then ./gradlew :app:clean :app:assembleDebug --stacktrace --console=plain 2>&1 | tee "$BUILD_LOG"; else ./gradlew :app:assembleDebug --stacktrace --console=plain 2>&1 | tee "$BUILD_LOG"; fi
  BUILD_RC=${PIPESTATUS[0]}
else
  if [ "$CLEAN_BUILD" = "1" ]; then gradle :app:clean :app:assembleDebug --stacktrace --console=plain 2>&1 | tee "$BUILD_LOG"; else gradle :app:assembleDebug --stacktrace --console=plain 2>&1 | tee "$BUILD_LOG"; fi
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
log "Copying APK to Downloads"
cp -f "$APK_SRC" "$APK_OUT"
sha256sum "$APK_OUT" | tee "$APK_OUT.sha256"
log "Build complete"
printf 'APK: %s\n' "$APK_OUT"

if [ "$AUTO_INSTALL" = "1" ]; then
  if command -v rish >/dev/null 2>&1; then
    log "Installing APK through Shizuku/rish"
    rish -c "cp '$APK_OUT' /data/local/tmp/Cortex-v50-debug.apk && chmod 644 /data/local/tmp/Cortex-v50-debug.apk && pm install -r /data/local/tmp/Cortex-v50-debug.apk"
  else
    printf 'CORTEX_AUTO_INSTALL=1 requested, but rish is not available. APK is ready in Downloads.\n' >&2
  fi
else
  printf 'Install over the existing Cortex build to preserve app data.\n'
fi
if [ "$AUTO_STASHED" = "1" ]; then printf 'Local pre-build edits were preserved in git stash: %s\n' "$AUTO_STASH_NAME"; fi
