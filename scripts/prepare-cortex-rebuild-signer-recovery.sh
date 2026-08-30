#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PKG="com.kareem.cortex.rebuild"
OLD_RUN53_FP="44b0180ba743460788171687f2dbcfb137c5e5a151f2783306747f84fbee39f4"
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
DEX="${CORTEX_RECOVERY_DEX:-$SELF_DIR/cortex-credential-bridge.dex}"
UNSIGNED="${CORTEX_UNSIGNED_APK:-$SELF_DIR/app-release-unsigned.apk}"
BACKUP_ROOT="${CORTEX_BACKUP_ROOT:-$HOME/.cortex-rebuild-migration}"
SIGN_ROOT="${CORTEX_SIGN_ROOT:-$HOME/.cortex-rebuild-signing}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$BACKUP_ROOT/$STAMP"

fail(){ echo "ERROR: $*" >&2; exit 1; }
need(){ command -v "$1" >/dev/null 2>&1; }
normalize_fp(){ printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -d ':\r\n '; }

[ -s "$DEX" ] || fail "Recovery dex missing: $DEX"
[ -s "$UNSIGNED" ] || fail "Unsigned Cortex 0.7.1 APK missing: $UNSIGNED"
need rish || fail "rish is required"
need python || { echo "Installing Python..."; pkg install -y python >/dev/null; }
need apksigner || { echo "Installing apksigner..."; pkg install -y apksigner >/dev/null; }
need openssl || { echo "Installing OpenSSL tools..."; pkg install -y openssl-tool >/dev/null; }

mkdir -p "$BACKUP_ROOT" "$SIGN_ROOT" "$BACKUP"
chmod 700 "$BACKUP_ROOT" "$SIGN_ROOT" "$BACKUP"

apk_fp_python(){
  python - "$1" <<'PY'
import hashlib, struct, sys
p=sys.argv[1]
d=open(p,'rb').read()
e=d.rfind(b'PK\x05\x06',max(0,len(d)-65557))
if e<0: raise SystemExit(2)
c=struct.unpack_from('<I',d,e+16)[0]
f=c-24
if f<0 or d[f+8:f+24]!=b'APK Sig Block 42': raise SystemExit(3)
sz=struct.unpack_from('<Q',d,f)[0]
s=c-(sz+8)
if s<0 or struct.unpack_from('<Q',d,s)[0]!=sz: raise SystemExit(4)
pairs={}; o=s+8
while o<f:
    n=struct.unpack_from('<Q',d,o)[0]; o+=8
    if n<4 or o+n>f: break
    i=struct.unpack_from('<I',d,o)[0]
    pairs[i]=d[o+4:o+n]; o+=n
def lp(b,p):
    n=struct.unpack_from('<I',b,p)[0]; p+=4
    if p+n>len(b): raise ValueError()
    return b[p:p+n],p+n
for i in (0x1b93ad61,0xf05368c0,0x7109871a):
    b=pairs.get(i)
    if not b: continue
    try:
        signer,_=lp(b,0); signed,_=lp(signer,0); _,q=lp(signed,0); certs,_=lp(signed,q); cert,_=lp(certs,0)
        print(hashlib.sha256(cert).hexdigest()); raise SystemExit(0)
    except (ValueError,struct.error): pass
raise SystemExit(5)
PY
}

installed_base(){
  local out base code
  out="$(rish -c "/system/bin/pm path $PKG" 2>/dev/null || true)"
  base="$(printf '%s\n' "$out" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
  [ -n "$base" ] && { printf '%s\n' "$base"; return 0; }
  out="$(rish -c "cmd package path $PKG" 2>/dev/null || true)"
  base="$(printf '%s\n' "$out" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
  [ -n "$base" ] && { printf '%s\n' "$base"; return 0; }
  code="$(rish -c "dumpsys package $PKG" 2>/dev/null | tr -d '\r' | sed -n 's/^[[:space:]]*codePath=//p' | head -n1)"
  [ -n "$code" ] && { printf '%s/base.apk\n' "${code%/}"; return 0; }
  return 1
}

BASE="$(installed_base || true)"
[ -n "$BASE" ] || fail "$PKG is not installed"
rish -c "/system/bin/cat '$BASE'" > "$BACKUP/installed-base.apk"
[ -s "$BACKUP/installed-base.apk" ] || fail "Could not copy installed APK"
OLD_FP="$(normalize_fp "$(apk_fp_python "$BACKUP/installed-base.apk")")"
[ -n "$OLD_FP" ] || fail "Could not inspect installed signer"

rish -c "dumpsys package $PKG" > "$BACKUP/package-dumpsys.txt"
VERSION="$(grep -m1 'versionName=' "$BACKUP/package-dumpsys.txt" | sed 's/.*versionName=//' | tr -d '\r')"
VERSION_CODE="$(grep -m1 'versionCode=' "$BACKUP/package-dumpsys.txt" | sed -E 's/.*versionCode=([0-9]+).*/\1/' | tr -d '\r')"

echo "===== CORTEX SIGNER PROVENANCE ====="
echo "Installed version: ${VERSION_CODE:-?} / ${VERSION:-?}"
echo "Installed signer: $OLD_FP"
if [ "$OLD_FP" = "$OLD_RUN53_FP" ]; then
  echo "Signer provenance: GitHub Actions run #53 disposable debug signer"
else
  echo "Signer provenance: not run #53 (backup still continues safely)"
fi

DATA_DIR="$(rish -c "run-as $PKG pwd" 2>/dev/null | tr -d '\r' | tail -n1)"
[ -n "$DATA_DIR" ] || DATA_DIR="/data/user/0/$PKG"

echo
echo "===== QUIESCE + SNAPSHOT CURRENT APP DATA ====="
rish -c "am force-stop $PKG"
REMOTE_TAR="$DATA_DIR/cache/cortex-signer-recovery.tar"
rish -c "run-as $PKG sh -c 'cd \"$DATA_DIR\" && toybox tar -cf \"$REMOTE_TAR\" databases shared_prefs files'"
rish -c "run-as $PKG /system/bin/cat '$REMOTE_TAR'" > "$BACKUP/app-data.tar"
rish -c "run-as $PKG rm -f '$REMOTE_TAR'" || true
[ -s "$BACKUP/app-data.tar" ] || fail "App-data snapshot is empty"
tar -tf "$BACKUP/app-data.tar" > "$BACKUP/app-data-files.txt"
grep -q '^databases/' "$BACKUP/app-data-files.txt" || fail "Database files missing from snapshot"
echo "Backup directory: $BACKUP"
echo "App-data bytes: $(wc -c < "$BACKUP/app-data.tar")"

echo
echo "===== EXPORT KEYSTORE CREDENTIALS ====="
REMOTE_DEX="$DATA_DIR/code_cache/cortex-credential-bridge.dex"
REMOTE_CREDS="$DATA_DIR/files/cortex-migration-credentials.properties"
REMOTE_LOG="$DATA_DIR/cache/cortex-credential-export.log"
CREDENTIAL_STATUS="unavailable"
CREDENTIAL_METHOD="none"
EXPORTED_COUNT="0"
GEMINI_PRESENT="0"
GROQ_PRESENT="0"

# Android 16 may abort one standalone ART launcher under run-as. Stage in code_cache and
# try dalvikvm first, then app_process. Failure is recorded but never invalidates the data backup.
rish -c "run-as $PKG sh -c 'cat > \"$REMOTE_DEX\"'" < "$DEX"
rish -c "run-as $PKG rm -f '$REMOTE_CREDS' '$REMOTE_LOG'" || true

try_export(){
  local method="$1" cmd="$2"
  rish -c "run-as $PKG sh -c 'rm -f \"$REMOTE_CREDS\" \"$REMOTE_LOG\"; $cmd > \"$REMOTE_LOG\" 2>&1'" >/dev/null 2>&1 || true
  if rish -c "run-as $PKG sh -c '[ -s \"$REMOTE_CREDS\" ]'" >/dev/null 2>&1; then
    rish -c "run-as $PKG /system/bin/cat '$REMOTE_CREDS'" > "$BACKUP/credentials.properties"
    chmod 600 "$BACKUP/credentials.properties"
    if grep -q '^format=CORTEX_REBUILD_CREDENTIALS_V1$' "$BACKUP/credentials.properties"; then
      CREDENTIAL_STATUS="exported"
      CREDENTIAL_METHOD="$method"
      return 0
    fi
  fi
  return 1
}

DALVIK_CMD="cd \"$DATA_DIR\" && /system/bin/dalvikvm -cp \"$REMOTE_DEX\" com.kareem.cortex.recovery.CortexCredentialBridge export \"$REMOTE_CREDS\""
APP_PROCESS_CMD="cd \"$DATA_DIR\" && CLASSPATH=\"$REMOTE_DEX\" /system/bin/app_process /system/bin com.kareem.cortex.recovery.CortexCredentialBridge export \"$REMOTE_CREDS\""

if try_export "dalvikvm" "$DALVIK_CMD"; then
  :
elif try_export "app_process" "$APP_PROCESS_CMD"; then
  :
else
  # Preserve diagnostics only; never print or copy decrypted secrets to the terminal.
  rish -c "run-as $PKG /system/bin/cat '$REMOTE_LOG'" > "$BACKUP/credential-export-diagnostic.txt" 2>/dev/null || true
  chmod 600 "$BACKUP/credential-export-diagnostic.txt" 2>/dev/null || true
  cat > "$BACKUP/credentials.properties" <<'EOF'
format=CORTEX_REBUILD_CREDENTIALS_V1_UNRECOVERED
gemini_api_key_present=unknown
groq_api_key_present=unknown
exported_count=0
EOF
  chmod 600 "$BACKUP/credentials.properties"
fi

if [ "$CREDENTIAL_STATUS" = "exported" ]; then
  GEMINI_PRESENT="$(sed -n 's/^gemini_api_key_present=//p' "$BACKUP/credentials.properties" | head -n1)"
  GROQ_PRESENT="$(sed -n 's/^groq_api_key_present=//p' "$BACKUP/credentials.properties" | head -n1)"
  EXPORTED_COUNT="$(sed -n 's/^exported_count=//p' "$BACKUP/credentials.properties" | head -n1)"
  echo "Credential export: SUCCESS via $CREDENTIAL_METHOD"
  echo "Gemini credential preserved: ${GEMINI_PRESENT:-0}"
  echo "Groq credential preserved: ${GROQ_PRESENT:-0}"
else
  echo "Credential export: UNAVAILABLE on this Android runtime"
  echo "Encrypted secure preferences remain preserved inside app-data.tar"
fi

rish -c "run-as $PKG rm -f '$REMOTE_CREDS' '$REMOTE_DEX' '$REMOTE_LOG'" || true

sha256sum "$BACKUP/installed-base.apk" "$BACKUP/app-data.tar" "$BACKUP/credentials.properties" > "$BACKUP/SHA256SUMS.txt"
chmod 600 "$BACKUP/SHA256SUMS.txt" "$BACKUP/package-dumpsys.txt" "$BACKUP/app-data-files.txt"

echo
echo "===== PREPARE NEW PERMANENT SIGNER (NOT INSTALLED) ====="
KEY_PEM="$SIGN_ROOT/cortex-rebuild-permanent-key.pem"
KEY_PK8="$SIGN_ROOT/cortex-rebuild-permanent-key.pk8"
CERT_PEM="$SIGN_ROOT/cortex-rebuild-permanent-cert.pem"
CERT_DER="$SIGN_ROOT/cortex-rebuild-permanent-cert.der"
P12="$SIGN_ROOT/cortex-rebuild-permanent.p12"
PASS_FILE="$SIGN_ROOT/cortex-rebuild-permanent.password"
STABLE_APK="$SIGN_ROOT/Cortex-0.7.1-stable-signed.apk"

if [ ! -s "$KEY_PK8" ] || [ ! -s "$CERT_DER" ] || [ ! -s "$P12" ] || [ ! -s "$PASS_FILE" ]; then
  if [ -e "$KEY_PEM" ] || [ -e "$KEY_PK8" ] || [ -e "$CERT_PEM" ] || [ -e "$CERT_DER" ] || [ -e "$P12" ] || [ -e "$PASS_FILE" ]; then
    fail "Partial permanent signer material already exists in $SIGN_ROOT; refusing regeneration"
  fi
  umask 077
  PASS="$(openssl rand -hex 32)"
  printf '%s\n' "$PASS" > "$PASS_FILE"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out "$KEY_PEM" >/dev/null 2>&1
  openssl req -new -x509 -sha256 -days 36500 -key "$KEY_PEM" -out "$CERT_PEM" \
    -subj "/CN=Cortex Rebuild Permanent/O=KAN1409/C=EG" >/dev/null 2>&1
  openssl pkcs8 -topk8 -nocrypt -in "$KEY_PEM" -outform DER -out "$KEY_PK8" >/dev/null 2>&1
  openssl x509 -in "$CERT_PEM" -outform DER -out "$CERT_DER"
  openssl pkcs12 -export -inkey "$KEY_PEM" -in "$CERT_PEM" -name cortexrebuild \
    -out "$P12" -passout "pass:$PASS" >/dev/null 2>&1
  chmod 600 "$KEY_PEM" "$KEY_PK8" "$CERT_PEM" "$CERT_DER" "$P12" "$PASS_FILE"
fi

NEW_FP="$(openssl x509 -in "$CERT_PEM" -outform DER | sha256sum | awk '{print $1}')"
apksigner sign --key "$KEY_PK8" --cert "$CERT_DER" --out "$STABLE_APK" "$UNSIGNED" >/dev/null
apksigner verify --print-certs "$STABLE_APK" >/dev/null
SIGNED_FP="$(apksigner verify --print-certs "$STABLE_APK" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1 | tr '[:upper:]' '[:lower:]' | tr -d ':\r\n ')"
[ "$SIGNED_FP" = "$NEW_FP" ] || fail "Stable APK signer verification failed"
chmod 600 "$STABLE_APK"

cat > "$BACKUP/RECOVERY_MANIFEST.txt" <<EOF
CORTEX_REBUILD_SIGNER_RECOVERY_V2
package=$PKG
installed_version_code=$VERSION_CODE
installed_version_name=$VERSION
installed_signer_sha256=$OLD_FP
installed_signer_is_run53=$([ "$OLD_FP" = "$OLD_RUN53_FP" ] && echo 1 || echo 0)
credential_export_status=$CREDENTIAL_STATUS
credential_export_method=$CREDENTIAL_METHOD
credentials_exported_count=${EXPORTED_COUNT:-0}
gemini_preserved=${GEMINI_PRESENT:-0}
groq_preserved=${GROQ_PRESENT:-0}
backup_path=$BACKUP
candidate_permanent_signer_sha256=$NEW_FP
candidate_signed_apk=$STABLE_APK
cutover_performed=0
EOF
chmod 600 "$BACKUP/RECOVERY_MANIFEST.txt"

echo "Candidate permanent signer: $NEW_FP"
echo "Candidate signed APK: $STABLE_APK"
echo
echo "CORTEX_SIGNER_RECOVERY_BACKUP_READY"
echo "Credential export status: $CREDENTIAL_STATUS"
echo "No uninstall, pm clear, install, package replacement, or database mutation was performed."
