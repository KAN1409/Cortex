#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_DIR="${CORTEX_REPO_DIR:-$HOME/Cortex}"
APK_SRC="$REPO_DIR/app/build/outputs/apk/debug/app-debug.apk"
APK_OUT="/sdcard/Download/Cortex-v43-debug.apk"

log(){ printf '\n==> %s\n' "$*"; }
fail(){ printf '\nERROR: %s\n' "$*" >&2; exit 1; }

log "Preparing Termux build environment"
command -v pkg >/dev/null || fail "Run this inside Termux"

# Install only what is missing. Android SDK is expected to already exist if Cortex was built on-device before.
MISSING=()
command -v git >/dev/null || MISSING+=(git)
command -v java >/dev/null || MISSING+=(openjdk-17)
command -v gradle >/dev/null || MISSING+=(gradle)
if [ ${#MISSING[@]} -gt 0 ]; then
  log "Installing: ${MISSING[*]}"
  pkg update -y
  pkg install -y "${MISSING[@]}"
fi

# Resolve repository.
if [ ! -d "$REPO_DIR/.git" ]; then
  if [ -d "$HOME/Cortex/.git" ]; then REPO_DIR="$HOME/Cortex";
  elif [ -d "$HOME/cortex/.git" ]; then REPO_DIR="$HOME/cortex";
  else
    fail "Cortex repo not found. Set CORTEX_REPO_DIR or clone it to $HOME/Cortex first."
  fi
fi
cd "$REPO_DIR"

log "Updating Cortex source"
git fetch origin main
git checkout main
git pull --ff-only origin main

# Android SDK discovery.
if [ -z "${ANDROID_HOME:-}" ]; then
  for d in "$HOME/android-sdk" "$PREFIX/share/android-sdk" "$HOME/Android/Sdk"; do
    if [ -d "$d" ]; then export ANDROID_HOME="$d"; break; fi
  done
fi
[ -n "${ANDROID_HOME:-}" ] || fail "ANDROID_HOME is not set and Android SDK was not found."
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Java 17 discovery from Termux package when JAVA_HOME is unset.
if [ -z "${JAVA_HOME:-}" ]; then
  JBIN="$(readlink -f "$(command -v java)")"
  export JAVA_HOME="$(dirname "$(dirname "$JBIN")")"
fi

log "Java: $(java -version 2>&1 | head -n 1)"
log "Android SDK: $ANDROID_HOME"
log "Building Cortex v43"

if [ -x ./gradlew ]; then
  ./gradlew :app:assembleDebug --stacktrace
else
  gradle :app:assembleDebug --stacktrace
fi

[ -f "$APK_SRC" ] || fail "Build finished but APK was not found at $APK_SRC"

log "Copying APK to Downloads"
mkdir -p /sdcard/Download
cp -f "$APK_SRC" "$APK_OUT"
sha256sum "$APK_OUT" | tee "$APK_OUT.sha256"

log "Build complete"
printf 'APK: %s\n' "$APK_OUT"
printf 'Install with your preferred installer, or use adb/rish install -r if available.\n'
