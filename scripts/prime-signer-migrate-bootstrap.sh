#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
ROOT="$HOME/.cortex-prime-signer-migration"
WORK="$ROOT/work"
PRIME_REF="prime/cortex-prime-v0"
CONTROL_REF="infra/termux-dev-bridge-v1"
SIGN_ROOT="$HOME/.cortex-prime-signing"
KEYSTORE="$SIGN_ROOT/cortex-prime-permanent.p12"
CREDS="$SIGN_ROOT/credentials.env"
KEY_ALIAS="cortexprime"

fail(){ printf 'PRIME_SIGNER_BOOTSTRAP_FAILED=%s\n' "$*" >&2; exit 1; }

command -v git >/dev/null 2>&1 || fail GIT_NOT_FOUND
command -v rish >/dev/null 2>&1 || fail RISH_NOT_FOUND
command -v keytool >/dev/null 2>&1 || fail KEYTOOL_NOT_FOUND
command -v sha256sum >/dev/null 2>&1 || fail SHA256SUM_NOT_FOUND
rish -c 'id' >/dev/null 2>&1 || fail SHIZUKU_RISH_UNAVAILABLE
[ -d "$LOCAL_REPO/.git" ] || fail LOCAL_CORTEX_REPO_NOT_FOUND

umask 077
mkdir -p "$ROOT" "$SIGN_ROOT"
chmod 700 "$ROOT" "$SIGN_ROOT" 2>/dev/null || true

if [ -f "$KEYSTORE" ] && [ ! -f "$CREDS" ]; then
  fail SIGNER_EXISTS_BUT_CREDENTIALS_MISSING
fi

if [ ! -f "$KEYSTORE" ]; then
  [ ! -e "$CREDS" ] || fail CREDENTIALS_EXIST_WITHOUT_SIGNER
  PASS="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
  [ ${#PASS} -ge 32 ] || fail RANDOM_PASSWORD_GENERATION_FAILED
  keytool -genkeypair \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 36500 \
    -storetype PKCS12 \
    -keystore "$KEYSTORE" \
    -storepass "$PASS" \
    -keypass "$PASS" \
    -dname "CN=Cortex Prime Permanent,O=Cortex Prime,C=EG" \
    -noprompt >/dev/null 2>&1 || fail SIGNER_GENERATION_FAILED

  CERT_TMP="$SIGN_ROOT/cert.der"
  keytool -exportcert -alias "$KEY_ALIAS" -keystore "$KEYSTORE" -storepass "$PASS" -file "$CERT_TMP" >/dev/null 2>&1 || fail SIGNER_CERT_EXPORT_FAILED
  FP="$(sha256sum "$CERT_TMP" | awk '{print $1}' | tr 'A-F' 'a-f')"
  rm -f "$CERT_TMP"
  [ ${#FP} -eq 64 ] || fail SIGNER_FINGERPRINT_FAILED

  cat > "$CREDS" <<EOF
export CORTEX_PRIME_KEYSTORE='$KEYSTORE'
export CORTEX_PRIME_STORE_PASSWORD='$PASS'
export CORTEX_PRIME_KEY_ALIAS='$KEY_ALIAS'
export CORTEX_PRIME_KEY_PASSWORD='$PASS'
export CORTEX_PRIME_EXPECTED_SIGNER_SHA256='$FP'
EOF
  chmod 600 "$KEYSTORE" "$CREDS" 2>/dev/null || true
  printf 'secure_signer_created=true\n'
else
  printf 'secure_signer_created=false\n'
fi

# shellcheck disable=SC1090
source "$CREDS"
[ "${CORTEX_PRIME_KEYSTORE:-}" = "$KEYSTORE" ] || fail CREDENTIAL_PATH_MISMATCH
[ -f "$CORTEX_PRIME_KEYSTORE" ] || fail SECURE_SIGNER_NOT_FOUND

CERT_TMP="$SIGN_ROOT/cert.verify.der"
keytool -exportcert -alias "$CORTEX_PRIME_KEY_ALIAS" -keystore "$CORTEX_PRIME_KEYSTORE" -storepass "$CORTEX_PRIME_STORE_PASSWORD" -file "$CERT_TMP" >/dev/null 2>&1 || fail SECURE_SIGNER_VERIFY_EXPORT_FAILED
ACTUAL_FP="$(sha256sum "$CERT_TMP" | awk '{print $1}' | tr 'A-F' 'a-f')"
rm -f "$CERT_TMP"
[ "$ACTUAL_FP" = "$CORTEX_PRIME_EXPECTED_SIGNER_SHA256" ] || fail SECURE_SIGNER_FINGERPRINT_MISMATCH
printf 'secure_signer_sha256=%s\n' "$ACTUAL_FP"
printf 'secure_signer_storage=%s\n' "$SIGN_ROOT"

git -C "$LOCAL_REPO" fetch origin "$PRIME_REF" "$CONTROL_REF"

if [ -e "$WORK" ]; then
  git -C "$LOCAL_REPO" worktree remove --force "$WORK" >/dev/null 2>&1 || true
  rm -rf "$WORK"
fi
git -C "$LOCAL_REPO" worktree add --detach "$WORK" "origin/$PRIME_REF"

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
