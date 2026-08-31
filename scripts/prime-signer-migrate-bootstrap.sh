#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
ROOT="$HOME/.cortex-prime-signer-migration"
WORK="$ROOT/work"
PRIME_REF="prime/cortex-prime-v0"
CONTROL_REF="infra/termux-dev-bridge-v1"

fail(){ printf 'PRIME_SIGNER_BOOTSTRAP_FAILED=%s\n' "$*" >&2; exit 1; }

command -v git >/dev/null 2>&1 || fail GIT_NOT_FOUND
command -v rish >/dev/null 2>&1 || fail RISH_NOT_FOUND
rish -c 'id' >/dev/null 2>&1 || fail SHIZUKU_RISH_UNAVAILABLE
[ -d "$LOCAL_REPO/.git" ] || fail LOCAL_CORTEX_REPO_NOT_FOUND

mkdir -p "$ROOT"
git -C "$LOCAL_REPO" fetch origin "$PRIME_REF" "$CONTROL_REF"

if [ -e "$WORK" ]; then
  git -C "$LOCAL_REPO" worktree remove --force "$WORK" >/dev/null 2>&1 || true
  rm -rf "$WORK"
fi
git -C "$LOCAL_REPO" worktree add --detach "$WORK" "origin/$PRIME_REF"

if [ -f "$LOCAL_REPO/app/cortex-debug.keystore" ]; then
  mkdir -p "$WORK/app"
  cp -f "$LOCAL_REPO/app/cortex-debug.keystore" "$WORK/app/cortex-debug.keystore"
  chmod 600 "$WORK/app/cortex-debug.keystore" 2>/dev/null || true
  printf 'signer_overlay=present\n'
else
  printf 'signer_overlay=repo_fallback\n'
fi

if [ -z "${ANDROID_HOME:-}" ]; then
  for d in "$HOME/android-sdk" "$PREFIX/share/android-sdk" "$HOME/Android/Sdk"; do
    if [ -d "$d" ]; then export ANDROID_HOME="$d"; break; fi
  done
fi
[ -n "${ANDROID_HOME:-}" ] || fail ANDROID_HOME_NOT_FOUND
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ -z "${JAVA_HOME:-}" ] && command -v java >/dev/null 2>&1; then
  jbin="$(readlink -f "$(command -v java)")"
  export JAVA_HOME="$(dirname "$(dirname "$jbin")")"
fi
command -v java >/dev/null 2>&1 || fail JAVA_NOT_FOUND

if command -v aapt2 >/dev/null 2>&1; then
  mkdir -p "$HOME/.gradle"
  touch "$HOME/.gradle/gradle.properties"
  prop="android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
  if grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties"; then
    sed -i "s|^android.aapt2FromMavenOverride=.*|$prop|" "$HOME/.gradle/gradle.properties"
  else
    printf '%s\n' "$prop" >> "$HOME/.gradle/gradle.properties"
  fi
fi

cd "$WORK"
chmod +x ./gradlew scripts/prime-signer-migrate-v1.sh 2>/dev/null || true
./gradlew --no-daemon --console=plain :primeSignerMigrate

printf 'PRIME_SIGNER_BOOTSTRAP_STATUS=MIGRATION_TASK_SUCCESS\n'

if git -C "$LOCAL_REPO" fetch origin "$CONTROL_REF" >/dev/null 2>&1; then
  if git -C "$LOCAL_REPO" show "origin/$CONTROL_REF:scripts/devbridge-bootstrap-v2.sh" | bash; then
    printf 'DEVBRIDGE_RESTART=SUCCESS\n'
  else
    printf 'DEVBRIDGE_RESTART=FAILED_MIGRATION_STILL_COMPLETE\n'
  fi
fi

cd "$HOME"
git -C "$LOCAL_REPO" worktree remove --force "$WORK" >/dev/null 2>&1 || true
rm -rf "$WORK"
printf 'PRIME_SIGNER_BOOTSTRAP_STATUS=SUCCESS\n'
