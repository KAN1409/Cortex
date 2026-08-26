#!/usr/bin/env bash
set -euo pipefail

BRANCH="ui-v2-information-architecture"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

printf 'Cortex UI V2 build\n'
printf 'repo: %s\n' "$ROOT"
printf 'branch: %s\n' "$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
printf 'head: %s\n' "$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"

if command -v git >/dev/null 2>&1; then
  current="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
  if [[ -n "$current" && "$current" != "$BRANCH" ]]; then
    printf 'warning: expected branch %s, currently %s\n' "$BRANCH" "$current" >&2
  fi
fi

if command -v gradle >/dev/null 2>&1; then
  GRADLE=(gradle)
elif [[ -x ./gradlew ]]; then
  GRADLE=(./gradlew)
else
  printf 'error: Gradle not found. Install Gradle 8.9 or provide ./gradlew.\n' >&2
  exit 2
fi

printf 'running: %s :app:assembleDebug --stacktrace\n' "${GRADLE[*]}"
"${GRADLE[@]}" :app:assembleDebug --stacktrace

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  printf 'error: build completed but APK was not found at %s\n' "$APK" >&2
  exit 3
fi

printf '\nBUILD OK\nAPK: %s\n' "$ROOT/$APK"
if command -v sha256sum >/dev/null 2>&1; then sha256sum "$APK"; fi
